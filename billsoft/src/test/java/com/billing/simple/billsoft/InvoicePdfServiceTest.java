package com.billing.simple.billsoft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

        when(firmService.get(any())).thenReturn(firm);
        when(firmService.getFirst()).thenReturn(firm);

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
            text.append(extractor.getTextFromPage(i)).append(" ");
        }
        reader.close();
        return text.toString().replaceAll("\\s+", " ");
    }

    @Test
    void pdfShouldContainCoreInvoiceDetails() throws Exception {
        Invoice invoice = sampleInvoice();
        byte[] pdf = pdfService.generatePdf(invoice, "A4");

        String text = extractPdfText(pdf);

        assertThat(text).containsIgnoringCase("TAX INVOICE");
        assertThat(text).contains("INV-010");
        assertThat(text).containsIgnoringCase("Customer A");
        assertThat(text).contains("212.40");
        assertThat(text).containsIgnoringCase("Test Firm");
        assertThat(text).contains("180.00");
    }

    @Test
    void pdfShouldShowEstimateTitleWhenStatusIsEstimate() throws Exception {
        Invoice invoice = sampleInvoice();
        invoice.setStatus(InvoiceStatus.ESTIMATE);
        invoice.setEstimateNumber("EST-009");

        byte[] pdf = pdfService.generatePdf(invoice, "A4");
        String text = extractPdfText(pdf);

        assertThat(text).containsIgnoringCase("Quotation");
        assertThat(text).contains("EST-009");
    }

    @Test
    void pdfShouldIncludeDiscountInLineItem() throws Exception {
        Invoice invoice = sampleInvoice();
        byte[] pdf = pdfService.generatePdf(invoice, "A4");
        String text = extractPdfText(pdf);

        assertThat(text).contains("20.00"); // discount amount visible
    }

    @Test
    void pdfShouldIncludeHsnCodeWhenPresent() throws Exception {
        Invoice invoice = sampleInvoice();
        invoice.getItems().get(0).setHsnCode("8481");

        byte[] pdf = pdfService.generatePdf(invoice, "A4");
        String text = extractPdfText(pdf);

        assertThat(text).contains("8481");
    }

    @Test
    void pdfShouldRenderUpiQrOnlyWhenUpiIdConfiguredAndNotQuotation() throws Exception {
        // 1. Without UPI ID -> Generates successfully without error
        Invoice invoice = sampleInvoice();
        byte[] pdfNoUpi = pdfService.generatePdf(invoice, "A4");
        assertThat(pdfNoUpi).isNotEmpty();

        // 2. With UPI ID -> Generates with UPI QR code
        FirmDetails firmWithUpi = new FirmDetails();
        firmWithUpi.setFirmName("Test Firm");
        firmWithUpi.setUpiId("teststore@okaxis");
        when(firmService.getFirst()).thenReturn(firmWithUpi);
        when(firmService.get(any())).thenReturn(firmWithUpi);

        byte[] pdfWithUpi = pdfService.generatePdf(invoice, "A4");
        assertThat(pdfWithUpi).isNotEmpty();
        String textWithUpi = extractPdfText(pdfWithUpi);
        assertThat(textWithUpi).contains("SCAN TO PAY");
        assertThat(textWithUpi).contains("Scan with any UPI app");
        assertThat(textWithUpi).doesNotContain("teststore@okaxis"); // Raw UPI ID suppressed for clean design

        // 3. Quotation with UPI ID -> Quotation must NOT have UPI QR code
        invoice.setStatus(InvoiceStatus.ESTIMATE);
        byte[] quotePdf = pdfService.generatePdf(invoice, "A4");
        String quoteText = extractPdfText(quotePdf);
        assertThat(quoteText).doesNotContain("SCAN TO PAY");

        // 4. Large multi-item invoice with UPI ID (multi-page) -> Generates successfully
        Invoice largeInv = sampleInvoice();
        for (int i = 2; i <= 60; i++) {
            InvoiceItem it = new InvoiceItem();
            it.setQty(i);
            it.setUnit("pcs");
            it.setPricePerUnit(new BigDecimal("50.00"));
            it.setLineTotal(new BigDecimal(50 * i + ".00"));
            it.setInvoice(largeInv);
            largeInv.getItems().add(it);
        }
        byte[] largePdf = pdfService.generatePdf(largeInv, "A4");
        assertThat(largePdf).isNotEmpty();
        PdfReader reader = new PdfReader(largePdf);
        int pageCount = reader.getNumberOfPages();
        reader.close();
        assertThat(pageCount).isGreaterThan(1);
        String largeText = extractPdfText(largePdf);
        assertThat(largeText).contains("Item name");
        System.out.println("Generated " + pageCount + " pages for 60 items");

        // 5. Massive 200-item invoice in A4 and A5
        Invoice massiveInv = sampleInvoice();
        for (int i = 2; i <= 200; i++) {
            InvoiceItem it = new InvoiceItem();
            it.setQty(i);
            it.setUnit("pcs");
            it.setPricePerUnit(new BigDecimal("10.00"));
            it.setLineTotal(new BigDecimal(10 * i + ".00"));
            it.setInvoice(massiveInv);
            massiveInv.getItems().add(it);
        }
        byte[] massiveA4 = pdfService.generatePdf(massiveInv, "A4");
        assertThat(massiveA4).isNotEmpty();
        PdfReader massiveA4Reader = new PdfReader(massiveA4);
        assertThat(massiveA4Reader.getNumberOfPages()).isGreaterThan(3);
        massiveA4Reader.close();

        byte[] massiveA5 = pdfService.generatePdf(massiveInv, "A5");
        assertThat(massiveA5).isNotEmpty();
        PdfReader massiveA5Reader = new PdfReader(massiveA5);
        assertThat(massiveA5Reader.getNumberOfPages()).isGreaterThan(massiveA4Reader.getNumberOfPages());
        massiveA5Reader.close();
    }

    @Test
    void directNumberToWordsTests() {
        // 1. Positive amount
        assertThat(InvoicePdfService.numberToWords(new BigDecimal("1250.00")))
                .isEqualTo("One Thousand Two Hundred Fifty Rupees only");

        // 2. Zero & Null
        assertThat(InvoicePdfService.numberToWords(new BigDecimal("0.00")))
                .isEqualTo("Zero Rupees only");
        assertThat(InvoicePdfService.numberToWords(BigDecimal.ZERO))
                .isEqualTo("Zero Rupees only");
        assertThat(InvoicePdfService.numberToWords(null))
                .isEqualTo("Zero Rupees only");

        // 3. Negative amount
        assertThat(InvoicePdfService.numberToWords(new BigDecimal("-1250.00")))
                .isEqualTo("Negative One Thousand Two Hundred Fifty Rupees only");

        // 4. Negative decimal amount
        assertThat(InvoicePdfService.numberToWords(new BigDecimal("-1250.75")))
                .isEqualTo("Negative One Thousand Two Hundred Fifty Rupees and Seventy Five Paise only");

        // 5. Large positive amount
        assertThat(InvoicePdfService.numberToWords(new BigDecimal("15000000.50")))
                .isEqualTo("One Crore Fifty Lakh Rupees and Fifty Paise only");

        // 6. Large negative amount within supported range
        assertThat(InvoicePdfService.numberToWords(new BigDecimal("-15000000.50")))
                .isEqualTo("Negative One Crore Fifty Lakh Rupees and Fifty Paise only");

        // 7. Fractional amounts around rounding boundaries
        assertThat(InvoicePdfService.numberToWords(new BigDecimal("1250.49")))
                .isEqualTo("One Thousand Two Hundred Fifty Rupees and Forty Nine Paise only");
        assertThat(InvoicePdfService.numberToWords(new BigDecimal("1250.50")))
                .isEqualTo("One Thousand Two Hundred Fifty Rupees and Fifty Paise only");
        assertThat(InvoicePdfService.numberToWords(new BigDecimal("1250.99")))
                .isEqualTo("One Thousand Two Hundred Fifty Rupees and Ninety Nine Paise only");

        assertThat(InvoicePdfService.numberToWords(new BigDecimal("-1250.49")))
                .isEqualTo("Negative One Thousand Two Hundred Fifty Rupees and Forty Nine Paise only");
        assertThat(InvoicePdfService.numberToWords(new BigDecimal("-1250.50")))
                .isEqualTo("Negative One Thousand Two Hundred Fifty Rupees and Fifty Paise only");
        assertThat(InvoicePdfService.numberToWords(new BigDecimal("-1250.99")))
                .isEqualTo("Negative One Thousand Two Hundred Fifty Rupees and Ninety Nine Paise only");

        assertThat(InvoicePdfService.numberToWords(new BigDecimal("-0.50")))
                .isEqualTo("Negative Zero Rupees and Fifty Paise only");
    }

    @Test
    void directConvertToIndianWordsTests() {
        assertThat(InvoicePdfService.convertToIndianWords(1250L))
                .isEqualTo("One Thousand Two Hundred Fifty");
        assertThat(InvoicePdfService.convertToIndianWords(0L))
                .isEqualTo("");
        assertThat(InvoicePdfService.convertToIndianWords(-1250L))
                .isEqualTo("Negative One Thousand Two Hundred Fifty");
        assertThat(InvoicePdfService.convertToIndianWords(15000000L))
                .isEqualTo("One Crore Fifty Lakh");
        assertThat(InvoicePdfService.convertToIndianWords(-15000000L))
                .isEqualTo("Negative One Crore Fifty Lakh");
        assertThat(InvoicePdfService.convertToIndianWords(99L))
                .isEqualTo("Ninety Nine");
        assertThat(InvoicePdfService.convertToIndianWords(100L))
                .isEqualTo("One Hundred");
        assertThat(InvoicePdfService.convertToIndianWords(105L))
                .isEqualTo("One Hundred Five");
    }

    private void assertValidPdf(byte[] pdfBytes) {
        assertThat(pdfBytes).isNotNull();
        assertThat(pdfBytes).isNotEmpty();
        String header = new String(pdfBytes, 0, Math.min(pdfBytes.length, 5), java.nio.charset.StandardCharsets.US_ASCII);
        assertThat(header).startsWith("%PDF");
    }

    @Test
    void pdfGenerationWithPositiveAmount() throws Exception {
        Invoice inv = sampleInvoice();
        inv.setTotalAmount(new BigDecimal("1250.00"));

        byte[] pdf = pdfService.generatePdf(inv, "A4");
        assertValidPdf(pdf);

        String text = extractPdfText(pdf);
        assertThat(text).contains("One Thousand Two Hundred Fifty Rupees only");
    }

    @Test
    void pdfGenerationWithZeroAmount() throws Exception {
        Invoice inv = sampleInvoice();
        inv.setTotalAmount(BigDecimal.ZERO);

        byte[] pdf = pdfService.generatePdf(inv, "A4");
        assertValidPdf(pdf);

        String text = extractPdfText(pdf);
        assertThat(text).contains("Zero Rupees only");
    }

    @Test
    void pdfGenerationWithNegativeAmount() throws Exception {
        Invoice inv = sampleInvoice();
        inv.setTotalAmount(new BigDecimal("-1250.00"));

        byte[] pdf = pdfService.generatePdf(inv, "A4");
        assertValidPdf(pdf);

        String text = extractPdfText(pdf);
        assertThat(text).contains("Negative One Thousand Two Hundred Fifty Rupees only");
    }

    @Test
    void pdfGenerationWithNegativeDecimalAmount() throws Exception {
        Invoice inv = sampleInvoice();
        inv.setTotalAmount(new BigDecimal("-1250.75"));

        byte[] pdf = pdfService.generatePdf(inv, "A4");
        assertValidPdf(pdf);

        String text = extractPdfText(pdf);
        assertThat(text).contains("Negative One Thousand Two Hundred Fifty Rupees and Seventy Five Paise only");
    }

    @Test
    void pdfGenerationWithLargePositiveAmount() throws Exception {
        Invoice inv = sampleInvoice();
        inv.setTotalAmount(new BigDecimal("15000000.50"));

        byte[] pdf = pdfService.generatePdf(inv, "A4");
        assertValidPdf(pdf);

        String text = extractPdfText(pdf);
        assertThat(text).contains("One Crore Fifty Lakh Rupees and Fifty Paise only");
    }

    @Test
    void pdfGenerationWithLargeNegativeAmount() throws Exception {
        Invoice inv = sampleInvoice();
        inv.setTotalAmount(new BigDecimal("-15000000.50"));

        byte[] pdf = pdfService.generatePdf(inv, "A4");
        assertValidPdf(pdf);

        String text = extractPdfText(pdf);
        assertThat(text).contains("Negative One Crore Fifty Lakh Rupees and Fifty Paise only");
    }

    @Test
    void pdfGenerationWithFractionalRoundingBoundaries() throws Exception {
        BigDecimal[] testAmounts = {
                new BigDecimal("1250.49"),
                new BigDecimal("1250.50"),
                new BigDecimal("1250.99"),
                new BigDecimal("-1250.49"),
                new BigDecimal("-1250.50"),
                new BigDecimal("-1250.99")
        };

        for (BigDecimal amount : testAmounts) {
            Invoice inv = sampleInvoice();
            inv.setTotalAmount(amount);

            byte[] pdf = pdfService.generatePdf(inv, "A4");
            assertValidPdf(pdf);

            String expectedWords = InvoicePdfService.numberToWords(amount);
            String text = extractPdfText(pdf);
            assertThat(text).contains(expectedWords);
        }
    }
}
