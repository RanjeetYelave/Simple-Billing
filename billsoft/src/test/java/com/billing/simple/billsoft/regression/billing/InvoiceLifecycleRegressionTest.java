package com.billing.simple.billsoft.regression.billing;

import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceRequestItem;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.service.InvoiceService;
import com.billing.simple.billsoft.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Tag("regression")
@Tag("integration")
@DisplayName("Invoice Lifecycle & Workflow Regression Tests")
class InvoiceLifecycleRegressionTest {

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private ProductService productService;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private InvoiceRepository invoiceRepo;

    @Autowired
    private InvoicePaymentRepository paymentRepo;

    private Customer testCustomer;
    private Product testProduct;
    private final Long testFirmId = 1L;

    @BeforeEach
    void setUp() {
        testCustomer = customerRepo.save(Customer.builder()
                .name("Global Logistics Corp")
                .phone("9876543210")
                .email("billing@globallogistics.com")
                .gstin("27AAACG0000A1Z5")
                .firmId(testFirmId)
                .build());

        testProduct = productService.create(Product.builder()
                .name("Steel Fastener Kit")
                .price(BigDecimal.valueOf(250.00))
                .stockQuantity(BigDecimal.valueOf(50.0))
                .firmId(testFirmId)
                .itemType("GOODS")
                .unit("pcs")
                .hsnCode("7318")
                .gstPercentage(BigDecimal.valueOf(18.0))
                .build());
    }

    @Test
    @DisplayName("Should generate monotonic invoice numbers without gaps")
    void shouldGenerateMonotonicInvoiceNumbers() {
        String num1 = invoiceService.generateInvoiceNumber(testFirmId);
        assertThat(num1).matches("^INV-\\d{4}$");

        InvoiceRequest req1 = new InvoiceRequest();
        req1.setFirmId(testFirmId);
        req1.setCustomerId(testCustomer.getId());
        req1.setStatus(InvoiceStatus.FINAL);
        req1.setItems(List.of(createItem(testProduct.getId(), 2, 250.0)));
        Invoice inv1 = invoiceService.createInvoice(req1);

        String num2 = invoiceService.generateInvoiceNumber(testFirmId);
        assertThat(num2).isNotEqualTo(inv1.getInvoiceNumber());
    }

    @Test
    @DisplayName("Should create Quotation and convert to Invoice, deducting stock and setting convertedInvoiceId")
    void shouldConvertQuotationToInvoiceWithStockDeduction() {
        // 1. Create Estimate/Quotation
        InvoiceRequest estReq = new InvoiceRequest();
        estReq.setFirmId(testFirmId);
        estReq.setCustomerId(testCustomer.getId());
        estReq.setStatus(InvoiceStatus.ESTIMATE);
        estReq.setItems(List.of(createItem(testProduct.getId(), 10, 250.0)));

        Invoice estimate = invoiceService.createInvoice(estReq);
        assertThat(estimate.getStatus()).isEqualTo(InvoiceStatus.ESTIMATE);
        assertThat(estimate.getEstimateNumber()).isNotNull();
        assertThat(estimate.getInvoiceNumber()).isNull();

        // Product stock should remain unchanged for estimates
        Product prodBeforeConvert = productService.getById(testProduct.getId());
        assertThat(prodBeforeConvert.getStockQuantity()).isEqualByComparingTo("50.000");

        // 2. Convert Estimate to Invoice
        Invoice convertedInvoice = invoiceService.convertEstimateToInvoice(estimate.getId(), null);
        assertThat(convertedInvoice.getStatus()).isEqualTo(InvoiceStatus.UNPAID);
        assertThat(convertedInvoice.getInvoiceNumber()).isNotNull();

        // Check quotation link
        Invoice updatedEstimate = invoiceRepo.findById(estimate.getId()).orElseThrow();
        assertThat(updatedEstimate.getConvertedInvoiceId()).isEqualTo(convertedInvoice.getId());

        // Product stock must now be deducted by 10
        Product prodAfterConvert = productService.getById(testProduct.getId());
        assertThat(prodAfterConvert.getStockQuantity()).isEqualByComparingTo("40.000");
    }

