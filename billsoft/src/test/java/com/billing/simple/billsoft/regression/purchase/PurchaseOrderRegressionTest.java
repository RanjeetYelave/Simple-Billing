package com.billing.simple.billsoft.regression.purchase;

import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.service.PartyService;
import com.billing.simple.billsoft.service.ProductService;
import com.billing.simple.billsoft.service.PurchaseOrderPdfService;
import com.billing.simple.billsoft.service.PurchaseOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Tag("regression")
@Tag("integration")
@DisplayName("Purchase Order Lifecycle & Stock Intake Regression Tests")
class PurchaseOrderRegressionTest {

    @Autowired
    private PurchaseOrderService poService;

    @Autowired
    private PurchaseOrderPdfService poPdfService;

    @Autowired
    private PartyService partyService;

    @Autowired
    private ProductService productService;

    private Party testSupplier;
    private Product testProduct;
    private final Long testFirmId = 1L;

    @BeforeEach
    void setUp() {
        testSupplier = partyService.createParty(Party.builder()
                .name("Standard Steel Mill Ltd")
                .phone("9811223344")
                .email("sales@standardsteel.com")
                .gstin("27AAACS1122D1Z3")
                .firmId(testFirmId)
                .build());

        testProduct = productService.create(Product.builder()
                .name("Steel Ingot Grade A")
                .price(BigDecimal.valueOf(1200.00))
                .costPrice(BigDecimal.valueOf(900.00))
                .stockQuantity(BigDecimal.valueOf(20.0))
                .firmId(testFirmId)
                .itemType("GOODS")
                .unit("kg")
                .build());
    }

    @Test
    @DisplayName("Should create Purchase Order, transition to RECEIVED, and automatically intake inventory stock")
    void shouldCreatePoAndIntakeStockOnReceived() {
        PurchaseOrder po = PurchaseOrder.builder()
                .party(testSupplier)
                .poDate(LocalDate.now())
                .firmId(testFirmId)
                .status(PurchaseOrderStatus.ISSUED)
                .build();

        PurchaseOrderItem item = PurchaseOrderItem.builder()
                .productId(testProduct.getId())
                .productName(testProduct.getName())
                .description("Bulk raw steel supply")
                .quantity(BigDecimal.valueOf(50.0))
                .unitPrice(BigDecimal.valueOf(900.00))
                .gstPercent(BigDecimal.valueOf(18.0))
                .build();

        List<PurchaseOrderItem> items = new ArrayList<>();
        items.add(item);
        po.setItems(items);

        PurchaseOrder createdPo = poService.createPurchaseOrder(po);
        assertThat(createdPo.getPoNumber()).startsWith("PO-");
        assertThat(createdPo.getTotalAmount()).isEqualByComparingTo("53100.00"); // 50 * 900 = 45000 + 18% (8100) = 53100

        // Before reception, stock remains 20
        Product prodBefore = productService.getById(testProduct.getId());
        assertThat(prodBefore.getStockQuantity()).isEqualByComparingTo("20.000");

        // 2. Mark as RECEIVED -> triggers automatic stock intake
        PurchaseOrder receivedPo = poService.updateStatus(createdPo.getId(), testFirmId, PurchaseOrderStatus.RECEIVED);
        assertThat(receivedPo.getStatus()).isEqualTo(PurchaseOrderStatus.RECEIVED);

        // Product stock should increase from 20 -> 70
        Product prodAfter = productService.getById(testProduct.getId());
        assertThat(prodAfter.getStockQuantity()).isEqualByComparingTo("70.000");

        // 3. Mark as CANCELLED -> triggers automatic stock reversal (70 -> 20)
        PurchaseOrder cancelledPo = poService.updateStatus(createdPo.getId(), testFirmId, PurchaseOrderStatus.CANCELLED);
        assertThat(cancelledPo.getStatus()).isEqualTo(PurchaseOrderStatus.CANCELLED);
        Product prodReversed = productService.getById(testProduct.getId());
        assertThat(prodReversed.getStockQuantity()).isEqualByComparingTo("20.000");

        // 4. Mark as RECEIVED again -> triggers stock intake (20 -> 70)
        poService.updateStatus(createdPo.getId(), testFirmId, PurchaseOrderStatus.RECEIVED);
        Product prodReReceived = productService.getById(testProduct.getId());
        assertThat(prodReReceived.getStockQuantity()).isEqualByComparingTo("70.000");

        // 5. Delete RECEIVED PO -> triggers stock reversal (70 -> 20)
        poService.deletePurchaseOrder(createdPo.getId(), testFirmId);
        Product prodDeleted = productService.getById(testProduct.getId());
        assertThat(prodDeleted.getStockQuantity()).isEqualByComparingTo("20.000");
    }

