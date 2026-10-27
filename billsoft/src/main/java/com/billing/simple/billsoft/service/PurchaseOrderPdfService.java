package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dtos.InvoicePrintOptions;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.Party;
import com.billing.simple.billsoft.entities.PurchaseOrder;
import com.billing.simple.billsoft.entities.PurchaseOrderItem;
import com.billing.simple.billsoft.enums.InvoiceColorPalette;
import com.billing.simple.billsoft.enums.InvoiceTheme;
import com.billing.simple.billsoft.enums.PrintFormat;
import com.billing.simple.billsoft.service.pdf.core.DocumentThemeHelper;
import com.billing.simple.billsoft.service.pdf.core.UniversalPageNumberHelper;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service to generate high quality, professional vector-based Purchase Order PDFs.
 * Supports A4 and A5 formats across Classic, Modern, Professional, and Minimal themes.
 */
@Service
public class PurchaseOrderPdfService {

    private final FirmDetailsService firmService;
    private static final DecimalFormat CURRENCY_FORMAT = new DecimalFormat("#,##,##0.00");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public PurchaseOrderPdfService(FirmDetailsService firmService) {
        this.firmService = firmService;
    }

    public byte[] generatePdf(PurchaseOrder po) throws Exception {
        return generatePdf(po, null, null, null, null);
    }

