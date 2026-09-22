package com.billing.simple.billsoft.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.billing.simple.billsoft.dtos.InvoicePrintOptions;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoiceItem;
import com.billing.simple.billsoft.enums.InvoiceTheme;
import com.billing.simple.billsoft.enums.PrintFormat;
import com.billing.simple.billsoft.service.pdf.theme.MinimalThemeRenderer;
import com.billing.simple.billsoft.service.pdf.theme.ModernThemeRenderer;
import com.billing.simple.billsoft.service.pdf.theme.ProfessionalThemeRenderer;
import com.billing.simple.billsoft.service.pdf.thermal.Thermal80mmRenderer;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
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
        FirmDetails firm = getFirm(invoice);
        InvoicePrintOptions options = InvoicePrintOptions.resolve(size, null, null, null, firm);
        return generatePdf(invoice, options);
    }

    public byte[] generatePdf(Invoice invoice, String size, String format, String theme, String color) throws Exception {
        FirmDetails firm = getFirm(invoice);
        InvoicePrintOptions options = InvoicePrintOptions.resolve(size, format, theme, color, firm);
        return generatePdf(invoice, options);
    }

    public byte[] generatePdf(Invoice invoice, InvoicePrintOptions options) throws Exception {
        FirmDetails firm = getFirm(invoice);
        if (options == null) {
            options = InvoicePrintOptions.resolve(null, null, null, null, firm);
        }

        if (options.getFormat() == PrintFormat.THERMAL_80MM) {
            return Thermal80mmRenderer.render(invoice, firm, options);
        }
        if (options.getTheme() == InvoiceTheme.MODERN) {
            return ModernThemeRenderer.render(invoice, firm, options);
        }
        if (options.getTheme() == InvoiceTheme.PROFESSIONAL) {
            return ProfessionalThemeRenderer.render(invoice, firm, options);
        }
        if (options.getTheme() == InvoiceTheme.MINIMAL) {
            return MinimalThemeRenderer.render(invoice, firm, options);
        }

        return renderClassic(invoice, firm, options);
    }

    private FirmDetails getFirm(Invoice invoice) {
        try {
            Long firmId = invoice != null ? invoice.getFirmId() : null;
            return firmId != null ? firmService.get(firmId) : firmService.getFirst();
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] renderClassic(Invoice invoice, FirmDetails firm, InvoicePrintOptions options) throws Exception {
        PrintFormat format = options != null ? options.getFormat() : PrintFormat.A4;
        boolean isA5 = format == PrintFormat.A5;
        boolean isA3 = format == PrintFormat.A3;
        Rectangle pageSize = isA5 ? PageSize.A5 : (isA3 ? PageSize.A3 : PageSize.A4);
        Color headerColor = (options != null && options.getColorPalette() != null) ? options.getColorPalette().getPrimaryColor() : HEADER_BLUE;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        float margin = isA5 ? 20 : (isA3 ? 36 : 28);
        Document doc = new Document(pageSize, margin, margin, margin, margin + 10);
        PdfWriter writer = PdfWriter.getInstance(doc, baos);

        // Multi-page page number event helper
        PageNumberHelper pageHelper = new PageNumberHelper(isA5 ? 7f : 8f);
        writer.setPageEvent(pageHelper);

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

        boolean isEstimate = invoice != null && invoice.getStatus() != null && invoice.getStatus().name().equalsIgnoreCase("ESTIMATE");
        String documentTitle = isEstimate ? "Quotation" : "Tax Invoice";

        // Top Document Title (Centered)
        Paragraph docTitleP = new Paragraph(documentTitle, docTitleFont);
        docTitleP.setAlignment(Element.ALIGN_CENTER);
        docTitleP.setSpacingAfter(6f);
        doc.add(docTitleP);

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

        PdfPTable firmBlockWrapper = new PdfPTable(1);
        firmBlockWrapper.setWidthPercentage(100);
        PdfPCell firmBlockWrap = new PdfPCell(firmBlock);
        firmBlockWrap.setBorder(Rectangle.BOX);
        firmBlockWrap.setBorderColor(DARK_BORDER);
        firmBlockWrap.setBorderWidth(1f);
        firmBlockWrap.setPadding(0);
        firmBlockWrapper.addCell(firmBlockWrap);
        doc.add(firmBlockWrapper);

        // ─────────────────────────────────────────────────────────────────────────────
        // 2. BILL TO & INVOICE META ROW
        // ─────────────────────────────────────────────────────────────────────────────
        PdfPTable billToMetaTable = new PdfPTable(new float[]{2.2f, 1.8f});
        billToMetaTable.setWidthPercentage(100);

        // Left Half: Bill To Section
        PdfPTable leftBillTo = new PdfPTable(1);
        leftBillTo.setWidthPercentage(100);

        PdfPCell billToHeader = new PdfPCell(new Phrase("Bill To", subTitleFont));
        billToHeader.setBackgroundColor(headerColor);
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

        PdfPTable billToWrapper = new PdfPTable(1);
        billToWrapper.setWidthPercentage(100);
        PdfPCell billToMetaWrap = new PdfPCell(billToMetaTable);
        billToMetaWrap.setBorder(Rectangle.LEFT | Rectangle.RIGHT | Rectangle.BOTTOM);
        billToMetaWrap.setBorderColor(DARK_BORDER);
        billToMetaWrap.setBorderWidth(1f);
        billToMetaWrap.setPadding(0);
        billToWrapper.addCell(billToMetaWrap);
        doc.add(billToWrapper);

        // ─────────────────────────────────────────────────────────────────────────────
        // 3. ITEMS TABLE (With Multi-Page Split & Repeating Headers)
        // ─────────────────────────────────────────────────────────────────────────────
        float[] itemColWidths = new float[]{0.35f, 2.3f, 0.9f, 0.8f, 0.6f, 1.0f, 1.05f};
        PdfPTable itemsTable = new PdfPTable(itemColWidths);
        itemsTable.setWidthPercentage(100);
        itemsTable.setHeaderRows(1);
        itemsTable.setSplitLate(false);
        itemsTable.setSplitRows(true);

        // Table Header Row
        String[] itemHeaders = {"#", "Item name", "HSN/ SAC", "Quantity", "Unit", "Price/ Unit", "Amount"};
        for (int i = 0; i < itemHeaders.length; i++) {
            PdfPCell th = new PdfPCell(new Phrase(itemHeaders[i], tableHeaderFont));
            th.setBackgroundColor(headerColor);
            th.setBorderColor(LIGHT_BORDER);
            th.setBorderWidth(0.5f);
            if (i == 0) {
                th.setBorderColorLeft(DARK_BORDER);
                th.setBorderWidthLeft(1f);
            }
            if (i == itemHeaders.length - 1) {
                th.setBorderColorRight(DARK_BORDER);
                th.setBorderWidthRight(1f);
            }
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
                    if (it.getHsnCode() != null && !it.getHsnCode().isBlank()) {
                        hsnCode = it.getHsnCode().trim();
                    } else if (it.getProduct() != null && it.getProduct().getHsnCode() != null && !it.getProduct().getHsnCode().isBlank()) {
                        hsnCode = it.getProduct().getHsnCode().trim();
                    }

                    if (it.getProductName() != null && !it.getProductName().isBlank()) {
                        itemName = it.getProductName().trim();
                    } else if (it.getProduct() != null && it.getProduct().getName() != null) {
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

                PdfPCell cIdx = makeCell(String.valueOf(idx++), normalText, Element.ALIGN_CENTER, 5f);
                cIdx.setBorderColorLeft(DARK_BORDER);
                cIdx.setBorderWidthLeft(1f);
                itemsTable.addCell(cIdx);

                itemsTable.addCell(makeCell(itemName, normalText, Element.ALIGN_LEFT, 5f));
                itemsTable.addCell(makeCell(hsnCode, normalText, Element.ALIGN_CENTER, 5f));
                itemsTable.addCell(makeCell(String.valueOf(q), normalText, Element.ALIGN_CENTER, 5f));
                itemsTable.addCell(makeCell(unit, normalText, Element.ALIGN_CENTER, 5f));
                itemsTable.addCell(makeCell(priceStr, normalText, Element.ALIGN_RIGHT, 5f));

                PdfPCell cAmt = makeCell(amountStr, normalText, Element.ALIGN_RIGHT, 5f);
                cAmt.setBorderColorRight(DARK_BORDER);
                cAmt.setBorderWidthRight(1f);
                itemsTable.addCell(cAmt);
            }
        } else {
            PdfPCell emptyCell = new PdfPCell(new Phrase("No items recorded", normalText));
            emptyCell.setColspan(itemHeaders.length);
            emptyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            emptyCell.setPadding(12f);
            emptyCell.setBorderColor(LIGHT_BORDER);
            emptyCell.setBorderColorLeft(DARK_BORDER);
            emptyCell.setBorderWidthLeft(1f);
            emptyCell.setBorderColorRight(DARK_BORDER);
            emptyCell.setBorderWidthRight(1f);
            itemsTable.addCell(emptyCell);
        }

        // Table Total Row
        BigDecimal grandTotal = nz(invoice != null ? invoice.getTotalAmount() : null);

        PdfPCell totalLabelCell = new PdfPCell(new Phrase("Total", boldText));
        totalLabelCell.setColspan(2);
        totalLabelCell.setBorderColor(LIGHT_BORDER);
        totalLabelCell.setBorderWidth(0.5f);
        totalLabelCell.setBorderColorLeft(DARK_BORDER);
        totalLabelCell.setBorderWidthLeft(1f);
        totalLabelCell.setBorderColorBottom(DARK_BORDER);
        totalLabelCell.setBorderWidthBottom(1f);
        totalLabelCell.setPadding(5f);
        totalLabelCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        itemsTable.addCell(totalLabelCell);

        PdfPCell cellHsnTotal = makeCell("", boldText, Element.ALIGN_CENTER, 5f);
        cellHsnTotal.setBorderColorBottom(DARK_BORDER);
        cellHsnTotal.setBorderWidthBottom(1f);
        itemsTable.addCell(cellHsnTotal);

        PdfPCell cellQtyTotal = makeCell(String.valueOf(totalQty), boldText, Element.ALIGN_CENTER, 5f);
        cellQtyTotal.setBorderColorBottom(DARK_BORDER);
        cellQtyTotal.setBorderWidthBottom(1f);
        itemsTable.addCell(cellQtyTotal);

        PdfPCell cellUnitTotal = makeCell("", boldText, Element.ALIGN_CENTER, 5f);
        cellUnitTotal.setBorderColorBottom(DARK_BORDER);
        cellUnitTotal.setBorderWidthBottom(1f);
        itemsTable.addCell(cellUnitTotal);

        PdfPCell cellPriceTotal = makeCell("", boldText, Element.ALIGN_RIGHT, 5f);
        cellPriceTotal.setBorderColorBottom(DARK_BORDER);
        cellPriceTotal.setBorderWidthBottom(1f);
        itemsTable.addCell(cellPriceTotal);

        PdfPCell cellGrandTotal = makeCell("₹ " + formatAmount(grandTotal), totalBoldFont, Element.ALIGN_RIGHT, 5f);
        cellGrandTotal.setBorderColorRight(DARK_BORDER);
        cellGrandTotal.setBorderWidthRight(1f);
        cellGrandTotal.setBorderColorBottom(DARK_BORDER);
        cellGrandTotal.setBorderWidthBottom(1f);
        itemsTable.addCell(cellGrandTotal);

        doc.add(itemsTable);

        // ─────────────────────────────────────────────────────────────────────────────
        // 4. AMOUNTS IN WORDS & SUMMARY BREAKDOWN
        // ─────────────────────────────────────────────────────────────────────────────
        PdfPTable summaryTable = new PdfPTable(new float[]{2.2f, 1.8f});
        summaryTable.setWidthPercentage(100);
        summaryTable.setKeepTogether(true);

        // Sub Header: "Invoice Amount In Words" vs "Amounts:"
        PdfPCell wordsHeader = new PdfPCell(new Phrase(isEstimate ? "Quotation Amount In Words" : "Invoice Amount In Words", subTitleFont));
        wordsHeader.setBackgroundColor(headerColor);
        wordsHeader.setBorder(Rectangle.NO_BORDER);
        wordsHeader.setPadding(4f);
        wordsHeader.setHorizontalAlignment(Element.ALIGN_CENTER);
        summaryTable.addCell(wordsHeader);

        PdfPCell amountsHeader = new PdfPCell(new Phrase("Amounts:", subTitleFont));
        amountsHeader.setBackgroundColor(headerColor);
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
        if (!isEstimate) {
            addBreakdownRow(amountsBreakdown, "Received", "₹ " + formatAmount(received), normalText, normalText);
            addBreakdownRow(amountsBreakdown, "Balance", "₹ " + formatAmount(balance), boldText, boldText);
        }

        PdfPCell amountsContent = new PdfPCell(amountsBreakdown);
        amountsContent.setBorder(Rectangle.LEFT);
        amountsContent.setBorderColor(DARK_BORDER);
        amountsContent.setBorderWidth(1f);
        amountsContent.setPadding(0);
        summaryTable.addCell(amountsContent);

        PdfPTable summaryWrapper = new PdfPTable(1);
        summaryWrapper.setWidthPercentage(100);
        summaryWrapper.setKeepTogether(true);
        PdfPCell summaryWrap = new PdfPCell(summaryTable);
        summaryWrap.setBorder(Rectangle.LEFT | Rectangle.RIGHT | Rectangle.BOTTOM);
        summaryWrap.setBorderColor(DARK_BORDER);
        summaryWrap.setBorderWidth(1f);
        summaryWrap.setPadding(0);
        summaryWrapper.addCell(summaryWrap);
        doc.add(summaryWrapper);

        // ─────────────────────────────────────────────────────────────────────────────
        // 5. FOOTER: UPI QR CODE & AUTHORIZED SIGNATORY
        // ─────────────────────────────────────────────────────────────────────────────
        PdfPTable footerTable = new PdfPTable(new float[]{2.2f, 1.8f});
        footerTable.setWidthPercentage(100);
        footerTable.setKeepTogether(true);

        // Left Half: UPI QR Code / Payment Box
        PdfPCell qrCell = new PdfPCell();
        qrCell.setBorder(Rectangle.NO_BORDER);
        qrCell.setPadding(10f);
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
                float qrSize = isA5 ? 58f : 66f;
                qrImage.scaleToFit(qrSize, qrSize);
                qrImage.setAlignment(Image.ALIGN_CENTER);

                // Professional Section Header
                Paragraph pTitle = new Paragraph("SCAN TO PAY", new Font(Font.HELVETICA, isA5 ? 7.5f : 8.5f, Font.BOLD, TEXT_DARK));
                pTitle.setAlignment(Element.ALIGN_CENTER);
                pTitle.setSpacingAfter(5f);
                qrCell.addElement(pTitle);

                qrCell.addElement(qrImage);

                // Professional Clean Caption (No raw UPI ID)
                Paragraph pSub = new Paragraph("Scan with any UPI app", new Font(Font.HELVETICA, isA5 ? 6f : 6.5f, Font.NORMAL, TEXT_MUTED));
                pSub.setAlignment(Element.ALIGN_CENTER);
                pSub.setSpacingBefore(4f);
                qrCell.addElement(pSub);
            } catch (Throwable t) {
                qrCell.addElement(new Paragraph(" ", normalText));
            }
        } else {
            // If Quotation or no UPI ID configured, show clean empty space / footer note
            qrCell.addElement(new Paragraph(" ", normalText));
        }

        footerTable.addCell(qrCell);

        // Right Half: Authorized Signatory
        PdfPCell signCell = new PdfPCell();
        signCell.setBorder(Rectangle.LEFT);
        signCell.setBorderColor(DARK_BORDER);
        signCell.setBorderWidth(1f);
        signCell.setPadding(8f);
        signCell.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph pFor = new Paragraph("For, " + fName, boldText);
        pFor.setAlignment(Element.ALIGN_CENTER);
        pFor.setSpacingAfter(4f);
        signCell.addElement(pFor);

        // Realistic Drawn Signature / Ink Path Simulation
        Paragraph pSigSpace = new Paragraph("\n\n", normalText);
        pSigSpace.setAlignment(Element.ALIGN_CENTER);
        signCell.addElement(pSigSpace);

        Paragraph pAuth = new Paragraph("Authorized Signatory", new Font(Font.HELVETICA, isA5 ? 7.5f : 8f, Font.NORMAL, TEXT_MUTED));
        pAuth.setAlignment(Element.ALIGN_CENTER);
        signCell.addElement(pAuth);

        footerTable.addCell(signCell);

        PdfPTable footerWrapper = new PdfPTable(1);
        footerWrapper.setWidthPercentage(100);
        footerWrapper.setKeepTogether(true);
        PdfPCell footerWrap = new PdfPCell(footerTable);
        footerWrap.setBorder(Rectangle.LEFT | Rectangle.RIGHT | Rectangle.BOTTOM);
        footerWrap.setBorderColor(DARK_BORDER);
        footerWrap.setBorderWidth(1f);
        footerWrap.setPadding(0);
        footerWrapper.addCell(footerWrap);
        doc.add(footerWrapper);

        doc.close();
        return baos.toByteArray();
    }

    /**
     * Page Number Helper for professional multi-page document pagination.
     */
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

        boolean isNegative = amount.signum() < 0;
        BigDecimal absAmount = amount.abs();
        long wholePart = absAmount.setScale(0, RoundingMode.FLOOR).longValue();
        int decimalPart = absAmount.remainder(BigDecimal.ONE).movePointRight(2).setScale(0, RoundingMode.FLOOR).intValue();

        String words = wholePart == 0 ? "Zero" : convertToIndianWords(wholePart);
        String result = (isNegative ? "Negative " : "") + words + " Rupees";
        if (decimalPart > 0) {
            result += " and " + convertToIndianWords(decimalPart) + " Paise";
        }
        result += " only";
        return result;
    }

    public static String convertToIndianWords(long n) {
        if (n < 0) {
            return "Negative " + convertToIndianWords(-n);
        }
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
