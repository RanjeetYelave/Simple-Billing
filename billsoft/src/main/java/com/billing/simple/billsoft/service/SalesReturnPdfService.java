package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dtos.InvoicePrintOptions;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.SalesReturn;
import com.billing.simple.billsoft.entities.SalesReturnItem;
import com.billing.simple.billsoft.enums.InvoiceColorPalette;
import com.billing.simple.billsoft.enums.InvoiceTheme;
import com.billing.simple.billsoft.enums.PrintFormat;
import com.billing.simple.billsoft.service.pdf.core.DocumentThemeHelper;
import com.billing.simple.billsoft.service.pdf.core.UniversalPageNumberHelper;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates official Credit Note / Sales Return Slip PDFs with multi-theme and multi-format support.
 */
@Service
public class SalesReturnPdfService {

    private final FirmDetailsService firmService;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public SalesReturnPdfService(FirmDetailsService firmService) {
        this.firmService = firmService;
    }

    public byte[] generatePdf(SalesReturn salesReturn, String size) throws Exception {
        return generatePdf(salesReturn, size, null, null, null);
    }

    public byte[] generatePdf(SalesReturn salesReturn, String size, String formatStr, String themeStr, String colorStr) throws Exception {
        FirmDetails firm = null;
        try {
            Long firmId = salesReturn != null ? salesReturn.getFirmId() : null;
            firm = firmId != null ? firmService.get(firmId) : firmService.getFirst();
        } catch (Exception ignored) {
            firm = null;
        }

        InvoicePrintOptions options = InvoicePrintOptions.resolve(size, formatStr, themeStr, colorStr, firm);

        if (options.getFormat() == PrintFormat.THERMAL_80MM) {
            return renderThermal80mm(salesReturn, firm, options);
        }

        return renderPagedPdf(salesReturn, firm, options);
    }

