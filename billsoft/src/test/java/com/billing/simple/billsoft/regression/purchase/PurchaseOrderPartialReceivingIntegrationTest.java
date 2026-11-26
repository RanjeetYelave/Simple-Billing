package com.billing.simple.billsoft.regression.purchase;

import com.billing.simple.billsoft.dtos.PoReceiptItemRequest;
import com.billing.simple.billsoft.dtos.PoReceiptRequest;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;
import com.billing.simple.billsoft.repositories.PurchaseOrderReceiptRepository;
import com.billing.simple.billsoft.repositories.PurchaseOrderRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import com.billing.simple.billsoft.service.PartyService;
import com.billing.simple.billsoft.service.ProductService;
import com.billing.simple.billsoft.service.PurchaseOrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Tag("regression")
@Tag("integration")
@DisplayName("PO Partial Receiving, Multi-Receipt Lifecycle & Stock Integrity Tests")
class PurchaseOrderPartialReceivingIntegrationTest {

        @Autowired
        private PurchaseOrderService poService;

        @Autowired
        private PurchaseOrderRepository poRepository;

        @Autowired
        private PurchaseOrderReceiptRepository receiptRepository;

        @Autowired
        private ProductService productService;

        @Autowired
        private PartyService partyService;

        @Autowired
        private FirmDetailsRepository firmRepo;

        private Long firmAId;
        private Long firmBId;
        private Party vendorA;
        private Product productA1;
        private Product productA2;

        @BeforeEach
        void setUp() {
                FirmDetails firmA = new FirmDetails();
                firmA.setFirmName("Firm A " + System.nanoTime());
                firmA = firmRepo.save(firmA);
                firmAId = firmA.getId();

                FirmDetails firmB = new FirmDetails();
                firmB.setFirmName("Firm B " + System.nanoTime());
                firmB = firmRepo.save(firmB);
                firmBId = firmB.getId();

                TenantContext.setCurrentFirmId(firmAId);

                vendorA = partyService.createParty(Party.builder()
                                .name("Supplier Alpha")
                                .phone("9800112233")
                                .firmId(firmAId)
                                .build());

                productA1 = productService.create(Product.builder()
                                .name("Industrial Valve 50mm")
                                .price(BigDecimal.valueOf(500.00))
                                .costPrice(BigDecimal.valueOf(350.00))
                                .stockQuantity(BigDecimal.ZERO)
                                .firmId(firmAId)
                                .itemType("GOODS")
                                .unit("pcs")
                                .build());

                productA2 = productService.create(Product.builder()
                                .name("Flange Gasket 50mm")
                                .price(BigDecimal.valueOf(100.00))
                                .costPrice(BigDecimal.valueOf(60.00))
                                .stockQuantity(BigDecimal.ZERO)
                                .firmId(firmAId)
                                .itemType("GOODS")
                                .unit("pcs")
                                .build());
        }

        @AfterEach
        void tearDown() {
                TenantContext.clear();
        }

