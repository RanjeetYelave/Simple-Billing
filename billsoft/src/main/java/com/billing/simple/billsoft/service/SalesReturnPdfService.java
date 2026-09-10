package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.SalesReturn;
import com.billing.simple.billsoft.entities.SalesReturnItem;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates official Credit Note / Sales Return Slip PDFs.
 */
@Service
public class SalesReturnPdfService {

    private final FirmDetailsService firmService;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    // Palette
    private static final Color HEADER_RED = new Color(220, 38, 38);   // #dc2626
    private static final Color DARK_BORDER = new Color(75, 85, 99);   // #4b5563
    private static final Color LIGHT_BORDER = new Color(209, 213, 219); // #d1d5db
    private static final Color TEXT_DARK = new Color(17, 24, 39);     // #111827
    private static final Color TEXT_MUTED = new Color(107, 114, 128); // #6b7280

    public SalesReturnPdfService(FirmDetailsService firmService) {
        this.firmService = firmService;
    }

    public byte[] generatePdf(SalesReturn salesReturn, String size) throws Exception {
        Rectangle pageSize = (size != null && size.equalsIgnoreCase("A5")) ? PageSize.A5 : PageSize.A4;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(pageSize, 28, 28, 28, 36);
        PdfWriter writer = PdfWriter.getInstance(doc, baos);

        PageNumberHelper pageHelper = new PageNumberHelper();
        writer.setPageEvent(pageHelper);

        doc.open();

        FirmDetails firm = null;
        try {
            Long firmId = salesReturn.getFirmId();
            firm = firmId != null ? firmService.get(firmId) : firmService.getFirst();
        } catch (Exception e) {
            firm = null;
        }
        Customer cust = salesReturn.getCustomer();

        // 1. Header Table
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{60, 40});

