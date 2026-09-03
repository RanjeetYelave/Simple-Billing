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
