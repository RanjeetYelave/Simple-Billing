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
        Document doc = new Document(PageSize.A4, 28, 28, 28, 28);
        PdfWriter.getInstance(doc, baos);
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

        // Main Outermost Border Frame Table
        PdfPTable mainFrame = new PdfPTable(1);
        mainFrame.setWidthPercentage(100);
        mainFrame.getDefaultCell().setBorder(Rectangle.BOX);
        mainFrame.getDefaultCell().setBorderWidth(1f);
        mainFrame.getDefaultCell().setBorderColor(BORDER_COLOR);
        mainFrame.getDefaultCell().setPadding(0);

        PdfPCell containerCell = new PdfPCell();
        containerCell.setBorder(Rectangle.BOX);
        containerCell.setBorderWidth(1f);
        containerCell.setBorderColor(BORDER_COLOR);
        containerCell.setPadding(0);

        PdfPTable content = new PdfPTable(1);
        content.setWidthPercentage(100);

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
        if (po != null && po.getPaymentTerms() != null && !po.getPaymentTerms().isEmpty()) {
            Paragraph pTerms = new Paragraph("Terms: " + po.getPaymentTerms(), smallMuted);
            pTerms.setAlignment(Element.ALIGN_RIGHT);
            docMetaCell.addElement(pTerms);
        }

        // Payment Flag
        String payStatus = po != null && po.getPaymentStatus() != null ? po.getPaymentStatus() : "YET_TO_PAY";
        String payLabel = "PAID".equalsIgnoreCase(payStatus) ? "PAID" : ("PARTIAL".equalsIgnoreCase(payStatus) ? "PARTIALLY PAID" : "YET TO PAY");
        Paragraph pPay = new Paragraph("Payment: " + payLabel, boldFont);
        pPay.setAlignment(Element.ALIGN_RIGHT);
        docMetaCell.addElement(pPay);

        headerTable.addCell(docMetaCell);

        PdfPCell hCell = new PdfPCell(headerTable);
        hCell.setBorder(Rectangle.BOTTOM);
        hCell.setBorderColor(BORDER_COLOR);
        hCell.setBorderWidth(1f);
        content.addCell(hCell);

        // 2. Vendor (Party) & Delivery Address Block
        PdfPTable partyShipTable = new PdfPTable(new float[]{50, 50});
        partyShipTable.setWidthPercentage(100);
        partyShipTable.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        PdfPCell vendorCell = new PdfPCell();
        vendorCell.setBorder(Rectangle.NO_BORDER);
        vendorCell.setPadding(10);
        vendorCell.addElement(new Paragraph("VENDOR / SUPPLIER (TO):", boldFont));

        String vendorName = po != null ? (po.getPartyName() != null ? po.getPartyName() : (po.getParty() != null ? po.getParty().getName() : "—")) : "—";
        Paragraph pVName = new Paragraph(vendorName, boldFont);
        vendorCell.addElement(pVName);

        if (po != null) {
            String cp = po.getPartyContactPerson() != null ? po.getPartyContactPerson() : (po.getParty() != null ? po.getParty().getContactPerson() : null);
            if (cp != null && !cp.isEmpty()) vendorCell.addElement(new Paragraph("Attn: " + cp, normalFont));

            String addr = po.getPartyAddress() != null ? po.getPartyAddress() : (po.getParty() != null ? po.getParty().getAddress() : null);
            if (addr != null && !addr.isEmpty()) vendorCell.addElement(new Paragraph(addr, normalFont));

            String gst = po.getPartyGstin() != null ? po.getPartyGstin() : (po.getParty() != null ? po.getParty().getGstin() : null);
            if (gst != null && !gst.isEmpty()) vendorCell.addElement(new Paragraph("GSTIN: " + gst, boldFont));

            String ph = po.getPartyPhone() != null ? po.getPartyPhone() : (po.getParty() != null ? po.getParty().getPhone() : null);
            if (ph != null && !ph.isEmpty()) vendorCell.addElement(new Paragraph("Phone: " + ph, normalFont));

            String em = po.getPartyEmail() != null ? po.getPartyEmail() : (po.getParty() != null ? po.getParty().getEmail() : null);
            if (em != null && !em.isEmpty()) vendorCell.addElement(new Paragraph("Email: " + em, normalFont));
        }
        partyShipTable.addCell(vendorCell);

        PdfPCell shipCell = new PdfPCell();
        shipCell.setBorder(Rectangle.NO_BORDER);
        shipCell.setPadding(10);
        shipCell.addElement(new Paragraph("DELIVER TO / SHIPPING ADDRESS:", boldFont));

        String shipAddr = po != null && po.getShippingAddress() != null && !po.getShippingAddress().isEmpty()
                ? po.getShippingAddress()
                : (firm != null && firm.getAddressLine1() != null ? (firm.getAddressLine1() + (firm.getCity() != null ? ", " + firm.getCity() : "")) : "Company Premises");
        shipCell.addElement(new Paragraph(shipAddr, normalFont));
        partyShipTable.addCell(shipCell);

        PdfPCell psCell = new PdfPCell(partyShipTable);
        psCell.setBorder(Rectangle.BOTTOM);
        psCell.setBorderColor(BORDER_COLOR);
        psCell.setBorderWidth(1f);
        content.addCell(psCell);

        // 3. Line Items Table
        PdfPTable itemTable = new PdfPTable(new float[]{5, 38, 12, 8, 8, 13, 16});
        itemTable.setWidthPercentage(100);
        itemTable.getDefaultCell().setBorder(Rectangle.BOX);
        itemTable.getDefaultCell().setBorderColor(BORDER_COLOR);

        // Headers
        String[] headers = {"#", "Item & Description", "HSN", "Qty", "Unit", "Rate (₹)", "Total (₹)"};
        int[] aligns = {Element.ALIGN_CENTER, Element.ALIGN_LEFT, Element.ALIGN_CENTER, Element.ALIGN_RIGHT, Element.ALIGN_CENTER, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT};

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
            emptyC.setColspan(7);
            emptyC.setPadding(14);
            emptyC.setHorizontalAlignment(Element.ALIGN_CENTER);
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
                String pName = it.getProductName() != null ? it.getProductName() : "Item";
                cName.addElement(new Paragraph(pName, boldFont));
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

        PdfPCell itCell = new PdfPCell(itemTable);
        itCell.setBorder(Rectangle.BOTTOM);
        itCell.setBorderColor(BORDER_COLOR);
        itCell.setBorderWidth(1f);
        content.addCell(itCell);

        // 4. Totals Summary & Notes Block
        PdfPTable bottomTable = new PdfPTable(new float[]{55, 45});
        bottomTable.setWidthPercentage(100);
        bottomTable.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        PdfPCell notesCell = new PdfPCell();
        notesCell.setBorder(Rectangle.NO_BORDER);
        notesCell.setPadding(10);
        if (po != null && po.getNotes() != null && !po.getNotes().isEmpty()) {
            notesCell.addElement(new Paragraph("Notes / Instructions:", boldFont));
            notesCell.addElement(new Paragraph(po.getNotes(), smallMuted));
        }
        if (po != null && po.getTermsAndConditions() != null && !po.getTermsAndConditions().isEmpty()) {
            notesCell.addElement(new Paragraph("Terms & Conditions:", boldFont));
            notesCell.addElement(new Paragraph(po.getTermsAndConditions(), smallMuted));
        }
        bottomTable.addCell(notesCell);

        PdfPCell summaryCell = new PdfPCell();
        summaryCell.setBorder(Rectangle.NO_BORDER);
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

        PdfPCell bCell = new PdfPCell(bottomTable);
        bCell.setBorder(Rectangle.BOTTOM);
        bCell.setBorderColor(BORDER_COLOR);
        bCell.setBorderWidth(1f);
        content.addCell(bCell);

        // 5. Signatory Footer Block
        PdfPTable signTable = new PdfPTable(new float[]{60, 40});
        signTable.setWidthPercentage(100);
        signTable.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        PdfPCell signLeft = new PdfPCell();
        signLeft.setBorder(Rectangle.NO_BORDER);
        signLeft.setPadding(12);
        signLeft.addElement(new Paragraph("This is a computer-generated official Purchase Order.", smallMuted));
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

        PdfPCell sCell = new PdfPCell(signTable);
        sCell.setBorder(Rectangle.NO_BORDER);
        content.addCell(sCell);

        containerCell.addElement(content);
        mainFrame.addCell(containerCell);
        doc.add(mainFrame);

        doc.close();
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