        @Test
        @DisplayName("Complete partial receiving sequence: 40 -> 70 -> 100 with stock movement reconciliation")
        void testPartialReceivingSequence_40_70_100() {
                // 1. Create PO with 100 units
                PurchaseOrder po = PurchaseOrder.builder()
                                .party(vendorA)
                                .poDate(LocalDate.now())
                                .firmId(firmAId)
                                .status(PurchaseOrderStatus.ISSUED)
                                .build();

                PurchaseOrderItem item = PurchaseOrderItem.builder()
                                .productId(productA1.getId())
                                .productName(productA1.getName())
                                .quantity(BigDecimal.valueOf(100.0))
                                .unitPrice(BigDecimal.valueOf(350.00))
                                .build();

                po.setItems(new ArrayList<>(List.of(item)));
                PurchaseOrder created = poService.createPurchaseOrder(po);
                Long poId = created.getId();
                Long itemId = created.getItems().get(0).getId();

                assertThat(created.getStatus()).isEqualTo(PurchaseOrderStatus.ISSUED);
                assertThat(productService.getById(productA1.getId()).getStockQuantity()).isEqualByComparingTo("0.000");

                // 2. First Receipt: 40 units
                PoReceiptRequest req1 = PoReceiptRequest.builder()
                                .idempotencyKey("REC-STEP-1-" + System.nanoTime())
                                .receivedBy("Receiver 1")
                                .notes("Batch 1 delivery")
                                .receiptDate(LocalDate.now())
                                .items(List.of(PoReceiptItemRequest.builder()
                                                .purchaseOrderItemId(itemId)
                                                .productId(productA1.getId())
                                                .receivedQuantity(BigDecimal.valueOf(40.0))
                                                .build()))
                                .build();

                PurchaseOrderReceipt receipt1 = poService.recordGoodsReceipt(poId, firmAId, req1);
                assertThat(receipt1).isNotNull();

                PurchaseOrder poAfter1 = poService.getPurchaseOrderById(poId, firmAId).orElseThrow();
                assertThat(poAfter1.getStatus()).isEqualTo(PurchaseOrderStatus.PARTIALLY_RECEIVED);
                assertThat(poAfter1.getItems().get(0).getReceivedQuantity()).isEqualByComparingTo("40.000");
                assertThat(poAfter1.getItems().get(0).getPendingQuantity()).isEqualByComparingTo("60.000");
                assertThat(productService.getById(productA1.getId()).getStockQuantity()).isEqualByComparingTo("40.000");

                // 3. Second Receipt: 30 units (cumulative 70)
                PoReceiptRequest req2 = PoReceiptRequest.builder()
                                .idempotencyKey("REC-STEP-2-" + System.nanoTime())
                                .receivedBy("Receiver 2")
                                .notes("Batch 2 delivery")
                                .receiptDate(LocalDate.now().plusDays(1))
                                .items(List.of(PoReceiptItemRequest.builder()
                                                .purchaseOrderItemId(itemId)
                                                .productId(productA1.getId())
                                                .receivedQuantity(BigDecimal.valueOf(30.0))
                                                .build()))
                                .build();

                PurchaseOrderReceipt receipt2 = poService.recordGoodsReceipt(poId, firmAId, req2);
                assertThat(receipt2).isNotNull();

                PurchaseOrder poAfter2 = poService.getPurchaseOrderById(poId, firmAId).orElseThrow();
                assertThat(poAfter2.getStatus()).isEqualTo(PurchaseOrderStatus.PARTIALLY_RECEIVED);
                assertThat(poAfter2.getItems().get(0).getReceivedQuantity()).isEqualByComparingTo("70.000");
                assertThat(poAfter2.getItems().get(0).getPendingQuantity()).isEqualByComparingTo("30.000");
                assertThat(productService.getById(productA1.getId()).getStockQuantity()).isEqualByComparingTo("70.000");

                // 4. Third Receipt: remaining 30 units (cumulative 100) -> Transitions to
                // RECEIVED
                PoReceiptRequest req3 = PoReceiptRequest.builder()
                                .idempotencyKey("REC-STEP-3-" + System.nanoTime())
                                .receivedBy("Receiver 3")
                                .notes("Final delivery")
                                .receiptDate(LocalDate.now().plusDays(2))
                                .items(List.of(PoReceiptItemRequest.builder()
                                                .purchaseOrderItemId(itemId)
                                                .productId(productA1.getId())
                                                .receivedQuantity(BigDecimal.valueOf(30.0))
                                                .build()))
                                .build();

                PurchaseOrderReceipt receipt3 = poService.recordGoodsReceipt(poId, firmAId, req3);
                assertThat(receipt3).isNotNull();

                PurchaseOrder poAfter3 = poService.getPurchaseOrderById(poId, firmAId).orElseThrow();
                assertThat(poAfter3.getStatus()).isEqualTo(PurchaseOrderStatus.RECEIVED);
                assertThat(poAfter3.getItems().get(0).getReceivedQuantity()).isEqualByComparingTo("100.000");
                assertThat(poAfter3.getItems().get(0).getPendingQuantity()).isEqualByComparingTo("0.000");
                assertThat(productService.getById(productA1.getId()).getStockQuantity())
                                .isEqualByComparingTo("100.000");

                // 5. Verify Receipts list
                List<PurchaseOrderReceipt> receipts = poService.getReceiptsForPurchaseOrder(poId, firmAId);
                assertThat(receipts).hasSize(3);
        }

