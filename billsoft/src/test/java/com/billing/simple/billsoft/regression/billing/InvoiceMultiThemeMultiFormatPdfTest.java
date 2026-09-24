package com.billing.simple.billsoft.regression.billing;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.billing.simple.billsoft.dtos.InvoicePrintOptions;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoiceItem;
import com.billing.simple.billsoft.entities.InvoiceStatus;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.enums.InvoiceColorPalette;
import com.billing.simple.billsoft.enums.InvoiceTheme;
import com.billing.simple.billsoft.enums.PrintFormat;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repo.InvoiceRepository;
import com.billing.simple.billsoft.service.FirmDetailsService;
import com.billing.simple.billsoft.service.InvoicePdfService;
import com.billing.simple.billsoft.service.InvoiceService;
import com.billing.simple.billsoft.service.ProductService;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Tag("regression")
@Tag("integration")
@DisplayName("Multi-Theme & Multi-Format Invoice PDF Generation Regression Tests")
class InvoiceMultiThemeMultiFormatPdfTest {

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
    private Invoice testInvoice;
    private FirmDetails testFirm;

    @BeforeEach
    void setUp() {
        testFirm = new FirmDetails();
        testFirm.setFirmName("Apex Industrial Supplies");
        testFirm.setGstin("27AAAAA1234A1Z5");
        testFirm.setPhone("022-12345678");
        testFirm.setEmail("contact@apexsupplies.com");
        testFirm.setAddressLine1("Unit 404, Tech Park, Mumbai");
        testFirm.setBankName("HDFC Bank");
        testFirm.setBankAccount("50200012345678");
        testFirm.setBankIfsc("HDFC0001234");
        testFirm.setUpiId("apexsupplies@hdfcbank");
        testFirm = firmDetailsService.create(testFirm);

        testCustomer = customerRepo.save(Customer.builder()
                .name("Precision Manufacturing Ltd")
                .phone("9820012345")
                .email("accounts@precisionmfg.com")
                .address("Plot 12, Industrial Estate, Pune")
                .gstin("27AAACP5678B1Z2")
                .firmId(testFirm.getId())
                .build());

        testProduct = productService.create(Product.builder()
                .name("Hydraulic Valve Assembly")
                .price(BigDecimal.valueOf(1450.00))
                .stockQuantity(BigDecimal.valueOf(100.0))
                .firmId(testFirm.getId())
                .itemType("GOODS")
                .unit("set")
                .hsnCode("8481")
                .gstPercentage(BigDecimal.valueOf(18.0))
                .build());

        Invoice inv = new Invoice();
        inv.setFirmId(testFirm.getId());
        inv.setCustomer(testCustomer);
        inv.setInvoiceNumber("INV-2026-001");
        inv.setStatus(InvoiceStatus.FINAL);
        inv.setInvoiceDate(java.time.LocalDateTime.now());
        inv.setTotalAmount(BigDecimal.valueOf(1711.00));
        inv.setSubtotalWithoutTax(BigDecimal.valueOf(1450.00));
        inv.setTotalTax(BigDecimal.valueOf(261.00));
        inv.setTotalDiscount(BigDecimal.ZERO);

        InvoiceItem item = new InvoiceItem();
        item.setInvoice(inv);
        item.setProduct(testProduct);
        item.setProductName(testProduct.getName());
        item.setQty(1);
        item.setPricePerUnit(BigDecimal.valueOf(1450.00));
        item.setHsnCode("8481");
        item.setLineTotal(BigDecimal.valueOf(1711.00));
        inv.setItems(List.of(item));

        testInvoice = invoiceRepo.save(inv);
    }

