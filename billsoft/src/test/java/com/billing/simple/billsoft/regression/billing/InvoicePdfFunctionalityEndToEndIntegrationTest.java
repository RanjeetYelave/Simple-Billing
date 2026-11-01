package com.billing.simple.billsoft.regression.billing;

import com.billing.simple.billsoft.dtos.InvoicePrintOptions;
import com.billing.simple.billsoft.dtos.SalesReturnRequest;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.enums.InvoiceColorPalette;
import com.billing.simple.billsoft.enums.InvoiceTheme;
import com.billing.simple.billsoft.enums.PrintFormat;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import com.billing.simple.billsoft.service.FirmDetailsService;
import com.billing.simple.billsoft.service.InvoicePdfService;
import com.billing.simple.billsoft.service.InvoiceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DisplayName("Invoice, Quotation & Credit Note PDF End-To-End Functionality Integration Tests")
public class InvoicePdfFunctionalityEndToEndIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InvoicePdfService invoicePdfService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private FirmDetailsService firmDetailsService;

    @Autowired
    private FirmDetailsRepository firmRepo;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private InvoiceRepository invoiceRepo;

    @Autowired
    private SalesReturnRepository salesReturnRepo;

    private FirmDetails firm1;
    private FirmDetails firm2;
    private Customer customer1;
    private Product product1;
    private Invoice standardInvoice;
    private Invoice quotation;

    @BeforeEach
    void setUp() {
        TenantContext.clear();

        firm1 = new FirmDetails();
        firm1.setFirmName("RupeeCRM Primary Electronics");
        firm1.setGstin("27AAAAA1234A1Z5");
        firm1.setPhone("022-25001122");
        firm1.setEmail("billing@rupeecrm.com");
        firm1.setAddressLine1("Shop 10, Crystal Plaza, Andheri West");
        firm1.setCity("Mumbai");
        firm1.setState("Maharashtra");
        firm1.setPincode("400053");
        firm1.setBankName("State Bank of India");
        firm1.setBankAccount("12345678901");
        firm1.setBankIfsc("SBIN0001234");
        firm1.setUpiId("rupeecrm@sbi");
        firm1 = firmRepo.save(firm1);

        firm2 = new FirmDetails();
        firm2.setFirmName("RupeeCRM Secondary Hardware");
        firm2.setGstin("27BBBBB5678B2Z6");
        firm2.setPhone("020-24003344");
        firm2.setEmail("pune@rupeecrm.com");
        firm2 = firmRepo.save(firm2);

        TenantContext.setCurrentFirmId(firm1.getId());

        customer1 = customerRepo.save(Customer.builder()
                .name("Acme Industrial Corporation")
                .phone("9820011223")
                .email("procurement@acme.com")
                .address("Plot 45, MIDC Industrial Area, Pune")
                .gstin("27AACCA1234C1Z9")
                .firmId(firm1.getId())
                .build());

        product1 = productRepo.save(Product.builder()
                .name("Industrial Sensor Controller V2")
                .price(BigDecimal.valueOf(2500.00))
                .stockQuantity(BigDecimal.valueOf(50.0))
                .firmId(firm1.getId())
                .itemType("GOODS")
                .unit("pcs")
                .hsnCode("9032")
                .gstPercentage(BigDecimal.valueOf(18.0))
                .build());

        // 1. Standard Tax Invoice
        Invoice inv = new Invoice();
        inv.setFirmId(firm1.getId());
        inv.setCustomer(customer1);
        inv.setInvoiceNumber("INV-2026-0001");
        inv.setStatus(InvoiceStatus.FINAL);
        inv.setInvoiceDate(LocalDateTime.now());
        inv.setTotalAmount(BigDecimal.valueOf(2950.00));
        inv.setSubtotalWithoutTax(BigDecimal.valueOf(2500.00));
        inv.setTotalTax(BigDecimal.valueOf(450.00));
        inv.setTotalDiscount(BigDecimal.valueOf(0.00));
        inv.setPaid(true);

        InvoiceItem item1 = new InvoiceItem();
        item1.setInvoice(inv);
        item1.setProduct(product1);
        item1.setProductName(product1.getName());
        item1.setQty(1);
        item1.setPricePerUnit(BigDecimal.valueOf(2500.00));
        item1.setHsnCode("9032");
        item1.setLineTotal(BigDecimal.valueOf(2950.00));
        inv.setItems(new ArrayList<>(List.of(item1)));

        standardInvoice = invoiceRepo.save(inv);

        // 2. Quotation / Estimate
        Invoice est = new Invoice();
        est.setFirmId(firm1.getId());
        est.setCustomer(customer1);
        est.setEstimateNumber("EST-2026-0001");
        est.setStatus(InvoiceStatus.ESTIMATE);
        est.setInvoiceDate(LocalDateTime.now());
        est.setTotalAmount(BigDecimal.valueOf(5900.00));
        est.setSubtotalWithoutTax(BigDecimal.valueOf(5000.00));
        est.setTotalTax(BigDecimal.valueOf(900.00));
        est.setTotalDiscount(BigDecimal.valueOf(100.00));
        est.setPaid(false);

        InvoiceItem estItem = new InvoiceItem();
        estItem.setInvoice(est);
        estItem.setProduct(product1);
        estItem.setProductName(product1.getName());
        estItem.setQty(2);
        estItem.setPricePerUnit(BigDecimal.valueOf(2500.00));
        estItem.setHsnCode("9032");
        estItem.setLineTotal(BigDecimal.valueOf(5900.00));
        est.setItems(new ArrayList<>(List.of(estItem)));

        quotation = invoiceRepo.save(est);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Verify Standard Invoice PDF Download and Preview returns valid PDF bytes and headers")
    void testStandardInvoicePdfPreviewAndDownload() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/invoices/" + standardInvoice.getId() + "/pdf")
                        .header("X-Firm-Id", firm1.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "attachment; filename=invoice-INV-2026-0001.pdf"))
                .andReturn();

        byte[] pdfBytes = result.getResponse().getContentAsByteArray();
        assertThat(pdfBytes).isNotEmpty();
        assertThat(pdfBytes.length).isGreaterThan(500);
        assertThat(new String(pdfBytes, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("Verify Quotation / Estimate PDF Download and Preview returns estimate filename and valid bytes")
    void testQuotationPdfPreviewAndDownload() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/invoices/" + quotation.getId() + "/pdf")
                        .header("X-Firm-Id", firm1.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "attachment; filename=estimate-EST-2026-0001.pdf"))
                .andReturn();

        byte[] pdfBytes = result.getResponse().getContentAsByteArray();
        assertThat(pdfBytes).isNotEmpty();
        assertThat(pdfBytes.length).isGreaterThan(500);
        assertThat(new String(pdfBytes, 0, 4)).isEqualTo("%PDF");
    }

    @ParameterizedTest(name = "Format={0}, Theme={1}")
    @CsvSource({
            "A4, CLASSIC",
            "A4, MODERN",
            "A4, PROFESSIONAL",
            "A4, MINIMAL",
            "A5, CLASSIC",
            "A5, MODERN",
            "A3, CLASSIC",
            "A3, PROFESSIONAL",
            "THERMAL_80MM, CLASSIC"
    })
    @DisplayName("Verify all format and theme combinations generate non-empty valid PDFs")
    void testAllFormatAndThemeCombinations(String format, String theme) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/invoices/" + standardInvoice.getId() + "/pdf")
                        .header("X-Firm-Id", firm1.getId())
                        .param("format", format)
                        .param("theme", theme)
                        .param("color", "#007AA6"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn();

        byte[] pdfBytes = result.getResponse().getContentAsByteArray();
        assertThat(pdfBytes).isNotEmpty();
        assertThat(new String(pdfBytes, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("Verify Credit Note / Sales Return PDF Download and Preview returns CreditNote filename and valid bytes")
    void testSalesReturnCreditNotePdfDownload() throws Exception {
        SalesReturnRequest req = new SalesReturnRequest();
        req.setReason("DEFECTIVE");
        req.setFirmId(firm1.getId());
        SalesReturnRequest.SalesReturnItemRequest itemReq = SalesReturnRequest.SalesReturnItemRequest.builder()
                .invoiceItemId(standardInvoice.getItems().get(0).getId())
                .returnQty(1)
                .build();
        req.setItems(List.of(itemReq));

        SalesReturn salesReturn = invoiceService.createSalesReturn(standardInvoice.getId(), req);
        assertNotNull(salesReturn);

        MvcResult result = mockMvc.perform(get("/api/returns/" + salesReturn.getId() + "/pdf")
                        .header("X-Firm-Id", firm1.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "attachment; filename=CreditNote-" + salesReturn.getReturnNumber() + ".pdf"))
                .andReturn();

        byte[] pdfBytes = result.getResponse().getContentAsByteArray();
        assertThat(pdfBytes).isNotEmpty();
        assertThat(new String(pdfBytes, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("Verify Legacy Invoice without firm snapshot gracefully renders PDF via controller")
    void testLegacyInvoicePdfDownloadGracefulFallback() throws Exception {
        // Create legacy invoice without firm snapshot
        Invoice legacy = new Invoice();
        legacy.setFirmId(firm1.getId());
        legacy.setCustomer(customer1);
        legacy.setInvoiceNumber("INV-LEGACY-999");
        legacy.setStatus(InvoiceStatus.FINAL);
        legacy.setInvoiceDate(LocalDateTime.now().minusMonths(6));
        legacy.setTotalAmount(BigDecimal.valueOf(1500.00));
        legacy.setSubtotalWithoutTax(BigDecimal.valueOf(1500.00));
        legacy.setTotalTax(BigDecimal.ZERO);
        legacy.setFirmNameSnapshot(null);
        legacy.setFirmGstinSnapshot(null);
        legacy.setFirmAddressSnapshot(null);
        legacy.setFirmPhoneSnapshot(null);
        legacy.setFirmEmailSnapshot(null);

        InvoiceItem legItem = new InvoiceItem();
        legItem.setInvoice(legacy);
        legItem.setProductName("Legacy Consultation Service");
        legItem.setQty(1);
        legItem.setPricePerUnit(BigDecimal.valueOf(1500.00));
        legItem.setLineTotal(BigDecimal.valueOf(1500.00));
        legacy.setItems(new ArrayList<>(List.of(legItem)));

        Invoice savedLegacy = invoiceRepo.save(legacy);

        // When accessed via controller without firm header, must succeed using primary firm
        TenantContext.clear();
        MvcResult result = mockMvc.perform(get("/api/invoices/" + savedLegacy.getId() + "/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn();

        byte[] pdfBytes = result.getResponse().getContentAsByteArray();
        assertThat(pdfBytes).isNotEmpty();
        assertThat(new String(pdfBytes, 0, 4)).isEqualTo("%PDF");

        // BUT direct call to invoicePdfService with NO firm and NO context still enforces AUDIT-022
        TenantContext.clear();
        Invoice directInv = new Invoice();
        directInv.setFirmId(null);
        directInv.setStatus(InvoiceStatus.FINAL);
        directInv.setInvoiceNumber("INV-AUDIT-022-DIRECT");
        assertThrows(TenantSecurityException.class, () -> {
            invoicePdfService.generatePdf(directInv, InvoicePrintOptions.builder().build());
        });
    }

    @Test
    @DisplayName("Verify cross-firm tenant isolation is strictly enforced on PDF endpoints")
    void testCrossFirmIsolationEnforcedOnPdf() throws Exception {
        // Invoice belongs to Firm 1. When requested with Firm 2 header, must return 404
        mockMvc.perform(get("/api/invoices/" + standardInvoice.getId() + "/pdf")
                        .header("X-Firm-Id", firm2.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Verify invoice with discounts, cash customer, and custom items generates valid PDF")
    void testInvoiceWithDiscountAndCashCustomer() throws Exception {
        Invoice customInv = new Invoice();
        customInv.setFirmId(firm1.getId());
        customInv.setCustomer(null); // Cash customer
        customInv.setInvoiceNumber("INV-CASH-DISC");
        customInv.setStatus(InvoiceStatus.FINAL);
        customInv.setInvoiceDate(LocalDateTime.now());
        customInv.setTotalAmount(BigDecimal.valueOf(900.00));
        customInv.setSubtotalWithoutTax(BigDecimal.valueOf(1000.00));
        customInv.setTotalTax(BigDecimal.ZERO);
        customInv.setTotalDiscount(BigDecimal.valueOf(100.00));
        customInv.setInvoiceDiscountType("VALUE");
        customInv.setInvoiceDiscountValue(BigDecimal.valueOf(100.00));

        InvoiceItem customItem = new InvoiceItem();
        customItem.setInvoice(customInv);
        customItem.setProductName("Custom Spare Parts");
        customItem.setQty(2);
        customItem.setUnit("box");
        customItem.setPricePerUnit(BigDecimal.valueOf(500.00));
        customItem.setLineTotal(BigDecimal.valueOf(900.00));
        customInv.setItems(new ArrayList<>(List.of(customItem)));

        Invoice saved = invoiceRepo.save(customInv);

        MvcResult result = mockMvc.perform(get("/api/invoices/" + saved.getId() + "/pdf")
                        .header("X-Firm-Id", firm1.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn();

        byte[] pdfBytes = result.getResponse().getContentAsByteArray();
        assertThat(pdfBytes).isNotEmpty();
        assertThat(new String(pdfBytes, 0, 4)).isEqualTo("%PDF");
    }
}