        @Test
        @DisplayName("Over-receiving beyond pending quantity is strictly rejected without modifying stock")
        void testOverReceivingRejected() {
                PurchaseOrder po = PurchaseOrder.builder()
                                .party(vendorA)
                                .poDate(LocalDate.now())
                                .firmId(firmAId)
                                .status(PurchaseOrderStatus.ISSUED)
                                .build();

                PurchaseOrderItem item = PurchaseOrderItem.builder()
                                .productId(productA1.getId())
                                .productName(productA1.getName())
                                .quantity(BigDecimal.valueOf(50.0))
                                .unitPrice(BigDecimal.valueOf(350.00))
                                .build();

                po.setItems(new ArrayList<>(List.of(item)));
                PurchaseOrder created = poService.createPurchaseOrder(po);
                Long poId = created.getId();
                Long itemId = created.getItems().get(0).getId();

                // Receive 40
                PoReceiptRequest req1 = PoReceiptRequest.builder()
                                .items(List.of(PoReceiptItemRequest.builder()
                                                .purchaseOrderItemId(itemId)
                                                .productId(productA1.getId())
                                                .receivedQuantity(BigDecimal.valueOf(40.0))
                                                .build()))
                                .build();
                poService.recordGoodsReceipt(poId, firmAId, req1);

                // Attempt to receive 15 (pending is only 10)
                PoReceiptRequest reqOver = PoReceiptRequest.builder()
                                .items(List.of(PoReceiptItemRequest.builder()
                                                .purchaseOrderItemId(itemId)
                                                .productId(productA1.getId())
                                                .receivedQuantity(BigDecimal.valueOf(15.0))
                                                .build()))
                                .build();

                IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                                () -> poService.recordGoodsReceipt(poId, firmAId, reqOver));
                assertThat(ex.getMessage()).contains("exceeds remaining pending quantity");

