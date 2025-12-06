package com.billing.simple.billsoft.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

import org.springframework.stereotype.Service;

import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoiceItem;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;

/**
 * Clean & industry-standard PDF generator for Invoice + Estimate.
 * Responsive A4 (Portrait) + A5 (Landscape)
 */
@Service
public class InvoicePdfService {

    private final FirmDetailsService firmService;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final int SCALE = 2;

    public InvoicePdfService(FirmDetailsService firmService) {
        this.firmService = firmService;
    }

    public byte[] generatePdf(Invoice invoice, String size) throws Exception {

        Rectangle pageSize = PageSize.A4;
        boolean isA5 = false;
        if (size != null && size.equalsIgnoreCase("A5")) {
            pageSize = PageSize.A5.rotate();
            isA5 = true;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(pageSize, 36, 36, 40, 40);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        // Fonts tuned for clarity
        float base = isA5 ? 9 : 11;
        Font titleFont = new Font(Font.HELVETICA, base + 4, Font.BOLD);
        Font hFont = new Font(Font.HELVETICA, base, Font.BOLD);
        Font normal = new Font(Font.HELVETICA, base, Font.NORMAL);
        Font small = new Font(Font.HELVETICA, base - 1, Font.NORMAL);
        Font bold = new Font(Font.HELVETICA, base, Font.BOLD);

        FirmDetails firm = safeFirm();

        // ---------------- HEADER ----------------
        PdfPTable header = new PdfPTable(new float[]{3f, 2f});
        header.setWidthPercentage(100);
        header.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        // LEFT → Logo + Firm identity
        PdfPCell left = noBorder();
        addLogo(left, firm, pageSize);

        addFirmText(left, firm, titleFont, normal);
        header.addCell(left);

        // RIGHT → Title + Invoice Meta
        PdfPCell right = noBorder();
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);

        right.addElement(p(pickTitle(invoice), titleFont, Element.ALIGN_RIGHT));
        right.addElement(p(numberLine(invoice), bold, Element.ALIGN_RIGHT));
        right.addElement(p("Date: " + (invoice.getInvoiceDate() == null ?
                "-" : invoice.getInvoiceDate().format(DATE_FMT)), normal, Element.ALIGN_RIGHT));

        header.addCell(right);
        doc.add(header);
        doc.add(space());

        // ---------------- CUSTOMER BLOCK ----------------
        PdfPTable cust = new PdfPTable(new float[]{1f, 1f});
        cust.setWidthPercentage(100);
        cust.addCell(customerCell(invoice, hFont, normal, small));
        cust.addCell(paymentCell(invoice, normal, small));
        doc.add(cust);
        doc.add(space());

        // ---------------- ITEMS TABLE ----------------
        PdfPTable table = buildItemsTable(invoice, hFont, normal, isA5);
        doc.add(table);
        doc.add(space());

        // ---------------- TOTALS ----------------
        PdfPTable totals = buildTotalsTable(invoice, hFont, bold, normal);
        doc.add(totals);
        doc.add(space());

        // ---------------- NOTES (Customer Notes ALWAYS SHOWN) ----------------
        if (invoice.getCustomerNote() != null && !invoice.getCustomerNote().isBlank()) {
            PdfPCell notes = new PdfPCell(new Phrase("Note: " + invoice.getCustomerNote(), normal));
            notes.setBorder(Rectangle.NO_BORDER);
            notes.setPadding(6);
            PdfPTable nt = new PdfPTable(1);
            nt.setWidthPercentage(100);
            nt.addCell(notes);
            doc.add(nt);
            doc.add(space());
        }

        // ---------------- FOOTER — Signature ----------------
        PdfPTable sig = new PdfPTable(new float[]{1f, 1f});
        sig.setWidthPercentage(100);

        sig.addCell(noBorderCell("\n\n\nFor " +
                (firm != null && firm.getFirmName() != null ? firm.getFirmName() : "________________"), normal));

        PdfPCell sign = noBorderCell("\n\n\nAuthorised Signatory", normal);
        sign.setHorizontalAlignment(Element.ALIGN_RIGHT);
        sig.addCell(sign);

        doc.add(sig);

        doc.close();
        return baos.toByteArray();
    }

    // ---------- Helper methods ----------

    private FirmDetails safeFirm() {
        try { return firmService.get(); } catch (Exception ex) { return null; }
    }

