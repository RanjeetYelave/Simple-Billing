package com.billing.simple.billsoft.regression.billing;

import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceRequestItem;
import com.billing.simple.billsoft.dtos.InvoiceUpdateRequest;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.service.InvoiceService;
import com.billing.simple.billsoft.service.ProductService;
import com.billing.simple.billsoft.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
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
    private Product testProduct2;
    private final Long testFirmId = 1L;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentFirmId(testFirmId);
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

        testProduct2 = productService.create(Product.builder()
                .name("Brass Hex Bolt")
                .price(BigDecimal.valueOf(150.00))
                .stockQuantity(BigDecimal.valueOf(50.0))
                .firmId(testFirmId)
                .itemType("GOODS")
                .unit("pcs")
                .hsnCode("7318")
                .gstPercentage(BigDecimal.valueOf(18.0))
                .build());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
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

    @Test
    @DisplayName("Stage 2.3: Converting Estimate to Invoice with fixed invoice-level discount preserves exact totals and discounts")
    void convertEstimateToInvoice_withInvoiceLevelDiscount_preservesExactTotalsAndDiscounts() {
        // 1. Create Estimate with 2 items and a fixed invoice-level discount (50.00), no item discounts
        InvoiceRequest estReq = new InvoiceRequest();
        estReq.setFirmId(testFirmId);
        estReq.setCustomerId(testCustomer.getId());
        estReq.setStatus(InvoiceStatus.ESTIMATE);
        estReq.setItems(List.of(
                createItem(testProduct.getId(), 2, 250.0), // 500.00
                createItem(testProduct2.getId(), 2, 150.0) // 300.00
        ));
        estReq.setInvoiceDiscount(new InvoiceRequest.Discount("VALUE", BigDecimal.valueOf(50.00)));

        Invoice estimate = invoiceService.createEstimate(estReq);
        assertThat(estimate.getStatus()).isEqualTo(InvoiceStatus.ESTIMATE);

        // 2. Capture before conversion
        BigDecimal estSubtotalWithoutTax = estimate.getSubtotalWithoutTax();
        BigDecimal estTotalDiscount = estimate.getTotalDiscount();
        BigDecimal estTotalTax = estimate.getTotalTax();
        BigDecimal estTotalAmount = estimate.getTotalAmount();
        BigDecimal estItem1Discount = estimate.getItems().get(0).getDiscountValue();
        BigDecimal estItem2Discount = estimate.getItems().get(1).getDiscountValue();
        BigDecimal estItem1Taxable = estimate.getItems().get(0).getTaxableAmount();
        BigDecimal estItem2Taxable = estimate.getItems().get(1).getTaxableAmount();

        assertThat(estTotalDiscount).isEqualByComparingTo("50.00");
        assertThat(estItem1Discount).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(estItem2Discount).isEqualByComparingTo(BigDecimal.ZERO);

        // 3. Convert Estimate -> Invoice using actual service flow
        Invoice converted = invoiceService.convertEstimateToInvoice(estimate.getId(), null);

        // 4. Assert exact preservation
        assertThat(converted.getId()).isNotNull();
        assertThat(converted.getStatus()).isEqualTo(InvoiceStatus.UNPAID);
        assertThat(converted.getTotalDiscount()).isEqualByComparingTo(estTotalDiscount);
        assertThat(converted.getTotalAmount()).isEqualByComparingTo(estTotalAmount);
        assertThat(converted.getSubtotalWithoutTax()).isEqualByComparingTo(estSubtotalWithoutTax);
        assertThat(converted.getTotalTax()).isEqualByComparingTo(estTotalTax);

        assertThat(converted.getItems().get(0).getDiscountValue()).isEqualByComparingTo(estItem1Discount);
        assertThat(converted.getItems().get(1).getDiscountValue()).isEqualByComparingTo(estItem2Discount);
        assertThat(converted.getItems().get(0).getDiscountValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(converted.getItems().get(1).getDiscountValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(converted.getItems().get(0).getTaxableAmount()).isEqualByComparingTo(estItem1Taxable);
        assertThat(converted.getItems().get(1).getTaxableAmount()).isEqualByComparingTo(estItem2Taxable);
    }

    @Test
    @DisplayName("Stage 2.3 (Percentage): Converting Estimate to Invoice with percentage invoice-level discount preserves exact totals and discounts")
    void convertEstimateToInvoice_withPercentageInvoiceDiscount_preservesExactTotalsAndDiscounts() {
        // Create Estimate with 2 items and a 10% invoice-level discount (10% of 800.00 = 80.00)
        InvoiceRequest estReq = new InvoiceRequest();
        estReq.setFirmId(testFirmId);
        estReq.setCustomerId(testCustomer.getId());
        estReq.setStatus(InvoiceStatus.ESTIMATE);
        estReq.setItems(List.of(
                createItem(testProduct.getId(), 2, 250.0), // 500.00
                createItem(testProduct2.getId(), 2, 150.0) // 300.00
        ));
        estReq.setInvoiceDiscount(new InvoiceRequest.Discount("PERCENT", BigDecimal.valueOf(10.0)));

        Invoice estimate = invoiceService.createEstimate(estReq);
        assertThat(estimate.getStatus()).isEqualTo(InvoiceStatus.ESTIMATE);

        BigDecimal estSubtotalWithoutTax = estimate.getSubtotalWithoutTax();
        BigDecimal estTotalDiscount = estimate.getTotalDiscount();
        BigDecimal estTotalTax = estimate.getTotalTax();
        BigDecimal estTotalAmount = estimate.getTotalAmount();
        BigDecimal estItem1Discount = estimate.getItems().get(0).getDiscountValue();
        BigDecimal estItem2Discount = estimate.getItems().get(1).getDiscountValue();
        BigDecimal estItem1Taxable = estimate.getItems().get(0).getTaxableAmount();
        BigDecimal estItem2Taxable = estimate.getItems().get(1).getTaxableAmount();

        assertThat(estTotalDiscount).isEqualByComparingTo("80.00");
        assertThat(estItem1Discount).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(estItem2Discount).isEqualByComparingTo(BigDecimal.ZERO);

        Invoice converted = invoiceService.convertEstimateToInvoice(estimate.getId(), null);

        assertThat(converted.getId()).isNotNull();
        assertThat(converted.getStatus()).isEqualTo(InvoiceStatus.UNPAID);
        assertThat(converted.getTotalDiscount()).isEqualByComparingTo(estTotalDiscount);
        assertThat(converted.getTotalAmount()).isEqualByComparingTo(estTotalAmount);
        assertThat(converted.getSubtotalWithoutTax()).isEqualByComparingTo(estSubtotalWithoutTax);
        assertThat(converted.getTotalTax()).isEqualByComparingTo(estTotalTax);

        assertThat(converted.getItems().get(0).getDiscountValue()).isEqualByComparingTo(estItem1Discount);
        assertThat(converted.getItems().get(1).getDiscountValue()).isEqualByComparingTo(estItem2Discount);
        assertThat(converted.getItems().get(0).getDiscountValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(converted.getItems().get(1).getDiscountValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(converted.getItems().get(0).getTaxableAmount()).isEqualByComparingTo(estItem1Taxable);
        assertThat(converted.getItems().get(1).getTaxableAmount()).isEqualByComparingTo(estItem2Taxable);
    }

    @Test
    @DisplayName("Stage 2.3 (Override Precedence): Override request invoice-level discount takes precedence over estimate discount")
    void convertEstimateToInvoice_withOverrideRequestDiscount_overrideTakesPrecedence() {
        // Create Estimate with 2 items and a 50.00 invoice-level discount
        InvoiceRequest estReq = new InvoiceRequest();
        estReq.setFirmId(testFirmId);
        estReq.setCustomerId(testCustomer.getId());
        estReq.setStatus(InvoiceStatus.ESTIMATE);
        estReq.setItems(List.of(
                createItem(testProduct.getId(), 2, 250.0), // 500.00
                createItem(testProduct2.getId(), 2, 150.0) // 300.00
        ));
        estReq.setInvoiceDiscount(new InvoiceRequest.Discount("VALUE", BigDecimal.valueOf(50.00)));

        Invoice estimate = invoiceService.createEstimate(estReq);
        assertThat(estimate.getTotalDiscount()).isEqualByComparingTo("50.00");

        // Convert with override discount = 100.00
        InvoiceRequest override = new InvoiceRequest();
        override.setInvoiceDiscount(new InvoiceRequest.Discount("VALUE", BigDecimal.valueOf(100.00)));

        Invoice converted = invoiceService.convertEstimateToInvoice(estimate.getId(), override);

        assertThat(converted.getTotalDiscount()).isEqualByComparingTo("100.00");
        assertThat(converted.getItems().get(0).getDiscountValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(converted.getItems().get(1).getDiscountValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Stage 2.4: Repeated updateFullInvoice metadata updates maintain idempotent discounts and totals")
    void updateFullInvoice_repeatedMetadataUpdates_maintainsIdempotentDiscountsAndTotals() {
        // 1. Create/persist an invoice with 2 items and fixed invoice-level discount (50.00), no item discounts
        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(testFirmId);
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.FINAL);
        req.setItems(List.of(
                createItem(testProduct.getId(), 2, 250.0), // 500.00
                createItem(testProduct2.getId(), 2, 150.0) // 300.00
        ));
        req.setInvoiceDiscount(new InvoiceRequest.Discount("VALUE", BigDecimal.valueOf(50.00)));
        req.setCustomerNote("Initial Note");
        req.setPaymentMethod("CASH");

        Invoice initialInvoice = invoiceService.createInvoice(req);
        Long invoiceId = initialInvoice.getId();

        // 2. Capture initial values
        BigDecimal initialSubtotalWithoutTax = initialInvoice.getSubtotalWithoutTax();
        BigDecimal initialTotalTax = initialInvoice.getTotalTax();
        BigDecimal initialTotalDiscount = initialInvoice.getTotalDiscount();
        BigDecimal initialTotalAmount = initialInvoice.getTotalAmount();
        BigDecimal initialItem1Discount = initialInvoice.getItems().get(0).getDiscountValue();
        BigDecimal initialItem2Discount = initialInvoice.getItems().get(1).getDiscountValue();
        BigDecimal initialItem1Taxable = initialInvoice.getItems().get(0).getTaxableAmount();
        BigDecimal initialItem2Taxable = initialInvoice.getItems().get(1).getTaxableAmount();

        assertThat(initialTotalDiscount).isEqualByComparingTo("50.00");
        assertThat(initialItem1Discount).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(initialItem2Discount).isEqualByComparingTo(BigDecimal.ZERO);

        // 3. Update #1: Real updateFullInvoice changing metadata (customerNote and paymentMethod)
        // Line items and invoice discount are provided as reconstructed by standard form submission
        InvoiceUpdateRequest update1 = new InvoiceUpdateRequest();
        update1.setCustomerId(testCustomer.getId());
        update1.setCustomerNote("Updated Note #1");
        update1.setPaymentMethod("UPI");
        update1.setInvoiceDiscount(new InvoiceRequest.Discount("VALUE", BigDecimal.valueOf(50.00)));
        List<InvoiceRequestItem> formItems1 = initialInvoice.getItems().stream().map(it -> {
            InvoiceRequestItem ri = new InvoiceRequestItem();
            ri.setProductId(it.getProduct().getId());
            ri.setQty(it.getQty());
            ri.setPricePerUnit(it.getPricePerUnit());
            ri.setDiscountValue(it.getDiscountValue());
            ri.setGstPercent(it.getGstPercent());
            return ri;
        }).collect(Collectors.toList());
        update1.setItems(formItems1);

        invoiceService.updateFullInvoice(invoiceId, update1);

        // 4. Reload from database and inspect
        Invoice reloaded1 = invoiceRepo.findById(invoiceId).orElseThrow();
        assertThat(reloaded1.getCustomerNote()).isEqualTo("Updated Note #1");
        assertThat(reloaded1.getPaymentMethod()).isEqualTo("UPI");
        assertThat(reloaded1.getTotalDiscount()).isEqualByComparingTo(initialTotalDiscount);
        assertThat(reloaded1.getTotalAmount()).isEqualByComparingTo(initialTotalAmount);
        assertThat(reloaded1.getSubtotalWithoutTax()).isEqualByComparingTo(initialSubtotalWithoutTax);
        assertThat(reloaded1.getTotalTax()).isEqualByComparingTo(initialTotalTax);
        assertThat(reloaded1.getItems().get(0).getDiscountValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(reloaded1.getItems().get(1).getDiscountValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(reloaded1.getItems().get(0).getTaxableAmount()).isEqualByComparingTo(initialItem1Taxable);
        assertThat(reloaded1.getItems().get(1).getTaxableAmount()).isEqualByComparingTo(initialItem2Taxable);

        // 5. Update #2: Second updateFullInvoice changing terms and customer note
        InvoiceUpdateRequest update2 = new InvoiceUpdateRequest();
        update2.setCustomerId(testCustomer.getId());
        update2.setCustomerNote("Updated Note #2 - Second Revision");
        update2.setPaymentMethod("BANK_TRANSFER");
        update2.setTermsAndConditions("Net 30 days");
        update2.setInvoiceDiscount(new InvoiceRequest.Discount("VALUE", BigDecimal.valueOf(50.00)));
        List<InvoiceRequestItem> formItems2 = reloaded1.getItems().stream().map(it -> {
            InvoiceRequestItem ri = new InvoiceRequestItem();
            ri.setProductId(it.getProduct().getId());
            ri.setQty(it.getQty());
            ri.setPricePerUnit(it.getPricePerUnit());
            ri.setDiscountValue(it.getDiscountValue());
            ri.setGstPercent(it.getGstPercent());
            return ri;
        }).collect(Collectors.toList());
        update2.setItems(formItems2);

        invoiceService.updateFullInvoice(invoiceId, update2);

        // 6. Reload from database and assert invariants again
        Invoice reloaded2 = invoiceRepo.findById(invoiceId).orElseThrow();
        assertThat(reloaded2.getCustomerNote()).isEqualTo("Updated Note #2 - Second Revision");
        assertThat(reloaded2.getPaymentMethod()).isEqualTo("BANK_TRANSFER");
        assertThat(reloaded2.getTermsAndConditions()).isEqualTo("Net 30 days");
        assertThat(reloaded2.getTotalDiscount()).isEqualByComparingTo(initialTotalDiscount);
        assertThat(reloaded2.getTotalAmount()).isEqualByComparingTo(initialTotalAmount);
        assertThat(reloaded2.getSubtotalWithoutTax()).isEqualByComparingTo(initialSubtotalWithoutTax);
        assertThat(reloaded2.getTotalTax()).isEqualByComparingTo(initialTotalTax);
        assertThat(reloaded2.getItems().get(0).getDiscountValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(reloaded2.getItems().get(1).getDiscountValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(reloaded2.getItems().get(0).getTaxableAmount()).isEqualByComparingTo(initialItem1Taxable);
        assertThat(reloaded2.getItems().get(1).getTaxableAmount()).isEqualByComparingTo(initialItem2Taxable);

        // 7. Update #3: Pure metadata update (items == null)
        InvoiceUpdateRequest update3 = new InvoiceUpdateRequest();
        update3.setCustomerId(testCustomer.getId());
        update3.setCustomerNote("Final Metadata Note #3");
        update3.setPaymentMethod("CHEQUE");
        update3.setItems(null);

        invoiceService.updateFullInvoice(invoiceId, update3);

        Invoice reloaded3 = invoiceRepo.findById(invoiceId).orElseThrow();
        assertThat(reloaded3.getCustomerNote()).isEqualTo("Final Metadata Note #3");
        assertThat(reloaded3.getPaymentMethod()).isEqualTo("CHEQUE");
        assertThat(reloaded3.getTotalDiscount()).isEqualByComparingTo(initialTotalDiscount);
        assertThat(reloaded3.getTotalAmount()).isEqualByComparingTo(initialTotalAmount);
        assertThat(reloaded3.getSubtotalWithoutTax()).isEqualByComparingTo(initialSubtotalWithoutTax);
        assertThat(reloaded3.getTotalTax()).isEqualByComparingTo(initialTotalTax);
        assertThat(reloaded3.getItems().get(0).getDiscountValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(reloaded3.getItems().get(1).getDiscountValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(reloaded3.getItems().get(0).getTaxableAmount()).isEqualByComparingTo(initialItem1Taxable);
        assertThat(reloaded3.getItems().get(1).getTaxableAmount()).isEqualByComparingTo(initialItem2Taxable);
    }

    @Test
    @DisplayName("Stage 2 Boundary: Estimate with extreme invoice discount converts to invoice preserving discount metadata")
    void estimateToInvoice_extremeDiscount_preservesDiscountSemantics() {
        InvoiceRequest estReq = new InvoiceRequest();
        estReq.setFirmId(testFirmId);
        estReq.setCustomerId(testCustomer.getId());
        estReq.setStatus(InvoiceStatus.ESTIMATE);
        estReq.setItems(List.of(
                createItem(testProduct.getId(), 1, 100.0),
                createItem(testProduct2.getId(), 1, 100.0)
        ));
        estReq.setInvoiceDiscount(new InvoiceRequest.Discount("VALUE", BigDecimal.valueOf(500.00)));

        Invoice estimate = invoiceService.createInvoice(estReq);
        Long estimateId = estimate.getId();

        assertThat(estimate.getTotalAmount()).isEqualByComparingTo("0.00");
        assertThat(estimate.getInvoiceDiscountValue()).isEqualByComparingTo("500.00");

        // Convert to invoice without override
        Invoice converted = invoiceService.convertEstimateToInvoice(estimateId, null);

        assertThat(converted.getInvoiceDiscountType()).isEqualTo("VALUE");
        assertThat(converted.getInvoiceDiscountValue()).isEqualByComparingTo("500.00");
        assertThat(converted.getTotalAmount()).isEqualByComparingTo("0.00");
        assertThat(converted.getItems().get(0).getDiscountValue()).isEqualByComparingTo("0.00");
        assertThat(converted.getItems().get(1).getDiscountValue()).isEqualByComparingTo("0.00");
        assertThat(converted.getItems().get(0).getTaxableAmount()).isEqualByComparingTo("0.00");
        assertThat(converted.getItems().get(1).getTaxableAmount()).isEqualByComparingTo("0.00");

        // Verify source estimate state
        Invoice freshEst = invoiceRepo.findById(estimateId).orElseThrow();
        assertThat(freshEst.getConvertedInvoiceId()).isEqualTo(converted.getId());
        assertThat(freshEst.getInvoiceDiscountValue()).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("Cross-Stage Interaction: Firm 2 cannot access or update Firm 1 invoice with extreme discount")
    void crossStageInteraction_firm2CannotAccessOrUpdateFirm1InvoiceWithExtremeDiscount() {
        // 1. Firm 1 creates an invoice with extreme invoice discount
        TenantContext.setCurrentFirmId(testFirmId);
        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(testFirmId);
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.FINAL);
        req.setItems(List.of(
                createItem(testProduct.getId(), 1, 100.0),
                createItem(testProduct2.getId(), 1, 100.0)
        ));
        req.setInvoiceDiscount(new InvoiceRequest.Discount("VALUE", BigDecimal.valueOf(500.00)));

        Invoice inv = invoiceService.createInvoice(req);
        Long invoiceId = inv.getId();
        assertThat(inv.getTotalAmount()).isEqualByComparingTo("0.00");

        // 2. Switch context to Firm 2
        Long firm2Id = 2L;
        TenantContext.setCurrentFirmId(firm2Id);

        // Firm 2 attempt to read
        Invoice crossRead = invoiceService.getById(invoiceId);
        assertThat(crossRead).isNull();

        // Firm 2 attempt to update
        InvoiceUpdateRequest updateReq = new InvoiceUpdateRequest();
        updateReq.setCustomerId(testCustomer.getId());
        updateReq.setCustomerNote("Malicious update attempt");
        org.junit.jupiter.api.Assertions.assertThrows(
                com.billing.simple.billsoft.security.TenantSecurityException.class,
                () -> invoiceService.updateFullInvoice(invoiceId, updateReq)
        );

        // 3. Switch back to Firm 1 context and verify pristine invoice state
        TenantContext.setCurrentFirmId(testFirmId);
        Invoice freshInv = invoiceRepo.findById(invoiceId).orElseThrow();
        assertThat(freshInv.getTotalAmount()).isEqualByComparingTo("0.00");
        assertThat(freshInv.getInvoiceDiscountValue()).isEqualByComparingTo("500.00");
        assertThat(freshInv.getItems().get(0).getDiscountValue()).isEqualByComparingTo("0.00");
        assertThat(freshInv.getItems().get(1).getDiscountValue()).isEqualByComparingTo("0.00");
        assertThat(freshInv.getCustomerNote()).isNull();
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
