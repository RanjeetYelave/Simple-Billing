package com.billing.simple.billsoft.service.pdf.theme;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.billing.simple.billsoft.dtos.InvoicePrintOptions;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoiceItem;
import com.billing.simple.billsoft.enums.InvoiceColorPalette;
import com.billing.simple.billsoft.enums.PrintFormat;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

public class ModernThemeRenderer {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final Color TEXT_DARK = new Color(17, 24, 39);
    private static final Color TEXT_MUTED = new Color(107, 114, 128);
    private static final Color BORDER_COLOR = new Color(229, 231, 235);

    public static byte[] render(Invoice invoice, FirmDetails firm, InvoicePrintOptions options) throws Exception {
        PrintFormat format = options.getFormat();
        InvoiceColorPalette palette = options.getColorPalette();
        Color primary = palette.getPrimaryColor();
        Color lightTint = palette.getLightTintColor();

        boolean isA5 = format == PrintFormat.A5;
        boolean isA3 = format == PrintFormat.A3;
        float scale = isA5 ? 0.85f : (isA3 ? 1.15f : 1.0f);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(format.getPageSize(), format.getMarginLeft(), format.getMarginRight(), format.getMarginTop(), format.getMarginBottom());
        PdfWriter writer = PdfWriter.getInstance(doc, baos);

        PageNumberHelper pageHelper = new PageNumberHelper(8f * scale);
        writer.setPageEvent(pageHelper);

        doc.open();

        Font firmNameFont = new Font(Font.HELVETICA, 14f * scale, Font.BOLD, primary);
        Font titleFont = new Font(Font.HELVETICA, 16f * scale, Font.BOLD, primary);
        Font boldText = new Font(Font.HELVETICA, 8.5f * scale, Font.BOLD, TEXT_DARK);
        Font normalText = new Font(Font.HELVETICA, 8f * scale, Font.NORMAL, TEXT_DARK);
        Font smallMuted = new Font(Font.HELVETICA, 7.5f * scale, Font.NORMAL, TEXT_MUTED);
        Font thFont = new Font(Font.HELVETICA, 8f * scale, Font.BOLD, Color.WHITE);
        Font totalBoldFont = new Font(Font.HELVETICA, 9.5f * scale, Font.BOLD, primary);

        boolean isEstimate = invoice != null && invoice.getStatus() != null && invoice.getStatus().name().equalsIgnoreCase("ESTIMATE");
        String docTitle = isEstimate ? "QUOTATION" : "TAX INVOICE";

        // 1. TOP HEADER: Split Firm on Left, Modern Document Title & Meta on Right
        PdfPTable headerTable = new PdfPTable(new float[]{2.5f, 1.5f});
        headerTable.setWidthPercentage(100);
        headerTable.setSpacingAfter(10f * scale);

        PdfPCell leftHeader = new PdfPCell();
        leftHeader.setBorder(Rectangle.NO_BORDER);

        // Logo + Firm Info
        boolean hasLogo = false;
        if (firm != null && firm.getLogoBase64() != null && !firm.getLogoBase64().isBlank()) {
            try {
                String b64 = firm.getLogoBase64().trim();
                if (b64.startsWith("data:")) {
                    int idx = b64.indexOf("base64,");
                    if (idx >= 0) b64 = b64.substring(idx + 7);
                }
                byte[] bytes = Base64.getDecoder().decode(b64);
                Image logo = Image.getInstance(bytes);
                logo.scaleToFit(80f * scale, 50f * scale);
                logo.setAlignment(Image.LEFT);
                leftHeader.addElement(logo);
                hasLogo = true;
            } catch (Exception ignored) {}
        }

        String fName = (firm != null && firm.getFirmName() != null && !firm.getFirmName().isBlank())
                ? firm.getFirmName().toUpperCase() : "RUPEECRM STORE";
        Paragraph pFirm = new Paragraph(fName, firmNameFont);
        if (hasLogo) pFirm.setSpacingBefore(4f);
        leftHeader.addElement(pFirm);

        if (firm != null) {
            StringBuilder addr = new StringBuilder();
            if (firm.getAddressLine1() != null) addr.append(firm.getAddressLine1());
            if (firm.getAddressLine2() != null) { if (addr.length() > 0) addr.append(", "); addr.append(firm.getAddressLine2()); }
            if (firm.getCity() != null) { if (addr.length() > 0) addr.append(", "); addr.append(firm.getCity()); }
            if (firm.getState() != null) { if (addr.length() > 0) addr.append(" - "); addr.append(firm.getState()); }
            if (firm.getPincode() != null) addr.append(" ").append(firm.getPincode());
            if (addr.length() > 0) leftHeader.addElement(new Paragraph(addr.toString(), smallMuted));

            StringBuilder contact = new StringBuilder();
            if (firm.getPhone() != null) contact.append("Phone: ").append(firm.getPhone());
            if (firm.getEmail() != null) { if (contact.length() > 0) contact.append(" | "); contact.append(firm.getEmail()); }
            if (contact.length() > 0) leftHeader.addElement(new Paragraph(contact.toString(), smallMuted));

            if (firm.getGstin() != null && !firm.getGstin().isBlank()) {
                leftHeader.addElement(new Paragraph("GSTIN: " + firm.getGstin(), boldText));
            }
        }
        headerTable.addCell(leftHeader);

        // Right Header Box
        PdfPCell rightHeader = new PdfPCell();
        rightHeader.setBorder(Rectangle.NO_BORDER);
        rightHeader.setHorizontalAlignment(Element.ALIGN_RIGHT);

        Paragraph pTitle = new Paragraph(docTitle, titleFont);
        pTitle.setAlignment(Element.ALIGN_RIGHT);
        rightHeader.addElement(pTitle);

        String numPrefix = isEstimate ? "Quotation #: " : "Invoice #: ";
        String invNum = isEstimate
                ? (invoice != null && invoice.getEstimateNumber() != null ? invoice.getEstimateNumber() : String.valueOf(invoice != null ? invoice.getId() : "-"))
                : (invoice != null && invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : String.valueOf(invoice != null ? invoice.getId() : "-"));
        Paragraph pInvNo = new Paragraph(numPrefix + invNum, boldText);
        pInvNo.setAlignment(Element.ALIGN_RIGHT);
        rightHeader.addElement(pInvNo);

        String dateStr = (invoice != null && invoice.getInvoiceDate() != null) ? invoice.getInvoiceDate().format(DATE_FMT) : "-";
        Paragraph pDate = new Paragraph("Date: " + dateStr, normalText);
        pDate.setAlignment(Element.ALIGN_RIGHT);
        rightHeader.addElement(pDate);

        if (invoice != null && invoice.getDueDate() != null) {
            Paragraph pDue = new Paragraph("Due Date: " + invoice.getDueDate().format(DATE_FMT), smallMuted);
            pDue.setAlignment(Element.ALIGN_RIGHT);
            rightHeader.addElement(pDue);
        }
        headerTable.addCell(rightHeader);
        doc.add(headerTable);

        // 2. BILL TO SECTION (Modern Card Style)
        PdfPTable billToCard = new PdfPTable(1);
        billToCard.setWidthPercentage(100);
        billToCard.setSpacingAfter(10f * scale);

        PdfPCell cardCell = new PdfPCell();
        cardCell.setBackgroundColor(lightTint);
        cardCell.setBorderColor(primary);
        cardCell.setBorderWidth(1f);
        cardCell.setPadding(8f * scale);

        Customer cust = invoice != null ? invoice.getCustomer() : null;
        Paragraph pBillToLabel = new Paragraph("BILLED TO", new Font(Font.HELVETICA, 7.5f * scale, Font.BOLD, primary));
        pBillToLabel.setSpacingAfter(2f);
        cardCell.addElement(pBillToLabel);

        String custName = cust != null && cust.getName() != null && !cust.getName().isBlank() ? cust.getName().toUpperCase() : "CASH CUSTOMER";
        cardCell.addElement(new Paragraph(custName, boldText));

        if (cust != null) {
            if (cust.getAddress() != null && !cust.getAddress().isBlank()) cardCell.addElement(new Paragraph(cust.getAddress(), normalText));
            if (cust.getPhone() != null && !cust.getPhone().isBlank()) cardCell.addElement(new Paragraph("Phone: " + cust.getPhone(), smallMuted));
            if (cust.getGstin() != null && !cust.getGstin().isBlank()) cardCell.addElement(new Paragraph("GSTIN: " + cust.getGstin(), boldText));
        }
        billToCard.addCell(cardCell);
        doc.add(billToCard);

        // 3. LINE ITEMS TABLE (Modern Striped)
        float[] itemColWidths = new float[]{0.35f, 2.3f, 0.9f, 0.8f, 0.6f, 1.0f, 1.05f};
        PdfPTable itemsTable = new PdfPTable(itemColWidths);
        itemsTable.setWidthPercentage(100);
        itemsTable.setHeaderRows(1);
        itemsTable.setSplitLate(false);
        itemsTable.setSplitRows(true);

        String[] itemHeaders = {"#", "Item Description", "HSN/SAC", "Qty", "Unit", "Rate", "Amount"};
        for (int i = 0; i < itemHeaders.length; i++) {
            PdfPCell th = new PdfPCell(new Phrase(itemHeaders[i], thFont));
            th.setBackgroundColor(primary);
            th.setBorder(Rectangle.NO_BORDER);
            th.setPadding(6f * scale);
            th.setHorizontalAlignment(i == 0 || i == 2 || i == 3 || i == 4 ? Element.ALIGN_CENTER : (i >= 5 ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT));
            itemsTable.addCell(th);
        }

        List<InvoiceItem> items = invoice != null ? invoice.getItems() : null;
        int totalQty = 0;

        if (items != null && !items.isEmpty()) {
            int idx = 1;
            for (InvoiceItem it : items) {
                boolean isEven = idx % 2 == 0;
                Color rowBg = isEven ? lightTint : Color.WHITE;

                String itemName = it.getProductName() != null && !it.getProductName().isBlank() ? it.getProductName().trim() : (it.getProduct() != null && it.getProduct().getName() != null ? it.getProduct().getName() : "Item");
                String hsnCode = it.getHsnCode() != null && !it.getHsnCode().isBlank() ? it.getHsnCode().trim() : (it.getProduct() != null && it.getProduct().getHsnCode() != null ? it.getProduct().getHsnCode().trim() : "-");
                int q = it.getQty() != null ? it.getQty() : 0;
                totalQty += q;
                String unit = it.getUnit() != null && !it.getUnit().isBlank() ? it.getUnit() : "-";
                String priceStr = "₹ " + formatAmount(it.getPricePerUnit());
                String amountStr = "₹ " + formatAmount(it.getLineTotal());

                itemsTable.addCell(makeModernCell(String.valueOf(idx++), normalText, Element.ALIGN_CENTER, 5f * scale, rowBg));
                itemsTable.addCell(makeModernCell(itemName, normalText, Element.ALIGN_LEFT, 5f * scale, rowBg));
                itemsTable.addCell(makeModernCell(hsnCode, normalText, Element.ALIGN_CENTER, 5f * scale, rowBg));
                itemsTable.addCell(makeModernCell(String.valueOf(q), normalText, Element.ALIGN_CENTER, 5f * scale, rowBg));
                itemsTable.addCell(makeModernCell(unit, normalText, Element.ALIGN_CENTER, 5f * scale, rowBg));
                itemsTable.addCell(makeModernCell(priceStr, normalText, Element.ALIGN_RIGHT, 5f * scale, rowBg));
                itemsTable.addCell(makeModernCell(amountStr, normalText, Element.ALIGN_RIGHT, 5f * scale, rowBg));
            }
        } else {
            PdfPCell empty = new PdfPCell(new Phrase("No items recorded", normalText));
            empty.setColspan(itemHeaders.length);
            empty.setHorizontalAlignment(Element.ALIGN_CENTER);
            empty.setPadding(10f);
            empty.setBorderColor(BORDER_COLOR);
            itemsTable.addCell(empty);
        }
        doc.add(itemsTable);

        // 4. SUMMARY & TOTALS SECTION
        PdfPTable summaryTable = new PdfPTable(new float[]{2.2f, 1.8f});
        summaryTable.setWidthPercentage(100);
        summaryTable.setKeepTogether(true);
        summaryTable.setSpacingBefore(8f * scale);

        BigDecimal grandTotal = nz(invoice != null ? invoice.getTotalAmount() : null);
        BigDecimal subtotal = nz(invoice != null ? invoice.getSubtotalWithoutTax() : null);
        if (subtotal.compareTo(BigDecimal.ZERO) == 0) subtotal = grandTotal;
        BigDecimal totalTax = nz(invoice != null ? invoice.getTotalTax() : null);
        BigDecimal discount = nz(invoice != null ? invoice.getTotalDiscount() : null);
        BigDecimal received = (invoice != null && Boolean.TRUE.equals(invoice.getPaid())) ? grandTotal : BigDecimal.ZERO.setScale(2);
        BigDecimal balance = grandTotal.subtract(received);
        if (balance.compareTo(BigDecimal.ZERO) < 0) balance = BigDecimal.ZERO.setScale(2);

        // Left Side: Amount in Words & Bank Details
        PdfPCell leftSummary = new PdfPCell();
        leftSummary.setBorder(Rectangle.NO_BORDER);
        leftSummary.setPadding(6f * scale);

        leftSummary.addElement(new Paragraph("AMOUNT IN WORDS", new Font(Font.HELVETICA, 7.5f * scale, Font.BOLD, primary)));
        leftSummary.addElement(new Paragraph(numberToWords(grandTotal), boldText));

        if (firm != null && firm.getBankName() != null && !firm.getBankName().isBlank()) {
            leftSummary.addElement(new Paragraph(" ", normalText));
            leftSummary.addElement(new Paragraph("BANK PAYMENT DETAILS", new Font(Font.HELVETICA, 7.5f * scale, Font.BOLD, primary)));
            leftSummary.addElement(new Paragraph("Bank: " + firm.getBankName(), smallMuted));
            if (firm.getBankAccount() != null) leftSummary.addElement(new Paragraph("A/C: " + firm.getBankAccount(), smallMuted));
            if (firm.getBankIfsc() != null) leftSummary.addElement(new Paragraph("IFSC: " + firm.getBankIfsc(), smallMuted));
        }
        summaryTable.addCell(leftSummary);

        // Right Side: Breakdown Box
        PdfPCell rightSummary = new PdfPCell();
        rightSummary.setBorder(Rectangle.NO_BORDER);
        rightSummary.setPadding(0);

        PdfPTable breakdownTbl = new PdfPTable(new float[]{1f, 1f});
        breakdownTbl.setWidthPercentage(100);

        addModernBreakdownRow(breakdownTbl, "Sub Total", "₹ " + formatAmount(subtotal), normalText, normalText);
        if (discount.compareTo(BigDecimal.ZERO) > 0) {
            addModernBreakdownRow(breakdownTbl, "Discount", "- ₹ " + formatAmount(discount), normalText, normalText);
        }
        if (totalTax.compareTo(BigDecimal.ZERO) > 0) {
            addModernBreakdownRow(breakdownTbl, "Tax / GST", "₹ " + formatAmount(totalTax), normalText, normalText);
        }

        // Grand Total Box
        PdfPCell grandLbl = new PdfPCell(new Phrase("Grand Total", totalBoldFont));
        grandLbl.setBackgroundColor(lightTint);
        grandLbl.setBorderColor(primary);
        grandLbl.setBorderWidth(1f);
        grandLbl.setPadding(6f * scale);
        breakdownTbl.addCell(grandLbl);

        PdfPCell grandVal = new PdfPCell(new Phrase("₹ " + formatAmount(grandTotal), totalBoldFont));
        grandVal.setBackgroundColor(lightTint);
        grandVal.setBorderColor(primary);
        grandVal.setBorderWidth(1f);
        grandVal.setPadding(6f * scale);
        grandVal.setHorizontalAlignment(Element.ALIGN_RIGHT);
        breakdownTbl.addCell(grandVal);

        if (!isEstimate) {
            addModernBreakdownRow(breakdownTbl, "Received", "₹ " + formatAmount(received), normalText, normalText);
            addModernBreakdownRow(breakdownTbl, "Balance Due", "₹ " + formatAmount(balance), boldText, boldText);
        }

        rightSummary.addElement(breakdownTbl);
        summaryTable.addCell(rightSummary);
        doc.add(summaryTable);

        // 5. FOOTER: UPI QR Code & Authorized Signatory
        PdfPTable footerTable = new PdfPTable(new float[]{2.2f, 1.8f});
        footerTable.setWidthPercentage(100);
        footerTable.setKeepTogether(true);
        footerTable.setSpacingBefore(12f * scale);

        // QR Code Box
        PdfPCell qrCell = new PdfPCell();
        qrCell.setBorder(Rectangle.NO_BORDER);
        qrCell.setHorizontalAlignment(Element.ALIGN_CENTER);

        boolean showUpiQr = !isEstimate && firm != null && firm.getUpiId() != null && !firm.getUpiId().trim().isBlank();
        if (showUpiQr) {
            try {
                String upiId = firm.getUpiId().trim();
                String upiFirmName = (firm.getFirmName() != null && !firm.getFirmName().isBlank()) ? firm.getFirmName().trim() : "Business";
                String encodedName = java.net.URLEncoder.encode(upiFirmName, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
                String upiUri = "upi://pay?pa=" + upiId + "&pn=" + encodedName + "&am=" + formatAmount(grandTotal) + "&cu=INR";

                QRCodeWriter qrWriter = new QRCodeWriter();
                Map<EncodeHintType, Object> hints = new HashMap<>();
                hints.put(EncodeHintType.MARGIN, 0);
                BitMatrix matrix = qrWriter.encode(upiUri, BarcodeFormat.QR_CODE, 140, 140, hints);
                int width = matrix.getWidth();
                int height = matrix.getHeight();
                byte[] rawData = new byte[width * height * 3];
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        boolean isBlack = matrix.get(x, y);
                        byte val = (byte) (isBlack ? 0 : 255);
                        int idx = (y * width + x) * 3;
                        rawData[idx] = val;
                        rawData[idx + 1] = val;
                        rawData[idx + 2] = val;
                    }
                }
                Image qrImage = Image.getInstance(width, height, 3, 8, rawData);
                float qrSize = isA5 ? 52f : 64f;
                qrImage.scaleToFit(qrSize, qrSize);
                qrImage.setAlignment(Image.ALIGN_CENTER);

                qrCell.addElement(new Paragraph("SCAN TO PAY", new Font(Font.HELVETICA, 7.5f * scale, Font.BOLD, primary)));
                qrCell.addElement(qrImage);
                qrCell.addElement(new Paragraph("Scan with any UPI app", smallMuted));
            } catch (Exception ignored) {
                qrCell.addElement(new Paragraph(" ", normalText));
            }
        } else {
            qrCell.addElement(new Paragraph(" ", normalText));
        }
        footerTable.addCell(qrCell);