    private static PdfPCell noBorder() {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(4);
        return c;
    }

    private static PdfPCell noBorderCell(String text, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(4);
        return c;
    }

    private static Paragraph p(String s, Font f, int a) {
        Paragraph p = new Paragraph(s, f);
        p.setAlignment(a);
        return p;
    }

    private static Paragraph space() {
        return new Paragraph("\n");
    }

    private static String numberLine(Invoice inv) {
        if (inv.getStatus() != null && inv.getStatus().name().equals("ESTIMATE"))
            return "Estimate No: " + safe(inv.getEstimateNumber());
        return "Invoice No: " + safe(inv.getInvoiceNumber());
    }

    private static String safe(String v) {
        return v == null ? "-" : v;
    }

    private static void addLogo(PdfPCell cell, FirmDetails firm, Rectangle pageSize) {
        try {
            if (firm == null || firm.getLogoBase64() == null) return;
            String b = firm.getLogoBase64().trim();
            if (b.startsWith("data:")) b = b.substring(b.indexOf("base64,") + 7);
            Image logo = Image.getInstance(Base64.getDecoder().decode(b));
            logo.scaleToFit(90, 50);
            cell.addElement(logo);
        } catch (Exception ignored) {}
    }

    private static void addFirmText(PdfPCell cell, FirmDetails firm, Font title, Font normal) {
        cell.addElement(new Paragraph(
                firm != null && firm.getFirmName() != null ? firm.getFirmName() : "Firm Name", title));
        if (firm == null) return;
        add(cell, firm.getOwnerName(), normal);
        add(cell, firm.getAddressLine1(), normal);
        add(cell, firm.getCity(), normal);
        add(cell, firm.getPhone(), normal);
        add(cell, "GSTIN: " + firm.getGstin(), normal);
    }

    private static void add(PdfPCell c, String txt, Font f) {
        if (txt != null && !txt.isBlank()) c.addElement(new Paragraph(txt, f));
    }

    private static PdfPCell customerCell(Invoice inv, Font h, Font n, Font s) {
        PdfPCell c = noBorder();
        c.addElement(new Paragraph("Bill To:", h));
        if (inv.getCustomer() != null) {
            add(c, inv.getCustomer().getName(), n);
            add(c, "Phone: " + inv.getCustomer().getPhone(), s);
            add(c, "Email: " + inv.getCustomer().getEmail(), s);
            add(c, "Address: " + inv.getCustomer().getAddress(), s);
        }
        return c;
    }

    private static PdfPCell paymentCell(Invoice inv, Font n, Font s) {
        PdfPCell c = noBorder();
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        add(c, "Payment: " + safe(inv.getPaymentMethod()), n);
        add(c, "Currency: " + safe(inv.getCurrency()), s);
        return c;
    }

    private PdfPTable buildItemsTable(Invoice inv, Font hFont, Font normal, boolean isA5) {

        float[] widths = isA5
                ? new float[]{0.7f, 4f, 1.2f, 0.9f, 1.2f, 1.4f, 1.3f, 1.4f, 1.2f, 1.4f}
                : new float[]{0.6f, 4f, 1.2f, 1.0f, 1.5f, 1.7f, 1.5f, 1.7f, 1.3f, 1.8f};

        PdfPTable tbl = new PdfPTable(widths);
        tbl.setWidthPercentage(100);
        tbl.setHeaderRows(1);

        String[] heads = { "#", "Item", "HSN/SAC", "Qty", "Unit", "Rate",
                "Discount", "Taxable", "GST%", "Line Total" };
        addHeader(tbl, heads, hFont);

        if (inv.getItems() == null) {
            cellSpan(tbl, "No items", normal, heads.length);
            return tbl;
        }

        int i = 1;
        for (InvoiceItem it : inv.getItems()) {
            tbl.addCell(center(i++ + "", normal));
            tbl.addCell(left(safe(it.getProduct() != null ? it.getProduct().getName() : "-"), normal));
            tbl.addCell(center("-", normal));
            tbl.addCell(center(q(it.getQty()), normal));
            tbl.addCell(center(safe(it.getUnit()), normal));
            tbl.addCell(right(money(it.getPricePerUnit()), normal));
            tbl.addCell(right(discount(it), normal));
            tbl.addCell(right(money(it.getTaxableAmount()), normal));
            tbl.addCell(center(percent(it.getGstPercent()), normal));
            tbl.addCell(right(money(it.getLineTotal()), normal));
        }

        return tbl;
    }

