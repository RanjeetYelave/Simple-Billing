package com.billing.simple.billsoft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoiceItem;
import com.billing.simple.billsoft.entities.InvoiceStatus;
import com.billing.simple.billsoft.service.FirmDetailsService;
import com.billing.simple.billsoft.service.InvoicePdfService;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

public class InvoicePdfServiceTest {

    private FirmDetailsService firmService;
    private InvoicePdfService pdfService;

    @BeforeEach
    void setup() {
        firmService = mock(FirmDetailsService.class);

        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Test Firm");
        firm.setOwnerName("Owner");
        firm.setEmail("test@example.com");
        firm.setGstin("29ABCDE1234F2Z5");
        firm.setPhone("9999999999");
        firm.setAddressLine1("123 Road");
        firm.setCity("City");
        firm.setState("State");
        firm.setPincode("123456");

        when(firmService.get(null)).thenReturn(firm);

        pdfService = new InvoicePdfService(firmService);
    }

    private Invoice sampleInvoice() {
        Invoice inv = new Invoice();
        inv.setStatus(InvoiceStatus.FINAL);
        inv.setInvoiceNumber("INV-010");
        inv.setInvoiceDate(LocalDateTime.now());
        inv.setTotalAmount(new BigDecimal("212.40"));
        inv.setSubtotalWithoutTax(new BigDecimal("180.00"));
        inv.setTotalDiscount(new BigDecimal("20.00"));
        inv.setTotalTax(new BigDecimal("32.40"));

        Customer c = new Customer();
        c.setName("Customer A");
        inv.setCustomer(c);

        InvoiceItem it = new InvoiceItem();
        it.setQty(2);
        it.setUnit("pcs");
        it.setPricePerUnit(new BigDecimal("100.00"));
        it.setAmountWithoutTax(new BigDecimal("200.00"));
        it.setDiscountType("VALUE");
        it.setDiscountValue(new BigDecimal("20.00"));
        it.setTaxableAmount(new BigDecimal("180.00"));
        it.setGstPercent(new BigDecimal("18.00"));
        it.setGstAmount(new BigDecimal("32.40"));
        it.setLineTotal(new BigDecimal("212.40"));
        it.setInvoice(inv);

        inv.getItems().add(it);

        return inv;
    }

    private String extractPdfText(byte[] pdfBytes) throws Exception {
        PdfReader reader = new PdfReader(pdfBytes);
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        StringBuilder text = new StringBuilder();

        int pages = reader.getNumberOfPages();
        for (int i = 1; i <= pages; i++) {
            text.append(extractor.getTextFromPage(i));
        }
        reader.close();
        return text.toString();
    }

    @Test
    void pdfShouldContainCoreInvoiceDetails() throws Exception {
        Invoice invoice = sampleInvoice();
        byte[] pdf = pdfService.generatePdf(invoice, "A4");

        String text = extractPdfText(pdf);

        assertThat(text).contains("TAX INVOICE");
        assertThat(text).contains("INV-010");
        assertThat(text).contains("Customer A");
        assertThat(text).contains("212.40");
        assertThat(text).contains("Test Firm");
        assertThat(text).contains("180.00");
        assertThat(text).contains("32.40");
    }

    @Test
    void pdfShouldShowEstimateTitleWhenStatusIsEstimate() throws Exception {
        Invoice invoice = sampleInvoice();
        invoice.setStatus(InvoiceStatus.ESTIMATE);
        invoice.setEstimateNumber("EST-009");

        byte[] pdf = pdfService.generatePdf(invoice, "A4");
        String text = extractPdfText(pdf);

        assertThat(text).contains("ESTIMATE");
        assertThat(text).contains("EST-009");
    }

    @Test
    void pdfShouldIncludeDiscountInLineItem() throws Exception {
        Invoice invoice = sampleInvoice();
        byte[] pdf = pdfService.generatePdf(invoice, "A4");
        String text = extractPdfText(pdf);

        assertThat(text).contains("20.00"); // discount amount visible
    }
}