    public byte[] generatePdf(PurchaseOrder po, String size, String format, String theme, String color) throws Exception {
        FirmDetails firm = null;
        try {
            if (po != null && po.getFirmId() != null) {
                firm = firmService.get(po.getFirmId());
            } else {
                firm = firmService.getFirst();
            }
        } catch (Exception ignored) {}

        InvoicePrintOptions options = InvoicePrintOptions.resolve(size, format, theme, color, firm);
        InvoiceTheme themeEnum = options.getTheme();
        InvoiceColorPalette palette = options.getColorPalette();
        Color primaryColor = palette.getPrimaryColor();
        Color lightTint = palette.getLightTintColor();

        Rectangle pageSize = (options.getFormat() == PrintFormat.A5) ? PageSize.A5 : PageSize.A4;
        float scale = (options.getFormat() == PrintFormat.A5) ? 0.85f : 1.0f;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(pageSize, 28, 28, 28, 38);
        PdfWriter writer = PdfWriter.getInstance(doc, baos);

        writer.setPageEvent(new UniversalPageNumberHelper(8f * scale));
        doc.open();

        Font titleFont = new Font(Font.HELVETICA, 16 * scale, Font.BOLD, primaryColor);
        Font firmNameFont = new Font(Font.HELVETICA, 14 * scale, Font.BOLD, DocumentThemeHelper.TEXT_DARK);
        Font boldFont = new Font(Font.HELVETICA, 9 * scale, Font.BOLD, DocumentThemeHelper.TEXT_DARK);
        Font normalFont = new Font(Font.HELVETICA, 8.5f * scale, Font.NORMAL, DocumentThemeHelper.TEXT_DARK);
        Font smallMuted = new Font(Font.HELVETICA, 8 * scale, Font.NORMAL, DocumentThemeHelper.TEXT_MUTED);
        Font tableHeaderFont = new Font(Font.HELVETICA, 8.5f * scale, Font.BOLD, (themeEnum == InvoiceTheme.MINIMAL) ? primaryColor : Color.WHITE);
        Font totalBoldFont = new Font(Font.HELVETICA, 10 * scale, Font.BOLD, primaryColor);

        // 0. Top Professional Banner if theme is PROFESSIONAL
        if (themeEnum == InvoiceTheme.PROFESSIONAL) {
            doc.add(DocumentThemeHelper.createProfessionalBanner("PURCHASE ORDER", primaryColor, scale));
        }

        // 1. Header Block (Firm Info & Document Title)
        PdfPTable headerTable = new PdfPTable(new float[]{60, 40});
        headerTable.setWidthPercentage(100);
        headerTable.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        PdfPCell firmCell = new PdfPCell();
        firmCell.setBorder(Rectangle.NO_BORDER);
        firmCell.setPadding(10 * scale);

        // Optional Firm Logo
        if (firm != null && firm.getLogoBase64() != null && !firm.getLogoBase64().trim().isEmpty()) {
            Image logo = DocumentThemeHelper.loadLogoImage(firm.getLogoBase64(), 90 * scale, 45 * scale);
            if (logo != null) firmCell.addElement(logo);
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
        docMetaCell.setPadding(10 * scale);
        docMetaCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

        if (themeEnum != InvoiceTheme.PROFESSIONAL) {
            Paragraph poTitle = new Paragraph("PURCHASE ORDER", titleFont);
            poTitle.setAlignment(Element.ALIGN_RIGHT);
            docMetaCell.addElement(poTitle);
        }

        String poNum = po != null && po.getPoNumber() != null ? po.getPoNumber() : "PO-XXXX";
        Paragraph pNum = new Paragraph("PO #: " + poNum, boldFont);
        pNum.setAlignment(Element.ALIGN_RIGHT);
        docMetaCell.addElement(pNum);

        String poDt = po != null && po.getPoDate() != null ? po.getPoDate().format(DATE_FORMATTER) : "-";
        Paragraph pDate = new Paragraph("PO Date: " + poDt, normalFont);
        pDate.setAlignment(Element.ALIGN_RIGHT);
        docMetaCell.addElement(pDate);

        if (po != null && po.getExpectedDeliveryDate() != null) {
            Paragraph pExp = new Paragraph("Expected Delivery: " + po.getExpectedDeliveryDate().format(DATE_FORMATTER), normalFont);
            pExp.setAlignment(Element.ALIGN_RIGHT);
            docMetaCell.addElement(pExp);
        }

        String st = po != null && po.getStatus() != null ? po.getStatus().name() : "ISSUED";
        Paragraph pStatus = new Paragraph("Status: " + st, boldFont);
        pStatus.setAlignment(Element.ALIGN_RIGHT);
        docMetaCell.addElement(pStatus);

        headerTable.addCell(docMetaCell);

        PdfPTable headerWrapTable = new PdfPTable(1);
        headerWrapTable.setWidthPercentage(100);
        PdfPCell hwCell = new PdfPCell(headerTable);
        if (themeEnum == InvoiceTheme.MINIMAL) {
            hwCell.setBorder(Rectangle.BOTTOM);
            hwCell.setBorderColor(primaryColor);
            hwCell.setBorderWidth(1.2f);
        } else if (themeEnum == InvoiceTheme.MODERN) {
            hwCell.setBorder(Rectangle.BOX);
            hwCell.setBorderColor(DocumentThemeHelper.BORDER_LIGHT);
            hwCell.setBackgroundColor(lightTint);
        } else {
            hwCell.setBorder(Rectangle.BOX);
            hwCell.setBorderColor(DocumentThemeHelper.BORDER_COLOR);
            hwCell.setBorderWidth(1f);
        }
        hwCell.setPadding(0);
        headerWrapTable.addCell(hwCell);
        doc.add(headerWrapTable);

        // 2. Vendor and Shipping Details Table
        PdfPTable partyTable = new PdfPTable(new float[]{50, 50});
        partyTable.setWidthPercentage(100);
        partyTable.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        PdfPCell vendorCell = new PdfPCell();
        vendorCell.setBorder(Rectangle.NO_BORDER);
        vendorCell.setPadding(10 * scale);

        Paragraph vHead = new Paragraph("VENDOR / SUPPLIER DETAILS", new Font(Font.HELVETICA, 8.5f * scale, Font.BOLD, primaryColor));
        vendorCell.addElement(vHead);

        Party party = po != null ? po.getParty() : null;
        String partyName = po != null && po.getPartyName() != null && !po.getPartyName().trim().isEmpty()
                ? po.getPartyName()
                : (party != null ? party.getName() : "—");
        vendorCell.addElement(new Paragraph(partyName, boldFont));

        if (party != null) {
            if (party.getAddress() != null && !party.getAddress().isEmpty()) vendorCell.addElement(new Paragraph(party.getAddress(), normalFont));
            String pCityState = "";
            if (party.getCity() != null) pCityState += party.getCity();
            if (party.getState() != null) pCityState += (pCityState.isEmpty() ? "" : ", ") + party.getState();
            if (party.getPincode() != null) pCityState += " - " + party.getPincode();
            if (!pCityState.isEmpty()) vendorCell.addElement(new Paragraph(pCityState, normalFont));
            if (party.getGstin() != null && !party.getGstin().isEmpty()) vendorCell.addElement(new Paragraph("GSTIN: " + party.getGstin(), boldFont));
            if (party.getPhone() != null && !party.getPhone().isEmpty()) vendorCell.addElement(new Paragraph("Phone: " + party.getPhone(), normalFont));
            if (party.getEmail() != null && !party.getEmail().isEmpty()) vendorCell.addElement(new Paragraph("Email: " + party.getEmail(), normalFont));
        }
        partyTable.addCell(vendorCell);

        PdfPCell shipCell = new PdfPCell();
        shipCell.setBorder(Rectangle.NO_BORDER);
        shipCell.setPadding(10 * scale);

        Paragraph sHead = new Paragraph("DELIVERY & SHIPPING ADDRESS", new Font(Font.HELVETICA, 8.5f * scale, Font.BOLD, primaryColor));
        shipCell.addElement(sHead);

        String shipTo = (po != null && po.getShippingAddress() != null && !po.getShippingAddress().trim().isEmpty())
                ? po.getShippingAddress()
                : (firm != null && firm.getAddressLine1() != null ? firm.getAddressLine1() : fName);
        shipCell.addElement(new Paragraph(shipTo, normalFont));

        if (po != null && po.getPaymentTerms() != null && !po.getPaymentTerms().trim().isEmpty()) {
            shipCell.addElement(new Paragraph("Payment Terms: " + po.getPaymentTerms(), boldFont));
        }
        if (po != null && po.getTermsAndConditions() != null && !po.getTermsAndConditions().trim().isEmpty()) {
            shipCell.addElement(new Paragraph("Terms: " + po.getTermsAndConditions(), normalFont));
        }
        partyTable.addCell(shipCell);

        PdfPTable partyWrapTable = new PdfPTable(1);
        partyWrapTable.setWidthPercentage(100);
        PdfPCell pwCell = new PdfPCell(partyTable);
        pwCell.setBorder(Rectangle.LEFT | Rectangle.RIGHT | Rectangle.BOTTOM);
        pwCell.setBorderColor(DocumentThemeHelper.BORDER_COLOR);
        pwCell.setBorderWidth(1f);
        pwCell.setPadding(0);
        partyWrapTable.addCell(pwCell);
        doc.add(partyWrapTable);

        // 3. Line Items Table
        PdfPTable itemsTable = new PdfPTable(new float[]{5, 33, 10, 10, 14, 12, 16});
        itemsTable.setWidthPercentage(100);
        itemsTable.setHeaderRows(1);

        String[] headers = {"#", "Item Description", "HSN", "Qty", "Unit Rate (₹)", "GST %", "Total (₹)"};
        for (String h : headers) {
            int align = h.contains("Description") ? Element.ALIGN_LEFT :
                    (h.contains("Rate") || h.contains("Total")) ? Element.ALIGN_RIGHT : Element.ALIGN_CENTER;
            itemsTable.addCell(DocumentThemeHelper.createHeaderCell(h, themeEnum, primaryColor, tableHeaderFont, align, scale));
        }

        List<PurchaseOrderItem> items = po != null ? po.getItems() : null;
        if (items != null && !items.isEmpty()) {
            int idx = 1;
            for (int i = 0; i < items.size(); i++) {
                PurchaseOrderItem it = items.get(i);
                boolean isEven = (i % 2 == 1);

                itemsTable.addCell(DocumentThemeHelper.createDataCell(new Phrase(String.valueOf(idx++), normalFont), themeEnum, isEven, Element.ALIGN_CENTER, scale));

                Phrase itemPhrase = new Phrase();
                itemPhrase.add(new Chunk(it.getProductName() != null ? it.getProductName() : "Product", boldFont));
                if (it.getDescription() != null && !it.getDescription().trim().isEmpty()) {
                    itemPhrase.add(new Chunk("\n" + it.getDescription().trim(), smallMuted));
                }
                itemsTable.addCell(DocumentThemeHelper.createDataCell(itemPhrase, themeEnum, isEven, Element.ALIGN_LEFT, scale));

                String hsn = it.getHsnCode() != null && !it.getHsnCode().trim().isEmpty() ? it.getHsnCode().trim() : "—";
                itemsTable.addCell(DocumentThemeHelper.createDataCell(new Phrase(hsn, normalFont), themeEnum, isEven, Element.ALIGN_CENTER, scale));

                String qtyStr = it.getQuantity() != null ? it.getQuantity().stripTrailingZeros().toPlainString() : "1";
                if (it.getUnit() != null && !it.getUnit().trim().isEmpty()) qtyStr += " " + it.getUnit().trim();
                itemsTable.addCell(DocumentThemeHelper.createDataCell(new Phrase(qtyStr, normalFont), themeEnum, isEven, Element.ALIGN_CENTER, scale));

                String rateStr = it.getUnitPrice() != null ? CURRENCY_FORMAT.format(it.getUnitPrice()) : "0.00";
                itemsTable.addCell(DocumentThemeHelper.createDataCell(new Phrase(rateStr, normalFont), themeEnum, isEven, Element.ALIGN_RIGHT, scale));

                String gstStr = it.getGstPercent() != null ? (it.getGstPercent().stripTrailingZeros().toPlainString() + "%") : "0%";
                itemsTable.addCell(DocumentThemeHelper.createDataCell(new Phrase(gstStr, normalFont), themeEnum, isEven, Element.ALIGN_CENTER, scale));

                String totStr = it.getTotalAmount() != null ? CURRENCY_FORMAT.format(it.getTotalAmount()) : "0.00";
                itemsTable.addCell(DocumentThemeHelper.createDataCell(new Phrase(totStr, boldFont), themeEnum, isEven, Element.ALIGN_RIGHT, scale));
            }
        }
        doc.add(itemsTable);

        // 4. Bottom Totals and Notes Block
        PdfPTable bottomTable = new PdfPTable(new float[]{55, 45});
        bottomTable.setWidthPercentage(100);
        bottomTable.setKeepTogether(true);
        bottomTable.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        PdfPCell notesCell = new PdfPCell();
        notesCell.setBorder(Rectangle.NO_BORDER);
        notesCell.setPadding(10 * scale);
        notesCell.addElement(new Paragraph("Terms & Instructions:", boldFont));

        String notes = (po != null && po.getNotes() != null && !po.getNotes().trim().isEmpty())
                ? po.getNotes().trim()
                : "1. Please supply the goods strictly as per specifications.\n" +
                  "2. Mention the PO Number on all dispatch documents, invoices & packages.\n" +
                  "3. Subject to jurisdiction of local courts.";
        notesCell.addElement(new Paragraph(notes, smallMuted));
        bottomTable.addCell(notesCell);

        PdfPCell totalsCell = new PdfPCell();
        totalsCell.setBorder(Rectangle.NO_BORDER);
        totalsCell.setPadding(10 * scale);

        PdfPTable totalsTable = new PdfPTable(new float[]{60, 40});
        totalsTable.setWidthPercentage(100);

        BigDecimal subtotal = po != null && po.getSubtotalWithoutTax() != null ? po.getSubtotalWithoutTax() : BigDecimal.ZERO;
        BigDecimal taxAmt = po != null && po.getTotalGstAmount() != null ? po.getTotalGstAmount() : BigDecimal.ZERO;
        BigDecimal totalAmt = po != null && po.getTotalAmount() != null ? po.getTotalAmount() : BigDecimal.ZERO;

        addSummaryRow(totalsTable, "Sub Total (Taxable):", "₹ " + CURRENCY_FORMAT.format(subtotal), normalFont);
        if (taxAmt.compareTo(BigDecimal.ZERO) > 0) {
            addSummaryRow(totalsTable, "Total Tax (GST):", "₹ " + CURRENCY_FORMAT.format(taxAmt), normalFont);
        }
        addSummaryRow(totalsTable, "Grand Total:", "₹ " + CURRENCY_FORMAT.format(totalAmt), totalBoldFont);

        totalsCell.addElement(totalsTable);
        bottomTable.addCell(totalsCell);

        PdfPTable bottomWrapTable = new PdfPTable(1);
        bottomWrapTable.setWidthPercentage(100);
        bottomWrapTable.setKeepTogether(true);
        PdfPCell bwCell = new PdfPCell(bottomTable);
        bwCell.setBorder(Rectangle.LEFT | Rectangle.RIGHT | Rectangle.BOTTOM);
        bwCell.setBorderColor(DocumentThemeHelper.BORDER_COLOR);
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
        signLeft.setPadding(12 * scale);
        signLeft.addElement(new Paragraph("Vendor Acknowledgment", boldFont));
        signLeft.addElement(new Paragraph("Signature & Stamp:\n\n", smallMuted));
        signTable.addCell(signLeft);

        PdfPCell signRight = new PdfPCell();
        signRight.setBorder(Rectangle.NO_BORDER);
        signRight.setPadding(12 * scale);
        signRight.setHorizontalAlignment(Element.ALIGN_RIGHT);
        signRight.addElement(new Paragraph("For " + fName + "\n\n", boldFont));
        Paragraph pSig = new Paragraph("Authorized Signatory", boldFont);
        pSig.setAlignment(Element.ALIGN_RIGHT);
        signRight.addElement(pSig);
        signTable.addCell(signRight);

        PdfPTable signWrapTable = new PdfPTable(1);
        signWrapTable.setWidthPercentage(100);
        signWrapTable.setKeepTogether(true);
        PdfPCell sCell = new PdfPCell(signTable);
        sCell.setBorder(Rectangle.LEFT | Rectangle.RIGHT | Rectangle.BOTTOM);
        sCell.setBorderColor(DocumentThemeHelper.BORDER_COLOR);
        sCell.setBorderWidth(1f);
        sCell.setPadding(0);
        signWrapTable.addCell(sCell);
        doc.add(signWrapTable);

        doc.close();
        return baos.toByteArray();
    }

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
