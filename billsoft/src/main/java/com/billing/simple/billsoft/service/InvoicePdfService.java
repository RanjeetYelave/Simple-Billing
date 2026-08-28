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
import com.lowagie.text.Chunk;
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
import com.lowagie.text.pdf.draw.LineSeparator;

/**
 * Produces a professional PDF for Invoice and Estimate using BigDecimal-safe computations.
 * Supports A4 / A5, handles logo base64 (raw or data: URL), and distinctly marks Invoices vs Quotations.
 */
@Service
public class InvoicePdfService {

    private final FirmDetailsService firmService;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter SHORT_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final int SCALE = 2;
    
    // Brand Colors
    private static final Color PRIMARY_COLOR = new Color(44, 62, 80); // Slate Gray
    private static final Color LIGHT_BG = new Color(248, 250, 252);
    private static final Color BORDER_COLOR = new Color(226, 232, 240);
    private static final Color TEXT_MUTED = new Color(100, 116, 139);
    private static final Color TEXT_DARK = new Color(15, 23, 42);

    public InvoicePdfService(FirmDetailsService firmService) {
        this.firmService = firmService;
    }

    @SuppressWarnings("deprecation")
    public byte[] generatePdf(Invoice invoice, String size) throws Exception {

        Rectangle pageSize = PageSize.A4;
        boolean isA5 = false;
        if (size != null && size.equalsIgnoreCase("A5")) {
            pageSize = PageSize.A5;
            isA5 = true;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // Adjust margins based on page size
        float margin = isA5 ? 24 : 36;
        Document doc = new Document(pageSize, margin, margin, margin + 12, margin + 12);
        PdfWriter.getInstance(doc, baos);

        doc.open();

        // Fonts scaled for A4 / A5
        Font titleFont = new Font(Font.HELVETICA, isA5 ? 18 : 24, Font.BOLD, PRIMARY_COLOR);
        Font firmNameFont = new Font(Font.HELVETICA, isA5 ? 14 : 16, Font.BOLD, TEXT_DARK);
        Font hFont = new Font(Font.HELVETICA, isA5 ? 9 : 10, Font.BOLD, TEXT_DARK);
        Font normal = new Font(Font.HELVETICA, isA5 ? 8 : 9, Font.NORMAL, TEXT_DARK);
        Font bold = new Font(Font.HELVETICA, isA5 ? 8 : 9, Font.BOLD, TEXT_DARK);
        Font small = new Font(Font.HELVETICA, isA5 ? 7 : 8, Font.NORMAL, TEXT_MUTED);
        Font smallBold = new Font(Font.HELVETICA, isA5 ? 7 : 8, Font.BOLD, TEXT_MUTED);
        
        // Font for table headers
        Font thFont = new Font(Font.HELVETICA, isA5 ? 8 : 9, Font.BOLD, Color.WHITE);

        FirmDetails firm = null;
        try {
            Long firmId = invoice != null ? invoice.getFirmId() : null;
            firm = firmId != null ? firmService.get(firmId) : firmService.getFirst();
        } catch (Exception e) { firm = null; }

        // ---------------- HEADER ----------------
        PdfPTable header = new PdfPTable(new float[]{2.5f, 1.5f});
        header.setWidthPercentage(100);
        
        // LEFT: Logo & Firm Details
        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);

        if (firm != null && firm.getLogoBase64() != null && !firm.getLogoBase64().isBlank()) {
            try {
                String b64 = firm.getLogoBase64().trim();
                if (b64.startsWith("data:")) {
                    int idx = b64.indexOf("base64,");
                    if (idx >= 0) b64 = b64.substring(idx + 7);
                }
                byte[] bytes = Base64.getDecoder().decode(b64);
                Image logo = Image.getInstance(bytes);
                float maxW = isA5 ? 90f : 120f;
                float maxH = isA5 ? 50f : 70f;
                logo.scaleToFit(maxW, maxH);
                logo.setAlignment(Image.LEFT);
                left.addElement(logo);
                left.addElement(new Paragraph(" ")); // spacing
            } catch (Exception ex) {}
        }

        left.addElement(new Paragraph(firm != null && firm.getFirmName() != null && !firm.getFirmName().isBlank()
                ? firm.getFirmName().toUpperCase() : "FIRM NAME", firmNameFont));

        if (firm != null) {
            StringBuilder addr = new StringBuilder();
            if (firm.getAddressLine1() != null && !firm.getAddressLine1().isBlank()) addr.append(firm.getAddressLine1());
            if (firm.getAddressLine2() != null && !firm.getAddressLine2().isBlank()) {
                if (addr.length() > 0) addr.append(", ");
                addr.append(firm.getAddressLine2());
            }
            if (addr.length() > 0) left.addElement(new Paragraph(addr.toString(), small));

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
            if (cityLine.length() > 0) left.addElement(new Paragraph(cityLine.toString(), small));

            if (firm.getPhone() != null && !firm.getPhone().isBlank())
                left.addElement(new Paragraph("Phone: " + firm.getPhone(), small));
            if (firm.getEmail() != null && !firm.getEmail().isBlank())
                left.addElement(new Paragraph("Email: " + firm.getEmail(), small));
            if (firm.getGstin() != null && !firm.getGstin().isBlank())
                left.addElement(new Paragraph("GSTIN: " + firm.getGstin(), smallBold));
        }
        header.addCell(left);

        // RIGHT: Document Title & Meta
        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);

