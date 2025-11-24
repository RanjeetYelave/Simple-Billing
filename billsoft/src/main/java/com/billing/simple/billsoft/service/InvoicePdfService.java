package com.billing.simple.billsoft.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

import org.springframework.stereotype.Service;

import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoiceItem;
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

@Service
public class InvoicePdfService {

    private final FirmDetailsService firmService;

    public InvoicePdfService(FirmDetailsService firmService) {
        this.firmService = firmService;
    }

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // =====================================================================
    // MAIN METHOD NOW SUPPORTS A4 / A5
    // =====================================================================
    public byte[] generatePdf(Invoice invoice, String size) throws Exception {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Determine paper size
        Document doc;

        if ("A5".equalsIgnoreCase(size)) {
            // A5 exact dimensions
            doc = new Document(PageSize.A5, 24, 24, 36, 36);
        } else {
            // Default A4
            doc = new Document(PageSize.A4, 36, 36, 48, 48);
        }

        PdfWriter.getInstance(doc, baos);
        doc.open();

        // =====================================================================
        // FONTS
        // =====================================================================
        Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);
        Font hFont = new Font(Font.HELVETICA, 11, Font.BOLD);
        Font normal = new Font(Font.HELVETICA, 10, Font.NORMAL);
        Font small = new Font(Font.HELVETICA, 9, Font.NORMAL);
        Font bold = new Font(Font.HELVETICA, 10, Font.BOLD);

        // =====================================================================
        // FIRM DETAILS
        // =====================================================================
        FirmDetails firm = null;
        try { firm = firmService.get(); } catch (Exception ignored) {}