    private byte[] renderPagedPdf(SalesReturn salesReturn, FirmDetails firm, InvoicePrintOptions options) throws Exception {
        InvoiceTheme theme = options.getTheme();
        InvoiceColorPalette palette = options.getColorPalette();
        Color primaryColor = palette.getPrimaryColor();
        Color lightTint = palette.getLightTintColor();

        Rectangle pageSize = (options.getFormat() == PrintFormat.A5) ? PageSize.A5 : PageSize.A4;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(pageSize, 28, 28, 28, 36);
        PdfWriter writer = PdfWriter.getInstance(doc, baos);

        writer.setPageEvent(new UniversalPageNumberHelper(8f));
        doc.open();

        Customer cust = salesReturn.getCustomer();
        float scale = (options.getFormat() == PrintFormat.A5) ? 0.85f : 1.0f;

        Font firmTitleFont = new Font(Font.HELVETICA, 14 * scale, Font.BOLD, DocumentThemeHelper.TEXT_DARK);
        Font firmSubFont = new Font(Font.HELVETICA, 8.5f * scale, Font.NORMAL, DocumentThemeHelper.TEXT_MUTED);
        Font docTypeFont = new Font(Font.HELVETICA, 13 * scale, Font.BOLD, primaryColor);
        Font metaFont = new Font(Font.HELVETICA, 8.5f * scale, Font.NORMAL, DocumentThemeHelper.TEXT_DARK);
        Font metaBold = new Font(Font.HELVETICA, 8.5f * scale, Font.BOLD, DocumentThemeHelper.TEXT_DARK);

        // 1. Professional Banner (if Professional theme)
        if (theme == InvoiceTheme.PROFESSIONAL) {
            doc.add(DocumentThemeHelper.createProfessionalBanner("CREDIT NOTE / SALES RETURN", primaryColor, scale));
        }

        // 2. Header Block
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{60, 40});

        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);

        Image logo = (firm != null) ? DocumentThemeHelper.loadLogoImage(firm.getLogoBase64(), 100 * scale, 45 * scale) : null;
        if (logo != null) {
            leftCell.addElement(logo);
        }

        String firmName = (firm != null && firm.getFirmName() != null && !firm.getFirmName().isBlank())
                ? firm.getFirmName() : "Enterprise Billing";
        leftCell.addElement(new Paragraph(firmName, firmTitleFont));

        if (firm != null) {
            String addr = (firm.getAddressLine1() != null ? firm.getAddressLine1() : "") +
                    (firm.getAddressLine2() != null ? ", " + firm.getAddressLine2() : "");
            if (!addr.isBlank()) leftCell.addElement(new Paragraph(addr, firmSubFont));
            if (firm.getPhone() != null && !firm.getPhone().isBlank()) leftCell.addElement(new Paragraph("Phone: " + firm.getPhone(), firmSubFont));
            if (firm.getGstin() != null && !firm.getGstin().isBlank()) leftCell.addElement(new Paragraph("GSTIN: " + firm.getGstin(), firmSubFont));
        }
        headerTable.addCell(leftCell);

        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

        if (theme != InvoiceTheme.PROFESSIONAL) {
            Paragraph pDocType = new Paragraph("CREDIT NOTE / SALES RETURN", docTypeFont);
            pDocType.setAlignment(Element.ALIGN_RIGHT);
            rightCell.addElement(pDocType);
        }

        Paragraph pNo = new Paragraph();
        pNo.setAlignment(Element.ALIGN_RIGHT);
        pNo.add(new Chunk("Credit Note #: ", metaBold));
        pNo.add(new Chunk(salesReturn.getReturnNumber() != null ? salesReturn.getReturnNumber() : "—", metaFont));
        rightCell.addElement(pNo);

        Paragraph pDate = new Paragraph();
        pDate.setAlignment(Element.ALIGN_RIGHT);
        pDate.add(new Chunk("Date: ", metaBold));
        pDate.add(new Chunk(salesReturn.getReturnDate() != null ? salesReturn.getReturnDate().format(DATE_FMT) : "", metaFont));
        rightCell.addElement(pDate);

        if (salesReturn.getInvoice() != null) {
            Paragraph pInv = new Paragraph();
            pInv.setAlignment(Element.ALIGN_RIGHT);
            pInv.add(new Chunk("Ref Invoice: ", metaBold));
            String invNum = salesReturn.getInvoice().getInvoiceNumber() != null
                    ? salesReturn.getInvoice().getInvoiceNumber() : "#" + salesReturn.getInvoice().getId();
            pInv.add(new Chunk(invNum, metaFont));
            rightCell.addElement(pInv);
        }

        headerTable.addCell(rightCell);
        doc.add(headerTable);
        doc.add(new Paragraph(" "));

        // 3. Customer & Return Details Box
        PdfPTable partyTable = new PdfPTable(2);
        partyTable.setWidthPercentage(100);
        partyTable.setWidths(new float[]{55, 45});

        PdfPCell billToCell = new PdfPCell();
        billToCell.setPadding(8 * scale);
        if (theme == InvoiceTheme.MINIMAL) {
            billToCell.setBorder(Rectangle.BOTTOM);
            billToCell.setBorderColor(DocumentThemeHelper.BORDER_LIGHT);
            billToCell.setBackgroundColor(Color.WHITE);
        } else if (theme == InvoiceTheme.MODERN) {
            billToCell.setBorder(Rectangle.BOX);
            billToCell.setBorderColor(DocumentThemeHelper.BORDER_LIGHT);
            billToCell.setBackgroundColor(lightTint);
        } else {
            billToCell.setBorder(Rectangle.BOX);
            billToCell.setBorderColor(DocumentThemeHelper.BORDER_COLOR);
            billToCell.setBackgroundColor(Color.WHITE);
        }

        billToCell.addElement(new Paragraph("CUSTOMER DETAILS", new Font(Font.HELVETICA, 8 * scale, Font.BOLD, primaryColor)));
        billToCell.addElement(new Paragraph(cust != null ? cust.getName() : "Direct Customer", new Font(Font.HELVETICA, 9.5f * scale, Font.BOLD, DocumentThemeHelper.TEXT_DARK)));
        if (cust != null) {
            if (cust.getAddress() != null) billToCell.addElement(new Paragraph(cust.getAddress(), firmSubFont));
            if (cust.getPhone() != null) billToCell.addElement(new Paragraph("Phone: " + cust.getPhone(), firmSubFont));
            if (cust.getGstin() != null) billToCell.addElement(new Paragraph("GSTIN: " + cust.getGstin(), firmSubFont));
        }
        partyTable.addCell(billToCell);

        PdfPCell reasonCell = new PdfPCell();
        reasonCell.setPadding(8 * scale);
        if (theme == InvoiceTheme.MINIMAL) {
            reasonCell.setBorder(Rectangle.BOTTOM);
            reasonCell.setBorderColor(DocumentThemeHelper.BORDER_LIGHT);
            reasonCell.setBackgroundColor(Color.WHITE);
        } else if (theme == InvoiceTheme.MODERN) {
            reasonCell.setBorder(Rectangle.BOX);
            reasonCell.setBorderColor(DocumentThemeHelper.BORDER_LIGHT);
            reasonCell.setBackgroundColor(lightTint);
        } else {
            reasonCell.setBorder(Rectangle.BOX);
            reasonCell.setBorderColor(DocumentThemeHelper.BORDER_COLOR);
            reasonCell.setBackgroundColor(Color.WHITE);
        }

        reasonCell.addElement(new Paragraph("RETURN DETAILS", new Font(Font.HELVETICA, 8 * scale, Font.BOLD, primaryColor)));
        reasonCell.addElement(new Paragraph("Reason: " + (salesReturn.getReason() != null ? salesReturn.getReason() : "Customer Return"), metaFont));
        reasonCell.addElement(new Paragraph("Refund Mode: " + (salesReturn.getRefundMode() != null ? salesReturn.getRefundMode() : "CASH"), metaFont));
        if (salesReturn.getNotes() != null && !salesReturn.getNotes().isBlank()) {
            reasonCell.addElement(new Paragraph("Notes: " + salesReturn.getNotes(), firmSubFont));
        }
        partyTable.addCell(reasonCell);
        doc.add(partyTable);
        doc.add(new Paragraph(" "));

        // 4. Returned Items Table
        PdfPTable itemsTable = new PdfPTable(7);
        itemsTable.setWidthPercentage(100);
        itemsTable.setWidths(new float[]{6, 38, 12, 10, 14, 10, 14});
        itemsTable.setHeaderRows(1);

        String[] headers = {"#", "Item Description", "HSN/SAC", "Qty", "Rate (₹)", "GST %", "Refund (₹)"};
        Font thFont = new Font(Font.HELVETICA, 8f * scale, Font.BOLD, (theme == InvoiceTheme.MINIMAL) ? primaryColor : Color.WHITE);

        for (String h : headers) {
            int align = h.equals("Item Description") ? Element.ALIGN_LEFT :
                    (h.contains("Rate") || h.contains("Refund")) ? Element.ALIGN_RIGHT : Element.ALIGN_CENTER;
            itemsTable.addCell(DocumentThemeHelper.createHeaderCell(h, theme, primaryColor, thFont, align, scale));
        }

        List<SalesReturnItem> items = salesReturn.getItems();
        Font rowFont = new Font(Font.HELVETICA, 8f * scale, Font.NORMAL, DocumentThemeHelper.TEXT_DARK);

        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                SalesReturnItem item = items.get(i);
                boolean isEven = (i % 2 == 1);

                itemsTable.addCell(DocumentThemeHelper.createDataCell(new Phrase(String.valueOf(i + 1), rowFont), theme, isEven, Element.ALIGN_CENTER, scale));
                itemsTable.addCell(DocumentThemeHelper.createDataCell(new Phrase(item.getProductName() != null ? item.getProductName() : "", rowFont), theme, isEven, Element.ALIGN_LEFT, scale));
                itemsTable.addCell(DocumentThemeHelper.createDataCell(new Phrase(item.getHsnCode() != null ? item.getHsnCode() : "—", rowFont), theme, isEven, Element.ALIGN_CENTER, scale));
                itemsTable.addCell(DocumentThemeHelper.createDataCell(new Phrase(String.valueOf(item.getReturnQty()) + (item.getUnit() != null ? " " + item.getUnit() : ""), rowFont), theme, isEven, Element.ALIGN_CENTER, scale));
                itemsTable.addCell(DocumentThemeHelper.createDataCell(new Phrase(item.getUnitPrice() != null ? String.format("%.2f", item.getUnitPrice()) : "0.00", rowFont), theme, isEven, Element.ALIGN_RIGHT, scale));
                itemsTable.addCell(DocumentThemeHelper.createDataCell(new Phrase(item.getGstPercent() != null ? item.getGstPercent() + "%" : "0%", rowFont), theme, isEven, Element.ALIGN_CENTER, scale));
                itemsTable.addCell(DocumentThemeHelper.createDataCell(new Phrase(item.getRefundTotal() != null ? String.format("%.2f", item.getRefundTotal()) : "0.00", rowFont), theme, isEven, Element.ALIGN_RIGHT, scale));
            }
        }
        doc.add(itemsTable);

        // 5. Summary Table
        PdfPTable summaryTable = new PdfPTable(2);
        summaryTable.setWidthPercentage(48);
        summaryTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
        summaryTable.setKeepTogether(true);
        summaryTable.setSpacingBefore(10 * scale);

        Font sumLabel = new Font(Font.HELVETICA, 8.5f * scale, Font.NORMAL, DocumentThemeHelper.TEXT_DARK);
        Font sumDeduct = new Font(Font.HELVETICA, 8.5f * scale, Font.NORMAL, new Color(220, 38, 38));
        Font sumBold = new Font(Font.HELVETICA, 9.5f * scale, Font.BOLD, primaryColor);

        if (salesReturn.getExcludedTaxAmount() != null && salesReturn.getExcludedTaxAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal grossValue = salesReturn.getSubtotal().add(salesReturn.getExcludedTaxAmount());
            addSummaryRow(summaryTable, "Gross Return (Incl. GST):", grossValue, sumLabel, false);
            addSummaryRow(summaryTable, "Less GST / Tax Deduction:", salesReturn.getExcludedTaxAmount(), sumDeduct, true);
        } else {
            addSummaryRow(summaryTable, "Taxable Return Amount:", salesReturn.getSubtotal(), sumLabel, false);
            if (salesReturn.getTaxAmount() != null && salesReturn.getTaxAmount().compareTo(BigDecimal.ZERO) > 0) {
                addSummaryRow(summaryTable, "GST Refund Total:", salesReturn.getTaxAmount(), sumLabel, false);
            }
        }

        if (salesReturn.getPenaltyAmount() != null && salesReturn.getPenaltyAmount().compareTo(BigDecimal.ZERO) > 0) {
            String pReason = (salesReturn.getPenaltyReason() != null && !salesReturn.getPenaltyReason().isBlank())
                    ? " (" + salesReturn.getPenaltyReason() + ")" : "";
            addSummaryRow(summaryTable, "Less Deduction" + pReason + ":", salesReturn.getPenaltyAmount(), sumDeduct, true);
        }

        addSummaryRow(summaryTable, "Total Refund / Credit:", salesReturn.getTotalRefundAmount(), sumBold, false);
        doc.add(summaryTable);

        // 6. Signoff
        PdfPTable signTable = new PdfPTable(2);
        signTable.setWidthPercentage(100);
        signTable.setKeepTogether(true);
        signTable.setSpacingBefore(20 * scale);

        PdfPCell signLeft = new PdfPCell(new Phrase("Customer Signature / Acknowledgement\n\n\n_______________________", metaFont));
        signLeft.setBorder(Rectangle.NO_BORDER);

        PdfPCell signRight = new PdfPCell(new Phrase("For " + firmName + "\n\n\nAuthorized Signatory", metaFont));
        signRight.setBorder(Rectangle.NO_BORDER);
        signRight.setHorizontalAlignment(Element.ALIGN_RIGHT);

        signTable.addCell(signLeft);
        signTable.addCell(signRight);
        doc.add(signTable);

        doc.close();
        return baos.toByteArray();
    }

    private byte[] renderThermal80mm(SalesReturn salesReturn, FirmDetails firm, InvoicePrintOptions options) throws Exception {
        // Dynamic roll height based on content
        int itemCount = (salesReturn != null && salesReturn.getItems() != null) ? salesReturn.getItems().size() : 1;
        float baseHeight = 240f;
        float calculatedHeight = Math.max(260f, baseHeight + (itemCount * 24f));
        Rectangle pageSize = new Rectangle(226.77f, calculatedHeight);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(pageSize, 8, 8, 10, 10);
        PdfWriter writer = PdfWriter.getInstance(doc, baos);
        doc.open();

        Font storeTitleFont = new Font(Font.HELVETICA, 10f, Font.BOLD, Color.BLACK);
        Font subFont = new Font(Font.HELVETICA, 7f, Font.NORMAL, Color.BLACK);
        Font boldSubFont = new Font(Font.HELVETICA, 7.5f, Font.BOLD, Color.BLACK);
        Font itemFont = new Font(Font.HELVETICA, 6.8f, Font.NORMAL, Color.BLACK);

        String firmName = (firm != null && firm.getFirmName() != null && !firm.getFirmName().isBlank())
                ? firm.getFirmName() : "STORE COUNTER";

        Paragraph pTitle = new Paragraph(firmName, storeTitleFont);
        pTitle.setAlignment(Element.ALIGN_CENTER);
        doc.add(pTitle);

        if (firm != null) {
            String addr = (firm.getAddressLine1() != null ? firm.getAddressLine1() : "");
            if (firm.getCity() != null) addr += (addr.isEmpty() ? "" : ", ") + firm.getCity();
            if (!addr.isBlank()) {
                Paragraph pAddr = new Paragraph(addr, subFont);
                pAddr.setAlignment(Element.ALIGN_CENTER);
                doc.add(pAddr);
            }
            if (firm.getGstin() != null && !firm.getGstin().isBlank()) {
                Paragraph pGstin = new Paragraph("GSTIN: " + firm.getGstin(), subFont);
                pGstin.setAlignment(Element.ALIGN_CENTER);
                doc.add(pGstin);
            }
            if (firm.getPhone() != null && !firm.getPhone().isBlank()) {
                Paragraph pPhone = new Paragraph("Phone: " + firm.getPhone(), subFont);
                pPhone.setAlignment(Element.ALIGN_CENTER);
                doc.add(pPhone);
            }
        }

        Paragraph pDashes = new Paragraph("---------------------------------------------------------", subFont);
        pDashes.setAlignment(Element.ALIGN_CENTER);
        doc.add(pDashes);

        Paragraph pHeader = new Paragraph("CREDIT NOTE / RETURN RECEIPT", boldSubFont);
        pHeader.setAlignment(Element.ALIGN_CENTER);
        doc.add(pHeader);

        Paragraph pMeta = new Paragraph();
        pMeta.add(new Chunk("CN #: " + (salesReturn.getReturnNumber() != null ? salesReturn.getReturnNumber() : "—") + "\n", boldSubFont));
        pMeta.add(new Chunk("Date: " + (salesReturn.getReturnDate() != null ? salesReturn.getReturnDate().format(DATE_FMT) : "") + "\n", subFont));
        if (salesReturn.getInvoice() != null) {
            String invNum = salesReturn.getInvoice().getInvoiceNumber() != null
                    ? salesReturn.getInvoice().getInvoiceNumber() : "#" + salesReturn.getInvoice().getId();
            pMeta.add(new Chunk("Ref Inv: " + invNum + "\n", subFont));
        }
        Customer cust = salesReturn.getCustomer();
        if (cust != null) {
            pMeta.add(new Chunk("Customer: " + cust.getName() + "\n", subFont));
            if (cust.getPhone() != null) pMeta.add(new Chunk("Phone: " + cust.getPhone() + "\n", subFont));
        }
        doc.add(pMeta);
        doc.add(pDashes);

        // Items Table
        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{50, 20, 30});

        PdfPCell h1 = new PdfPCell(new Phrase("ITEM", boldSubFont));
        h1.setBorder(Rectangle.NO_BORDER);
        PdfPCell h2 = new PdfPCell(new Phrase("QTY", boldSubFont));
        h2.setBorder(Rectangle.NO_BORDER);
        h2.setHorizontalAlignment(Element.ALIGN_CENTER);
        PdfPCell h3 = new PdfPCell(new Phrase("REFUND", boldSubFont));
        h3.setBorder(Rectangle.NO_BORDER);
        h3.setHorizontalAlignment(Element.ALIGN_RIGHT);

        table.addCell(h1);
        table.addCell(h2);
        table.addCell(h3);

        List<SalesReturnItem> items = salesReturn.getItems();
        if (items != null) {
            for (SalesReturnItem item : items) {
                PdfPCell c1 = new PdfPCell(new Phrase(item.getProductName(), itemFont));
                c1.setBorder(Rectangle.NO_BORDER);
                PdfPCell c2 = new PdfPCell(new Phrase(String.valueOf(item.getReturnQty()), itemFont));
                c2.setBorder(Rectangle.NO_BORDER);
                c2.setHorizontalAlignment(Element.ALIGN_CENTER);
                PdfPCell c3 = new PdfPCell(new Phrase(String.format("%.2f", item.getRefundTotal() != null ? item.getRefundTotal() : BigDecimal.ZERO), itemFont));
                c3.setBorder(Rectangle.NO_BORDER);
                c3.setHorizontalAlignment(Element.ALIGN_RIGHT);

                table.addCell(c1);
                table.addCell(c2);
                table.addCell(c3);
            }
        }
        doc.add(table);
        doc.add(pDashes);

        // Summary
        PdfPTable sumTable = new PdfPTable(2);
        sumTable.setWidthPercentage(100);
        sumTable.setWidths(new float[]{60, 40});

        addThermalRow(sumTable, "Taxable Return:", salesReturn.getSubtotal(), subFont);
        if (salesReturn.getTaxAmount() != null && salesReturn.getTaxAmount().compareTo(BigDecimal.ZERO) > 0) {
            addThermalRow(sumTable, "GST Refund:", salesReturn.getTaxAmount(), subFont);
        }
        if (salesReturn.getPenaltyAmount() != null && salesReturn.getPenaltyAmount().compareTo(BigDecimal.ZERO) > 0) {
            addThermalRow(sumTable, "Less Deduction:", salesReturn.getPenaltyAmount().negate(), subFont);
        }
        addThermalRow(sumTable, "TOTAL REFUND:", salesReturn.getTotalRefundAmount(), boldSubFont);
        doc.add(sumTable);

        doc.add(pDashes);
        Paragraph pFooter = new Paragraph();
        pFooter.setAlignment(Element.ALIGN_CENTER);
        pFooter.add(new Chunk("Refund Mode: " + (salesReturn.getRefundMode() != null ? salesReturn.getRefundMode() : "CASH") + "\n", subFont));
        if (salesReturn.getReason() != null) pFooter.add(new Chunk("Reason: " + salesReturn.getReason() + "\n", subFont));
        pFooter.add(new Chunk("\nThank You! Please Retain Slip.\n", subFont));
        doc.add(pFooter);

        doc.close();
        return baos.toByteArray();
    }

    private void addThermalRow(PdfPTable table, String label, BigDecimal amt, Font font) {
        PdfPCell c1 = new PdfPCell(new Phrase(label, font));
        c1.setBorder(Rectangle.NO_BORDER);
        PdfPCell c2 = new PdfPCell(new Phrase(amt != null ? String.format("₹ %.2f", amt) : "₹ 0.00", font));
        c2.setBorder(Rectangle.NO_BORDER);
        c2.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(c1);
        table.addCell(c2);
    }

    private void addSummaryRow(PdfPTable table, String label, BigDecimal amt, Font font, boolean isDeduction) {
        PdfPCell c1 = new PdfPCell(new Phrase(label, font));
        c1.setBorder(Rectangle.NO_BORDER);
        c1.setPadding(3);
        table.addCell(c1);

        String prefix = isDeduction ? "-₹ " : "₹ ";
        PdfPCell c2 = new PdfPCell(new Phrase(amt != null ? prefix + String.format("%.2f", amt) : "₹ 0.00", font));
        c2.setBorder(Rectangle.NO_BORDER);
        c2.setHorizontalAlignment(Element.ALIGN_RIGHT);
        c2.setPadding(3);
        table.addCell(c2);
    }
}