        boolean isEstimate = invoice != null && invoice.getStatus() != null && invoice.getStatus().name().equalsIgnoreCase("ESTIMATE");
        String title = isEstimate ? "ESTIMATE" : "TAX INVOICE";
        
        Paragraph titleP = new Paragraph(title, titleFont);
        titleP.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(titleP);
        
        right.addElement(new Paragraph(" ", small)); // spacing

        // Meta details grid in right header
        PdfPTable metaTable = new PdfPTable(new float[]{1f, 1.5f});
        metaTable.setWidthPercentage(100);
        
        String numLabel = isEstimate ? "Quotation #:" : "Invoice #:";
        String numValue = isEstimate ? (invoice.getEstimateNumber() == null ? "-" : invoice.getEstimateNumber())
                                     : (invoice.getInvoiceNumber() == null ? "-" : invoice.getInvoiceNumber());
        
        addMetaRow(metaTable, numLabel, numValue, small, bold);
        addMetaRow(metaTable, "Date:", invoice.getInvoiceDate() != null ? invoice.getInvoiceDate().format(DATE_FMT) : "-", small, normal);
        
        if (invoice.getDueDate() != null) {
            addMetaRow(metaTable, "Due Date:", invoice.getDueDate().format(SHORT_DATE_FMT), small, normal);
        }
        
        if (!isEstimate && invoice.getStatus() != null) {
            String statusStr = invoice.getStatus().name();
            if (statusStr.equals("PAID") || statusStr.equals("OVERDUE") || statusStr.equals("CANCELLED")) {
                addMetaRow(metaTable, "Status:", statusStr, small, bold);
            }
        }

        right.addElement(metaTable);
        header.addCell(right);
        doc.add(header);

        // Separator
        doc.add(new Paragraph(" "));
        LineSeparator ls = new LineSeparator(1f, 100, PRIMARY_COLOR, Element.ALIGN_CENTER, -2f);
        doc.add(new Chunk(ls));
        doc.add(new Paragraph(" "));

        // ---------- CUSTOMER BLOCK ----------
        PdfPTable custBlock = new PdfPTable(new float[]{1.2f, 1f});
        custBlock.setWidthPercentage(100);

        // Bill To (Left)
        PdfPCell billToCell = new PdfPCell();
        billToCell.setBorder(Rectangle.NO_BORDER);
        billToCell.setBackgroundColor(LIGHT_BG);
        billToCell.setPadding(12);
        
        billToCell.addElement(new Paragraph("BILL TO:", smallBold));
        if (invoice.getCustomer() != null) {
            billToCell.addElement(new Paragraph(invoice.getCustomer().getName() != null ? invoice.getCustomer().getName() : "-", hFont));
            if (invoice.getCustomer().getPhone() != null && !invoice.getCustomer().getPhone().isBlank())
                billToCell.addElement(new Paragraph("Phone: " + invoice.getCustomer().getPhone(), normal));
            if (invoice.getCustomer().getEmail() != null && !invoice.getCustomer().getEmail().isBlank())
                billToCell.addElement(new Paragraph("Email: " + invoice.getCustomer().getEmail(), normal));
            if (invoice.getCustomer().getAddress() != null && !invoice.getCustomer().getAddress().isBlank())
                billToCell.addElement(new Paragraph(invoice.getCustomer().getAddress(), normal));
            if (invoice.getCustomer().getGstin() != null && !invoice.getCustomer().getGstin().isBlank())
                billToCell.addElement(new Paragraph("GSTIN: " + invoice.getCustomer().getGstin(), bold));
        } else {
            billToCell.addElement(new Paragraph("-", normal));
        }
        custBlock.addCell(billToCell);

