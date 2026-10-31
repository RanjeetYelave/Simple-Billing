package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.entities.*;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SalesReturnPdfServiceTest {

    private FirmDetailsService firmService;
    private SalesReturnPdfService pdfService;
    private FirmDetails mockFirm;

    @BeforeEach
    void setUp() {
        firmService = mock(FirmDetailsService.class);
        mockFirm = new FirmDetails();
        mockFirm.setFirmName("Rupee Traders Pvt Ltd");
        mockFirm.setGstin("27AAAAA0000A1Z5");
        mockFirm.setAddressLine1("123 Commerce Way");
        mockFirm.setCity("Mumbai");
        mockFirm.setState("Maharashtra");
        mockFirm.setPincode("400001");
        mockFirm.setPhone("9876543210");
        mockFirm.setEmail("billing@rupeetraders.com");

        when(firmService.get(any())).thenReturn(mockFirm);
        when(firmService.getFirst()).thenReturn(mockFirm);

        pdfService = new SalesReturnPdfService(firmService);
    }

    private SalesReturn createSampleReturn(boolean isDiscountAware) {
        Customer customer = new Customer();
        customer.setName("Acme Industries");
        customer.setGstin("27BBBBB1111B1Z2");

        Invoice invoice = new Invoice();
        invoice.setId(500L);
        invoice.setInvoiceNumber("INV-500");

        SalesReturn sr = new SalesReturn();
        sr.setId(10L);
        sr.setReturnNumber("CN-1001");
        sr.setReturnDate(LocalDate.of(2026, 10, 28));
        sr.setCustomer(customer);
        sr.setInvoice(invoice);
        sr.setReason("Defective batch");
        sr.setRefundMode("BANK_TRANSFER");

        SalesReturnItem item = new SalesReturnItem();
        item.setProductName("Precision Bearing");
        item.setHsnCode("8482");
        item.setUnit("pcs");
        item.setReturnQty(10);
        item.setUnitPrice(BigDecimal.valueOf(100.00));
        item.setGstPercent(BigDecimal.valueOf(18.00));
        item.setGstAmount(BigDecimal.valueOf(145.80));
        item.setRefundTotal(BigDecimal.valueOf(955.80));
        sr.setItems(Collections.singletonList(item));

        if (isDiscountAware) {
            sr.setIncludeDiscounts(true);
            sr.setGrossAmount(BigDecimal.valueOf(1000.00));
            sr.setProductDiscountAmount(BigDecimal.valueOf(100.00));
            sr.setInvoiceDiscountAmount(BigDecimal.valueOf(90.00));
            sr.setSubtotal(BigDecimal.valueOf(810.00)); // net taxable
            sr.setTaxAmount(BigDecimal.valueOf(145.80));
            sr.setCgstAmount(BigDecimal.valueOf(72.90));
            sr.setSgstAmount(BigDecimal.valueOf(72.90));
            sr.setTotalRefundAmount(BigDecimal.valueOf(955.80));
        } else {
            sr.setIncludeDiscounts(false);
            sr.setGrossAmount(BigDecimal.ZERO);
            sr.setProductDiscountAmount(BigDecimal.ZERO);
            sr.setInvoiceDiscountAmount(BigDecimal.ZERO);
            sr.setSubtotal(BigDecimal.valueOf(900.00));
            sr.setTaxAmount(BigDecimal.valueOf(162.00));
            sr.setCgstAmount(BigDecimal.valueOf(81.00));
            sr.setSgstAmount(BigDecimal.valueOf(81.00));
            sr.setTotalRefundAmount(BigDecimal.valueOf(1062.00));
        }

        return sr;
    }

    @Test
    void testLegacyCreditNotePdfRenderingDoesNotShowDiscountBreakdown() throws Exception {
        SalesReturn legacyReturn = createSampleReturn(false);
        byte[] pdfBytes = pdfService.generatePdf(legacyReturn, "A4");

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);

        PdfReader reader = new PdfReader(pdfBytes);
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        String text = extractor.getTextFromPage(1);

        assertTrue(text.contains("CREDIT NOTE"));
        assertTrue(text.contains("CN-1001"));
        assertTrue(text.contains("Taxable Return Amount"));
        assertFalse(text.contains("Gross Value of Returned Items"));
        assertFalse(text.contains("Product-Level Discount"));
        assertFalse(text.contains("Invoice-Level Discount"));
        reader.close();
    }

    @Test
    void testDiscountAwareCreditNotePdfRenderingShowsFullBreakdown() throws Exception {
        SalesReturn discountReturn = createSampleReturn(true);
        byte[] pdfBytes = pdfService.generatePdf(discountReturn, "A4");

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);

        PdfReader reader = new PdfReader(pdfBytes);
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        String text = extractor.getTextFromPage(1);
        assertTrue(text.contains("CREDIT NOTE"));
        assertTrue(text.contains("CN-1001"));
        assertTrue(text.contains("Gross Value of Returned Items"));
        assertTrue(text.contains("Product-Level Discount"));
        assertTrue(text.contains("Invoice-Level Discount"));
        assertTrue(text.contains("Net Taxable Return Value"));
        assertTrue(text.contains("CGST Reversal"));
        assertTrue(text.contains("SGST Reversal"));
        assertTrue(text.contains("Total Refund / Credit"));
        reader.close();
    }

    @Test
    void testThermal80mmDiscountAwarePdfRendering() throws Exception {
        SalesReturn discountReturn = createSampleReturn(true);
        byte[] pdfBytes = pdfService.generatePdf(discountReturn, "80mm", "THERMAL_80MM", null, null);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);

        PdfReader reader = new PdfReader(pdfBytes);
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        String text = extractor.getTextFromPage(1);

        assertTrue(text.contains("CREDIT NOTE"));
        assertTrue(text.contains("Gross Returned"));
        assertTrue(text.contains("Product Disc"));
        assertTrue(text.contains("Inv Disc"));
        assertTrue(text.contains("Net Taxable"));
        reader.close();
    }
}