                // Stock remains 40
                assertThat(productService.getById(productA1.getId()).getStockQuantity()).isEqualByComparingTo("40.000");
        }

        @Test
        @DisplayName("Idempotent retry returns existing receipt and prevents duplicate stock addition")
        void testIdempotentRetryPreventsDuplicateStock() {
                PurchaseOrder po = PurchaseOrder.builder()
                                .party(vendorA)
                                .poDate(LocalDate.now())
                                .firmId(firmAId)
                                .status(PurchaseOrderStatus.ISSUED)
                                .build();

                PurchaseOrderItem item = PurchaseOrderItem.builder()
                                .productId(productA1.getId())
                                .productName(productA1.getName())
                                .quantity(BigDecimal.valueOf(50.0))
                                .unitPrice(BigDecimal.valueOf(350.00))
                                .build();

                po.setItems(new ArrayList<>(List.of(item)));
                PurchaseOrder created = poService.createPurchaseOrder(po);
                Long poId = created.getId();
                Long itemId = created.getItems().get(0).getId();

                String idempKey = "CLIENT-RECEIPT-KEY-12345";
                PoReceiptRequest req = PoReceiptRequest.builder()
                                .idempotencyKey(idempKey)
                                .items(List.of(PoReceiptItemRequest.builder()
                                                .purchaseOrderItemId(itemId)
                                                .productId(productA1.getId())
                                                .receivedQuantity(BigDecimal.valueOf(25.0))
                                                .build()))
                                .build();

                PurchaseOrderReceipt firstReceipt = poService.recordGoodsReceipt(poId, firmAId, req);
                assertThat(firstReceipt).isNotNull();
                assertThat(productService.getById(productA1.getId()).getStockQuantity()).isEqualByComparingTo("25.000");

                // Retry same request
                PurchaseOrderReceipt secondReceipt = poService.recordGoodsReceipt(poId, firmAId, req);
                assertThat(secondReceipt.getId()).isEqualTo(firstReceipt.getId());
                // Stock must still be 25.000, NOT 50.000!
                assertThat(productService.getById(productA1.getId()).getStockQuantity()).isEqualByComparingTo("25.000");
        }

        @Test
        @DisplayName("Marking partially-received PO as RECEIVED intakes only the unreceived quantity")
        void testMarkAsReceivedOnlyIntakesRemainingQuantity() {
                PurchaseOrder po = PurchaseOrder.builder()
                                .party(vendorA)
                                .poDate(LocalDate.now())
                                .firmId(firmAId)
                                .status(PurchaseOrderStatus.ISSUED)
                                .build();

                PurchaseOrderItem item = PurchaseOrderItem.builder()
                                .productId(productA1.getId())
                                .productName(productA1.getName())
                                .quantity(BigDecimal.valueOf(100.0))
                                .unitPrice(BigDecimal.valueOf(350.00))
                                .build();

                po.setItems(new ArrayList<>(List.of(item)));
                PurchaseOrder created = poService.createPurchaseOrder(po);
                Long poId = created.getId();
                Long itemId = created.getItems().get(0).getId();

                // 1. Partially receive 60 units
                PoReceiptRequest req = PoReceiptRequest.builder()
                                .items(List.of(PoReceiptItemRequest.builder()
                                                .purchaseOrderItemId(itemId)
                                                .productId(productA1.getId())
                                                .receivedQuantity(BigDecimal.valueOf(60.0))
                                                .build()))
                                .build();
                poService.recordGoodsReceipt(poId, firmAId, req);
                assertThat(productService.getById(productA1.getId()).getStockQuantity()).isEqualByComparingTo("60.000");

                // 2. User marks PO as RECEIVED directly (e.g. from status dropdown)
                poService.updateStatus(poId, firmAId, PurchaseOrderStatus.RECEIVED);

                // Total stock must be exactly 100, NOT 160 (60 + 100)!
                assertThat(productService.getById(productA1.getId()).getStockQuantity())
                                .isEqualByComparingTo("100.000");
                PurchaseOrder poFinal = poService.getPurchaseOrderById(poId, firmAId).orElseThrow();
                assertThat(poFinal.getStatus()).isEqualTo(PurchaseOrderStatus.RECEIVED);
                assertThat(poFinal.getItems().get(0).getReceivedQuantity()).isEqualByComparingTo("100.000");
        }

        @Test
        @DisplayName("Multi-tenant firm isolation: Cross-firm goods receipt and status update are rejected")
        void testFirmIsolation() {
                PurchaseOrder po = PurchaseOrder.builder()
                                .party(vendorA)
                                .poDate(LocalDate.now())
                                .firmId(firmAId)
                                .status(PurchaseOrderStatus.ISSUED)
                                .build();

                PurchaseOrderItem item = PurchaseOrderItem.builder()
                                .productId(productA1.getId())
                                .productName(productA1.getName())
                                .quantity(BigDecimal.valueOf(50.0))
                                .unitPrice(BigDecimal.valueOf(350.00))
                                .build();

                po.setItems(new ArrayList<>(List.of(item)));
                PurchaseOrder created = poService.createPurchaseOrder(po);
                Long poId = created.getId();
                Long itemId = created.getItems().get(0).getId();

                // Switch tenant context to Firm B
                TenantContext.setCurrentFirmId(firmBId);

                PoReceiptRequest req = PoReceiptRequest.builder()
                                .items(List.of(PoReceiptItemRequest.builder()
                                                .purchaseOrderItemId(itemId)
                                                .productId(productA1.getId())
                                                .receivedQuantity(BigDecimal.valueOf(10.0))
                                                .build()))
                                .build();

                // Attempt receipt by Firm B
                assertThrows(TenantSecurityException.class, () -> poService.recordGoodsReceipt(poId, firmAId, req));

                // Attempt status update by Firm B
                assertThrows(TenantSecurityException.class,
                                () -> poService.updateStatus(poId, firmAId, PurchaseOrderStatus.PARTIALLY_RECEIVED));
        }
}
