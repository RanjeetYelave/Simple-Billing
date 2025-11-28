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

    @SuppressWarnings("deprecation")
    public byte[] generatePdf(Invoice invoice, String size) throws Exception {

        Rectangle pageSize = PageSize.A4;
        if (size != null && size.equalsIgnoreCase("A5")) {
            pageSize = PageSize.A5;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(pageSize, 36, 36, 48, 48);
        PdfWriter.getInstance(doc, baos);

        doc.open();

        // -------------------- Fonts ----------------------
        Font titleFont = new Font(Font.HELVETICA, pageSize == PageSize.A5 ? 16 : 18, Font.BOLD);
        Font hFont = new Font(Font.HELVETICA, pageSize == PageSize.A5 ? 10 : 11, Font.BOLD);
        Font normal = new Font(Font.HELVETICA, pageSize == PageSize.A5 ? 9 : 10, Font.NORMAL);
        Font small = new Font(Font.HELVETICA, pageSize == PageSize.A5 ? 8 : 9, Font.NORMAL);
        Font bold = new Font(Font.HELVETICA, pageSize == PageSize.A5 ? 10 : 10, Font.BOLD);

        FirmDetails firm = null;
        try {
            firm = firmService.get();
        } catch (Exception e) {
            firm = null;
        }

        // ---------- HEADER ----------
        PdfPTable header = new PdfPTable(new float[]{2f, 1.5f});
        header.setWidthPercentage(100);
        header.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);

        // ---------- LOGO FIX (handles raw base64 or data: URL) ----------
        if (firm != null && firm.getLogoBase64() != null && !firm.getLogoBase64().isBlank()) {
            try {
                String b64 = firm.getLogoBase64().trim();

                // Accept either raw base64 or data URL
                if (b64.startsWith("data:")) {
                    int idx = b64.indexOf("base64,");
                    if (idx >= 0) b64 = b64.substring(idx + 7);
                }

                // decode
                byte[] bytes = Base64.getDecoder().decode(b64);
                Image logo = Image.getInstance(bytes);

                // scale to reasonable invoice sizes
                float maxW = pageSize == PageSize.A5 ? 80f : 110f;
                float maxH = pageSize == PageSize.A5 ? 45f : 60f;
                logo.scaleToFit(maxW, maxH);
                logo.setAlignment(Image.LEFT);
                left.addElement(logo);
            } catch (Exception ex) {
                // don't fail PDF generation because of logo errors
                System.out.println("Logo decode error: " + ex.getMessage());
            }
        }

        // Firm name
        if (firm != null && firm.getFirmName() != null && !firm.getFirmName().isBlank()) {
            Paragraph fn = new Paragraph(firm.getFirmName(), titleFont);
            left.addElement(fn);
        } else {
            left.addElement(new Paragraph("Firm Name", titleFont));
        }

        // Firm meta
        if (firm != null) {
            if (firm.getOwnerName() != null && !firm.getOwnerName().isBlank())
                left.addElement(new Paragraph("Owner: " + firm.getOwnerName(), normal));

            StringBuilder addr = new StringBuilder();
            if (firm.getAddressLine1() != null) addr.append(firm.getAddressLine1());
            if (firm.getAddressLine2() != null && !firm.getAddressLine2().isBlank()) {
                if (addr.length() > 0) addr.append(", ");
                addr.append(firm.getAddressLine2());
            }
            if (addr.length() > 0) left.addElement(new Paragraph(addr.toString(), normal));

            StringBuilder cityLine = new StringBuilder();
            if (firm.getCity() != null) cityLine.append(firm.getCity());
            if (firm.getState() != null && !firm.getState().isBlank()) {
                if (!cityLine.toString().isEmpty()) cityLine.append(" - ");
                cityLine.append(firm.getState());
            }
            if (firm.getPincode() != null && !firm.getPincode().isBlank()) {
                if (!cityLine.toString().isEmpty()) cityLine.append(" ");
                cityLine.append(firm.getPincode());
            }
            if (!cityLine.toString().isEmpty()) left.addElement(new Paragraph(cityLine.toString(), normal));

            if (firm.getPhone() != null && !firm.getPhone().isBlank())
                left.addElement(new Paragraph("Phone: " + firm.getPhone(), normal));
            if (firm.getEmail() != null && !firm.getEmail().isBlank())
                left.addElement(new Paragraph("Email: " + firm.getEmail(), normal));
            if (firm.getGstin() != null && !firm.getGstin().isBlank())
                left.addElement(new Paragraph("GSTIN: " + firm.getGstin(), normal));
        }

        header.addCell(left);

        // Right cell: invoice meta
        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);

        Paragraph invTitle = new Paragraph("TAX INVOICE", titleFont);
        invTitle.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(invTitle);

        right.addElement(new Paragraph("Invoice: " + (invoice.getInvoiceNumber() == null ? "-" : invoice.getInvoiceNumber()), bold));
        if (invoice.getInvoiceDate() != null) {
            right.addElement(new Paragraph("Date: " + invoice.getInvoiceDate().format(DATE_FMT), normal));
        } else {
            right.addElement(new Paragraph("Date: -", normal));
        }
        if (invoice.getId() != null) right.addElement(new Paragraph("Invoice ID: " + invoice.getId(), small));

        header.addCell(right);

        doc.add(header);
        doc.add(new Paragraph("\n"));

        // ---------- Customer block ----------
        PdfPTable cust = new PdfPTable(new float[]{1f, 1f});
        cust.setWidthPercentage(100);
        cust.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        PdfPCell cLeft = new PdfPCell();
        cLeft.setBorder(Rectangle.NO_BORDER);
        cLeft.addElement(new Paragraph("Bill To:", hFont));

        if (invoice.getCustomer() != null) {
            if (invoice.getCustomer().getName() != null)
                cLeft.addElement(new Paragraph(invoice.getCustomer().getName(), normal));
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
        cust.addCell(cRight);

        doc.add(cust);
        doc.add(new Paragraph("\n"));

        // ---------- Items table ----------
        // Columns: #, Description, HSN/SAC, Qty, Unit, Rate, Amount (base), GST%, Total
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

        List<InvoiceItem> items = invoice.getItems();
        if (items != null && !items.isEmpty()) {
            int idx = 1;
            for (InvoiceItem it : items) {
                String desc = "-";
                try {
                    if (it.getProduct() != null && it.getProduct().getName() != null)
                        desc = it.getProduct().getName();
                    else if (it.getUnit() != null)
                        desc = it.getUnit();
                } catch (Exception e) {
                    desc = "-";
                }

                table.addCell(new PdfPCell(new Phrase(String.valueOf(idx++), normal)));
                table.addCell(new PdfPCell(new Phrase(desc, normal)));

                String hsn = "-";
                table.addCell(new PdfPCell(new Phrase(hsn, normal)));

                table.addCell(new PdfPCell(new Phrase(String.valueOf(it.getQty() == null ? 0 : it.getQty()), normal)));
                table.addCell(new PdfPCell(new Phrase(it.getUnit() == null ? "-" : it.getUnit(), normal)));
                table.addCell(new PdfPCell(new Phrase(formatAmount(it.getPricePerUnit()), normal)));

                // Amount (base amount = qty * pricePerUnit, but we display stored amountWithoutTax)
                table.addCell(new PdfPCell(new Phrase(formatAmount(it.getAmountWithoutTax()), normal)));

                table.addCell(new PdfPCell(new Phrase(formatPercent(it.getGstPercent()), normal)));

                // Line total (taxable after invoice-level distribution + gst)
                table.addCell(new PdfPCell(new Phrase(formatAmount(it.getLineTotal()), normal)));
            }
        } else {
            PdfPCell empty = new PdfPCell(new Phrase("No items", normal));
            empty.setColspan(heads.length);
            empty.setPadding(10);
            table.addCell(empty);
        }

        doc.add(table);

        // ---------- Totals / Discounts (Option A format) ----------
        doc.add(new Paragraph("\n"));

        // Compute raw subtotal and product (item) discounts from item fields
        double rawSubtotal = 0.0;
        double productDiscount = 0.0;

        if (items != null && !items.isEmpty()) {
            for (InvoiceItem it : items) {
                double qty = it.getQty() != null ? it.getQty() : 0.0;
                double rate = it.getPricePerUnit() != null ? it.getPricePerUnit() : 0.0;
                double base = qty * rate;
                rawSubtotal += base;

                double idisc = 0.0;
                if (it.getDiscountPercent() != null && it.getDiscountPercent() > 0) {
                    idisc = base * (it.getDiscountPercent() / 100.0);
                } else if (it.getDiscountValue() != null) {
                    idisc = it.getDiscountValue();
                }
                productDiscount += idisc;
            }
        }

        // Total tax (already computed & stored by InvoiceService after invoice-level distribution)
        double totalTax = invoice.getTotalTax() != null ? invoice.getTotalTax() : 0.0;

        // totalDiscount stored on invoice = productDiscount + invoiceLevelDiscount (per InvoiceService)
        double storedTotalDiscount = invoice.getTotalDiscount() != null ? invoice.getTotalDiscount() : 0.0;
        double additionalDiscount = storedTotalDiscount - productDiscount;
        if (additionalDiscount < 0) additionalDiscount = 0.0; // safety clamp

        // Taxable value before invoice-level discount (i.e. after product discounts)
        double taxableBeforeInvoiceDiscount = rawSubtotal - productDiscount;
        if (taxableBeforeInvoiceDiscount < 0) taxableBeforeInvoiceDiscount = 0.0;

        double grand = invoice.getTotalAmount() != null ? invoice.getTotalAmount() : (taxableBeforeInvoiceDiscount + totalTax - additionalDiscount);

        // Totals table (right-aligned)
        PdfPTable totalsTbl = new PdfPTable(new float[] { 3f, 1f });
        totalsTbl.setWidthPercentage(50);
        totalsTbl.setHorizontalAlignment(Element.ALIGN_RIGHT);

        addTotalsRow(totalsTbl, "Subtotal (Before Discount)", formatAmount(rawSubtotal), normal, hFont);
        addTotalsRow(totalsTbl, "Product Discount", "- " + formatAmount(productDiscount), normal, hFont);
        addTotalsRow(totalsTbl, "Taxable Value", formatAmount(taxableBeforeInvoiceDiscount), normal, hFont);
        addTotalsRow(totalsTbl, "Total Tax", formatAmount(totalTax), normal, hFont);
        addTotalsRow(totalsTbl, "Additional Discount", "- " + formatAmount(additionalDiscount), normal, hFont);
        addTotalsRow(totalsTbl, "Grand Total", formatAmount(grand), normal, bold);

        doc.add(totalsTbl);

        doc.add(new Paragraph("\n"));

        // Bank details (if available)
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

        // Footer note
        if (firm != null && firm.getFooterNote() != null && !firm.getFooterNote().isBlank()) {
            Paragraph foot = new Paragraph(firm.getFooterNote(), small);
            foot.setAlignment(Element.ALIGN_CENTER);
            doc.add(foot);
            doc.add(new Paragraph("\n"));
        }

        // Signature placeholders
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
        return baos.toByteArray();
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

    private static String formatAmount(Double d) {
        if (d == null) return "0.00";
        return String.format("%.2f", d);
    }

    private static String formatPercent(Double p) {
        if (p == null) return "0.00";
        return String.format("%.2f", p);
    }
}
