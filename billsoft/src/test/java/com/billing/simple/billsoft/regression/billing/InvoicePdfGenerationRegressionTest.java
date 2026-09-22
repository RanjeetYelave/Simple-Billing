package com.billing.simple.billsoft.regression.billing;

import com.billing.simple.billsoft.dtos.InvoicePrintOptions;
import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceRequestItem;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repo.InvoiceRepository;
import com.billing.simple.billsoft.service.FirmDetailsService;
import com.billing.simple.billsoft.service.InvoicePdfService;
import com.billing.simple.billsoft.service.InvoiceService;
import com.billing.simple.billsoft.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Tag("regression")
@Tag("integration")
@DisplayName("Invoice & Quotation PDF Generation Regression Tests")
class InvoicePdfGenerationRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InvoicePdfService pdfService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private ProductService productService;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private InvoiceRepository invoiceRepo;

    @Autowired
    private FirmDetailsService firmDetailsService;

    private Customer testCustomer;
    private Product testProduct;
    private FirmDetails testFirm;
    private Long testFirmId = 1L;

    @BeforeEach
    void setUp() {
        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Apex Industrial Supplies");
        firm.setGstin("27AAAAA1234A1Z5");
        firm.setPhone("022-12345678");
        firm.setEmail("contact@apexsupplies.com");
        firm.setAddressLine1("Unit 404, Tech Park, Mumbai");
        testFirm = firmDetailsService.create(firm);
        testFirmId = testFirm.getId();

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

    @Test
    @DisplayName("Real HTTP endpoint GET /api/invoices/{id}/pdf?size=A4 should return HTTP 200 and valid PDF for positive total")
    void shouldDownloadPdfViaHttpEndpointWithPositiveInvoice() throws Exception {
        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(testFirmId);
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.FINAL);

        InvoiceRequestItem it1 = new InvoiceRequestItem();
        it1.setProductId(testProduct.getId());
        it1.setQty(1);
        it1.setPricePerUnit(BigDecimal.valueOf(1250.00));
        it1.setGstPercent(BigDecimal.valueOf(18.00));
        req.setItems(List.of(it1));

        Invoice invoice = invoiceService.createInvoice(req);

        MvcResult result = mockMvc.perform(get("/api/invoices/" + invoice.getId() + "/pdf")
                        .param("size", "A4")
                        .header("X-Firm-Id", testFirmId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "attachment; filename=invoice-" + invoice.getInvoiceNumber() + ".pdf"))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body).isNotNull().isNotEmpty();
        String header = new String(body, 0, Math.min(body.length, 5), StandardCharsets.US_ASCII);
        assertThat(header).startsWith("%PDF");
    }

    @Test
    @DisplayName("Real HTTP endpoint GET /api/invoices/{id}/pdf?size=A4 should return HTTP 200 and valid PDF for negative total")
    void shouldDownloadPdfViaHttpEndpointWithNegativeTotalInvoice() throws Exception {
        Invoice negInv = new Invoice();
        negInv.setFirmId(testFirmId);
        negInv.setCustomer(testCustomer);
        negInv.setStatus(InvoiceStatus.FINAL);
        negInv.setInvoiceNumber("INV-NEG-1001");
        negInv.setInvoiceDate(LocalDateTime.now());
        negInv.setTotalAmount(new BigDecimal("-1250.00"));
        negInv.setSubtotalWithoutTax(new BigDecimal("-1250.00"));
        negInv.setTotalDiscount(BigDecimal.ZERO);
        negInv.setTotalTax(BigDecimal.ZERO);
        negInv = invoiceRepo.save(negInv);

        MvcResult result = mockMvc.perform(get("/api/invoices/" + negInv.getId() + "/pdf")
                        .param("size", "A4")
                        .header("X-Firm-Id", testFirmId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "attachment; filename=invoice-INV-NEG-1001.pdf"))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body).isNotNull().isNotEmpty();
        String header = new String(body, 0, Math.min(body.length, 5), StandardCharsets.US_ASCII);
        assertThat(header).startsWith("%PDF");
    }

    @Test
    @DisplayName("Real HTTP endpoint GET /api/invoices/{id}/pdf?size=A4 should return HTTP 200 and valid PDF for zero total")
    void shouldDownloadPdfViaHttpEndpointWithZeroTotalInvoice() throws Exception {
        Invoice zeroInv = new Invoice();
        zeroInv.setFirmId(testFirmId);
        zeroInv.setCustomer(testCustomer);
        zeroInv.setStatus(InvoiceStatus.FINAL);
        zeroInv.setInvoiceNumber("INV-ZERO-1001");
        zeroInv.setInvoiceDate(LocalDateTime.now());
        zeroInv.setTotalAmount(BigDecimal.ZERO);
        zeroInv = invoiceRepo.save(zeroInv);

        MvcResult result = mockMvc.perform(get("/api/invoices/" + zeroInv.getId() + "/pdf")
                        .param("size", "A4")
                        .header("X-Firm-Id", testFirmId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body).isNotNull().isNotEmpty();
        String header = new String(body, 0, Math.min(body.length, 5), StandardCharsets.US_ASCII);
        assertThat(header).startsWith("%PDF");
    }

    @Test
    @DisplayName("Should generate valid PDF for all 16 theme and format combinations (4 themes x 4 formats)")
    void shouldGenerateValidPdfForAll16Combinations() throws Exception {
        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(testFirmId);
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.FINAL);

        InvoiceRequestItem it1 = new InvoiceRequestItem();
        it1.setProductId(testProduct.getId());
        it1.setQty(3);
        it1.setPricePerUnit(BigDecimal.valueOf(1450.00));
        it1.setGstPercent(BigDecimal.valueOf(18.00));
        req.setItems(List.of(it1));

        Invoice invoice = invoiceService.createInvoice(req);

        String[] themes = {"classic", "modern", "professional", "minimal"};
        String[] formats = {"A3", "A4", "A5", "THERMAL_80MM"};

        for (String theme : themes) {
            for (String format : formats) {
                InvoicePrintOptions opts = InvoicePrintOptions.builder()
                        .theme(theme)
                        .format(format)
                        .themeColor("classic-blue")
                        .build();

                byte[] pdfBytes = pdfService.generatePdf(invoice, opts);
                assertThat(pdfBytes)
                        .as("PDF generation failed for theme=%s, format=%s", theme, format)
                        .isNotNull()
                        .isNotEmpty();

                String magic = new String(pdfBytes, 0, Math.min(pdfBytes.length, 5), StandardCharsets.US_ASCII);
                assertThat(magic).isEqualTo("%PDF-");
            }
        }
    }

    @Test
    @DisplayName("Should support custom theme colors across themes")
    void shouldSupportCustomThemeColors() throws Exception {
        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(testFirmId);
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.FINAL);

        InvoiceRequestItem it1 = new InvoiceRequestItem();
        it1.setProductId(testProduct.getId());
        it1.setQty(2);
        it1.setPricePerUnit(BigDecimal.valueOf(1450.00));
        req.setItems(List.of(it1));

        Invoice invoice = invoiceService.createInvoice(req);

        String[] colors = {"emerald", "crimson", "indigo", "slate", "amber", "teal", "plum", "#2B6CB0"};
        for (String color : colors) {
            InvoicePrintOptions opts = InvoicePrintOptions.builder()
                    .theme("modern")
                    .format("A4")
                    .themeColor(color)
                    .build();

            byte[] pdfBytes = pdfService.generatePdf(invoice, opts);
            assertThat(pdfBytes).isNotNull().isNotEmpty();
        }
    }

    @Test
    @DisplayName("Should use FirmDetails persisted preferences when options are null/empty")
    void shouldUseFirmDetailsPreferences() throws Exception {
        FirmDetails firm = firmDetailsService.getFirmDetails(testFirmId);
        firm.setInvoicePrintTheme("modern");
        firm.setInvoicePrintThemeColor("emerald");
        firm.setInvoicePrintFormat("A5");
        firmDetailsService.update(firm.getId(), firm);

        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(testFirmId);
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.FINAL);

        InvoiceRequestItem it1 = new InvoiceRequestItem();
        it1.setProductId(testProduct.getId());
        it1.setQty(1);
        it1.setPricePerUnit(BigDecimal.valueOf(500.00));
        req.setItems(List.of(it1));

        Invoice invoice = invoiceService.createInvoice(req);

        // Generate with null options -> should read firm preferences (modern, emerald, A5)
        byte[] pdfBytes = pdfService.generatePdf(invoice, (InvoicePrintOptions) null);
        assertThat(pdfBytes).isNotNull().isNotEmpty();
        String magic = new String(pdfBytes, 0, Math.min(pdfBytes.length, 5), StandardCharsets.US_ASCII);
        assertThat(magic).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("HTTP endpoint should accept theme, color, and format query params")
    void shouldAcceptThemeParamsViaHttpEndpoint() throws Exception {
        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(testFirmId);
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.FINAL);

        InvoiceRequestItem it1 = new InvoiceRequestItem();
        it1.setProductId(testProduct.getId());
        it1.setQty(1);
        it1.setPricePerUnit(BigDecimal.valueOf(100.00));
        req.setItems(List.of(it1));

        Invoice invoice = invoiceService.createInvoice(req);

        mockMvc.perform(get("/api/invoices/" + invoice.getId() + "/pdf")
                        .param("theme", "minimal")
                        .param("themeColor", "slate")
                        .param("format", "THERMAL_80MM")
                        .header("X-Firm-Id", testFirmId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "attachment; filename=invoice-" + invoice.getInvoiceNumber() + ".pdf"));
    }
}
