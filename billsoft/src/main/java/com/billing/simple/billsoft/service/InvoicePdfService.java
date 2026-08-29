package com.billing.simple.billsoft.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

import org.springframework.stereotype.Service;

import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoiceItem;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

/**
 * Produces a professional, structured Invoice and Quotation template matching the
 * enterprise Tax Invoice reference specification.
 */
@Service
public class InvoicePdfService {

    private final FirmDetailsService firmService;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private static final int SCALE = 2;

    // Theme Colors
    private static final Color HEADER_BLUE = new Color(0, 122, 166); // #007aa6
    private static final Color DARK_BORDER = new Color(75, 85, 99);   // #4b5563
    private static final Color LIGHT_BORDER = new Color(156, 163, 175); // #9ca3af
    private static final Color TEXT_DARK = new Color(17, 24, 39);     // #111827
    private static final Color TEXT_MUTED = new Color(75, 85, 99);    // #4b5563
    private static final Color UPI_GREEN = new Color(16, 185, 129);   // #10b981

    public InvoicePdfService(FirmDetailsService firmService) {
        this.firmService = firmService;
    }

    public byte[] generatePdf(Invoice invoice, String size) throws Exception {
        Rectangle pageSize = PageSize.A4;
        boolean isA5 = false;
        if (size != null && size.equalsIgnoreCase("A5")) {
            pageSize = PageSize.A5;
            isA5 = true;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        float margin = isA5 ? 20 : 28;
        Document doc = new Document(pageSize, margin, margin, margin, margin);
        PdfWriter.getInstance(doc, baos);

        doc.open();

        // Typography
        Font docTitleFont = new Font(Font.HELVETICA, isA5 ? 12 : 14, Font.BOLD, TEXT_DARK);
        Font firmNameFont = new Font(Font.HELVETICA, isA5 ? 12 : 14, Font.BOLD, TEXT_DARK);
        Font subTitleFont = new Font(Font.HELVETICA, isA5 ? 8 : 9, Font.BOLD, Color.WHITE);
        Font boldText = new Font(Font.HELVETICA, isA5 ? 8 : 9, Font.BOLD, TEXT_DARK);
        Font normalText = new Font(Font.HELVETICA, isA5 ? 7.5f : 8.5f, Font.NORMAL, TEXT_DARK);
        Font smallMuted = new Font(Font.HELVETICA, isA5 ? 7 : 8, Font.NORMAL, TEXT_MUTED);
        Font tableHeaderFont = new Font(Font.HELVETICA, isA5 ? 7.5f : 8.5f, Font.BOLD, Color.WHITE);
        Font totalBoldFont = new Font(Font.HELVETICA, isA5 ? 8.5f : 9.5f, Font.BOLD, TEXT_DARK);

        FirmDetails firm = null;
        try {
            Long firmId = invoice != null ? invoice.getFirmId() : null;
            firm = firmId != null ? firmService.get(firmId) : firmService.getFirst();
        } catch (Exception e) {
            firm = null;
        }

        boolean isEstimate = invoice != null && invoice.getStatus() != null && invoice.getStatus().name().equalsIgnoreCase("ESTIMATE");
        String documentTitle = isEstimate ? "Quotation" : "Tax Invoice";

        // Top Document Title (Centered)
        Paragraph docTitleP = new Paragraph(documentTitle, docTitleFont);
        docTitleP.setAlignment(Element.ALIGN_CENTER);
        docTitleP.setSpacingAfter(6f);
        doc.add(docTitleP);

        // ═══════════════════════════════════════════════════════════════════════════════
        // MAIN ENCLOSING BOX (Unified Outer Frame)
        // ═══════════════════════════════════════════════════════════════════════════════
        PdfPTable mainFrame = new PdfPTable(1);
        mainFrame.setWidthPercentage(100);
        mainFrame.getDefaultCell().setBorder(Rectangle.BOX);
        mainFrame.getDefaultCell().setBorderWidth(1f);
        mainFrame.getDefaultCell().setBorderColor(DARK_BORDER);
        mainFrame.getDefaultCell().setPadding(0);

        PdfPCell innerContainerCell = new PdfPCell();
        innerContainerCell.setBorder(Rectangle.BOX);
        innerContainerCell.setBorderWidth(1f);
        innerContainerCell.setBorderColor(DARK_BORDER);
        innerContainerCell.setPadding(0);

        PdfPTable contentTable = new PdfPTable(1);
        contentTable.setWidthPercentage(100);

        // ─────────────────────────────────────────────────────────────────────────────
        // 1. FIRM HEADER BLOCK (Logo on Left, Firm Info on Right)
        // ─────────────────────────────────────────────────────────────────────────────
        PdfPTable firmBlock = new PdfPTable(new float[]{1.2f, 2.8f});
        firmBlock.setWidthPercentage(100);

        // Left: Firm Logo
        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setPadding(10);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);

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
                float maxW = isA5 ? 65f : 85f;
                float maxH = isA5 ? 40f : 55f;
                logo.scaleToFit(maxW, maxH);
                logo.setAlignment(Image.LEFT);
                logoCell.addElement(logo);
                hasLogo = true;
            } catch (Exception ignored) {}
        }
        if (!hasLogo) {
            logoCell.addElement(new Paragraph(" ", normalText));
        }
        firmBlock.addCell(logoCell);

        // Right: Firm Name, Address & Contact Details (Right-aligned)
        PdfPCell firmInfoCell = new PdfPCell();
        firmInfoCell.setBorder(Rectangle.NO_BORDER);
        firmInfoCell.setPadding(10);
        firmInfoCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

        String fName = (firm != null && firm.getFirmName() != null && !firm.getFirmName().isBlank())
                ? firm.getFirmName().toUpperCase() : "RUPEECRM STORE";
        Paragraph pFirmName = new Paragraph(fName, firmNameFont);
        pFirmName.setAlignment(Element.ALIGN_RIGHT);
        firmInfoCell.addElement(pFirmName);

        if (firm != null) {
            StringBuilder addrSb = new StringBuilder();
            if (firm.getAddressLine1() != null && !firm.getAddressLine1().isBlank()) addrSb.append(firm.getAddressLine1());
            if (firm.getAddressLine2() != null && !firm.getAddressLine2().isBlank()) {
                if (addrSb.length() > 0) addrSb.append(", ");
                addrSb.append(firm.getAddressLine2());
            }
            if (firm.getCity() != null && !firm.getCity().isBlank()) {
                if (addrSb.length() > 0) addrSb.append(", ");
                addrSb.append(firm.getCity());
            }
            if (firm.getState() != null && !firm.getState().isBlank()) {
                if (addrSb.length() > 0) addrSb.append(" - ");
                addrSb.append(firm.getState());
            }
            if (firm.getPincode() != null && !firm.getPincode().isBlank()) {
                addrSb.append(" ").append(firm.getPincode());
            }
            if (addrSb.length() > 0) {
                Paragraph pAddr = new Paragraph(addrSb.toString(), smallMuted);
                pAddr.setAlignment(Element.ALIGN_RIGHT);
                firmInfoCell.addElement(pAddr);
            }

            StringBuilder contactSb = new StringBuilder();
            if (firm.getPhone() != null && !firm.getPhone().isBlank())
                contactSb.append("Phone no.: ").append(firm.getPhone());
            if (firm.getEmail() != null && !firm.getEmail().isBlank()) {
                if (contactSb.length() > 0) contactSb.append(" ");
                contactSb.append("Email: ").append(firm.getEmail());
            }
            if (contactSb.length() > 0) {
                Paragraph pContact = new Paragraph(contactSb.toString(), smallMuted);
                pContact.setAlignment(Element.ALIGN_RIGHT);
                firmInfoCell.addElement(pContact);
            }

            if (firm.getGstin() != null && !firm.getGstin().isBlank()) {
                Paragraph pGst = new Paragraph("GSTIN: " + firm.getGstin(), boldText);
                pGst.setAlignment(Element.ALIGN_RIGHT);
                firmInfoCell.addElement(pGst);
            }
        }
        firmBlock.addCell(firmInfoCell);

        PdfPCell firmBlockWrap = new PdfPCell(firmBlock);
        firmBlockWrap.setBorder(Rectangle.BOTTOM);
        firmBlockWrap.setBorderColor(DARK_BORDER);
        firmBlockWrap.setBorderWidth(1f);
        firmBlockWrap.setPadding(0);
        contentTable.addCell(firmBlockWrap);

        // ─────────────────────────────────────────────────────────────────────────────
        // 2. BILL TO & INVOICE META ROW
        // ─────────────────────────────────────────────────────────────────────────────
        PdfPTable billToMetaTable = new PdfPTable(new float[]{2.2f, 1.8f});
        billToMetaTable.setWidthPercentage(100);

        // Left Half: Bill To Section
        PdfPTable leftBillTo = new PdfPTable(1);
        leftBillTo.setWidthPercentage(100);

        PdfPCell billToHeader = new PdfPCell(new Phrase("Bill To", subTitleFont));
        billToHeader.setBackgroundColor(HEADER_BLUE);
        billToHeader.setBorder(Rectangle.NO_BORDER);
        billToHeader.setPadding(4f);
        billToHeader.setPaddingLeft(8f);
        leftBillTo.addCell(billToHeader);

        PdfPCell billToContent = new PdfPCell();
        billToContent.setBorder(Rectangle.NO_BORDER);
        billToContent.setPadding(6f);
        billToContent.setPaddingLeft(8f);

        Customer cust = invoice != null ? invoice.getCustomer() : null;
        String custName = cust != null && cust.getName() != null && !cust.getName().isBlank()
                ? cust.getName().toUpperCase() : "CASH CUSTOMER";
        billToContent.addElement(new Paragraph(custName, boldText));

        if (cust != null) {
            if (cust.getAddress() != null && !cust.getAddress().isBlank()) {
                billToContent.addElement(new Paragraph(cust.getAddress(), normalText));
            }
            if (cust.getPhone() != null && !cust.getPhone().isBlank()) {
                billToContent.addElement(new Paragraph("Phone: " + cust.getPhone(), normalText));
            }
            if (cust.getGstin() != null && !cust.getGstin().isBlank()) {
                billToContent.addElement(new Paragraph("GSTIN: " + cust.getGstin(), boldText));
            }
        }
        leftBillTo.addCell(billToContent);

        PdfPCell leftBillToWrap = new PdfPCell(leftBillTo);
        leftBillToWrap.setBorder(Rectangle.NO_BORDER);
        leftBillToWrap.setPadding(0);
        billToMetaTable.addCell(leftBillToWrap);

        // Right Half: Invoice Details (Invoice No, Date)
        PdfPCell rightMeta = new PdfPCell();
        rightMeta.setBorder(Rectangle.LEFT);
        rightMeta.setBorderColor(DARK_BORDER);
        rightMeta.setBorderWidth(1f);
        rightMeta.setPadding(8f);
        rightMeta.setVerticalAlignment(Element.ALIGN_MIDDLE);

        String numPrefix = isEstimate ? "Quotation No. : " : "Invoice No. : ";
        String invNum = isEstimate
                ? (invoice != null && invoice.getEstimateNumber() != null ? invoice.getEstimateNumber() : String.valueOf(invoice != null ? invoice.getId() : "-"))
                : (invoice != null && invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : String.valueOf(invoice != null ? invoice.getId() : "-"));

        Paragraph pInvNo = new Paragraph(numPrefix + invNum, boldText);
        pInvNo.setAlignment(Element.ALIGN_RIGHT);
        rightMeta.addElement(pInvNo);

        String dateStr = (invoice != null && invoice.getInvoiceDate() != null)
                ? invoice.getInvoiceDate().format(DATE_FMT) : "-";
        Paragraph pDate = new Paragraph("Date : " + dateStr, boldText);
        pDate.setAlignment(Element.ALIGN_RIGHT);
        rightMeta.addElement(pDate);

        if (invoice != null && invoice.getDueDate() != null) {
            Paragraph pDueDate = new Paragraph("Due Date : " + invoice.getDueDate().format(DATE_FMT), normalText);
            pDueDate.setAlignment(Element.ALIGN_RIGHT);
            rightMeta.addElement(pDueDate);
        }

        billToMetaTable.addCell(rightMeta);

        PdfPCell billToMetaWrap = new PdfPCell(billToMetaTable);
        billToMetaWrap.setBorder(Rectangle.BOTTOM);
        billToMetaWrap.setBorderColor(DARK_BORDER);
        billToMetaWrap.setBorderWidth(1f);
        billToMetaWrap.setPadding(0);
        contentTable.addCell(billToMetaWrap);

        // ─────────────────────────────────────────────────────────────────────────────
        // 3. ITEMS TABLE
        // ─────────────────────────────────────────────────────────────────────────────
        float[] itemColWidths = new float[]{0.35f, 2.3f, 0.9f, 0.8f, 0.6f, 1.0f, 1.05f};
        PdfPTable itemsTable = new PdfPTable(itemColWidths);
        itemsTable.setWidthPercentage(100);

        // Table Header Row
        String[] itemHeaders = {"#", "Item name", "HSN/ SAC", "Quantity", "Unit", "Price/ Unit", "Amount"};
        for (int i = 0; i < itemHeaders.length; i++) {
            PdfPCell th = new PdfPCell(new Phrase(itemHeaders[i], tableHeaderFont));
            th.setBackgroundColor(HEADER_BLUE);
            th.setBorderColor(LIGHT_BORDER);
            th.setBorderWidth(0.5f);
            th.setPadding(5f);
            th.setHorizontalAlignment(i == 0 || i == 2 || i == 3 || i == 4 ? Element.ALIGN_CENTER : (i >= 5 ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT));
            itemsTable.addCell(th);
        }

        List<InvoiceItem> items = invoice != null ? invoice.getItems() : null;
        int totalQty = 0;

        if (items != null && !items.isEmpty()) {
            int idx = 1;
            for (InvoiceItem it : items) {
                String itemName = "-";
                String hsnCode = "-";
                try {
                    if (it.getProduct() != null && it.getProduct().getName() != null) {
                        itemName = it.getProduct().getName();
                    } else if (it.getUnit() != null) {
                        itemName = "Custom Item";
                    }
                } catch (Exception ignored) {}

                int q = it.getQty() != null ? it.getQty() : 0;
                totalQty += q;
                String unit = it.getUnit() != null && !it.getUnit().isBlank() ? it.getUnit() : "-";
                String priceStr = "₹ " + formatAmount(it.getPricePerUnit());
                String amountStr = "₹ " + formatAmount(it.getLineTotal());

                itemsTable.addCell(makeCell(String.valueOf(idx++), normalText, Element.ALIGN_CENTER, 5f));
                itemsTable.addCell(makeCell(itemName, normalText, Element.ALIGN_LEFT, 5f));
                itemsTable.addCell(makeCell(hsnCode, normalText, Element.ALIGN_CENTER, 5f));
                itemsTable.addCell(makeCell(String.valueOf(q), normalText, Element.ALIGN_CENTER, 5f));
                itemsTable.addCell(makeCell(unit, normalText, Element.ALIGN_CENTER, 5f));
                itemsTable.addCell(makeCell(priceStr, normalText, Element.ALIGN_RIGHT, 5f));
                itemsTable.addCell(makeCell(amountStr, normalText, Element.ALIGN_RIGHT, 5f));
            }
        } else {
            PdfPCell emptyCell = new PdfPCell(new Phrase("No items recorded", normalText));
            emptyCell.setColspan(itemHeaders.length);
            emptyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            emptyCell.setPadding(12f);
            emptyCell.setBorderColor(LIGHT_BORDER);
            itemsTable.addCell(emptyCell);
        }

        // Table Total Row
        BigDecimal grandTotal = nz(invoice != null ? invoice.getTotalAmount() : null);

        PdfPCell totalLabelCell = new PdfPCell(new Phrase("Total", boldText));
        totalLabelCell.setColspan(2);
        totalLabelCell.setBorderColor(LIGHT_BORDER);
        totalLabelCell.setBorderWidth(0.5f);
        totalLabelCell.setPadding(5f);
        totalLabelCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        itemsTable.addCell(totalLabelCell);

        itemsTable.addCell(makeCell("", boldText, Element.ALIGN_CENTER, 5f)); // HSN blank
        itemsTable.addCell(makeCell(String.valueOf(totalQty), boldText, Element.ALIGN_CENTER, 5f)); // Total Qty
        itemsTable.addCell(makeCell("", boldText, Element.ALIGN_CENTER, 5f)); // Unit blank
        itemsTable.addCell(makeCell("", boldText, Element.ALIGN_RIGHT, 5f)); // Price/Unit blank
        itemsTable.addCell(makeCell("₹ " + formatAmount(grandTotal), totalBoldFont, Element.ALIGN_RIGHT, 5f)); // Total Amount

        PdfPCell itemsTableWrap = new PdfPCell(itemsTable);
        itemsTableWrap.setBorder(Rectangle.BOTTOM);
        itemsTableWrap.setBorderColor(DARK_BORDER);
        itemsTableWrap.setBorderWidth(1f);
        itemsTableWrap.setPadding(0);
        contentTable.addCell(itemsTableWrap);

        // ─────────────────────────────────────────────────────────────────────────────
        // 4. AMOUNTS IN WORDS & SUMMARY BREAKDOWN
        // ─────────────────────────────────────────────────────────────────────────────
        PdfPTable summaryTable = new PdfPTable(new float[]{2.2f, 1.8f});
        summaryTable.setWidthPercentage(100);

        // Sub Header: "Invoice Amount In Words" vs "Amounts:"
        PdfPCell wordsHeader = new PdfPCell(new Phrase("Invoice Amount In Words", subTitleFont));
        wordsHeader.setBackgroundColor(HEADER_BLUE);
        wordsHeader.setBorder(Rectangle.NO_BORDER);
        wordsHeader.setPadding(4f);
        wordsHeader.setHorizontalAlignment(Element.ALIGN_CENTER);
        summaryTable.addCell(wordsHeader);

        PdfPCell amountsHeader = new PdfPCell(new Phrase("Amounts:", subTitleFont));
        amountsHeader.setBackgroundColor(HEADER_BLUE);
        amountsHeader.setBorder(Rectangle.LEFT);
        amountsHeader.setBorderColor(DARK_BORDER);
        amountsHeader.setBorderWidth(1f);
        amountsHeader.setPadding(4f);
        amountsHeader.setPaddingLeft(8f);
        amountsHeader.setHorizontalAlignment(Element.ALIGN_LEFT);
        summaryTable.addCell(amountsHeader);

        // Content Row: Words on Left, Summary Lines on Right
        PdfPCell wordsContent = new PdfPCell();
        wordsContent.setBorder(Rectangle.NO_BORDER);
        wordsContent.setPadding(8f);
        String amountInWords = numberToWords(grandTotal);
        Paragraph pWords = new Paragraph(amountInWords, boldText);
        wordsContent.addElement(pWords);
        summaryTable.addCell(wordsContent);

        // Right Breakdown: Sub Total, Discount, GST, Total, Received, Balance
        PdfPTable amountsBreakdown = new PdfPTable(new float[]{1f, 1f});
        amountsBreakdown.setWidthPercentage(100);

        BigDecimal subtotal = nz(invoice != null ? invoice.getSubtotalWithoutTax() : null);
        if (subtotal.compareTo(BigDecimal.ZERO) == 0) subtotal = grandTotal;
        BigDecimal totalTax = nz(invoice != null ? invoice.getTotalTax() : null);
        BigDecimal discount = nz(invoice != null ? invoice.getTotalDiscount() : null);
        BigDecimal received = (invoice != null && Boolean.TRUE.equals(invoice.getPaid())) ? grandTotal : BigDecimal.ZERO.setScale(SCALE);
        BigDecimal balance = grandTotal.subtract(received);
        if (balance.compareTo(BigDecimal.ZERO) < 0) balance = BigDecimal.ZERO.setScale(SCALE);

        addBreakdownRow(amountsBreakdown, "Sub Total", "₹ " + formatAmount(subtotal), normalText, normalText);
        if (discount.compareTo(BigDecimal.ZERO) > 0) {
            addBreakdownRow(amountsBreakdown, "Discount", "- ₹ " + formatAmount(discount), normalText, normalText);
        }
        if (totalTax.compareTo(BigDecimal.ZERO) > 0) {
            addBreakdownRow(amountsBreakdown, "Tax / GST", "₹ " + formatAmount(totalTax), normalText, normalText);
        }
        addBreakdownRow(amountsBreakdown, "Total", "₹ " + formatAmount(grandTotal), totalBoldFont, totalBoldFont);
        addBreakdownRow(amountsBreakdown, "Received", "₹ " + formatAmount(received), normalText, normalText);
        addBreakdownRow(amountsBreakdown, "Balance", "₹ " + formatAmount(balance), boldText, boldText);

        PdfPCell amountsContent = new PdfPCell(amountsBreakdown);
        amountsContent.setBorder(Rectangle.LEFT);
        amountsContent.setBorderColor(DARK_BORDER);
        amountsContent.setBorderWidth(1f);
        amountsContent.setPadding(0);
        summaryTable.addCell(amountsContent);

        PdfPCell summaryWrap = new PdfPCell(summaryTable);
        summaryWrap.setBorder(Rectangle.BOTTOM);
        summaryWrap.setBorderColor(DARK_BORDER);
        summaryWrap.setBorderWidth(1f);
        summaryWrap.setPadding(0);
        contentTable.addCell(summaryWrap);

        // ─────────────────────────────────────────────────────────────────────────────
        // 5. FOOTER: UPI QR CODE & AUTHORIZED SIGNATORY
        // ─────────────────────────────────────────────────────────────────────────────
        PdfPTable footerTable = new PdfPTable(new float[]{2.2f, 1.8f});
        footerTable.setWidthPercentage(100);

        // Left Half: UPI QR Code & Scan To Pay Badge
        PdfPCell qrCell = new PdfPCell();
        qrCell.setBorder(Rectangle.NO_BORDER);
        qrCell.setPadding(8f);
        qrCell.setHorizontalAlignment(Element.ALIGN_LEFT);

        try {
            String upiPayee = (firm != null && firm.getPhone() != null && !firm.getPhone().isBlank()) ? firm.getPhone() : "9822972403";
            String upiFirmName = (firm != null && firm.getFirmName() != null) ? firm.getFirmName() : "RupeeCRM";
            String upiUri = String.format("upi://pay?pa=%s@upi&pn=%s&am=%s&cu=INR", upiPayee, upiFirmName.replaceAll(" ", "%20"), formatAmount(grandTotal));

            QRCodeWriter qrWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrWriter.encode(upiUri, BarcodeFormat.QR_CODE, 140, 140);
            ByteArrayOutputStream qrBaos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", qrBaos);

            Image qrImage = Image.getInstance(qrBaos.toByteArray());
            qrImage.scaleToFit(isA5 ? 65f : 78f, isA5 ? 65f : 78f);
            qrImage.setAlignment(Image.LEFT);
            qrCell.addElement(qrImage);

            // UPI Scan to Pay Badge
            Paragraph pUpiBadge = new Paragraph("  UPI SCAN TO PAY  ", new Font(Font.HELVETICA, 7.5f, Font.BOLD, Color.WHITE));
            PdfPTable badgeTable = new PdfPTable(1);
            badgeTable.setWidthPercentage(42);
            badgeTable.setHorizontalAlignment(Element.ALIGN_LEFT);
            PdfPCell badgeCell = new PdfPCell(pUpiBadge);
            badgeCell.setBackgroundColor(UPI_GREEN);
            badgeCell.setBorder(Rectangle.NO_BORDER);
            badgeCell.setPadding(2f);
            badgeCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            badgeTable.addCell(badgeCell);
            qrCell.addElement(badgeTable);
        } catch (Exception ignored) {}

        footerTable.addCell(qrCell);

        // Right Half: Authorized Signatory
        PdfPCell signCell = new PdfPCell();
        signCell.setBorder(Rectangle.LEFT);
        signCell.setBorderColor(DARK_BORDER);
        signCell.setBorderWidth(1f);
        signCell.setPadding(10f);
        signCell.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph pFor = new Paragraph("For, " + fName, boldText);
        pFor.setAlignment(Element.ALIGN_CENTER);
        signCell.addElement(pFor);

        // Realistic Drawn Signature / Ink Path Simulation
        Paragraph pSigSpace = new Paragraph("\n\n\n", normalText);
        pSigSpace.setAlignment(Element.ALIGN_CENTER);
        signCell.addElement(pSigSpace);

        Paragraph pAuth = new Paragraph("Authorized Signatory", normalText);
        pAuth.setAlignment(Element.ALIGN_CENTER);
        signCell.addElement(pAuth);

        footerTable.addCell(signCell);

        PdfPCell footerWrap = new PdfPCell(footerTable);
        footerWrap.setBorder(Rectangle.NO_BORDER);
        footerWrap.setPadding(0);
        contentTable.addCell(footerWrap);

        // Add to main document
        innerContainerCell.addElement(contentTable);
        mainFrame.addCell(innerContainerCell);
        doc.add(mainFrame);

        doc.close();
        return baos.toByteArray();
    }

    private static PdfPCell makeCell(String text, Font font, int alignment, float padding) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(padding);
        cell.setBorderColor(LIGHT_BORDER);
        cell.setBorderWidth(0.5f);
        return cell;
    }

    private static void addBreakdownRow(PdfPTable tbl, String label, String value, Font lFont, Font vFont) {
        PdfPCell l = new PdfPCell(new Phrase(label, lFont));
        l.setBorder(Rectangle.BOTTOM);
        l.setBorderColor(LIGHT_BORDER);
        l.setBorderWidth(0.5f);
        l.setPadding(4f);
        l.setPaddingLeft(8f);
        l.setHorizontalAlignment(Element.ALIGN_LEFT);
        tbl.addCell(l);

        PdfPCell v = new PdfPCell(new Phrase(value, vFont));
        v.setBorder(Rectangle.BOTTOM);
        v.setBorderColor(LIGHT_BORDER);
        v.setBorderWidth(0.5f);
        v.setPadding(4f);
        v.setPaddingRight(8f);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        tbl.addCell(v);
    }

    public static String numberToWords(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) return "Zero Rupees only";
        long wholePart = amount.setScale(0, RoundingMode.FLOOR).longValue();
        int decimalPart = amount.remainder(BigDecimal.ONE).movePointRight(2).abs().intValue();

        String words = wholePart == 0 ? "Zero" : convertToIndianWords(wholePart);
        String result = words + " Rupees";
        if (decimalPart > 0) {
            result += " and " + convertToIndianWords(decimalPart) + " Paise";
        }
        result += " only";
        return result;
    }

    private static String convertToIndianWords(long n) {
        String[] units = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
                "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen",
                "Eighteen", "Nineteen"};
        String[] tens = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};

        if (n < 20) return units[(int) n];
        if (n < 100) return tens[(int) (n / 10)] + (n % 10 > 0 ? " " + units[(int) (n % 10)] : "");
        if (n < 1000) return units[(int) (n / 100)] + " Hundred" + (n % 100 > 0 ? " " + convertToIndianWords(n % 100) : "");
        if (n < 100000) return convertToIndianWords(n / 1000) + " Thousand" + (n % 1000 > 0 ? " " + convertToIndianWords(n % 1000) : "");
        if (n < 10000000) return convertToIndianWords(n / 100000) + " Lakh" + (n % 100000 > 0 ? " " + convertToIndianWords(n % 100000) : "");
        return convertToIndianWords(n / 10000000) + " Crore" + (n % 10000000 > 0 ? " " + convertToIndianWords(n % 10000000) : "");
    }

    private static String formatAmount(BigDecimal d) {
        if (d == null) return "0.00";
        return d.setScale(SCALE, RoundingMode.HALF_UP).toPlainString();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO.setScale(SCALE) : v.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