        // Additional Info (Right)
        PdfPCell addInfoCell = new PdfPCell();
        addInfoCell.setBorder(Rectangle.NO_BORDER);
        addInfoCell.setBackgroundColor(LIGHT_BG);
        addInfoCell.setPadding(12);
        
        addInfoCell.addElement(new Paragraph("ADDITIONAL DETAILS:", smallBold));
        
        PdfPTable detailsTable = new PdfPTable(new float[]{1f, 1.5f});
        detailsTable.setWidthPercentage(100);
        detailsTable.setSpacingBefore(4);
        
        if (invoice.getPaymentMethod() != null && !invoice.getPaymentMethod().isBlank())
            addMetaRow(detailsTable, "Payment:", invoice.getPaymentMethod(), small, normal);
        if (invoice.getCurrency() != null) 
            addMetaRow(detailsTable, "Currency:", invoice.getCurrency(), small, normal);
        if (invoice.getTags() != null && !invoice.getTags().isBlank())
            addMetaRow(detailsTable, "Tags:", invoice.getTags(), small, normal);
            
        addInfoCell.addElement(detailsTable);
        
        // Add a white gap between cells
        PdfPTable wrapper = new PdfPTable(new float[]{1f, 0.05f, 1f});
        wrapper.setWidthPercentage(100);
        wrapper.getDefaultCell().setBorder(Rectangle.NO_BORDER);
        wrapper.addCell(billToCell);
        wrapper.addCell(new PdfPCell(new Phrase(" ", normal)) {{ setBorder(Rectangle.NO_BORDER); }});
        wrapper.addCell(addInfoCell);
        
        doc.add(wrapper);
        doc.add(new Paragraph(" "));
        doc.add(new Paragraph(" "));