        PdfPTable header = new PdfPTable(new float[] { 2f, 1.5f });
        header.setWidthPercentage(100);
        header.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        // LEFT = FIRM
        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);

        // Logo
        if (firm != null && firm.getLogoBase64() != null && !firm.getLogoBase64().isBlank()) {
            try {
                String b64 = firm.getLogoBase64();
                if (b64.startsWith("data:")) {
                    b64 = b64.substring(b64.indexOf("base64,") + 7);
                }
                byte[] imgBytes = Base64.getDecoder().decode(b64);
                Image logo = Image.getInstance(imgBytes);

                if ("A5".equalsIgnoreCase(size)) {
                    logo.scaleToFit(80, 40); // smaller
                } else {
                    logo.scaleToFit(110, 60); // A4 size
                }

                left.addElement(logo);
            } catch (Exception ignored) {}
        }

        left.addElement(new Paragraph(
                firm != null && firm.getFirmName() != null ? firm.getFirmName() : "Firm Name",
                titleFont));

        if (firm != null) {
            if (firm.getOwnerName() != null)
                left.addElement(new Paragraph("Owner: " + firm.getOwnerName(), normal));

            if (firm.getAddressLine1() != null)
                left.addElement(new Paragraph(firm.getAddressLine1(), normal));

            if (firm.getAddressLine2() != null)
                left.addElement(new Paragraph(firm.getAddressLine2(), normal));

            String city = "";
            if (firm.getCity() != null) city += firm.getCity();
            if (firm.getState() != null) city += ", " + firm.getState();
            if (firm.getPincode() != null) city += " " + firm.getPincode();

            if (!city.isBlank())
                left.addElement(new Paragraph(city, normal));

            if (firm.getPhone() != null)
                left.addElement(new Paragraph("Phone: " + firm.getPhone(), normal));

            if (firm.getEmail() != null)
                left.addElement(new Paragraph("Email: " + firm.getEmail(), normal));

            if (firm.getGstin() != null)
                left.addElement(new Paragraph("GSTIN: " + firm.getGstin(), normal));
        }

        header.addCell(left);

        // RIGHT = INVOICE META
        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);

        Paragraph invTitle = new Paragraph("TAX INVOICE", titleFont);
        invTitle.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(invTitle);

        right.addElement(new Paragraph("Invoice: " + invoice.getInvoiceNumber(), bold));
        right.addElement(new Paragraph("Date: " +
                (invoice.getInvoiceDate() != null
                        ? invoice.getInvoiceDate().format(DATE_FMT)
                        : "-"),
                normal));

        if (invoice.getId() != null)
            right.addElement(new Paragraph("Invoice ID: " + invoice.getId(), small));

        header.addCell(right);

        doc.add(header);
        doc.add(new Paragraph("\n"));

        // =====================================================================
        // CUSTOMER DETAILS
        // =====================================================================
        PdfPTable cust = new PdfPTable(new float[]{1f, 1f});
        cust.setWidthPercentage(100);
        cust.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        PdfPCell cLeft = new PdfPCell();
        cLeft.setBorder(Rectangle.NO_BORDER);
        cLeft.addElement(new Paragraph("Bill To:", hFont));

        if (invoice.getCustomer() != null) {
            if (invoice.getCustomer().getName() != null)
                cLeft.addElement(new Paragraph(invoice.getCustomer().getName(), normal));

            if (invoice.getCustomer().getPhone() != null)
                cLeft.addElement(new Paragraph("Phone: " + invoice.getCustomer().getPhone(), small));

            if (invoice.getCustomer().getEmail() != null)
                cLeft.addElement(new Paragraph("Email: " + invoice.getCustomer().getEmail(), small));

            if (invoice.getCustomer().getAddress() != null)
                cLeft.addElement(new Paragraph("Address: " + invoice.getCustomer().getAddress(), small));
        }

        cust.addCell(cLeft);

        PdfPCell cRight = new PdfPCell();
        cRight.setBorder(Rectangle.NO_BORDER);
        cust.addCell(cRight);

        doc.add(cust);
        doc.add(new Paragraph("\n"));

        // =====================================================================
        // ITEMS TABLE
        // =====================================================================
        float[] widths = new float[]{0.6f, 3f, 1f, 0.8f, 0.8f, 1f, 1f, 0.8f, 1f};
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);
        table.setHeaderRows(1);

        String[] heads = {"#", "Description", "HSN/SAC", "Qty", "Unit", "Rate", "Amount", "GST%", "Total"};
        for (String head : heads) {
            PdfPCell cell = new PdfPCell(new Phrase(head, hFont));
            cell.setBackgroundColor(Color.LIGHT_GRAY);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(6);
            table.addCell(cell);
        }

        int idx = 1;
        List<InvoiceItem> items = invoice.getItems();

        for (InvoiceItem it : items) {
            table.addCell(new Phrase(String.valueOf(idx++), normal));

            String desc = it.getProduct() != null ? it.getProduct().getName() : "-";
            table.addCell(new Phrase(desc, normal));

            table.addCell(new Phrase("-", normal)); // HSN placeholder

            table.addCell(new Phrase(String.valueOf(it.getQty()), normal));
            table.addCell(new Phrase(it.getUnit() != null ? it.getUnit() : "-", normal));
            table.addCell(new Phrase(formatAmount(it.getPricePerUnit()), normal));
            table.addCell(new Phrase(formatAmount(it.getAmountWithoutTax()), normal));
            table.addCell(new Phrase(formatPercent(it.getGstPercent()), normal));
            table.addCell(new Phrase(formatAmount(it.getLineTotal()), normal));
        }

        doc.add(table);
        doc.add(new Paragraph("\n"));

        // =====================================================================
        // TOTAL SECTION
        // =====================================================================
        double subtotal = invoice.getSubtotalWithoutTax() != null ? invoice.getSubtotalWithoutTax() : 0.0;
        double totalTax = invoice.getTotalTax() != null ? invoice.getTotalTax() : 0.0;
        double totalAmount = invoice.getTotalAmount() != null ? invoice.getTotalAmount() : subtotal + totalTax;

        PdfPTable totalsTbl = new PdfPTable(new float[]{3f, 1f});
        totalsTbl.setWidthPercentage(50);
        totalsTbl.setHorizontalAlignment(Element.ALIGN_RIGHT);

        addTotalsRow(totalsTbl, "Subtotal", formatAmount(subtotal), normal, bold);
        addTotalsRow(totalsTbl, "Total Tax", formatAmount(totalTax), normal, bold);
        addTotalsRow(totalsTbl, "Grand Total", formatAmount(totalAmount), bold, bold);

        doc.add(totalsTbl);
        doc.add(new Paragraph("\n"));

        // =====================================================================
        // SIGNATURES
        // =====================================================================
        PdfPTable sig = new PdfPTable(new float[]{1f, 1f});
        sig.setWidthPercentage(100);

        PdfPCell leftSig = new PdfPCell(new Phrase("\n\nFor " +
                (firm != null && firm.getFirmName() != null ? firm.getFirmName() : "_____________"),
                normal));
        leftSig.setBorder(Rectangle.NO_BORDER);

        PdfPCell rightSig = new PdfPCell(new Phrase("\n\nAuthorised Signatory", normal));
        rightSig.setBorder(Rectangle.NO_BORDER);
        rightSig.setHorizontalAlignment(Element.ALIGN_RIGHT);

        sig.addCell(leftSig);
        sig.addCell(rightSig);

        doc.add(sig);

        doc.close();
        return baos.toByteArray();
    }

    // =====================================================================
    // UTIL HELPERS
    // =====================================================================
    private static void addTotalsRow(PdfPTable tbl, String label, String value, Font vFont, Font labelFont) {
        PdfPCell l = new PdfPCell(new Phrase(label, labelFont));
        l.setBorder(Rectangle.NO_BORDER);
        l.setPadding(4);
        tbl.addCell(l);

        PdfPCell v = new PdfPCell(new Phrase(value, vFont));
        v.setBorder(Rectangle.NO_BORDER);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        v.setPadding(4);
        tbl.addCell(v);
    }

    private static String formatAmount(Double d) {
        if (d == null) return "0.00";
        return String.format("%.2f", d);
    }

    private static String formatPercent(Double p) {
        if (p == null) return "0";
        return String.format("%.2f", p);
    }
}
