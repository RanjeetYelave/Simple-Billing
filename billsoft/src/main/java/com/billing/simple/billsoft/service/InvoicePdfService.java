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
 * Produces a professional PDF for Invoice and Estimate using BigDecimal-safe computations.
 * Supports A4 / A5, handles logo base64 (raw or data: URL), and uses invoice.status to pick title.
 */
@Service
public class InvoicePdfService {

    private final FirmDetailsService firmService;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final int SCALE = 2;

    public InvoicePdfService(FirmDetailsService firmService) {
        this.firmService = firmService;
    }

    @SuppressWarnings("deprecation")
    public byte[] generatePdf(Invoice invoice, String size) throws Exception {

        Rectangle pageSize = PageSize.A4;
        if (size != null && size.equalsIgnoreCase("A5")) pageSize = PageSize.A5;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(pageSize, 36, 36, 48, 48);
        PdfWriter.getInstance(doc, baos);

        doc.open();

        // Fonts scaled for A4 / A5
        Font titleFont = new Font(Font.HELVETICA, pageSize == PageSize.A5 ? 16 : 18, Font.BOLD);
        Font hFont = new Font(Font.HELVETICA, pageSize == PageSize.A5 ? 10 : 11, Font.BOLD);
        Font normal = new Font(Font.HELVETICA, pageSize == PageSize.A5 ? 9 : 10, Font.NORMAL);
        Font small = new Font(Font.HELVETICA, pageSize == PageSize.A5 ? 8 : 9, Font.NORMAL);
        Font bold = new Font(Font.HELVETICA, pageSize == PageSize.A5 ? 10 : 10, Font.BOLD);

        FirmDetails firm = null;
        try {
            Long firmId = invoice != null ? invoice.getFirmId() : null;
            firm = firmId != null ? firmService.get(firmId) : firmService.getFirst();
        } catch (Exception e) { firm = null; }

        // ---------------- HEADER ----------------
        PdfPTable header = new PdfPTable(new float[]{2f, 1.6f});
        header.setWidthPercentage(100);
        header.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);

        // Logo (safe)
        if (firm != null && firm.getLogoBase64() != null && !firm.getLogoBase64().isBlank()) {
            try {
                String b64 = firm.getLogoBase64().trim();
                if (b64.startsWith("data:")) {
                    int idx = b64.indexOf("base64,");
                    if (idx >= 0) b64 = b64.substring(idx + 7);
                }
                byte[] bytes = Base64.getDecoder().decode(b64);
                Image logo = Image.getInstance(bytes);
                float maxW = pageSize == PageSize.A5 ? 80f : 110f;
                float maxH = pageSize == PageSize.A5 ? 45f : 60f;
                logo.scaleToFit(maxW, maxH);
                logo.setAlignment(Image.LEFT);
                left.addElement(logo);
            } catch (Exception ex) {
                // swallow; keep building
            }
        }

        // Firm name & meta
        left.addElement(new Paragraph(firm != null && firm.getFirmName() != null && !firm.getFirmName().isBlank()
                ? firm.getFirmName() : "Firm Name", titleFont));

        if (firm != null) {
            if (firm.getOwnerName() != null && !firm.getOwnerName().isBlank())
                left.addElement(new Paragraph("Owner: " + firm.getOwnerName(), normal));

            StringBuilder addr = new StringBuilder();
            if (firm.getAddressLine1() != null && !firm.getAddressLine1().isBlank()) addr.append(firm.getAddressLine1());
            if (firm.getAddressLine2() != null && !firm.getAddressLine2().isBlank()) {
                if (addr.length() > 0) addr.append(", ");
                addr.append(firm.getAddressLine2());
            }
            if (addr.length() > 0) left.addElement(new Paragraph(addr.toString(), normal));

            StringBuilder cityLine = new StringBuilder();
            if (firm.getCity() != null && !firm.getCity().isBlank()) cityLine.append(firm.getCity());
            if (firm.getState() != null && !firm.getState().isBlank()) {
                if (cityLine.length() > 0) cityLine.append(" - ");
                cityLine.append(firm.getState());
            }
            if (firm.getPincode() != null && !firm.getPincode().isBlank()) {
                if (cityLine.length() > 0) cityLine.append(" ");
                cityLine.append(firm.getPincode());
            }
            if (cityLine.length() > 0) left.addElement(new Paragraph(cityLine.toString(), normal));

            if (firm.getPhone() != null && !firm.getPhone().isBlank())
                left.addElement(new Paragraph("Phone: " + firm.getPhone(), normal));
            if (firm.getEmail() != null && !firm.getEmail().isBlank())
                left.addElement(new Paragraph("Email: " + firm.getEmail(), normal));
            if (firm.getGstin() != null && !firm.getGstin().isBlank())
                left.addElement(new Paragraph("GSTIN: " + firm.getGstin(), normal));
        }