        // ---------- ITEMS TABLE ----------
        float[] widths = new float[]{0.5f, 2.5f, 0.8f, 0.8f, 1f, 1f, 1f, 1f};
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);
        table.setHeaderRows(1);

        String[] heads = {"#", "Item Description", "HSN", "Qty", "Rate", "Discount", "Taxable", "Amount"};
        for (int i = 0; i < heads.length; i++) {
            PdfPCell cell = new PdfPCell(new Phrase(heads[i], thFont));
            cell.setBackgroundColor(PRIMARY_COLOR);
            cell.setBorderColor(PRIMARY_COLOR);
            cell.setHorizontalAlignment(i >= 3 ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            cell.setPadding(isA5 ? 6 : 8);
            cell.setPaddingBottom(isA5 ? 8 : 10);
            table.addCell(cell);
        }

        List<InvoiceItem> items = invoice.getItems();
        boolean alternate = false;
        
        if (items != null && !items.isEmpty()) {
            int idx = 1;
            for (InvoiceItem it : items) {
                String desc = "-";
                try {
                    if (it.getProduct() != null && it.getProduct().getName() != null) desc = it.getProduct().getName();
                    else if (it.getUnit() != null) desc = "Custom Item";
                } catch (Exception e) { desc = "-"; }

                String disc = "-";
                if (it.getDiscountPercent() != null) disc = formatPercent(it.getDiscountPercent()) + "%";
                else if (it.getDiscountValue() != null) disc = formatAmount(it.getDiscountValue());

                // Cells
                table.addCell(createItemCell(String.valueOf(idx++), normal, alternate, Element.ALIGN_LEFT));
                table.addCell(createItemCell(desc, normal, alternate, Element.ALIGN_LEFT));
                table.addCell(createItemCell("-", normal, alternate, Element.ALIGN_LEFT));
                table.addCell(createItemCell(String.valueOf(it.getQty() == null ? 0 : it.getQty()) + (it.getUnit() != null ? " " + it.getUnit() : ""), normal, alternate, Element.ALIGN_RIGHT));
                table.addCell(createItemCell(formatAmount(it.getPricePerUnit()), normal, alternate, Element.ALIGN_RIGHT));
                table.addCell(createItemCell(disc, normal, alternate, Element.ALIGN_RIGHT));
                table.addCell(createItemCell(formatAmount(it.getTaxableAmount()), normal, alternate, Element.ALIGN_RIGHT));
                table.addCell(createItemCell(formatAmount(it.getLineTotal()), bold, alternate, Element.ALIGN_RIGHT));
                
                alternate = !alternate;
            }
        } else {
            PdfPCell empty = new PdfPCell(new Phrase("No items found", normal));
            empty.setColspan(heads.length);
            empty.setPadding(15);
            empty.setHorizontalAlignment(Element.ALIGN_CENTER);
            empty.setBorderColor(BORDER_COLOR);
            table.addCell(empty);
        }

        doc.add(table);
        doc.add(new Paragraph(" "));

        // ---------------- TOTALS / SUMMARY ----------------
        BigDecimal rawSubtotal = BigDecimal.ZERO.setScale(SCALE);
        BigDecimal productDiscount = BigDecimal.ZERO.setScale(SCALE);
        BigDecimal taxableBeforeInvoiceDiscount = BigDecimal.ZERO.setScale(SCALE);
        BigDecimal totalTax = nz(invoice.getTotalTax());
        BigDecimal storedTotalDiscount = nz(invoice.getTotalDiscount()); 

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

        BigDecimal invoiceLevelDiscount = storedTotalDiscount.subtract(productDiscount);
        if (invoiceLevelDiscount.compareTo(BigDecimal.ZERO) < 0) invoiceLevelDiscount = BigDecimal.ZERO.setScale(SCALE);
        BigDecimal grand = nz(invoice.getTotalAmount());
        BigDecimal roundOff = nz(invoice.getRoundOff());

        PdfPTable bottomBlock = new PdfPTable(new float[]{1.2f, 1f});
        bottomBlock.setWidthPercentage(100);
        
        // Bottom Left: Bank Details
        PdfPCell bLeft = new PdfPCell();
        bLeft.setBorder(Rectangle.NO_BORDER);
        
        if (firm != null && ((firm.getBankName() != null && !firm.getBankName().isBlank())
                || (firm.getBankAccount() != null && !firm.getBankAccount().isBlank()))) {
            
            PdfPTable bank = new PdfPTable(1);
            bank.setWidthPercentage(100);
            
            PdfPCell bh = new PdfPCell(new Phrase("BANK DETAILS", smallBold));
            bh.setBorder(Rectangle.NO_BORDER);
            bh.setPaddingTop(10);
            bank.addCell(bh);

            StringBuilder bsb = new StringBuilder();
            if (firm.getBankName() != null && !firm.getBankName().isBlank())
                bsb.append("Bank: ").append(firm.getBankName()).append("\n");
            if (firm.getBankAccount() != null && !firm.getBankAccount().isBlank())
                bsb.append("Account No: ").append(firm.getBankAccount()).append("\n");
            if (firm.getBankIfsc() != null && !firm.getBankIfsc().isBlank())
                bsb.append("IFSC: ").append(firm.getBankIfsc()).append("\n");

            PdfPCell bcell = new PdfPCell(new Phrase(bsb.toString().trim(), normal));
            bcell.setBorder(Rectangle.NO_BORDER);
            bank.addCell(bcell);
            
            bLeft.addElement(bank);
        }
        bottomBlock.addCell(bLeft);

        // Bottom Right: Totals
        PdfPTable totalsTbl = new PdfPTable(new float[] {1f, 1f});
        totalsTbl.setWidthPercentage(100);

        addTotalsRow(totalsTbl, "Subtotal", formatAmount(rawSubtotal), normal, small);
        if (productDiscount.compareTo(BigDecimal.ZERO) > 0)
            addTotalsRow(totalsTbl, "Product Discount", "- " + formatAmount(productDiscount), normal, small);
        if (invoiceLevelDiscount.compareTo(BigDecimal.ZERO) > 0)
            addTotalsRow(totalsTbl, "Invoice Discount", "- " + formatAmount(invoiceLevelDiscount), normal, small);
            
        addTotalsRow(totalsTbl, "Taxable Value", formatAmount(taxableBeforeInvoiceDiscount), normal, small);
        addTotalsRow(totalsTbl, "Total GST", formatAmount(totalTax), normal, small);
        
        if (roundOff.compareTo(BigDecimal.ZERO) != 0)
            addTotalsRow(totalsTbl, "Round Off", formatAmount(roundOff), normal, small);

        // Grand Total Box
        PdfPCell gtLabel = new PdfPCell(new Phrase("Grand Total", hFont));
        gtLabel.setBorder(Rectangle.TOP);
        gtLabel.setBorderColor(PRIMARY_COLOR);
        gtLabel.setBorderWidthTop(2f);
        gtLabel.setHorizontalAlignment(Element.ALIGN_LEFT);
        gtLabel.setPaddingTop(10);
        gtLabel.setPaddingBottom(10);
        totalsTbl.addCell(gtLabel);

        PdfPCell gtValue = new PdfPCell(new Phrase((invoice.getCurrency() != null ? invoice.getCurrency() + " " : "") + formatAmount(grand), hFont));
        gtValue.setBorder(Rectangle.TOP);
        gtValue.setBorderColor(PRIMARY_COLOR);
        gtValue.setBorderWidthTop(2f);
        gtValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
        gtValue.setPaddingTop(10);
        gtValue.setPaddingBottom(10);
        totalsTbl.addCell(gtValue);
        
        PdfPCell bRight = new PdfPCell(totalsTbl);
        bRight.setBorder(Rectangle.NO_BORDER);
        bottomBlock.addCell(bRight);
        
        doc.add(bottomBlock);
        doc.add(new Paragraph("\n"));

        // ---------------- FOOTER / SIGNATURE ----------------
        PdfPTable footer = new PdfPTable(new float[] {1f, 1f});
        footer.setWidthPercentage(100);
        
        // Terms & Conditions / Notes
        PdfPCell tncCell = new PdfPCell();
        tncCell.setBorder(Rectangle.NO_BORDER);
        
        if (invoice.getTermsAndConditions() != null && !invoice.getTermsAndConditions().isBlank()) {
            tncCell.addElement(new Paragraph("Terms & Conditions:", smallBold));
            tncCell.addElement(new Paragraph(invoice.getTermsAndConditions(), small));
        } else if (firm != null && firm.getFooterNote() != null && !firm.getFooterNote().isBlank()) {
            tncCell.addElement(new Paragraph("Notes:", smallBold));
            tncCell.addElement(new Paragraph(firm.getFooterNote(), small));
        }
        footer.addCell(tncCell);
        
        // Signature
        PdfPCell sigCell = new PdfPCell();
        sigCell.setBorder(Rectangle.NO_BORDER);
        sigCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        
        Paragraph authFor = new Paragraph("For " + (firm != null && firm.getFirmName() != null ? firm.getFirmName() : "________________"), bold);
        authFor.setAlignment(Element.ALIGN_RIGHT);
        sigCell.addElement(authFor);
        
        Paragraph authSig = new Paragraph("\n\n\nAuthorized Signatory", normal);
        authSig.setAlignment(Element.ALIGN_RIGHT);
        sigCell.addElement(authSig);
        
        footer.addCell(sigCell);
        
        doc.add(footer);

        doc.close();
        return baos.toByteArray();
    }
    
    private PdfPCell createItemCell(String text, Font font, boolean alternate, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(6);
        cell.setBorderColor(BORDER_COLOR);
        cell.setBorderWidthLeft(0);
        cell.setBorderWidthRight(0);
        if (alternate) cell.setBackgroundColor(LIGHT_BG);
        return cell;
    }

    private static void addMetaRow(PdfPTable tbl, String label, String value, Font labelFont, Font vFont) {
        PdfPCell l = new PdfPCell(new Phrase(label, labelFont));
        l.setBorder(Rectangle.NO_BORDER);
        l.setHorizontalAlignment(Element.ALIGN_RIGHT);
        l.setPaddingBottom(4);
        tbl.addCell(l);

        PdfPCell v = new PdfPCell(new Phrase(value, vFont));
        v.setBorder(Rectangle.NO_BORDER);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        v.setPaddingBottom(4);
        tbl.addCell(v);
    }

    private static void addTotalsRow(PdfPTable tbl, String label, String value, Font vFont, Font labelFont) {
        PdfPCell l = new PdfPCell(new Phrase(label, labelFont));
        l.setBorder(Rectangle.NO_BORDER);
        l.setHorizontalAlignment(Element.ALIGN_LEFT);
        l.setPaddingBottom(6);
        tbl.addCell(l);

        PdfPCell v = new PdfPCell(new Phrase(value, vFont));
        v.setBorder(Rectangle.NO_BORDER);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        v.setPaddingBottom(6);
        tbl.addCell(v);
    }

    // Formatting helpers
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
