package com.billing.simple.billsoft.service.pdf.thermal;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.billing.simple.billsoft.dtos.InvoicePrintOptions;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoiceItem;
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

public class Thermal80mmRenderer {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final Color TEXT_DARK = new Color(0, 0, 0);
    private static final Color TEXT_MUTED = new Color(75, 85, 99);
    private static final Color SEPARATOR_COLOR = new Color(156, 163, 175);

    public static byte[] render(Invoice invoice, FirmDetails firm, InvoicePrintOptions options) throws Exception {
        // 80mm width = 226.77 pt
        // Page height dynamically sized or standard POS roll height
        Rectangle thermalSize = new Rectangle(226.77f, 842.0f);
        float margin = 6f;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(thermalSize, margin, margin, margin, margin);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        Font storeNameFont = new Font(Font.HELVETICA, 11f, Font.BOLD, TEXT_DARK);
        Font titleFont = new Font(Font.HELVETICA, 9f, Font.BOLD, TEXT_DARK);
        Font boldFont = new Font(Font.HELVETICA, 7.5f, Font.BOLD, TEXT_DARK);
        Font normalFont = new Font(Font.HELVETICA, 7f, Font.NORMAL, TEXT_DARK);
        Font smallFont = new Font(Font.HELVETICA, 6.5f, Font.NORMAL, TEXT_MUTED);
        Font totalFont = new Font(Font.HELVETICA, 9f, Font.BOLD, TEXT_DARK);

        boolean isEstimate = invoice != null && invoice.getStatus() != null && invoice.getStatus().name().equalsIgnoreCase("ESTIMATE");
        String docTitle = isEstimate ? "--- ESTIMATE / QUOTATION ---" : "--- TAX INVOICE ---";

        // 1. FIRM HEADER (Centered)
        String fName = (firm != null && firm.getFirmName() != null && !firm.getFirmName().isBlank())
                ? firm.getFirmName().toUpperCase() : "RUPEECRM STORE";
        Paragraph pFirm = new Paragraph(fName, storeNameFont);
        pFirm.setAlignment(Element.ALIGN_CENTER);
        doc.add(pFirm);

        if (firm != null) {
            StringBuilder addr = new StringBuilder();
            if (firm.getAddressLine1() != null) addr.append(firm.getAddressLine1());
            if (firm.getCity() != null) { if (addr.length() > 0) addr.append(", "); addr.append(firm.getCity()); }
            if (firm.getPincode() != null) addr.append(" ").append(firm.getPincode());
            if (addr.length() > 0) {
                Paragraph pAddr = new Paragraph(addr.toString(), smallFont);
                pAddr.setAlignment(Element.ALIGN_CENTER);
                doc.add(pAddr);
            }

            if (firm.getPhone() != null && !firm.getPhone().isBlank()) {
                Paragraph pPhone = new Paragraph("Ph: " + firm.getPhone(), smallFont);
                pPhone.setAlignment(Element.ALIGN_CENTER);
                doc.add(pPhone);
            }

            if (firm.getGstin() != null && !firm.getGstin().isBlank()) {
                Paragraph pGst = new Paragraph("GSTIN: " + firm.getGstin(), smallFont);
                pGst.setAlignment(Element.ALIGN_CENTER);
                doc.add(pGst);
            }
        }

        // Title
        Paragraph pTitle = new Paragraph(docTitle, titleFont);
        pTitle.setAlignment(Element.ALIGN_CENTER);
        pTitle.setSpacingBefore(3f);
        pTitle.setSpacingAfter(3f);
        doc.add(pTitle);

        doc.add(createDivider());

        // 2. INVOICE META
        PdfPTable metaTbl = new PdfPTable(new float[]{1.2f, 1f});
        metaTbl.setWidthPercentage(100);
        metaTbl.setSpacingBefore(2f);
        metaTbl.setSpacingAfter(2f);

        String numPrefix = isEstimate ? "Quote #: " : "Inv #: ";
        String invNum = isEstimate
                ? (invoice != null && invoice.getEstimateNumber() != null ? invoice.getEstimateNumber() : String.valueOf(invoice != null ? invoice.getId() : "-"))
                : (invoice != null && invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : String.valueOf(invoice != null ? invoice.getId() : "-"));
        metaTbl.addCell(makeNoBorderCell(numPrefix + invNum, boldFont, Element.ALIGN_LEFT));

        String dateStr = (invoice != null && invoice.getInvoiceDate() != null) ? invoice.getInvoiceDate().format(DATE_FMT) : "-";
        metaTbl.addCell(makeNoBorderCell("Date: " + dateStr, normalFont, Element.ALIGN_RIGHT));

        Customer cust = invoice != null ? invoice.getCustomer() : null;
        String custName = cust != null && cust.getName() != null && !cust.getName().isBlank() ? cust.getName().toUpperCase() : "CASH CUSTOMER";
        PdfPCell custCell = makeNoBorderCell("Cust: " + custName, boldFont, Element.ALIGN_LEFT);
        custCell.setColspan(2);
        metaTbl.addCell(custCell);

        if (cust != null && cust.getGstin() != null && !cust.getGstin().isBlank()) {
            PdfPCell cGst = makeNoBorderCell("Cust GST: " + cust.getGstin(), smallFont, Element.ALIGN_LEFT);
            cGst.setColspan(2);
            metaTbl.addCell(cGst);
        }
        doc.add(metaTbl);

        doc.add(createDivider());

        // 3. ITEMS (Stacked 2-line layout)
        PdfPTable itemsHeaderTbl = new PdfPTable(new float[]{2f, 1f});
        itemsHeaderTbl.setWidthPercentage(100);
        itemsHeaderTbl.addCell(makeNoBorderCell("ITEM / DETAILS", boldFont, Element.ALIGN_LEFT));
        itemsHeaderTbl.addCell(makeNoBorderCell("AMOUNT", boldFont, Element.ALIGN_RIGHT));
        doc.add(itemsHeaderTbl);
        doc.add(createDivider());

        List<InvoiceItem> items = invoice != null ? invoice.getItems() : null;
        if (items != null && !items.isEmpty()) {
            int idx = 1;
            for (InvoiceItem it : items) {
                String itemName = it.getProductName() != null && !it.getProductName().isBlank() ? it.getProductName().trim() : (it.getProduct() != null && it.getProduct().getName() != null ? it.getProduct().getName() : "Item");
                int q = it.getQty() != null ? it.getQty() : 0;
                String unit = it.getUnit() != null && !it.getUnit().isBlank() ? it.getUnit() : "";
                String rate = formatAmount(it.getPricePerUnit());
                String total = "₹" + formatAmount(it.getLineTotal());

                // Line 1: Item Name
                Paragraph pItem = new Paragraph(idx++ + ". " + itemName, boldFont);
                doc.add(pItem);

                // Line 2: Qty x Rate & Amount
                PdfPTable itemDetailTbl = new PdfPTable(new float[]{2f, 1f});
                itemDetailTbl.setWidthPercentage(100);
                String detailsStr = "   " + q + " " + unit + " x ₹" + rate;
                itemDetailTbl.addCell(makeNoBorderCell(detailsStr, smallFont, Element.ALIGN_LEFT));
                itemDetailTbl.addCell(makeNoBorderCell(total, normalFont, Element.ALIGN_RIGHT));
                doc.add(itemDetailTbl);
            }
        } else {
            Paragraph pEmpty = new Paragraph("No items recorded", normalFont);
            pEmpty.setAlignment(Element.ALIGN_CENTER);
            doc.add(pEmpty);
        }

        doc.add(createDivider());

        // 4. TOTALS & SUMMARY
        BigDecimal grandTotal = nz(invoice != null ? invoice.getTotalAmount() : null);
        BigDecimal subtotal = nz(invoice != null ? invoice.getSubtotalWithoutTax() : null);
        if (subtotal.compareTo(BigDecimal.ZERO) == 0) subtotal = grandTotal;
        BigDecimal totalTax = nz(invoice != null ? invoice.getTotalTax() : null);
        BigDecimal discount = nz(invoice != null ? invoice.getTotalDiscount() : null);
        BigDecimal received = (invoice != null && Boolean.TRUE.equals(invoice.getPaid())) ? grandTotal : BigDecimal.ZERO.setScale(2);
        BigDecimal balance = grandTotal.subtract(received);
        if (balance.compareTo(BigDecimal.ZERO) < 0) balance = BigDecimal.ZERO.setScale(2);

        PdfPTable totalsTbl = new PdfPTable(new float[]{1.4f, 1f});
        totalsTbl.setWidthPercentage(100);

        addThermalRow(totalsTbl, "Sub Total:", "₹" + formatAmount(subtotal), normalFont);
        if (discount.compareTo(BigDecimal.ZERO) > 0) {
            addThermalRow(totalsTbl, "Discount:", "-₹" + formatAmount(discount), normalFont);
        }
        if (totalTax.compareTo(BigDecimal.ZERO) > 0) {
            addThermalRow(totalsTbl, "Tax / GST:", "₹" + formatAmount(totalTax), normalFont);
        }
        addThermalRow(totalsTbl, "TOTAL AMOUNT:", "₹" + formatAmount(grandTotal), totalFont);

        if (!isEstimate) {
            addThermalRow(totalsTbl, "Received:", "₹" + formatAmount(received), normalFont);
            addThermalRow(totalsTbl, "Balance Due:", "₹" + formatAmount(balance), boldFont);
        }
        doc.add(totalsTbl);

        doc.add(createDivider());

        // 5. UPI QR CODE & FOOTER
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
                BitMatrix matrix = qrWriter.encode(upiUri, BarcodeFormat.QR_CODE, 100, 100, hints);
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
                qrImage.scaleToFit(55f, 55f);
                qrImage.setAlignment(Image.ALIGN_CENTER);

                Paragraph pQrTitle = new Paragraph("SCAN TO PAY", boldFont);
                pQrTitle.setAlignment(Element.ALIGN_CENTER);
                pQrTitle.setSpacingBefore(3f);
                doc.add(pQrTitle);
                doc.add(qrImage);
            } catch (Exception ignored) {}
        }

        Paragraph pThanks = new Paragraph("Thank you for your business!", smallFont);
        pThanks.setAlignment(Element.ALIGN_CENTER);
        pThanks.setSpacingBefore(4f);
        doc.add(pThanks);

        doc.close();
        return baos.toByteArray();
    }

    private static PdfPTable createDivider() {
        PdfPTable tbl = new PdfPTable(1);
        tbl.setWidthPercentage(100);
        tbl.setSpacingBefore(2f);
        tbl.setSpacingAfter(2f);
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(SEPARATOR_COLOR);
        c.setBorderWidth(0.5f);
        c.setPadding(0);
        tbl.addCell(c);
        return tbl;
    }

    private static PdfPCell makeNoBorderCell(String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(1.5f);
        return cell;
    }

    private static void addThermalRow(PdfPTable tbl, String label, String value, Font font) {
        tbl.addCell(makeNoBorderCell(label, font, Element.ALIGN_LEFT));
        tbl.addCell(makeNoBorderCell(value, font, Element.ALIGN_RIGHT));
    }

    private static String formatAmount(BigDecimal d) {
        if (d == null) return "0.00";
        return d.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO.setScale(2) : v.setScale(2, RoundingMode.HALF_UP);
    }
}
