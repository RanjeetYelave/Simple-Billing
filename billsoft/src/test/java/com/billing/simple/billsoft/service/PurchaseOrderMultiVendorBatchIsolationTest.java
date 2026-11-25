package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dtos.BatchPurchaseOrderRequest;
import com.billing.simple.billsoft.dtos.BatchPurchaseOrderResponse;
import com.billing.simple.billsoft.dtos.BatchPurchaseOrderResult;
import com.billing.simple.billsoft.entities.Party;
import com.billing.simple.billsoft.entities.PurchaseOrder;
import com.billing.simple.billsoft.entities.PurchaseOrderItem;
import com.billing.simple.billsoft.entities.PurchaseOrderStatus;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.repositories.PartyPaymentRepository;
import com.billing.simple.billsoft.repositories.PartyRepository;
import com.billing.simple.billsoft.repositories.PurchaseOrderBatchRecordRepository;
import com.billing.simple.billsoft.repositories.PurchaseOrderItemRepository;
import com.billing.simple.billsoft.repositories.PurchaseOrderRepository;
import com.billing.simple.billsoft.service.impl.PurchaseOrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PurchaseOrderMultiVendorBatchIsolationTest {

    @Mock
    private PurchaseOrderRepository poRepository;
    @Mock
    private PurchaseOrderItemRepository poItemRepository;
    @Mock
    private PartyRepository partyRepository;
    @Mock
    private PartyPaymentRepository partyPaymentRepository;
    @Mock
    private PurchaseOrderPdfService pdfService;
    @Mock
    private ProductService productService;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private DocumentSequenceService sequenceService;
    @Mock
    private PurchaseOrderBatchRecordRepository batchRecordRepository;
    @Mock
    private PurchaseOrderSupplierTxService supplierTxService;

    private PurchaseOrderServiceImpl poService;
    private final Long firmId = 33L;

    @BeforeEach
    void setUp() {
        poService = new PurchaseOrderServiceImpl(
                poRepository,
                poItemRepository,
                partyRepository,
                partyPaymentRepository,
                pdfService,
                productService,
                productRepository,
                sequenceService
        );
        ReflectionTestUtils.setField(poService, "batchRecordRepository", batchRecordRepository);
        ReflectionTestUtils.setField(poService, "supplierTxService", supplierTxService);
    }

    @Test
    @DisplayName("Multi-vendor batch PO: Supplier B failure does not abort Supplier A and C; failures recorded independently")
    void testIndependentSupplierTransactionsAndFailureIsolation() {
        String clientRequestId = "batch_test_req_" + System.currentTimeMillis();

        Party partyA = Party.builder().id(101L).name("Supplier Alpha").firmId(firmId).build();
        Party partyB = Party.builder().id(102L).name("Supplier Beta").firmId(firmId).build();
        Party partyC = Party.builder().id(103L).name("Supplier Gamma").firmId(firmId).build();

        PurchaseOrder poA = PurchaseOrder.builder().party(Party.builder().id(101L).build()).poDate(LocalDate.now()).items(new ArrayList<>()).build();
        PurchaseOrder poB = PurchaseOrder.builder().party(Party.builder().id(102L).build()).poDate(LocalDate.now()).items(new ArrayList<>()).build();
        PurchaseOrder poC = PurchaseOrder.builder().party(Party.builder().id(103L).build()).poDate(LocalDate.now()).items(new ArrayList<>()).build();

        PurchaseOrder committedA = PurchaseOrder.builder().id(1001L).firmId(firmId).poNumber("PO-2026-0001").party(partyA).partyName(partyA.getName()).status(PurchaseOrderStatus.ISSUED).build();
        PurchaseOrder committedC = PurchaseOrder.builder().id(1003L).firmId(firmId).poNumber("PO-2026-0003").party(partyC).partyName(partyC.getName()).status(PurchaseOrderStatus.ISSUED).build();

        when(supplierTxService.createSupplierPoInNewTransaction(eq(poA), eq(firmId))).thenReturn(committedA);
        when(supplierTxService.createSupplierPoInNewTransaction(eq(poB), eq(firmId)))
                .thenThrow(new IllegalArgumentException("Credit limit exceeded for Supplier Beta"));
        when(supplierTxService.createSupplierPoInNewTransaction(eq(poC), eq(firmId))).thenReturn(committedC);

        BatchPurchaseOrderRequest request = BatchPurchaseOrderRequest.builder()
                .clientRequestId(clientRequestId)
                .firmId(firmId)
                .orders(List.of(poA, poB, poC))
                .build();

        BatchPurchaseOrderResult result = poService.createPurchaseOrdersBatch(request, firmId);

        assertNotNull(result);
        assertEquals(2, result.getSuccessfulCount(), "Supplier A and Supplier C should be successfully committed");
        assertEquals(1, result.getFailedCount(), "Supplier B should be recorded as failed");
        assertTrue(result.hasFailures());

        assertEquals("PO-2026-0001", result.get(0).getPoNumber());
        assertEquals("PO-2026-0003", result.get(1).getPoNumber());

        assertEquals(102L, result.getFailedOrders().get(0).getPartyId());
        assertTrue(result.getFailedOrders().get(0).getErrorMessage().contains("Credit limit exceeded"));

        BatchPurchaseOrderResponse responseDto = result.toResponseDto();
        assertEquals(2, responseDto.getSuccessfulCount());
        assertEquals(1, responseDto.getFailedCount());
        assertEquals(3, responseDto.getTotalOrders());
    }

    @Test
    @DisplayName("Durable idempotency: Retrying batch reuses already committed POs without re-creating them")
    void testDurableIdempotencyOnRetry() {
        String clientRequestId = "retry_req_" + System.currentTimeMillis();
        String supplierAOpId = clientRequestId + "_ord0_party_101";

        com.billing.simple.billsoft.entities.PurchaseOrderBatchRecord existingRecordA =
                com.billing.simple.billsoft.entities.PurchaseOrderBatchRecord.builder()
                        .firmId(firmId)
                        .clientRequestId(clientRequestId)
                        .supplierOpId(supplierAOpId)
                        .partyId(101L)
                        .partyName("Supplier Alpha")
                        .status("SUCCESS")
                        .purchaseOrderId(1001L)
                        .poNumber("PO-2026-0001")
                        .build();

        when(batchRecordRepository.findByFirmIdAndSupplierOpId(firmId, supplierAOpId))
                .thenReturn(Optional.of(existingRecordA));

        PurchaseOrder existingPoA = PurchaseOrder.builder()
                .id(1001L)
                .firmId(firmId)
                .poNumber("PO-2026-0001")
                .party(Party.builder().id(101L).name("Supplier Alpha").build())
                .status(PurchaseOrderStatus.ISSUED)
                .build();

        when(poRepository.findByIdAndFirmId(1001L, firmId)).thenReturn(Optional.of(existingPoA));

        // Supplier B was previously failed, so it will be retried and succeeds this time
        Party partyB = Party.builder().id(102L).name("Supplier Beta").firmId(firmId).build();
        PurchaseOrder poB = PurchaseOrder.builder().party(Party.builder().id(102L).build()).poDate(LocalDate.now()).build();
        PurchaseOrder committedB = PurchaseOrder.builder().id(1002L).firmId(firmId).poNumber("PO-2026-0002").party(partyB).partyName(partyB.getName()).status(PurchaseOrderStatus.ISSUED).build();

        when(supplierTxService.createSupplierPoInNewTransaction(eq(poB), eq(firmId))).thenReturn(committedB);

        PurchaseOrder poA = PurchaseOrder.builder().party(Party.builder().id(101L).build()).poDate(LocalDate.now()).build();

        BatchPurchaseOrderRequest retryRequest = BatchPurchaseOrderRequest.builder()
                .clientRequestId(clientRequestId)
                .firmId(firmId)
                .retry(true)
                .orders(List.of(poA, poB))
                .build();

        BatchPurchaseOrderResult result = poService.createPurchaseOrdersBatch(retryRequest, firmId);

        assertEquals(2, result.getSuccessfulCount());
        assertEquals(0, result.getFailedCount());
        assertFalse(result.hasFailures());

        // Supplier A was NOT re-created; existing PO was reused
        verify(supplierTxService, never()).createSupplierPoInNewTransaction(eq(poA), eq(firmId));
        // Supplier B was processed
        verify(supplierTxService, times(1)).createSupplierPoInNewTransaction(eq(poB), eq(firmId));
    }
}