        // Signatory Box
        PdfPCell signCell = new PdfPCell();
        signCell.setBorder(Rectangle.NO_BORDER);
        signCell.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph pFor = new Paragraph("For, " + fName, boldText);
        pFor.setAlignment(Element.ALIGN_CENTER);
        signCell.addElement(pFor);

        Paragraph pSigSpace = new Paragraph("\n\n", normalText);
        signCell.addElement(pSigSpace);

        Paragraph pAuth = new Paragraph("Authorized Signatory", smallMuted);
        pAuth.setAlignment(Element.ALIGN_CENTER);
        signCell.addElement(pAuth);

        footerTable.addCell(signCell);
        doc.add(footerTable);

        doc.close();
        return baos.toByteArray();
    }

    private static PdfPCell makeModernCell(String text, Font font, int alignment, float padding, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(padding);
        cell.setBackgroundColor(bg);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(BORDER_COLOR);
        cell.setBorderWidth(0.5f);
        return cell;
    }

    private static void addModernBreakdownRow(PdfPTable tbl, String label, String value, Font lFont, Font vFont) {
        PdfPCell l = new PdfPCell(new Phrase(label, lFont));
        l.setBorder(Rectangle.BOTTOM);
        l.setBorderColor(BORDER_COLOR);
        l.setBorderWidth(0.5f);
        l.setPadding(4f);
        l.setHorizontalAlignment(Element.ALIGN_LEFT);
        tbl.addCell(l);

        PdfPCell v = new PdfPCell(new Phrase(value, vFont));
        v.setBorder(Rectangle.BOTTOM);
        v.setBorderColor(BORDER_COLOR);
        v.setBorderWidth(0.5f);
        v.setPadding(4f);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        tbl.addCell(v);
    }

    private static String formatAmount(BigDecimal d) {
        if (d == null) return "0.00";
        return d.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO.setScale(2) : v.setScale(2, RoundingMode.HALF_UP);
    }

    private static String numberToWords(BigDecimal amount) {
        return com.billing.simple.billsoft.service.InvoicePdfService.numberToWords(amount);
    }

    private static class PageNumberHelper extends com.lowagie.text.pdf.PdfPageEventHelper {
        private com.lowagie.text.pdf.PdfTemplate totalPagesTemplate;
        private com.lowagie.text.pdf.BaseFont baseFont;
        private final float fontSize;

        public PageNumberHelper(float fontSize) {
            this.fontSize = fontSize;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            try {
                baseFont = com.lowagie.text.pdf.BaseFont.createFont(com.lowagie.text.pdf.BaseFont.HELVETICA, com.lowagie.text.pdf.BaseFont.WINANSI, com.lowagie.text.pdf.BaseFont.NOT_EMBEDDED);
                totalPagesTemplate = writer.getDirectContent().createTemplate(30, 16);
            } catch (Exception ignored) {}
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            if (baseFont == null || totalPagesTemplate == null) return;
            com.lowagie.text.pdf.PdfContentByte cb = writer.getDirectContent();
            String text = "Page " + writer.getPageNumber() + " of ";
            float textSize = baseFont.getWidthPoint(text, fontSize);
            float textBase = document.bottom() - 12;
            float x = document.right() - 60;

            cb.beginText();
            cb.setFontAndSize(baseFont, fontSize);
            cb.setColorFill(new Color(156, 163, 175));
            cb.setTextMatrix(x, textBase);
            cb.showText(text);
            cb.endText();
            cb.addTemplate(totalPagesTemplate, x + textSize, textBase);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            if (baseFont == null || totalPagesTemplate == null) return;
            totalPagesTemplate.beginText();
            totalPagesTemplate.setFontAndSize(baseFont, fontSize);
            totalPagesTemplate.setColorFill(new Color(156, 163, 175));
            totalPagesTemplate.setTextMatrix(0, 0);
            totalPagesTemplate.showText(String.valueOf(writer.getPageNumber()));
            totalPagesTemplate.endText();
        }
    }
}
