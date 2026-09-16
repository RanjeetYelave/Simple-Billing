package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dtos.BatchPurchaseOrderRequest;
import com.billing.simple.billsoft.dtos.PreferredVendorUpdate;
import com.billing.simple.billsoft.dtos.ProductVendorHistoryDto;
import com.billing.simple.billsoft.entities.Party;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.entities.PurchaseOrder;
import com.billing.simple.billsoft.entities.PurchaseOrderItem;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.repositories.PartyRepository;
import com.billing.simple.billsoft.repositories.PurchaseOrderItemRepository;
import com.billing.simple.billsoft.repositories.PurchaseOrderRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.service.impl.PurchaseOrderServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PurchaseOrderBatchTest {

    @Mock
    private PurchaseOrderRepository poRepository;

    @Mock
    private PurchaseOrderItemRepository poItemRepository;

    @Mock
    private PartyRepository partyRepository;

    @Mock
    private com.billing.simple.billsoft.repositories.PartyPaymentRepository partyPaymentRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private PurchaseOrderPdfService pdfService;

    @Mock
    private ProductService productService;

    @InjectMocks
    private PurchaseOrderServiceImpl poService;

    private AutoCloseable mocks;
    private final Long firmId = 1L;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        TenantContext.setCurrentFirmId(firmId);
    }

    @AfterEach
    void tearDown() throws Exception {
        TenantContext.clear();
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    void testCreatePurchaseOrdersBatch_Success() {
        Party party1 = Party.builder().id(10L).name("Vendor One").firmId(firmId).build();
        Party party2 = Party.builder().id(20L).name("Vendor Two").firmId(firmId).build();

        when(partyRepository.findByIdAndFirmId(10L, firmId)).thenReturn(Optional.of(party1));
        when(partyRepository.findByIdAndFirmId(20L, firmId)).thenReturn(Optional.of(party2));
        when(partyRepository.existsByIdAndFirmId(any(), eq(firmId))).thenReturn(true);
        when(productRepository.existsByIdAndFirmId(any(), eq(firmId))).thenReturn(true);
        when(poRepository.countByFirmId(firmId)).thenReturn(0L, 1L);
        when(poRepository.save(any(PurchaseOrder.class))).thenAnswer(i -> {
            PurchaseOrder po = i.getArgument(0);
            po.setId(100L + (long) (Math.random() * 100));
            return po;
        });

        PurchaseOrder po1 = PurchaseOrder.builder()
                .party(Party.builder().id(10L).build())
                .poDate(LocalDate.now())
                .hidePricesOnPo(false)
                .items(new ArrayList<>(List.of(
                        PurchaseOrderItem.builder().productId(1L).productName("Item 1").quantity(new BigDecimal("5.000")).unitPrice(new BigDecimal("100.00")).gstPercent(new BigDecimal("18.00")).build()
                )))
                .build();

        PurchaseOrder po2 = PurchaseOrder.builder()
                .party(Party.builder().id(20L).build())
                .poDate(LocalDate.now())
                .hidePricesOnPo(true)
                .items(new ArrayList<>(List.of(
                        PurchaseOrderItem.builder().productId(2L).productName("Item 2").quantity(new BigDecimal("10.000")).unitPrice(new BigDecimal("50.00")).gstPercent(new BigDecimal("18.00")).build()
                )))
                .build();

        BatchPurchaseOrderRequest request = BatchPurchaseOrderRequest.builder()
                .clientRequestId("test_req_" + System.currentTimeMillis())
                .orders(List.of(po1, po2))
                .updatePreferredVendors(List.of(new PreferredVendorUpdate(1L, 10L)))
                .build();

        List<PurchaseOrder> created = poService.createPurchaseOrdersBatch(request, firmId);

        assertNotNull(created);
        assertEquals(2, created.size());
        verify(poRepository, times(2)).save(any(PurchaseOrder.class));
        verify(productRepository, times(1)).updatePreferredPartyId(1L, 10L, firmId);
    }

    @Test
    void testCreatePurchaseOrdersBatch_DuplicateSubmission() {
        String reqId = "dup_req_" + System.currentTimeMillis();
        Party party1 = Party.builder().id(10L).name("Vendor One").firmId(firmId).build();
        when(partyRepository.findByIdAndFirmId(10L, firmId)).thenReturn(Optional.of(party1));
        when(poRepository.save(any(PurchaseOrder.class))).thenAnswer(i -> i.getArgument(0));

        PurchaseOrder po1 = PurchaseOrder.builder()
                .party(Party.builder().id(10L).build())
                .poDate(LocalDate.now())
                .items(new ArrayList<>())
                .build();

        BatchPurchaseOrderRequest request = BatchPurchaseOrderRequest.builder()
                .clientRequestId(reqId)
                .orders(List.of(po1))
                .build();

        // First call succeeds
        poService.createPurchaseOrdersBatch(request, firmId);

        // Immediate second call with identical clientRequestId throws IllegalStateException
        assertThrows(IllegalStateException.class, () -> poService.createPurchaseOrdersBatch(request, firmId));
    }

    @Test
    void testGetProductVendorHistory() {
        Object[] row1 = new Object[]{1L, 10L, "Vendor One", "PO-2026-0001", LocalDate.of(2026, 9, 15), new BigDecimal("10.000")};
        Object[] row2 = new Object[]{1L, 15L, "Old Vendor", "PO-2026-0000", LocalDate.of(2026, 8, 1), new BigDecimal("5.000")};
        Object[] row3 = new Object[]{2L, 20L, "Vendor Two", "PO-2026-0002", LocalDate.of(2026, 9, 16), new BigDecimal("20.000")};

        when(poRepository.findProductVendorHistoryRaw(firmId)).thenReturn(List.of(row1, row2, row3));

        Map<Long, ProductVendorHistoryDto> history = poService.getProductVendorHistory(firmId);

        assertNotNull(history);
        assertEquals(2, history.size());
        // For product 1, row1 was the latest (ordered by date desc)
        assertEquals(10L, history.get(1L).getLatestPartyId());
        assertEquals("Vendor One", history.get(1L).getLatestPartyName());
        assertEquals(new BigDecimal("15.000"), history.get(1L).getTotalQuantityOrdered());

        // For product 2
        assertEquals(20L, history.get(2L).getLatestPartyId());
        assertEquals("Vendor Two", history.get(2L).getLatestPartyName());
    }
}