        header.addCell(left);

        // RIGHT: invoice metadata + dynamic title
        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);

        String title = pickTitle(invoice);
        Paragraph titleP = new Paragraph(title, titleFont);
        titleP.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(titleP);

        // number & date
        String numLabel = (invoice.getStatus() != null && invoice.getStatus().name().equalsIgnoreCase("ESTIMATE"))
                ? "Estimate: " + (invoice.getEstimateNumber() == null ? "-" : invoice.getEstimateNumber())
                : "Invoice: " + (invoice.getInvoiceNumber() == null ? "-" : invoice.getInvoiceNumber());
        right.addElement(new Paragraph(numLabel, bold));

        if (invoice.getInvoiceDate() != null)
            right.addElement(new Paragraph("Date: " + invoice.getInvoiceDate().format(DATE_FMT), normal));
        else right.addElement(new Paragraph("Date: -", normal));

        if (invoice.getId() != null) right.addElement(new Paragraph("ID: " + invoice.getId(), small));

        header.addCell(right);

        doc.add(header);
        doc.add(new Paragraph("\n"));

        // ---------- CUSTOMER BLOCK ----------
        PdfPTable cust = new PdfPTable(new float[]{1f, 1f});
        cust.setWidthPercentage(100);
        cust.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        PdfPCell cLeft = new PdfPCell();
        cLeft.setBorder(Rectangle.NO_BORDER);
        cLeft.addElement(new Paragraph("Bill To:", hFont));
        if (invoice.getCustomer() != null) {
            if (invoice.getCustomer().getName() != null) cLeft.addElement(new Paragraph(invoice.getCustomer().getName(), normal));
            if (invoice.getCustomer().getPhone() != null && !invoice.getCustomer().getPhone().isBlank())
                cLeft.addElement(new Paragraph("Phone: " + invoice.getCustomer().getPhone(), small));
            if (invoice.getCustomer().getEmail() != null && !invoice.getCustomer().getEmail().isBlank())
                cLeft.addElement(new Paragraph("Email: " + invoice.getCustomer().getEmail(), small));
            if (invoice.getCustomer().getAddress() != null && !invoice.getCustomer().getAddress().isBlank())
                cLeft.addElement(new Paragraph("Address: " + invoice.getCustomer().getAddress(), small));
        } else {
            cLeft.addElement(new Paragraph("-", normal));
        }
        cust.addCell(cLeft);

        PdfPCell cRight = new PdfPCell();
        cRight.setBorder(Rectangle.NO_BORDER);
        cRight.setHorizontalAlignment(Element.ALIGN_RIGHT);

        // Optional invoice meta right-side (payment method, currency, tags)
        if (invoice.getPaymentMethod() != null && !invoice.getPaymentMethod().isBlank())
            cRight.addElement(new Paragraph("Payment: " + invoice.getPaymentMethod(), normal));
        if (invoice.getCurrency() != null) cRight.addElement(new Paragraph("Currency: " + invoice.getCurrency(), normal));
        if (invoice.getTags() != null && !invoice.getTags().isBlank())
            cRight.addElement(new Paragraph("Tags: " + invoice.getTags(), small));

        cust.addCell(cRight);
        doc.add(cust);
        doc.add(new Paragraph("\n"));

        // ---------- ITEMS TABLE ----------
        float[] widths = new float[]{0.5f, 3f, 1f, 0.8f, 0.8f, 1f, 1f, 1f, 0.8f, 1f, 1f};
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);
        table.setHeaderRows(1);

        String[] heads = {"#", "Item", "HSN/SAC", "Qty", "Unit", "Rate", "Discount", "Taxable", "GST%", "GST Amt", "Line Total"};
        for (String head : heads) {
            PdfPCell cell = new PdfPCell(new Phrase(head, hFont));
            cell.setBackgroundColor(Color.LIGHT_GRAY);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(6);
            table.addCell(cell);
        }

        List<InvoiceItem> items = invoice.getItems();
        if (items != null && !items.isEmpty()) {
            int idx = 1;
            for (InvoiceItem it : items) {
                String desc = "-";
                try {
                    if (it.getProduct() != null && it.getProduct().getName() != null) desc = it.getProduct().getName();
                    else if (it.getUnit() != null) desc = it.getUnit();
                } catch (Exception e) { desc = "-"; }

                table.addCell(new PdfPCell(new Phrase(String.valueOf(idx++), normal)));
                table.addCell(new PdfPCell(new Phrase(desc, normal)));
                table.addCell(new PdfPCell(new Phrase("-", normal))); // HSN placeholder

                table.addCell(new PdfPCell(new Phrase(String.valueOf(it.getQty() == null ? 0 : it.getQty()), normal)));
                table.addCell(new PdfPCell(new Phrase(it.getUnit() == null ? "-" : it.getUnit(), normal)));
                table.addCell(new PdfPCell(new Phrase(formatAmount(it.getPricePerUnit()), normal)));

                // Discount: show percent or value
                String disc = "-";
                if (it.getDiscountPercent() != null) disc = formatPercent(it.getDiscountPercent());
                else if (it.getDiscountValue() != null) disc = "- " + formatAmount(it.getDiscountValue());
                table.addCell(new PdfPCell(new Phrase(disc, normal)));

                table.addCell(new PdfPCell(new Phrase(formatAmount(it.getTaxableAmount()), normal)));
                table.addCell(new PdfPCell(new Phrase(formatPercent(it.getGstPercent()), normal)));
                table.addCell(new PdfPCell(new Phrase(formatAmount(it.getGstAmount()), normal)));
                table.addCell(new PdfPCell(new Phrase(formatAmount(it.getLineTotal()), normal)));
            }
        } else {
            PdfPCell empty = new PdfPCell(new Phrase("No items", normal));
            empty.setColspan(heads.length);
            empty.setPadding(10);
            table.addCell(empty);
        }

        doc.add(table);
        doc.add(new Paragraph("\n"));

        // ---------------- TOTALS / SUMMARY ----------------
        // Compute consistent BigDecimal totals from stored fields (safest)
        BigDecimal rawSubtotal = BigDecimal.ZERO.setScale(SCALE);
        BigDecimal productDiscount = BigDecimal.ZERO.setScale(SCALE);
        BigDecimal taxableBeforeInvoiceDiscount = BigDecimal.ZERO.setScale(SCALE);
        BigDecimal totalTax = nz(invoice.getTotalTax());
        BigDecimal storedTotalDiscount = nz(invoice.getTotalDiscount()); // product + invoice-level

        if (items != null && !items.isEmpty()) {
            for (InvoiceItem it : items) {
                BigDecimal qty = BigDecimal.valueOf(it.getQty() == null ? 0 : it.getQty());
                BigDecimal rate = nz(it.getPricePerUnit());
                BigDecimal base = rate.multiply(qty).setScale(SCALE, RoundingMode.HALF_UP);
                rawSubtotal = rawSubtotal.add(base);

                BigDecimal idisc = BigDecimal.ZERO.setScale(SCALE);
                if (it.getDiscountPercent() != null && it.getDiscountPercent().compareTo(BigDecimal.ZERO) > 0) {
                    idisc = it.getDiscountPercent().multiply(base).divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                            .setScale(SCALE, RoundingMode.HALF_UP);
                } else if (it.getDiscountValue() != null) {
                    idisc = it.getDiscountValue();
                }
                productDiscount = productDiscount.add(idisc);

                taxableBeforeInvoiceDiscount = taxableBeforeInvoiceDiscount.add(nz(it.getTaxableAmount()));
            }
        }

        // invoice-level discount = storedTotalDiscount - productDiscount (safety clamp)
        BigDecimal invoiceLevelDiscount = storedTotalDiscount.subtract(productDiscount);
        if (invoiceLevelDiscount.compareTo(BigDecimal.ZERO) < 0) invoiceLevelDiscount = BigDecimal.ZERO.setScale(SCALE);

        BigDecimal grand = nz(invoice.getTotalAmount());
        BigDecimal roundOff = nz(invoice.getRoundOff());

        // Totals table (right aligned)
        PdfPTable totalsTbl = new PdfPTable(new float[] {3f, 1f});
        totalsTbl.setWidthPercentage(50);
        totalsTbl.setHorizontalAlignment(Element.ALIGN_RIGHT);

        addTotalsRow(totalsTbl, "Subtotal (Before Discount)", formatAmount(rawSubtotal), normal, hFont);
        addTotalsRow(totalsTbl, "Product Discount", "- " + formatAmount(productDiscount), normal, hFont);
        addTotalsRow(totalsTbl, "Invoice-level Discount", "- " + formatAmount(invoiceLevelDiscount), normal, hFont);
        addTotalsRow(totalsTbl, "Taxable Value", formatAmount(taxableBeforeInvoiceDiscount), normal, hFont);
        addTotalsRow(totalsTbl, "Total GST", formatAmount(totalTax), normal, hFont);
        addTotalsRow(totalsTbl, "Round Off", formatAmount(roundOff), normal, hFont);
        addTotalsRow(totalsTbl, "Grand Total", formatAmount(grand), normal, bold);

        doc.add(totalsTbl);
        doc.add(new Paragraph("\n"));

        // Bank details block (boxed if present)
        if (firm != null && ((firm.getBankName() != null && !firm.getBankName().isBlank())
                || (firm.getBankAccount() != null && !firm.getBankAccount().isBlank()))) {

            PdfPTable bank = new PdfPTable(1);
            bank.setWidthPercentage(100);
            PdfPCell bh = new PdfPCell(new Phrase("Bank Details", hFont));
            bh.setBorder(Rectangle.NO_BORDER);
            bank.addCell(bh);

            StringBuilder bsb = new StringBuilder();
            if (firm.getBankName() != null && !firm.getBankName().isBlank())
                bsb.append("Bank: ").append(firm.getBankName()).append("\n");
            if (firm.getBankAccount() != null && !firm.getBankAccount().isBlank())
                bsb.append("A/C: ").append(firm.getBankAccount()).append("\n");
            if (firm.getBankIfsc() != null && !firm.getBankIfsc().isBlank())
                bsb.append("IFSC: ").append(firm.getBankIfsc()).append("\n");

            PdfPCell bcell = new PdfPCell(new Phrase(bsb.toString().trim(), normal));
            bcell.setBorder(Rectangle.NO_BORDER);
            bank.addCell(bcell);

            doc.add(bank);
            doc.add(new Paragraph("\n"));
        }

        // Footer / signature
        if (firm != null && firm.getFooterNote() != null && !firm.getFooterNote().isBlank()) {
            Paragraph foot = new Paragraph(firm.getFooterNote(), small);
            foot.setAlignment(Element.ALIGN_CENTER);
            doc.add(foot);
            doc.add(new Paragraph("\n"));
        }

        PdfPTable sig = new PdfPTable(new float[] {1f, 1f});
        sig.setWidthPercentage(100);
        PdfPCell leftSig = new PdfPCell(new Phrase("\n\n\nFor " + (firm != null && firm.getFirmName() != null ? firm.getFirmName() : "________________"), normal));
        leftSig.setBorder(Rectangle.NO_BORDER);
        leftSig.setHorizontalAlignment(Element.ALIGN_LEFT);
        sig.addCell(leftSig);

        PdfPCell rightSig = new PdfPCell(new Phrase("\n\n\nAuthorised Signatory", normal));
        rightSig.setBorder(Rectangle.NO_BORDER);
        rightSig.setHorizontalAlignment(Element.ALIGN_RIGHT);
        sig.addCell(rightSig);

        doc.add(sig);

        doc.close();

        // The caller expects filename building; we only return bytes here.
        return baos.toByteArray();
    }

    // Determine title from invoice status
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

    private static void addTotalsRow(PdfPTable tbl, String label, String value, Font vFont, Font labelFont) {
        PdfPCell l = new PdfPCell(new Phrase(label, labelFont));
        l.setBorder(Rectangle.NO_BORDER);
        l.setHorizontalAlignment(Element.ALIGN_LEFT);
        l.setPadding(6);
        tbl.addCell(l);

        PdfPCell v = new PdfPCell(new Phrase(value, vFont));
        v.setBorder(Rectangle.NO_BORDER);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        v.setPadding(6);
        tbl.addCell(v);
    }

    // Formatting helpers (BigDecimal-aware)
    private static String formatAmount(BigDecimal d) {
        if (d == null) return "0.00";
        return d.setScale(SCALE, RoundingMode.HALF_UP).toPlainString();
    }

    private static String formatPercent(BigDecimal p) {
        if (p == null) return "0.00";
        return p.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    // null-safe helper
    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO.setScale(SCALE) : v.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