        Font firmTitleFont = new Font(Font.HELVETICA, 14, Font.BOLD, TEXT_DARK);
        Font firmSubFont = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, TEXT_MUTED);
        Font docTypeFont = new Font(Font.HELVETICA, 14, Font.BOLD, HEADER_RED);
        Font metaFont = new Font(Font.HELVETICA, 9, Font.NORMAL, TEXT_DARK);
        Font metaBold = new Font(Font.HELVETICA, 9, Font.BOLD, TEXT_DARK);

        // Left Cell: Firm
        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);
        String firmName = (firm != null && firm.getFirmName() != null) ? firm.getFirmName() : "Enterprise Billing";
        leftCell.addElement(new Paragraph(firmName, firmTitleFont));
        if (firm != null) {
            String addr = (firm.getAddressLine1() != null ? firm.getAddressLine1() : "") + (firm.getAddressLine2() != null ? ", " + firm.getAddressLine2() : "");
            if (!addr.isBlank()) leftCell.addElement(new Paragraph(addr, firmSubFont));
            if (firm.getPhone() != null) leftCell.addElement(new Paragraph("Phone: " + firm.getPhone(), firmSubFont));
            if (firm.getGstin() != null) leftCell.addElement(new Paragraph("GSTIN: " + firm.getGstin(), firmSubFont));
        }
        headerTable.addCell(leftCell);

        // Right Cell: Return Note info
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph pDocType = new Paragraph("CREDIT NOTE / SALES RETURN", docTypeFont);
        pDocType.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(pDocType);

        Paragraph pNo = new Paragraph();
        pNo.setAlignment(Element.ALIGN_RIGHT);
        pNo.add(new Chunk("Credit Note No: ", metaBold));
        pNo.add(new Chunk(salesReturn.getReturnNumber(), metaFont));
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
            pInv.add(new Chunk(salesReturn.getInvoice().getInvoiceNumber() != null ? salesReturn.getInvoice().getInvoiceNumber() : "#" + salesReturn.getInvoice().getId(), metaFont));
            rightCell.addElement(pInv);
        }

        headerTable.addCell(rightCell);
        doc.add(headerTable);

        doc.add(new Paragraph(" "));

        // 2. Customer & Reason Box
        PdfPTable partyTable = new PdfPTable(2);
        partyTable.setWidthPercentage(100);
        partyTable.setWidths(new float[]{55, 45});

        PdfPCell billToCell = new PdfPCell();
        billToCell.setBorder(Rectangle.BOX);
        billToCell.setBorderColor(LIGHT_BORDER);
        billToCell.setPadding(8);
        billToCell.addElement(new Paragraph("CUSTOMER DETAILS", new Font(Font.HELVETICA, 8, Font.BOLD, HEADER_RED)));
        billToCell.addElement(new Paragraph(cust != null ? cust.getName() : "Direct Customer", new Font(Font.HELVETICA, 9.5f, Font.BOLD, TEXT_DARK)));
        if (cust != null) {
            if (cust.getAddress() != null) billToCell.addElement(new Paragraph(cust.getAddress(), firmSubFont));
            if (cust.getPhone() != null) billToCell.addElement(new Paragraph("Phone: " + cust.getPhone(), firmSubFont));
            if (cust.getGstin() != null) billToCell.addElement(new Paragraph("GSTIN: " + cust.getGstin(), firmSubFont));
        }
        partyTable.addCell(billToCell);

        PdfPCell reasonCell = new PdfPCell();
        reasonCell.setBorder(Rectangle.BOX);
        reasonCell.setBorderColor(LIGHT_BORDER);
        reasonCell.setPadding(8);
        reasonCell.addElement(new Paragraph("RETURN DETAILS", new Font(Font.HELVETICA, 8, Font.BOLD, HEADER_RED)));
        reasonCell.addElement(new Paragraph("Reason: " + (salesReturn.getReason() != null ? salesReturn.getReason() : "Customer Return"), metaFont));
        reasonCell.addElement(new Paragraph("Refund Mode: " + (salesReturn.getRefundMode() != null ? salesReturn.getRefundMode() : "CASH"), metaFont));
        if (salesReturn.getNotes() != null && !salesReturn.getNotes().isBlank()) {
            reasonCell.addElement(new Paragraph("Notes: " + salesReturn.getNotes(), firmSubFont));
        }
        partyTable.addCell(reasonCell);

        doc.add(partyTable);
        doc.add(new Paragraph(" "));

        // 3. Returned Items Table
        PdfPTable itemsTable = new PdfPTable(7);
        itemsTable.setWidthPercentage(100);
        itemsTable.setWidths(new float[]{6, 38, 12, 10, 14, 10, 14});
        itemsTable.setHeaderRows(1);
        itemsTable.setSplitRows(true);
        itemsTable.setSplitLate(false);

        String[] headers = {"#", "Item Description", "HSN/SAC", "Qty", "Rate (₹)", "GST %", "Refund (₹)"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, new Font(Font.HELVETICA, 8.5f, Font.BOLD, Color.WHITE)));
            cell.setBackgroundColor(HEADER_RED);
            cell.setPadding(5);
            cell.setHorizontalAlignment(h.equals("Item Description") ? Element.ALIGN_LEFT : Element.ALIGN_CENTER);
            itemsTable.addCell(cell);
        }

        List<SalesReturnItem> items = salesReturn.getItems();
        Font rowFont = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, TEXT_DARK);
        for (int i = 0; i < items.size(); i++) {
            SalesReturnItem item = items.get(i);
            Color bg = (i % 2 == 0) ? Color.WHITE : new Color(249, 250, 251);

            itemsTable.addCell(createCell(String.valueOf(i + 1), rowFont, Element.ALIGN_CENTER, bg));
            itemsTable.addCell(createCell(item.getProductName(), rowFont, Element.ALIGN_LEFT, bg));
            itemsTable.addCell(createCell(item.getHsnCode() != null ? item.getHsnCode() : "—", rowFont, Element.ALIGN_CENTER, bg));
            itemsTable.addCell(createCell(String.valueOf(item.getReturnQty()) + (item.getUnit() != null ? " " + item.getUnit() : ""), rowFont, Element.ALIGN_CENTER, bg));
            itemsTable.addCell(createCell(item.getUnitPrice() != null ? String.format("%.2f", item.getUnitPrice()) : "0.00", rowFont, Element.ALIGN_RIGHT, bg));
            itemsTable.addCell(createCell(item.getGstPercent() != null ? item.getGstPercent() + "%" : "0%", rowFont, Element.ALIGN_CENTER, bg));
            itemsTable.addCell(createCell(item.getRefundTotal() != null ? String.format("%.2f", item.getRefundTotal()) : "0.00", rowFont, Element.ALIGN_RIGHT, bg));
        }

        doc.add(itemsTable);

        // 4. Summary Table
        PdfPTable summaryTable = new PdfPTable(2);
        summaryTable.setWidthPercentage(48);
        summaryTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
        summaryTable.setKeepTogether(true);
        summaryTable.setSpacingBefore(10);

        Font sumLabel = new Font(Font.HELVETICA, 9, Font.NORMAL, TEXT_DARK);
        Font sumDeduct = new Font(Font.HELVETICA, 9, Font.NORMAL, HEADER_RED);
        Font sumBold = new Font(Font.HELVETICA, 9.5f, Font.BOLD, HEADER_RED);

        if (salesReturn.getExcludedTaxAmount() != null && salesReturn.getExcludedTaxAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal grossValue = salesReturn.getSubtotal().add(salesReturn.getExcludedTaxAmount());
            addSummaryRow(summaryTable, "Gross Return Value (Incl. GST):", grossValue, sumLabel, false);
            addSummaryRow(summaryTable, "Less: GST / Tax Deduction:", salesReturn.getExcludedTaxAmount(), sumDeduct, true);
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

        // 5. Signoff
        PdfPTable signTable = new PdfPTable(2);
        signTable.setWidthPercentage(100);
        signTable.setKeepTogether(true);
        signTable.setSpacingBefore(20);

        PdfPCell signLeft = new PdfPCell(new Phrase("Customer Signature / Acknowledgement\n\n\n_______________________", metaFont));
        signLeft.setBorder(Rectangle.NO_BORDER);

        PdfPCell signRight = new PdfPCell(new Phrase("For " + (firm != null && firm.getFirmName() != null ? firm.getFirmName() : "Enterprise") + "\n\n\nAuthorized Signatory", metaFont));
        signRight.setBorder(Rectangle.NO_BORDER);
        signRight.setHorizontalAlignment(Element.ALIGN_RIGHT);

        signTable.addCell(signLeft);
        signTable.addCell(signRight);
        doc.add(signTable);

        doc.close();
        return baos.toByteArray();
    }

    private PdfPCell createCell(String text, Font font, int align, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setHorizontalAlignment(align);
        cell.setBackgroundColor(bg);
        cell.setBorderColor(LIGHT_BORDER);
        cell.setPadding(5);
        return cell;
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

    private static class PageNumberHelper extends PdfPageEventHelper {
        private PdfTemplate totalPagesTemplate;
        private BaseFont baseFont;

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            try {
                baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
                totalPagesTemplate = writer.getDirectContent().createTemplate(50, 50);
            } catch (Exception e) {
                // fallback
            }
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            String pageText = "Page " + writer.getPageNumber() + " of ";
            float textSize = 8f;
            float textWidth = baseFont.getWidthPoint(pageText, textSize);

            float x = (document.right() + document.left()) / 2f - (textWidth / 2f);
            float y = document.bottom() - 14;

            cb.beginText();
            cb.setFontAndSize(baseFont, textSize);
            cb.setColorFill(new Color(156, 163, 175));
            cb.setTextMatrix(x, y);
            cb.showText(pageText);
            cb.endText();

            cb.addTemplate(totalPagesTemplate, x + textWidth, y);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            if (totalPagesTemplate != null && baseFont != null) {
                totalPagesTemplate.beginText();
                totalPagesTemplate.setFontAndSize(baseFont, 8f);
                totalPagesTemplate.setColorFill(new Color(156, 163, 175));
                totalPagesTemplate.showText(String.valueOf(writer.getPageNumber() - 1));
                totalPagesTemplate.endText();
            }
        }
    }
}
