package com.billing.simple.billsoft.regression.billing;

import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceRequestItem;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.service.FirmDetailsService;
import com.billing.simple.billsoft.service.InvoicePdfService;
import com.billing.simple.billsoft.service.InvoiceService;
import com.billing.simple.billsoft.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Tag("regression")
@Tag("integration")
@DisplayName("Invoice & Quotation PDF Generation Regression Tests")
class InvoicePdfGenerationRegressionTest {

    @Autowired
    private InvoicePdfService pdfService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private ProductService productService;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private FirmDetailsService firmDetailsService;

    private Customer testCustomer;
    private Product testProduct;
    private final Long testFirmId = 1L;

    @BeforeEach
    void setUp() {
        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Apex Industrial Supplies");
        firm.setGstin("27AAAAA1234A1Z5");
        firm.setPhone("022-12345678");
        firm.setEmail("contact@apexsupplies.com");
        firm.setAddressLine1("Unit 404, Tech Park, Mumbai");
        firmDetailsService.create(firm);

        testCustomer = customerRepo.save(Customer.builder()
                .name("Precision Manufacturing Ltd")
                .phone("9820012345")
                .email("accounts@precisionmfg.com")
                .address("Plot 12, Industrial Estate, Pune")
                .gstin("27AAACP5678B1Z2")
                .firmId(testFirmId)
                .build());

        testProduct = productService.create(Product.builder()
                .name("Hydraulic Valve Assembly")
                .price(BigDecimal.valueOf(1450.00))
                .stockQuantity(BigDecimal.valueOf(100.0))
                .firmId(testFirmId)
                .itemType("GOODS")
                .unit("set")
                .hsnCode("8481")
                .gstPercentage(BigDecimal.valueOf(18.0))
                .build());
    }

    @Test
    @DisplayName("Should generate valid non-empty PDF bytes with %PDF magic header for Final Sales Invoice")
    void shouldGenerateValidInvoicePdf() throws Exception {
        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(testFirmId);
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.FINAL);

        InvoiceRequestItem it1 = new InvoiceRequestItem();
        it1.setProductId(testProduct.getId());
        it1.setQty(2);
        it1.setPricePerUnit(BigDecimal.valueOf(1450.00));
        it1.setDiscountValue(BigDecimal.valueOf(100.00));
        it1.setGstPercent(BigDecimal.valueOf(18.00));
        req.setItems(List.of(it1));

        Invoice invoice = invoiceService.createInvoice(req);

        byte[] pdfBytes = pdfService.generatePdf(invoice, "A4");

        assertThat(pdfBytes).isNotNull();
        assertThat(pdfBytes.length).isGreaterThan(1000);

        // Verify PDF Magic header %PDF-
        String header = new String(pdfBytes, 0, Math.min(pdfBytes.length, 8), StandardCharsets.US_ASCII);
        assertThat(header).startsWith("%PDF-");
    }

    @Test
    @DisplayName("Should generate valid PDF for Estimate / Quotation without errors")
    void shouldGenerateValidQuotationPdf() throws Exception {
        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(testFirmId);
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.ESTIMATE);

        InvoiceRequestItem it1 = new InvoiceRequestItem();
        it1.setProductId(testProduct.getId());
        it1.setQty(5);
        it1.setPricePerUnit(BigDecimal.valueOf(1450.00));
        it1.setGstPercent(BigDecimal.valueOf(18.00));
        req.setItems(List.of(it1));

        Invoice estimate = invoiceService.createInvoice(req);

        byte[] pdfBytes = pdfService.generatePdf(estimate, "A4");

        assertThat(pdfBytes).isNotNull();
        assertThat(pdfBytes.length).isGreaterThan(1000);
        String header = new String(pdfBytes, 0, Math.min(pdfBytes.length, 8), StandardCharsets.US_ASCII);
        assertThat(header).startsWith("%PDF-");
    }
}