    @Test
    @DisplayName("Should intake stock immediately when PO is created directly in RECEIVED status")
    void shouldIntakeStockWhenCreatedDirectlyAsReceived() {
        PurchaseOrder po = PurchaseOrder.builder()
                .party(testSupplier)
                .poDate(LocalDate.now())
                .firmId(testFirmId)
                .status(PurchaseOrderStatus.RECEIVED)
                .build();

        PurchaseOrderItem item = PurchaseOrderItem.builder()
                .productId(testProduct.getId())
                .productName(testProduct.getName())
                .quantity(BigDecimal.valueOf(30.0))
                .unitPrice(BigDecimal.valueOf(900.00))
                .build();

        po.setItems(List.of(item));
        poService.createPurchaseOrder(po);

        // Initial 20 + 30 received = 50
        Product prod = productService.getById(testProduct.getId());
        assertThat(prod.getStockQuantity()).isEqualByComparingTo("50.000");
    }

    @Test
    @DisplayName("Should auto-create completely new product in inventory and sync vendor ledger when PO is received and paid")
    void shouldAutoCreateNewProductInInventoryAndSyncVendorLedgerWhenReceivedAndPaid() {
        // 1. Create PO with a brand new product that does NOT exist in catalog
        String newProductName = "High Grade Titanium Bolt M12";
        PurchaseOrder po = PurchaseOrder.builder()
                .party(testSupplier)
                .poDate(LocalDate.now())
                .firmId(testFirmId)
                .status(PurchaseOrderStatus.ISSUED)
                .build();

        PurchaseOrderItem item = PurchaseOrderItem.builder()
                .productId(null) // No existing product ID
                .productName(newProductName)
                .description("Specialized titanium fasteners")
                .hsnCode("7318")
                .unit("pcs")
                .quantity(BigDecimal.valueOf(100.0))
                .unitPrice(BigDecimal.valueOf(45.00))
                .gstPercent(BigDecimal.valueOf(18.0))
                .build();

        po.setItems(List.of(item));
        PurchaseOrder created = poService.createPurchaseOrder(po);
        assertThat(created.getId()).isNotNull();

        // 2. Receive the PO -> triggers auto-creation in inventory + stock intake
        PurchaseOrder received = poService.updateStatus(created.getId(), testFirmId, PurchaseOrderStatus.RECEIVED);
        assertThat(received.getStatus()).isEqualTo(PurchaseOrderStatus.RECEIVED);

        // Product should now exist in product catalog
        List<Product> products = productService.getAll(testFirmId);
        Optional<Product> found = products.stream()
                .filter(p -> newProductName.equalsIgnoreCase(p.getName()))
                .findFirst();

        assertThat(found).isPresent();
        Product newProd = found.get();
        assertThat(newProd.getStockQuantity()).isEqualByComparingTo("100.000");
        assertThat(newProd.getHsnCode()).isEqualTo("7318");
        assertThat(newProd.getCostPrice()).isEqualByComparingTo("45.00");

        // 3. Record Quick Pay on the PO
        BigDecimal poTotal = created.getTotalAmount(); // 100 * 45 = 4500 + 18% (810) = 5310
        assertThat(poTotal).isEqualByComparingTo("5310.00");

        PurchaseOrder paidPo = poService.recordPoPayment(
                created.getId(),
                testFirmId,
                poTotal,
                LocalDate.now(),
                "UPI",
                "UPI-TITANIUM-001",
                "Paid full balance for titanium fasteners"
        );
        assertThat(paidPo.getPaymentStatus()).isEqualTo("PAID");
        assertThat(paidPo.getPaidAmount()).isEqualByComparingTo("5310.00");

        // 4. Verify Vendor Financial Summary / Ledger
        var summary = partyService.getFinancialSummary(testSupplier.getId(), testFirmId);
        assertThat(summary.getTotalPurchases()).isEqualByComparingTo("5310.00");
        assertThat(summary.getTotalPaid()).isEqualByComparingTo("5310.00");
        assertThat(summary.getNetBalance()).isEqualByComparingTo("0.00");
        assertThat(summary.getBalanceStatus()).isEqualTo("SETTLED");
    }

    @Test
    @DisplayName("Should generate valid Purchase Order PDF with %PDF- header")
    void shouldGenerateValidPurchaseOrderPdf() throws Exception {
        PurchaseOrder po = PurchaseOrder.builder()
                .party(testSupplier)
                .poDate(LocalDate.now())
                .firmId(testFirmId)
                .status(PurchaseOrderStatus.ISSUED)
                .build();

        PurchaseOrderItem item = PurchaseOrderItem.builder()
                .productId(testProduct.getId())
                .productName(testProduct.getName())
                .description("Steel Ingot")
                .quantity(BigDecimal.valueOf(10.0))
                .unitPrice(BigDecimal.valueOf(900.00))
                .gstPercent(BigDecimal.valueOf(18.0))
                .build();

        po.setItems(List.of(item));
        PurchaseOrder created = poService.createPurchaseOrder(po);

        byte[] pdf = poPdfService.generatePdf(created);
        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(500);

        String header = new String(pdf, 0, Math.min(pdf.length, 8), StandardCharsets.US_ASCII);
        assertThat(header).startsWith("%PDF-");
    }
}