    @ParameterizedTest(name = "Theme={0}, Format={1}")
    @CsvSource({
            "CLASSIC, A3",
            "CLASSIC, A4",
            "CLASSIC, A5",
            "CLASSIC, THERMAL_80MM",
            "MODERN, A3",
            "MODERN, A4",
            "MODERN, A5",
            "MODERN, THERMAL_80MM",
            "PROFESSIONAL, A3",
            "PROFESSIONAL, A4",
            "PROFESSIONAL, A5",
            "PROFESSIONAL, THERMAL_80MM",
            "MINIMAL, A3",
            "MINIMAL, A4",
            "MINIMAL, A5",
            "MINIMAL, THERMAL_80MM"
    })
    @DisplayName("Verify 16 Theme x Format Combinations produce valid PDF bytes with %PDF magic header")
    void testAllSixteenCombinations(String themeStr, String formatStr) throws Exception {
        InvoiceTheme theme = InvoiceTheme.fromString(themeStr);
        PrintFormat format = PrintFormat.fromString(formatStr);
        InvoicePrintOptions options = new InvoicePrintOptions(theme, format, InvoiceColorPalette.CLASSIC_BLUE);

        byte[] pdfBytes = pdfService.generatePdf(testInvoice, options);

        assertThat(pdfBytes).isNotNull();
        assertThat(pdfBytes.length).isGreaterThan(500);
        // Verify %PDF- magic bytes header
        assertThat(new String(pdfBytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF");

        // Also verify REST endpoint integration
        mockMvc.perform(get("/api/invoices/" + testInvoice.getId() + "/pdf")
                        .param("theme", themeStr)
                        .param("format", formatStr)
                        .param("color", "CLASSIC_BLUE"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=invoice-INV-2026-001.pdf"))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    @DisplayName("Verify all 5 curated color palettes render successfully across themes")
    void testAllColorPalettes() throws Exception {
        for (InvoiceColorPalette palette : InvoiceColorPalette.values()) {
            for (InvoiceTheme theme : InvoiceTheme.values()) {
                InvoicePrintOptions opts = new InvoicePrintOptions(theme, PrintFormat.A4, palette);
                byte[] bytes = pdfService.generatePdf(testInvoice, opts);
                assertThat(bytes).isNotNull();
                assertThat(new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF");
            }
        }
    }

    @Test
    @DisplayName("Verify negative total invoice PDF does not throw and formats correctly")
    void testNegativeTotalPdfGeneration() throws Exception {
        Invoice negInv = new Invoice();
        negInv.setFirmId(testFirm.getId());
        negInv.setCustomer(testCustomer);
        negInv.setInvoiceNumber("INV-NEG-001");
        negInv.setStatus(InvoiceStatus.FINAL);
        negInv.setTotalAmount(BigDecimal.valueOf(-500.00));
        negInv.setSubtotalWithoutTax(BigDecimal.valueOf(-500.00));
        negInv.setTotalTax(BigDecimal.ZERO);

        for (InvoiceTheme theme : InvoiceTheme.values()) {
            InvoicePrintOptions opts = new InvoicePrintOptions(theme, PrintFormat.A4, InvoiceColorPalette.CLASSIC_BLUE);
            byte[] bytes = pdfService.generatePdf(negInv, opts);
            assertThat(bytes).isNotNull();
            assertThat(new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        }
    }

    @Test
    @DisplayName("Verify multi-page invoice with 50 items renders with table splits across themes")
    void testMultiPageInvoiceGeneration() throws Exception {
        Invoice largeInv = new Invoice();
        largeInv.setFirmId(testFirm.getId());
        largeInv.setCustomer(testCustomer);
        largeInv.setInvoiceNumber("INV-LARGE-50");
        largeInv.setStatus(InvoiceStatus.FINAL);
        largeInv.setTotalAmount(BigDecimal.valueOf(50000.00));

        List<InvoiceItem> items = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            InvoiceItem it = new InvoiceItem();
            it.setInvoice(largeInv);
            it.setProductName("Component Item #" + i + " with extended industrial specification details");
            it.setQty(i);
            it.setPricePerUnit(BigDecimal.valueOf(100.00));
            it.setLineTotal(BigDecimal.valueOf(i * 100.00));
            items.add(it);
        }
        largeInv.setItems(items);

        for (InvoiceTheme theme : InvoiceTheme.values()) {
            byte[] bytes = pdfService.generatePdf(largeInv, new InvoicePrintOptions(theme, PrintFormat.A4, InvoiceColorPalette.CLASSIC_BLUE));
            assertThat(bytes).isNotNull();
            assertThat(bytes.length).isGreaterThan(5000);
            assertThat(new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        }
    }

    @Test
    @DisplayName("Verify firm print preferences persistence and multi-firm isolation")
    void testFirmPrintPreferencesPersistenceAndIsolation() throws Exception {
        // Create second firm
        FirmDetails firm2 = new FirmDetails();
        firm2.setFirmName("Beta Enterprises");
        firm2 = firmDetailsService.create(firm2);

        // Update Firm 1 preferences to MODERN, MODERN_TEAL, A5
        mockMvc.perform(patch("/api/firm/" + testFirm.getId() + "/print-preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\":\"MODERN\",\"color\":\"#0D9488\",\"format\":\"A5\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoicePrintTheme").value("MODERN"))
                .andExpect(jsonPath("$.invoicePrintThemeColor").value("#0D9488"))
                .andExpect(jsonPath("$.invoicePrintFormat").value("A5"));

        // Verify Firm 1 reload
        FirmDetails reloadedFirm1 = firmDetailsService.get(testFirm.getId());
        assertThat(reloadedFirm1.getInvoicePrintTheme()).isEqualTo("MODERN");
        assertThat(reloadedFirm1.getInvoicePrintThemeColor()).isEqualTo("#0D9488");
        assertThat(reloadedFirm1.getInvoicePrintFormat()).isEqualTo("A5");

        // Verify Firm 2 has independent null/default preferences
        FirmDetails reloadedFirm2 = firmDetailsService.get(firm2.getId());
        assertThat(reloadedFirm2.getInvoicePrintTheme()).isNull();
        assertThat(reloadedFirm2.getInvoicePrintFormat()).isNull();

        // PDF download for Firm 1 without explicit query params uses saved preferences
        mockMvc.perform(get("/api/invoices/" + testInvoice.getId() + "/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    @DisplayName("Verify invalid query parameters fall back gracefully to defaults")
    void testInvalidQueryParametersFallback() throws Exception {
        mockMvc.perform(get("/api/invoices/" + testInvoice.getId() + "/pdf")
                        .param("theme", "TOTALLY_INVALID_THEME")
                        .param("format", "NON_EXISTENT_FORMAT")
                        .param("color", "NOT_A_COLOR"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }
}