    private static void addHeader(PdfPTable t, String[] h, Font f) {
        for (String s : h) {
            PdfPCell c = new PdfPCell(new Phrase(s, f));
            c.setBackgroundColor(Color.LIGHT_GRAY);
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            c.setPadding(6);
            t.addCell(c);
        }
    }

    private static void cellSpan(PdfPTable t, String txt, Font f, int span) {
        PdfPCell c = new PdfPCell(new Phrase(txt, f));
        c.setColspan(span);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setPadding(4);
        t.addCell(c);
    }

    private PdfPTable buildTotalsTable(Invoice inv, Font lbl, Font bold, Font normal) {

        BigDecimal sub = bd(inv.getSubtotalWithoutTax());
        BigDecimal prodDisc = bd(inv.getTotalDiscount()).subtract(bd(inv.getInvoiceDiscountValue()));
        BigDecimal invDisc = bd(inv.getInvoiceDiscountValue());
        BigDecimal gst = bd(inv.getTotalTax());
        BigDecimal grand = bd(inv.getTotalAmount());
        BigDecimal round = bd(inv.getRoundOff());

        PdfPTable t = new PdfPTable(new float[]{3f, 1f});
        t.setWidthPercentage(45);
        t.setHorizontalAlignment(Element.ALIGN_RIGHT);

        addTotal(t, "Subtotal (Before Discount)", money(sub), lbl, normal);
        addTotal(t, "Product Discount", "- " + money(prodDisc), lbl, normal);
        addTotal(t, "Invoice Discount", "- " + money(invDisc), lbl, normal);
        addTotal(t, "Total GST", money(gst), lbl, normal);
        addTotal(t, "Round Off", money(round), lbl, normal);

        PdfPCell l = new PdfPCell(new Phrase("GRAND TOTAL", bold));
        l.setBorder(Rectangle.TOP);
        l.setPadding(6);
        t.addCell(l);

        PdfPCell v = new PdfPCell(new Phrase(money(grand), bold));
        v.setBorder(Rectangle.TOP);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        v.setPadding(6);
        t.addCell(v);

        return t;
    }

    // --- formatting helpers ---
    private static String q(Integer q) {
        return q == null ? "0" : q.toString();
    }

    private static String discount(InvoiceItem it) {
        if (it.getDiscountPercent() != null)
            return percent(it.getDiscountPercent());
        if (it.getDiscountValue() != null)
            return money(it.getDiscountValue());
        return "-";
    }

    private static String percent(BigDecimal p) {
        if (p == null) return "-";
        return p.stripTrailingZeros().toPlainString() + "%";
    }

    private static BigDecimal bd(BigDecimal b) {
        return b == null ? BigDecimal.ZERO.setScale(SCALE) : b.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static String money(BigDecimal b) {
        return "₹" + bd(b).toPlainString();
    }

    private static PdfPCell left(String s, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(s, f));
        c.setHorizontalAlignment(Element.ALIGN_LEFT);
        c.setPadding(5);
        return c;
    }

    private static PdfPCell center(String s, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(s, f));
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setPadding(5);
        return c;
    }

    private static PdfPCell right(String s, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(s, f));
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        c.setPadding(5);
        return c;
    }

    private static void addTotal(PdfPTable tbl, String label, String value, Font lf, Font vf) {
        PdfPCell l = new PdfPCell(new Phrase(label, lf));
        l.setBorder(Rectangle.NO_BORDER);
        l.setPadding(4);
        tbl.addCell(l);

        PdfPCell v = new PdfPCell(new Phrase(value, vf));
        v.setBorder(Rectangle.NO_BORDER);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        v.setPadding(4);
        tbl.addCell(v);
    }

    private static String pickTitle(Invoice inv) {
        if (inv == null || inv.getStatus() == null) return "TAX INVOICE";
        switch (inv.getStatus()) {
            case ESTIMATE: return "ESTIMATE";
            case DRAFT: return "DRAFT";
            case CANCELLED: return "CANCELLED";
            case PAID: return "TAX INVOICE (PAID)";
            case SENT: return "TAX INVOICE (SENT)";
            case OVERDUE: return "TAX INVOICE (OVERDUE)";
            default: return "TAX INVOICE";
        }
    }
}