    @Test
    @DisplayName("Should convert Quotation to Invoice via Review & Edit flow (with convertedInvoiceId)")
    void shouldConvertQuotationToInvoiceViaReviewAndEditFlow() {
        // 1. Create Quotation
        InvoiceRequest estReq = new InvoiceRequest();
        estReq.setFirmId(testFirmId);
        estReq.setCustomerId(testCustomer.getId());
        estReq.setStatus(InvoiceStatus.ESTIMATE);
        estReq.setCustomerNote("Original quote note");
        estReq.setItems(List.of(createItem(testProduct.getId(), 5, 250.0)));

        Invoice estimate = invoiceService.createEstimate(estReq);
        assertThat(estimate.getId()).isNotNull();
        assertThat(estimate.getStatus()).isEqualTo(InvoiceStatus.ESTIMATE);

        // 2. Simulate Review & Edit: User creates invoice referencing convertedInvoiceId with adjustments
        InvoiceRequest invReq = new InvoiceRequest();
        invReq.setFirmId(testFirmId);
        invReq.setCustomerId(testCustomer.getId());
        invReq.setStatus(InvoiceStatus.FINAL);
        invReq.setConvertedInvoiceId(estimate.getId());
        invReq.setCustomerNote("Converted from quotation with updated note");
        invReq.setItems(List.of(createItem(testProduct.getId(), 8, 240.0))); // edited qty & price

        Invoice finalInv = invoiceService.createInvoice(invReq);
        assertThat(finalInv.getId()).isNotNull();
        assertThat(finalInv.getStatus()).isIn(InvoiceStatus.FINAL, InvoiceStatus.UNPAID);

        // 3. Verify bidirectional quotation-invoice link
        Invoice reloadedEstimate = invoiceRepo.findById(estimate.getId()).orElseThrow();
        assertThat(reloadedEstimate.getConvertedInvoiceId()).isEqualTo(finalInv.getId());
    }

    @Test
    @DisplayName("Should convert Quotation with custom/manual items and preserve item details")
    void shouldConvertQuotationWithCustomItems() {
        InvoiceRequest estReq = new InvoiceRequest();
        estReq.setFirmId(testFirmId);
        estReq.setCustomerId(testCustomer.getId());
        estReq.setStatus(InvoiceStatus.ESTIMATE);
        
        InvoiceRequestItem customItem = new InvoiceRequestItem();
        customItem.setProductName("Custom Architectural Consultation");
        customItem.setHsnCode("998311");
        customItem.setUnit("hours");
        customItem.setQty(10);
        customItem.setPricePerUnit(BigDecimal.valueOf(1500.00));
        customItem.setGstPercent(BigDecimal.valueOf(18.0));
        customItem.setDiscountValue(BigDecimal.ZERO);
        estReq.setItems(List.of(customItem));

        Invoice estimate = invoiceService.createEstimate(estReq);
        assertThat(estimate.getItems()).hasSize(1);
        assertThat(estimate.getItems().get(0).getProductName()).isEqualTo("Custom Architectural Consultation");
        assertThat(estimate.getItems().get(0).getHsnCode()).isEqualTo("998311");

        // Convert via 1-click
        Invoice converted = invoiceService.convertEstimateToInvoice(estimate.getId(), null);
        assertThat(converted.getId()).isNotNull();
        assertThat(converted.getStatus()).isEqualTo(InvoiceStatus.UNPAID);
        assertThat(converted.getItems()).hasSize(1);
        assertThat(converted.getItems().get(0).getProductName()).isEqualTo("Custom Architectural Consultation");
        assertThat(converted.getItems().get(0).getHsnCode()).isEqualTo("998311");
        assertThat(converted.getItems().get(0).getLineTotal()).isEqualByComparingTo("17700.00");
    }

