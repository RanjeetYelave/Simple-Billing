package com.billing.simple.billsoft.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;

import org.springframework.stereotype.Service;

import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoiceItem;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

@Service
public class InvoicePdfService {

    public byte[] generatePdf(Invoice invoice) throws Exception {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
        PdfWriter.getInstance(doc, baos);

        doc.open();

        // ------------------------- HEADER -------------------------
        Font title = new Font(Font.HELVETICA, 20, Font.BOLD);
        doc.add(new Paragraph("INVOICE " + invoice.getInvoiceNumber(), title));
        doc.add(new Paragraph("\n"));

        // ------------------------- CUSTOMER -------------------------
        Font h = new Font(Font.HELVETICA, 12, Font.BOLD);

        doc.add(new Paragraph("Customer Details", h));
        doc.add(new Paragraph(invoice.getCustomer().getName()));
        if (invoice.getCustomer().getPhone() != null)
            doc.add(new Paragraph("Phone: " + invoice.getCustomer().getPhone()));
        if (invoice.getCustomer().getEmail() != null)
            doc.add(new Paragraph("Email: " + invoice.getCustomer().getEmail()));
        if (invoice.getCustomer().getAddress() != null)
            doc.add(new Paragraph("Address: " + invoice.getCustomer().getAddress()));

        doc.add(new Paragraph("\nDate: " + invoice.getInvoiceDate()));
        doc.add(new Paragraph("\n"));

        // ------------------------- ITEMS TABLE -------------------------
        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);

        String[] heads = {"Product", "Qty", "Unit", "Price", "GST%", "Total"};
        for (String head : heads) {
            PdfPCell cell = new PdfPCell(new Phrase(head, h));
            cell.setBackgroundColor(Color.LIGHT_GRAY);
            table.addCell(cell);
        }

        for (InvoiceItem it : invoice.getItems()) {
            table.addCell(it.getProduct().getName());
            table.addCell(String.valueOf(it.getQty()));
            table.addCell(it.getUnit() != null ? it.getUnit() : "-");
            table.addCell(String.valueOf(it.getPricePerUnit()));
            table.addCell(String.valueOf(it.getGstPercent()));
            table.addCell(String.valueOf(it.getLineTotal()));
        }

        doc.add(table);

        // ------------------------- TOTALS -------------------------
        doc.add(new Paragraph("\n"));
        Font big = new Font(Font.HELVETICA, 14, Font.BOLD);
        doc.add(new Paragraph("Grand Total: ₹ " + invoice.getTotalAmount(), big));

        // ------------------------- NOTES -------------------------
        if (invoice.getNotes() != null && !invoice.getNotes().isBlank()) {
            doc.add(new Paragraph("\nNotes:\n" + invoice.getNotes()));
        }

        doc.close();
        return baos.toByteArray();
    }
}
