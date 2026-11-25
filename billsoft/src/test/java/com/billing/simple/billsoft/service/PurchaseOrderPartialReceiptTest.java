package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dtos.PoReceiptItemRequest;
import com.billing.simple.billsoft.dtos.PoReceiptRequest;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.repositories.*;
import com.billing.simple.billsoft.service.impl.PurchaseOrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PurchaseOrderPartialReceiptTest {

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
    private PurchaseOrderReceiptRepository receiptRepository;

    private PurchaseOrderServiceImpl poService;

    private final Long FIRM_ID = 1L;

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
                null
        );
        ReflectionTestUtils.setField(poService, "receiptRepository", receiptRepository);
    }

    @Test
    @DisplayName("Partial receipt of line item updates item receivedQty, records stock movement, and sets PO status to PARTIALLY_RECEIVED")
    void recordGoodsReceipt_partialReceipt_updatesStatusToPartiallyReceived() {
        Long poId = 100L;
        PurchaseOrder po = PurchaseOrder.builder()
                .id(poId)
                .firmId(FIRM_ID)
                .poNumber("PO-2026-001")
                .status(PurchaseOrderStatus.ISSUED)
                .party(Party.builder().id(50L).firmId(FIRM_ID).name("Vendor Alpha").build())
                .items(new ArrayList<>())
                .build();

        PurchaseOrderItem item1 = PurchaseOrderItem.builder()
                .id(1L)
                .purchaseOrder(po)
                .productId(201L)
                .productName("Widget A")
                .quantity(new BigDecimal("10.000"))
                .receivedQuantity(BigDecimal.ZERO)
                .unitPrice(new BigDecimal("100.00"))
                .build();
        po.getItems().add(item1);

        when(poRepository.findByIdAndFirmId(poId, FIRM_ID)).thenReturn(Optional.of(po));
        when(receiptRepository.countByFirmId(FIRM_ID)).thenReturn(0L);
        when(receiptRepository.save(any(PurchaseOrderReceipt.class))).thenAnswer(inv -> inv.getArgument(0));

        PoReceiptRequest request = PoReceiptRequest.builder()
                .receiptDate(LocalDate.now())
                .receivedBy("Warehouse Staff")
                .notes("Partial delivery 4 of 10")
                .items(Collections.singletonList(
                        PoReceiptItemRequest.builder()
                                .purchaseOrderItemId(1L)
                                .productId(201L)
                                .receivedQuantity(new BigDecimal("4.000"))
                                .build()
                ))
                .build();

        PurchaseOrderReceipt receipt = poService.recordGoodsReceipt(poId, FIRM_ID, request);

        assertNotNull(receipt);
        assertEquals("REC-PO-2026-001-001", receipt.getReceiptNumber());
        assertEquals(1, receipt.getItems().size());
        assertEquals(new BigDecimal("4.000"), receipt.getItems().get(0).getReceivedQuantity());

        // Item received quantity updated
        assertEquals(0, new BigDecimal("4.000").compareTo(item1.getReceivedQuantity()));
        assertEquals(0, new BigDecimal("6.000").compareTo(item1.getPendingQuantity()));

        // PO status transitioned to PARTIALLY_RECEIVED
        assertEquals(PurchaseOrderStatus.PARTIALLY_RECEIVED, po.getStatus());
        verify(poRepository).save(po);

        // Authoritative stock movement recorded for the received quantity only
        verify(productService).recordStockMovement(
                eq(201L),
                eq(FIRM_ID),
                eq("PURCHASE_RECEIPT"),
                eq(new BigDecimal("4.000")),
                eq("PURCHASE_ORDER"),
                eq("PO-2026-001"),
                contains("Receipt #REC-PO-2026-001-001")
        );
    }

    @Test
    @DisplayName("Second partial receipt fulfilling remainder sets PO status to RECEIVED")
    void recordGoodsReceipt_fullReceipt_updatesStatusToReceived() {
        Long poId = 100L;
        PurchaseOrder po = PurchaseOrder.builder()
                .id(poId)
                .firmId(FIRM_ID)
                .poNumber("PO-2026-001")
                .status(PurchaseOrderStatus.PARTIALLY_RECEIVED)
                .party(Party.builder().id(50L).firmId(FIRM_ID).name("Vendor Alpha").build())
                .items(new ArrayList<>())
                .build();

        PurchaseOrderItem item1 = PurchaseOrderItem.builder()
                .id(1L)
                .purchaseOrder(po)
                .productId(201L)
                .productName("Widget A")
                .quantity(new BigDecimal("10.000"))
                .receivedQuantity(new BigDecimal("4.000")) // 4 already received
                .unitPrice(new BigDecimal("100.00"))
                .build();
        po.getItems().add(item1);

        when(poRepository.findByIdAndFirmId(poId, FIRM_ID)).thenReturn(Optional.of(po));
        when(receiptRepository.countByFirmId(FIRM_ID)).thenReturn(1L);
        when(receiptRepository.save(any(PurchaseOrderReceipt.class))).thenAnswer(inv -> inv.getArgument(0));

        PoReceiptRequest request = PoReceiptRequest.builder()
                .receiptDate(LocalDate.now())
                .receivedBy("Warehouse Staff")
                .notes("Final delivery 6 of 10")
                .items(Collections.singletonList(
                        PoReceiptItemRequest.builder()
                                .purchaseOrderItemId(1L)
                                .productId(201L)
                                .receivedQuantity(new BigDecimal("6.000")) // Remaining 6
                                .build()
                ))
                .build();

        PurchaseOrderReceipt receipt = poService.recordGoodsReceipt(poId, FIRM_ID, request);

        assertNotNull(receipt);
        assertEquals(0, new BigDecimal("10.000").compareTo(item1.getReceivedQuantity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(item1.getPendingQuantity()));
        assertEquals(PurchaseOrderStatus.RECEIVED, po.getStatus());

        verify(productService).recordStockMovement(
                eq(201L),
                eq(FIRM_ID),
                eq("PURCHASE_RECEIPT"),
                eq(new BigDecimal("6.000")),
                eq("PURCHASE_ORDER"),
                eq("PO-2026-001"),
                anyString()
        );
    }

    @Test
    @DisplayName("Attempting to receive more than pending quantity throws IllegalArgumentException")
    void recordGoodsReceipt_overReceipt_throwsException() {
        Long poId = 100L;
        PurchaseOrder po = PurchaseOrder.builder()
                .id(poId)
                .firmId(FIRM_ID)
                .poNumber("PO-2026-001")
                .status(PurchaseOrderStatus.PARTIALLY_RECEIVED)
                .items(new ArrayList<>())
                .build();

        PurchaseOrderItem item1 = PurchaseOrderItem.builder()
                .id(1L)
                .purchaseOrder(po)
                .productId(201L)
                .productName("Widget A")
                .quantity(new BigDecimal("10.000"))
                .receivedQuantity(new BigDecimal("8.000")) // 2 pending
                .build();
        po.getItems().add(item1);

        when(poRepository.findByIdAndFirmId(poId, FIRM_ID)).thenReturn(Optional.of(po));

        PoReceiptRequest request = PoReceiptRequest.builder()
                .items(Collections.singletonList(
                        PoReceiptItemRequest.builder()
                                .purchaseOrderItemId(1L)
                                .receivedQuantity(new BigDecimal("3.000")) // Exceeds pending (2)
                                .build()
                ))
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                poService.recordGoodsReceipt(poId, FIRM_ID, request));

        assertTrue(ex.getMessage().contains("exceeds remaining pending quantity"));
        verifyNoInteractions(productService);
    }

    @Test
    @DisplayName("Idempotency: Re-submitting with same idempotency key returns existing receipt without duplicate stock movements")
    void recordGoodsReceipt_idempotency_returnsExistingReceiptWithoutDuplicateMovement() {
        Long poId = 100L;
        String idempKey = "REC_CLIENT_ID_999";

        PurchaseOrderReceipt existingReceipt = PurchaseOrderReceipt.builder()
                .id(55L)
                .firmId(FIRM_ID)
                .purchaseOrderId(poId)
                .receiptNumber("REC-PO-2026-001-001")
                .idempotencyKey(idempKey)
                .build();

        when(receiptRepository.findByIdempotencyKeyAndFirmId(idempKey, FIRM_ID))
                .thenReturn(Optional.of(existingReceipt));

        PoReceiptRequest request = PoReceiptRequest.builder()
                .idempotencyKey(idempKey)
                .items(Collections.singletonList(
                        PoReceiptItemRequest.builder().productId(201L).receivedQuantity(new BigDecimal("5")).build()
                ))
                .build();

        PurchaseOrderReceipt returned = poService.recordGoodsReceipt(poId, FIRM_ID, request);

        assertSame(existingReceipt, returned);
        verifyNoInteractions(productService);
        verify(poRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deleting a PO that has recorded receipts is strictly prohibited to preserve audit trail")
    void deletePurchaseOrder_withExistingReceipts_isRestricted() {
        Long poId = 100L;
        PurchaseOrder po = PurchaseOrder.builder()
                .id(poId)
                .firmId(FIRM_ID)
                .poNumber("PO-2026-001")
                .build();

        when(poRepository.findByIdAndFirmId(poId, FIRM_ID)).thenReturn(Optional.of(po));
        when(receiptRepository.findByPurchaseOrderIdAndFirmIdOrderByReceiptDateDescCreatedAtDesc(poId, FIRM_ID))
                .thenReturn(Collections.singletonList(PurchaseOrderReceipt.builder().id(10L).build()));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                poService.deletePurchaseOrder(poId, FIRM_ID));

        assertTrue(ex.getMessage().contains("recorded goods receipt(s)"));
        verify(poRepository, never()).delete(any());
    }
}