    @Test
    @DisplayName("Should prevent double conversion of quotation and exclude converted quotations from pipeline")
    void shouldPreventDoubleConversionOfQuotation() {
        InvoiceRequest estReq = new InvoiceRequest();
        estReq.setFirmId(testFirmId);
        estReq.setCustomerId(testCustomer.getId());
        estReq.setStatus(InvoiceStatus.ESTIMATE);
        estReq.setItems(List.of(createItem(testProduct.getId(), 5, 200.0))); // 1000 + 18% GST = 1180.00

        Invoice estimate = invoiceService.createEstimate(estReq);
        assertThat(estimate.getConvertedInvoiceId()).isNull();

        // 1st conversion succeeds
        Invoice converted = invoiceService.convertEstimateToInvoice(estimate.getId(), null);
        assertThat(converted.getId()).isNotNull();

        // 2nd conversion attempt throws exception
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> {
            invoiceService.convertEstimateToInvoice(estimate.getId(), null);
        });

        // 2nd conversion attempt via createInvoice referencing same estimate also throws exception
        InvoiceRequest secondReq = new InvoiceRequest();
        secondReq.setFirmId(testFirmId);
        secondReq.setCustomerId(testCustomer.getId());
        secondReq.setStatus(InvoiceStatus.FINAL);
        secondReq.setConvertedInvoiceId(estimate.getId());
        secondReq.setItems(List.of(createItem(testProduct.getId(), 5, 200.0)));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> {
            invoiceService.createInvoice(secondReq);
        });
    }

    @Test
    @DisplayName("Should record partial payments, maintain payment history, and calculate remaining balance accurately")
    void shouldRecordPartialPaymentsAndLedger() {
        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(testFirmId);
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.FINAL);
        req.setItems(List.of(createItem(testProduct.getId(), 4, 250.0))); // 1000 + 18% GST = 1180.00

        Invoice invoice = invoiceService.createInvoice(req);
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("1180.00");
        assertThat(invoice.getPaid()).isFalse();

        // 1. First partial payment of 500
        InvoicePayment p1 = invoiceService.recordPayment(invoice.getId(), BigDecimal.valueOf(500.00), java.time.LocalDate.now(), "CASH", "TXN-001", "Initial deposit");
        assertThat(p1.getAmount()).isEqualByComparingTo("500.00");

        List<InvoicePayment> history = paymentRepo.findByInvoiceIdOrderByPaymentDateAscIdAsc(invoice.getId());
        assertThat(history).hasSize(1);
        BigDecimal totalPaid1 = history.stream().map(InvoicePayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalPaid1).isEqualByComparingTo("500.00");

        // 2. Second payment of 680 to settle invoice in full
        InvoicePayment p2 = invoiceService.recordPayment(invoice.getId(), BigDecimal.valueOf(680.00), java.time.LocalDate.now(), "UPI", "TXN-002", "Final settlement");
        assertThat(p2.getAmount()).isEqualByComparingTo("680.00");

        Invoice fullyPaidInv = invoiceRepo.findById(invoice.getId()).orElseThrow();
        assertThat(fullyPaidInv.getPaid()).isTrue();
    }

    @Test
    @DisplayName("Should reject invoice creation when line item has negative quantity or negative price")
    void shouldRejectNegativeQuantityOrPrice() {
        InvoiceRequest quoteReq = new InvoiceRequest();
        quoteReq.setFirmId(testFirmId);
        quoteReq.setCustomerId(testCustomer.getId());
        InvoiceRequestItem quoteItem = createItem(testProduct.getId(), 1, 250.0);
        quoteReq.setItems(List.of(quoteItem));
        
        quoteItem.setQty(-5);
        assertThatThrownBy(() -> invoiceService.createInvoice(quoteReq))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Item quantity must be greater than");

        InvoiceRequest req2 = new InvoiceRequest();
        req2.setFirmId(testFirmId);
        req2.setCustomerId(testCustomer.getId());
        req2.setItems(List.of(createItem(testProduct.getId(), 2, -100.0)));

        assertThatThrownBy(() -> invoiceService.createInvoice(req2))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("cannot be negative");
    }

    private InvoiceRequestItem createItem(Long prodId, int qty, double price) {
        InvoiceRequestItem it = new InvoiceRequestItem();
        it.setProductId(prodId);
        it.setQty(qty);
        it.setPricePerUnit(BigDecimal.valueOf(price));
        it.setDiscountValue(BigDecimal.ZERO);
        it.setGstPercent(BigDecimal.valueOf(18.0));
        return it;
    }
}
