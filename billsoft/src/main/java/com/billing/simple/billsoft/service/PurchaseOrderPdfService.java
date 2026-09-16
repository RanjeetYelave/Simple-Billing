package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.Party;
import com.billing.simple.billsoft.entities.PurchaseOrder;
import com.billing.simple.billsoft.entities.PurchaseOrderItem;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

@Service
public class PurchaseOrderPdfService {

    private final FirmDetailsService firmService;
    private static final DecimalFormat CURRENCY_FORMAT = new DecimalFormat("#,##,##0.00");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private static final Color THEME_PRIMARY = new Color(79, 70, 229); // #4f46e5
    private static final Color HEADER_BG = new Color(243, 244, 246);   // #f3f4f6
    private static final Color BORDER_COLOR = new Color(209, 213, 219); // #d1d5db
    private static final Color TEXT_DARK = new Color(17, 24, 39);      // #111827
    private static final Color TEXT_MUTED = new Color(107, 114, 128);  // #6b7280

    public PurchaseOrderPdfService(FirmDetailsService firmService) {
        this.firmService = firmService;
    }

    public byte[] generatePdf(PurchaseOrder po) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 28, 28, 28, 38);
        PdfWriter writer = PdfWriter.getInstance(doc, baos);

        // Multi-page page number event helper
        PageNumberHelper pageHelper = new PageNumberHelper(8f);
        writer.setPageEvent(pageHelper);

        doc.open();

        Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD, THEME_PRIMARY);
        Font firmNameFont = new Font(Font.HELVETICA, 14, Font.BOLD, TEXT_DARK);
        Font boldFont = new Font(Font.HELVETICA, 9, Font.BOLD, TEXT_DARK);
        Font normalFont = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, TEXT_DARK);
        Font smallMuted = new Font(Font.HELVETICA, 8, Font.NORMAL, TEXT_MUTED);
        Font tableHeaderFont = new Font(Font.HELVETICA, 8.5f, Font.BOLD, Color.WHITE);
        Font totalBoldFont = new Font(Font.HELVETICA, 10, Font.BOLD, TEXT_DARK);

        FirmDetails firm = null;
        try {
            if (po != null && po.getFirmId() != null) {
                firm = firmService.get(po.getFirmId());
            } else {
                firm = firmService.getFirst();
            }
        } catch (Exception ignored) {}

        // 1. Header Block (Firm Info & Document Title)
        PdfPTable headerTable = new PdfPTable(new float[]{60, 40});
        headerTable.setWidthPercentage(100);
        headerTable.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        PdfPCell firmCell = new PdfPCell();
        firmCell.setBorder(Rectangle.NO_BORDER);
        firmCell.setPadding(12);

        // Optional Firm Logo
        if (firm != null && firm.getLogoBase64() != null && !firm.getLogoBase64().trim().isEmpty()) {
            try {
                String cleanBase64 = firm.getLogoBase64();
                if (cleanBase64.contains(",")) cleanBase64 = cleanBase64.split(",")[1];
                byte[] imgBytes = Base64.getDecoder().decode(cleanBase64);
                Image logo = Image.getInstance(imgBytes);
                logo.scaleToFit(90, 45);
                firmCell.addElement(logo);
            } catch (Exception ignored) {}
        }

        String fName = (firm != null && firm.getFirmName() != null && !firm.getFirmName().trim().isEmpty())
                ? firm.getFirmName()
                : "Official Purchase Order";
        Paragraph pFirm = new Paragraph(fName, firmNameFont);
        firmCell.addElement(pFirm);

        if (firm != null) {
            if (firm.getAddressLine1() != null) firmCell.addElement(new Paragraph(firm.getAddressLine1(), normalFont));
            String cityState = "";
            if (firm.getCity() != null) cityState += firm.getCity();
            if (firm.getState() != null) cityState += (cityState.isEmpty() ? "" : ", ") + firm.getState();
            if (firm.getPincode() != null) cityState += " - " + firm.getPincode();
            if (!cityState.isEmpty()) firmCell.addElement(new Paragraph(cityState, normalFont));
            if (firm.getGstin() != null && !firm.getGstin().isEmpty()) firmCell.addElement(new Paragraph("GSTIN: " + firm.getGstin(), boldFont));
            if (firm.getPhone() != null && !firm.getPhone().isEmpty()) firmCell.addElement(new Paragraph("Phone: " + firm.getPhone(), normalFont));
            if (firm.getEmail() != null && !firm.getEmail().isEmpty()) firmCell.addElement(new Paragraph("Email: " + firm.getEmail(), normalFont));
        }
        headerTable.addCell(firmCell);

        PdfPCell docMetaCell = new PdfPCell();
        docMetaCell.setBorder(Rectangle.NO_BORDER);
        docMetaCell.setPadding(12);
        docMetaCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

        Paragraph poTitle = new Paragraph("PURCHASE ORDER", titleFont);
        poTitle.setAlignment(Element.ALIGN_RIGHT);
        docMetaCell.addElement(poTitle);

        String poNum = po != null && po.getPoNumber() != null ? po.getPoNumber() : "PO-XXXX";
        Paragraph pNum = new Paragraph("PO #: " + poNum, boldFont);
        pNum.setAlignment(Element.ALIGN_RIGHT);
        docMetaCell.addElement(pNum);

        String poDateStr = (po != null && po.getPoDate() != null) ? po.getPoDate().format(DATE_FORMATTER) : LocalDate.now().format(DATE_FORMATTER);
        Paragraph pDate = new Paragraph("PO Date: " + poDateStr, normalFont);
        pDate.setAlignment(Element.ALIGN_RIGHT);
        docMetaCell.addElement(pDate);

        if (po != null && po.getExpectedDeliveryDate() != null) {
            Paragraph pExp = new Paragraph("Expected Delivery: " + po.getExpectedDeliveryDate().format(DATE_FORMATTER), normalFont);
            pExp.setAlignment(Element.ALIGN_RIGHT);
            docMetaCell.addElement(pExp);
        }

        if (po != null && po.getReferenceNumber() != null && !po.getReferenceNumber().isEmpty()) {
            Paragraph pRef = new Paragraph("Ref / Quotation: " + po.getReferenceNumber(), normalFont);
            pRef.setAlignment(Element.ALIGN_RIGHT);
            docMetaCell.addElement(pRef);
        }

        if (po != null && po.getStatus() != null) {
            String statusColor = "#4f46e5";
            if ("PAID".equals(po.getStatus())) statusColor = "#16a34a";
            else if ("CANCELLED".equals(po.getStatus())) statusColor = "#dc2626";
            else if ("RECEIVED".equals(po.getStatus())) statusColor = "#0284c7";

            Paragraph pStatus = new Paragraph("Status: " + po.getStatus().name().replace("_", " "), boldFont);
            pStatus.setAlignment(Element.ALIGN_RIGHT);
            docMetaCell.addElement(pStatus);
        }
        headerTable.addCell(docMetaCell);

        PdfPTable headerWrapTable = new PdfPTable(1);
        headerWrapTable.setWidthPercentage(100);
        PdfPCell headerWrapCell = new PdfPCell(headerTable);
        headerWrapCell.setBorder(Rectangle.BOX);
        headerWrapCell.setBorderColor(BORDER_COLOR);
        headerWrapCell.setBorderWidth(1f);
        headerWrapCell.setPadding(0);
        headerWrapTable.addCell(headerWrapCell);
        doc.add(headerWrapTable);

        // 2. Vendor / Party & Shipping Address Block
        PdfPTable partyShipTable = new PdfPTable(new float[]{50, 50});
        partyShipTable.setWidthPercentage(100);
        partyShipTable.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        Party party = po != null ? po.getParty() : null;
        PdfPCell partyCell = new PdfPCell();
        partyCell.setBorder(Rectangle.NO_BORDER);
        partyCell.setPadding(10);
        partyCell.addElement(new Paragraph("VENDOR / SUPPLIER:", boldFont));

        String pName = party != null ? party.getName() : "Vendor";
        partyCell.addElement(new Paragraph(pName, boldFont));
        if (party != null) {
            if (party.getAddress() != null && !party.getAddress().isEmpty()) {
                partyCell.addElement(new Paragraph(party.getAddress(), normalFont));
            }
            if (party.getGstin() != null && !party.getGstin().isEmpty()) {
                partyCell.addElement(new Paragraph("GSTIN: " + party.getGstin(), boldFont));
            }
            if (party.getPhone() != null && !party.getPhone().isEmpty()) {
                partyCell.addElement(new Paragraph("Phone: " + party.getPhone(), normalFont));
            }
            if (party.getEmail() != null && !party.getEmail().isEmpty()) {
                partyCell.addElement(new Paragraph("Email: " + party.getEmail(), normalFont));
            }
        }
        partyShipTable.addCell(partyCell);

        PdfPCell shipCell = new PdfPCell();
        shipCell.setBorder(Rectangle.LEFT);
        shipCell.setBorderColor(BORDER_COLOR);
        shipCell.setBorderWidth(1f);
        shipCell.setPadding(10);
        shipCell.addElement(new Paragraph("DELIVER TO / SHIPPING ADDRESS:", boldFont));

        String shipAddr = po != null && po.getShippingAddress() != null && !po.getShippingAddress().isEmpty()
                ? po.getShippingAddress()
                : (firm != null && firm.getAddressLine1() != null ? (firm.getAddressLine1() + (firm.getCity() != null ? ", " + firm.getCity() : "")) : "Company Premises");
        shipCell.addElement(new Paragraph(shipAddr, normalFont));
        partyShipTable.addCell(shipCell);

        PdfPTable partyShipWrapTable = new PdfPTable(1);
        partyShipWrapTable.setWidthPercentage(100);
        PdfPCell psCell = new PdfPCell(partyShipTable);
        psCell.setBorder(Rectangle.LEFT | Rectangle.RIGHT | Rectangle.BOTTOM);
        psCell.setBorderColor(BORDER_COLOR);
        psCell.setBorderWidth(1f);
        psCell.setPadding(0);
        partyShipWrapTable.addCell(psCell);
        doc.add(partyShipWrapTable);

        // 3. Line Items Table (Multi-Page Split & Repeating Headers)
        boolean hidePrices = Boolean.TRUE.equals(po != null ? po.getHidePricesOnPo() : false);
        PdfPTable itemTable = hidePrices
                ? new PdfPTable(new float[]{6, 52, 16, 12, 14})
                : new PdfPTable(new float[]{5, 38, 12, 8, 8, 13, 16});
        itemTable.setWidthPercentage(100);
        itemTable.setHeaderRows(1);
        itemTable.setSplitLate(false);
        itemTable.setSplitRows(true);
        itemTable.getDefaultCell().setBorder(Rectangle.BOX);
        itemTable.getDefaultCell().setBorderColor(BORDER_COLOR);

        // Headers
        String[] headers = hidePrices
                ? new String[]{"#", "Item & Description", "HSN", "Qty", "Unit"}
                : new String[]{"#", "Item & Description", "HSN", "Qty", "Unit", "Rate (₹)", "Total (₹)"};
        int[] aligns = hidePrices
                ? new int[]{Element.ALIGN_CENTER, Element.ALIGN_LEFT, Element.ALIGN_CENTER, Element.ALIGN_RIGHT, Element.ALIGN_CENTER}
                : new int[]{Element.ALIGN_CENTER, Element.ALIGN_LEFT, Element.ALIGN_CENTER, Element.ALIGN_RIGHT, Element.ALIGN_CENTER, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT};

        for (int i = 0; i < headers.length; i++) {
            PdfPCell th = new PdfPCell(new Phrase(headers[i], tableHeaderFont));
            th.setBackgroundColor(THEME_PRIMARY);
            th.setHorizontalAlignment(aligns[i]);
            th.setPaddingTop(6);
            th.setPaddingBottom(6);
            th.setBorder(Rectangle.BOX);
            th.setBorderColor(BORDER_COLOR);
            itemTable.addCell(th);
        }

        // Data Rows
        List<PurchaseOrderItem> items = po != null ? po.getItems() : null;
        if (items == null || items.isEmpty()) {
            PdfPCell emptyC = new PdfPCell(new Phrase("No line items in this purchase order.", normalFont));
            emptyC.setColspan(headers.length);
            emptyC.setPadding(14);
            emptyC.setHorizontalAlignment(Element.ALIGN_CENTER);
            emptyC.setBorderColor(BORDER_COLOR);
            itemTable.addCell(emptyC);
        } else {
            int rowIdx = 1;
            for (PurchaseOrderItem it : items) {
                Color rowBg = (rowIdx % 2 == 0) ? HEADER_BG : Color.WHITE;

                PdfPCell cNum = new PdfPCell(new Phrase(String.valueOf(rowIdx++), normalFont));
                cNum.setHorizontalAlignment(Element.ALIGN_CENTER);
                cNum.setBackgroundColor(rowBg);
                cNum.setPadding(6);
                cNum.setBorderColor(BORDER_COLOR);
                itemTable.addCell(cNum);

                PdfPCell cName = new PdfPCell();
                cName.setBackgroundColor(rowBg);
                cName.setPadding(6);
                cName.setBorderColor(BORDER_COLOR);
                String pNameStr = it.getProductName() != null ? it.getProductName() : "Item";
                cName.addElement(new Paragraph(pNameStr, boldFont));
                if (it.getDescription() != null && !it.getDescription().isEmpty()) {
                    cName.addElement(new Paragraph(it.getDescription(), smallMuted));
                }
                itemTable.addCell(cName);

                PdfPCell cHsn = new PdfPCell(new Phrase(it.getHsnCode() != null ? it.getHsnCode() : "-", normalFont));
                cHsn.setHorizontalAlignment(Element.ALIGN_CENTER);
                cHsn.setBackgroundColor(rowBg);
                cHsn.setPadding(6);
                cHsn.setBorderColor(BORDER_COLOR);
                itemTable.addCell(cHsn);

                PdfPCell cQty = new PdfPCell(new Phrase(it.getQuantity() != null ? it.getQuantity().stripTrailingZeros().toPlainString() : "1", normalFont));
                cQty.setHorizontalAlignment(Element.ALIGN_RIGHT);
                cQty.setBackgroundColor(rowBg);
                cQty.setPadding(6);
                cQty.setBorderColor(BORDER_COLOR);
                itemTable.addCell(cQty);

                PdfPCell cUnit = new PdfPCell(new Phrase(it.getUnit() != null ? it.getUnit() : "PCS", normalFont));
                cUnit.setHorizontalAlignment(Element.ALIGN_CENTER);
                cUnit.setBackgroundColor(rowBg);
                cUnit.setPadding(6);
                cUnit.setBorderColor(BORDER_COLOR);
                itemTable.addCell(cUnit);

                if (!hidePrices) {
                    PdfPCell cRate = new PdfPCell(new Phrase(CURRENCY_FORMAT.format(it.getUnitPrice() != null ? it.getUnitPrice() : BigDecimal.ZERO), normalFont));
                    cRate.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    cRate.setBackgroundColor(rowBg);
                    cRate.setPadding(6);
                    cRate.setBorderColor(BORDER_COLOR);
                    itemTable.addCell(cRate);

                    PdfPCell cTot = new PdfPCell(new Phrase(CURRENCY_FORMAT.format(it.getTotalAmount() != null ? it.getTotalAmount() : BigDecimal.ZERO), boldFont));
                    cTot.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    cTot.setBackgroundColor(rowBg);
                    cTot.setPadding(6);
                    cTot.setBorderColor(BORDER_COLOR);
                    itemTable.addCell(cTot);
                }
            }
        }

        doc.add(itemTable);

        // 4. Totals Summary & Notes Block
        PdfPTable bottomTable = hidePrices ? new PdfPTable(1) : new PdfPTable(new float[]{55, 45});
        bottomTable.setWidthPercentage(100);
        bottomTable.setKeepTogether(true);
        bottomTable.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        PdfPCell notesCell = new PdfPCell();
        notesCell.setBorder(Rectangle.NO_BORDER);
        notesCell.setPadding(10);
        if (hidePrices) {
            Paragraph specNotice = new Paragraph("Official Supply Order (Quantities & Specifications)", boldFont);
            specNotice.getFont().setColor(THEME_PRIMARY);
            notesCell.addElement(specNotice);
            notesCell.addElement(new Paragraph("\n"));
        }
        if (po != null && po.getNotes() != null && !po.getNotes().isEmpty()) {
            notesCell.addElement(new Paragraph("Notes / Instructions:", boldFont));
            notesCell.addElement(new Paragraph(po.getNotes(), smallMuted));
        }
        if (po != null && po.getTermsAndConditions() != null && !po.getTermsAndConditions().isEmpty()) {
            notesCell.addElement(new Paragraph("Terms & Conditions:", boldFont));
            notesCell.addElement(new Paragraph(po.getTermsAndConditions(), smallMuted));
        }
        bottomTable.addCell(notesCell);

        if (!hidePrices) {
            PdfPCell summaryCell = new PdfPCell();
            summaryCell.setBorder(Rectangle.LEFT);
            summaryCell.setBorderColor(BORDER_COLOR);
            summaryCell.setBorderWidth(1f);
            summaryCell.setPadding(8);

            PdfPTable sumGrid = new PdfPTable(new float[]{60, 40});
            sumGrid.setWidthPercentage(100);
            sumGrid.getDefaultCell().setBorder(Rectangle.NO_BORDER);

            addSummaryRow(sumGrid, "Subtotal (Taxable):", "₹ " + CURRENCY_FORMAT.format(po != null && po.getSubtotalWithoutTax() != null ? po.getSubtotalWithoutTax() : BigDecimal.ZERO), normalFont);
            if (po != null && po.getTotalDiscountAmount() != null && po.getTotalDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
                addSummaryRow(sumGrid, "Total Discount:", "- ₹ " + CURRENCY_FORMAT.format(po.getTotalDiscountAmount()), normalFont);
            }
            if (po != null && po.getTotalGstAmount() != null && po.getTotalGstAmount().compareTo(BigDecimal.ZERO) > 0) {
                addSummaryRow(sumGrid, "GST Amount:", "₹ " + CURRENCY_FORMAT.format(po.getTotalGstAmount()), normalFont);
            }
            if (po != null && po.getRoundOff() != null && po.getRoundOff().compareTo(BigDecimal.ZERO) != 0) {
                addSummaryRow(sumGrid, "Round Off:", (po.getRoundOff().compareTo(BigDecimal.ZERO) > 0 ? "+" : "") + CURRENCY_FORMAT.format(po.getRoundOff()), smallMuted);
            }
            addSummaryRow(sumGrid, "Grand Total:", "₹ " + CURRENCY_FORMAT.format(po != null && po.getTotalAmount() != null ? po.getTotalAmount() : BigDecimal.ZERO), totalBoldFont);

            // Paid & Balance
            if (po != null && po.getPaidAmount() != null && po.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
                addSummaryRow(sumGrid, "Amount Paid:", "₹ " + CURRENCY_FORMAT.format(po.getPaidAmount()), normalFont);
                BigDecimal balance = po.getTotalAmount().subtract(po.getPaidAmount()).max(BigDecimal.ZERO);
                addSummaryRow(sumGrid, "Balance Due:", "₹ " + CURRENCY_FORMAT.format(balance), boldFont);
            }

            summaryCell.addElement(sumGrid);
            bottomTable.addCell(summaryCell);
        }

        PdfPTable bottomWrapTable = new PdfPTable(1);
        bottomWrapTable.setWidthPercentage(100);
        bottomWrapTable.setKeepTogether(true);
        PdfPCell bwCell = new PdfPCell(bottomTable);
        bwCell.setBorder(Rectangle.LEFT | Rectangle.RIGHT | Rectangle.BOTTOM);
        bwCell.setBorderColor(BORDER_COLOR);
        bwCell.setBorderWidth(1f);
        bwCell.setPadding(0);
        bottomWrapTable.addCell(bwCell);
        doc.add(bottomWrapTable);

        // 5. Signature Block
        PdfPTable signTable = new PdfPTable(new float[]{50, 50});
        signTable.setWidthPercentage(100);
        signTable.setKeepTogether(true);
        signTable.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        PdfPCell signLeft = new PdfPCell();
        signLeft.setBorder(Rectangle.NO_BORDER);
        signLeft.setPadding(12);
        signLeft.addElement(new Paragraph("Vendor Acknowledgment", boldFont));
        signLeft.addElement(new Paragraph("Signature & Stamp:", smallMuted));
        signLeft.addElement(new Paragraph("\n\n"));
        signTable.addCell(signLeft);

        PdfPCell signRight = new PdfPCell();
        signRight.setBorder(Rectangle.NO_BORDER);
        signRight.setPadding(12);
        signRight.setHorizontalAlignment(Element.ALIGN_RIGHT);
        signRight.addElement(new Paragraph("For " + fName, boldFont));
        signRight.addElement(new Paragraph("\n\n"));
        Paragraph pSig = new Paragraph("Authorized Signatory", boldFont);
        pSig.setAlignment(Element.ALIGN_RIGHT);
        signRight.addElement(pSig);
        signTable.addCell(signRight);

        PdfPTable signWrapTable = new PdfPTable(1);
        signWrapTable.setWidthPercentage(100);
        signWrapTable.setKeepTogether(true);
        PdfPCell sCell = new PdfPCell(signTable);
        sCell.setBorder(Rectangle.LEFT | Rectangle.RIGHT | Rectangle.BOTTOM);
        sCell.setBorderColor(BORDER_COLOR);
        sCell.setBorderWidth(1f);
        sCell.setPadding(0);
        signWrapTable.addCell(sCell);
        doc.add(signWrapTable);

        doc.close();
        return baos.toByteArray();
    }

    /**
     * Generates a single combined/merged multi-page PDF containing all provided purchase orders.
     */
    public byte[] generateMergedPdf(List<PurchaseOrder> poList) throws Exception {
        if (poList == null || poList.isEmpty()) {
            return new byte[0];
        }
        if (poList.size() == 1) {
            return generatePdf(poList.get(0));
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document mergedDoc = new Document();
        com.lowagie.text.pdf.PdfCopy copy = new com.lowagie.text.pdf.PdfCopy(mergedDoc, baos);
        mergedDoc.open();

        for (PurchaseOrder po : poList) {
            byte[] singlePdf = generatePdf(po);
            com.lowagie.text.pdf.PdfReader reader = new com.lowagie.text.pdf.PdfReader(singlePdf);
            int pages = reader.getNumberOfPages();
            for (int i = 1; i <= pages; i++) {
                copy.addPage(copy.getImportedPage(reader, i));
            }
            copy.freeReader(reader);
            reader.close();
        }

        mergedDoc.close();
        return baos.toByteArray();
    }

    /**
     * Generates a ZIP archive containing individual PDFs for each purchase order.
     */
    public byte[] generateZipBundle(List<PurchaseOrder> poList) throws Exception {
        if (poList == null || poList.isEmpty()) {
            return new byte[0];
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            for (PurchaseOrder po : poList) {
                byte[] pdfBytes = generatePdf(po);
                String safePoNum = po.getPoNumber() != null ? po.getPoNumber() : ("PO-" + po.getId());
                String safeParty = po.getPartyName() != null ? po.getPartyName().replaceAll("[^a-zA-Z0-9.-]", "_") : "Vendor";
                String filename = safePoNum + "_" + safeParty + ".pdf";

                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(filename);
                zos.putNextEntry(entry);
                zos.write(pdfBytes);
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    /**
     * Multi-page page number event helper.
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

    private void addSummaryRow(PdfPTable table, String label, String value, Font font) {
        PdfPCell lCell = new PdfPCell(new Phrase(label, font));
        lCell.setBorder(Rectangle.NO_BORDER);
        lCell.setPadding(3);
        table.addCell(lCell);

        PdfPCell vCell = new PdfPCell(new Phrase(value, font));
        vCell.setBorder(Rectangle.NO_BORDER);
        vCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        vCell.setPadding(3);
        table.addCell(vCell);
    }
}
