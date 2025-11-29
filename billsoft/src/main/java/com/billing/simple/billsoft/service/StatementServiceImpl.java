package com.billing.simple.billsoft.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.billing.simple.billsoft.dtos.CustomerStatementResponse;
import com.billing.simple.billsoft.dtos.FirmStatementResponse;
import com.billing.simple.billsoft.dtos.GstSummaryItem;
import com.billing.simple.billsoft.dtos.StatementEntry;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoiceItem;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;
import com.billing.simple.billsoft.repo.InvoiceRepository;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

@Service
public class StatementServiceImpl implements StatementService {

    private final InvoiceRepository invoiceRepo;
    private final CustomerRepository customerRepo;
    private final FirmDetailsRepository firmRepo;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public StatementServiceImpl(
            InvoiceRepository invoiceRepo,
            CustomerRepository customerRepo,
            FirmDetailsRepository firmRepo
    ) {
        this.invoiceRepo = invoiceRepo;
        this.customerRepo = customerRepo;
        this.firmRepo = firmRepo;
    }

    /* ============================================================
         CUSTOMER STATEMENT – JSON
    ============================================================ */
    @Override
    public CustomerStatementResponse getCustomerStatement(Long customerId, LocalDate from, LocalDate to) {
        Customer customer = customerRepo.findById(customerId).orElse(null);
        if (customer == null) throw new RuntimeException("Customer not found: " + customerId);

        LocalDate toDate = (to == null) ? LocalDate.now() : to;
        LocalDate fromDate = (from == null) ? LocalDate.of(1970, 1, 1) : from;

        // use repository method that returns invoices for customer (ensure repo has findByCustomer_Id)
        List<Invoice> all = invoiceRepo.findByCustomer_Id(customerId);

        // Opening balance calculation (invoices before fromDate)
        double billedBefore = all.stream()
                .filter(i -> i.getInvoiceDate() != null && toLocalDate(i.getInvoiceDate()).isBefore(fromDate))
                .mapToDouble(i -> i.getTotalAmount() == null ? 0.0 : i.getTotalAmount())
                .sum();

        double paidBefore = all.stream()
                .filter(i -> i.getInvoiceDate() != null && toLocalDate(i.getInvoiceDate()).isBefore(fromDate))
                .filter(i -> Boolean.TRUE.equals(i.getPaid()))
                .mapToDouble(i -> i.getTotalAmount() == null ? 0.0 : i.getTotalAmount())
                .sum();

        double openingBalance = billedBefore - paidBefore;

        // All invoices in period
        List<Invoice> inRange = all.stream()
                .filter(i -> i.getInvoiceDate() != null)
                .filter(i -> {
                    LocalDate d = toLocalDate(i.getInvoiceDate());
                    return ( !d.isBefore(fromDate) && !d.isAfter(toDate) );
                })
                .sorted(Comparator.comparing(i -> toLocalDate(i.getInvoiceDate())))
                .toList();

        List<StatementEntry> entries = new ArrayList<>();
        double balance = openingBalance;
        double totalBilled = 0;
        double totalPaid = 0;

        for (Invoice inv : inRange) {
            double amt = inv.getTotalAmount() == null ? 0.0 : inv.getTotalAmount();
            LocalDate invDate = toLocalDate(inv.getInvoiceDate());

            // Invoice entry
            StatementEntry invoiceEntry = new StatementEntry();
            invoiceEntry.setDate(invDate);
            invoiceEntry.setType("INVOICE");
            invoiceEntry.setRef(inv.getInvoiceNumber());
            invoiceEntry.setDescription("Invoice");
            invoiceEntry.setDebit(amt);
            invoiceEntry.setCredit(0.0);
            balance += amt;
            invoiceEntry.setBalance(balance);
            entries.add(invoiceEntry);
            totalBilled += amt;

            // Payment entry (if invoice marked paid)
            if (Boolean.TRUE.equals(inv.getPaid())) {
                StatementEntry pay = new StatementEntry();
                pay.setDate(invDate);
                pay.setType("PAYMENT");
                pay.setRef(inv.getInvoiceNumber());
                pay.setDescription("Invoice Paid");
                pay.setDebit(0.0);
                pay.setCredit(amt);
                balance -= amt;
                pay.setBalance(balance);
                entries.add(pay);
                totalPaid += amt;
            }
        }

        CustomerStatementResponse resp = new CustomerStatementResponse();
        resp.setCustomerId(customerId);
        resp.setCustomerName(customer.getName());
        resp.setFrom(fromDate);
        resp.setTo(toDate);
        resp.setOpeningBalance(openingBalance);
        resp.setTotalBilled(totalBilled);
        resp.setTotalPaid(totalPaid);
        resp.setClosingBalance(balance);
        resp.setEntries(entries);
        return resp;
    }

    /* ============================================================
         CUSTOMER STATEMENT – PDF
    ============================================================ */
    @Override
    public byte[] generateCustomerStatementPdf(Long customerId, LocalDate from, LocalDate to) throws Exception {
        CustomerStatementResponse data = getCustomerStatement(customerId, from, to);
        FirmDetails firm = firmRepo.findAll().stream().findFirst().orElse(null);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 48, 48);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        Font title = new Font(Font.HELVETICA, 16, Font.BOLD);
        Font bold = new Font(Font.HELVETICA, 10, Font.BOLD);
        Font normal = new Font(Font.HELVETICA, 10);

        Paragraph head = new Paragraph("Customer Statement", title);
        head.setAlignment(Element.ALIGN_CENTER);
        doc.add(head);
        doc.add(new Paragraph("\n"));

        if (firm != null) {
            doc.add(new Paragraph(firm.getFirmName(), bold));
            if (firm.getAddressLine1() != null) doc.add(new Paragraph(firm.getAddressLine1(), normal));
            if (firm.getCity() != null) doc.add(new Paragraph(firm.getCity(), normal));
            doc.add(new Paragraph("\n"));
        }

        doc.add(new Paragraph("Customer: " + data.getCustomerName(), bold));
        doc.add(new Paragraph("Period: " + data.getFrom().format(DATE_FMT) +
                " to " + data.getTo().format(DATE_FMT), normal));
        doc.add(new Paragraph("\n"));

        doc.add(new Paragraph("Opening Balance: " + amount(data.getOpeningBalance()), bold));
        doc.add(new Paragraph("\n"));

        float[] widths = {1.2f, 1.2f, 1.2f, 3f, 1.2f, 1.2f, 1.2f};
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);

        headerCell(table, "Date");
        headerCell(table, "Type");
        headerCell(table, "Ref");
        headerCell(table, "Description");
        headerCell(table, "Debit");
        headerCell(table, "Credit");
        headerCell(table, "Balance");

        for (StatementEntry e : data.getEntries()) {
            table.addCell(textCell(e.getDate() == null ? "-" : e.getDate().format(DATE_FMT)));
            table.addCell(textCell(e.getType()));
            table.addCell(textCell(e.getRef()));
            table.addCell(textCell(e.getDescription()));
            table.addCell(textCell(amount(e.getDebit())));
            table.addCell(textCell(amount(e.getCredit())));
            table.addCell(textCell(amount(e.getBalance())));
        }

        doc.add(table);
        doc.add(new Paragraph("\n"));

        doc.add(new Paragraph("Total Billed: " + amount(data.getTotalBilled()), bold));
        doc.add(new Paragraph("Total Paid: " + amount(data.getTotalPaid()), bold));
        doc.add(new Paragraph("Closing Balance: " + amount(data.getClosingBalance()), bold));

        doc.close();
        return baos.toByteArray();
    }

    /* ============================================================
         FIRM STATEMENT JSON
    ============================================================ */
    @Override
    public FirmStatementResponse getFirmStatement(LocalDate from, LocalDate to) {
        LocalDate toDate = (to == null) ? LocalDate.now() : to;
        LocalDate fromDate = (from == null) ? LocalDate.of(1970, 1, 1) : from;

        List<Invoice> all = invoiceRepo.findAll();

        List<Invoice> list = all.stream()
                .filter(i -> i.getInvoiceDate() != null)
                .filter(i -> {
                    LocalDate d = toLocalDate(i.getInvoiceDate());
                    return (!d.isBefore(fromDate) && !d.isAfter(toDate));
                })
                .sorted(Comparator.comparing(i -> toLocalDate(i.getInvoiceDate())))
                .toList();

        double totalBilled = list.stream().mapToDouble(i -> i.getTotalAmount() == null ? 0.0 : i.getTotalAmount()).sum();
        double totalPaid = list.stream().filter(i -> Boolean.TRUE.equals(i.getPaid()))
                .mapToDouble(i -> i.getTotalAmount() == null ? 0.0 : i.getTotalAmount()).sum();
        double outstanding = totalBilled - totalPaid;
        double totalTax = list.stream().mapToDouble(i -> i.getTotalTax() == null ? 0.0 : i.getTotalTax()).sum();

        Map<Double, GstSummaryItem> gstMap = new LinkedHashMap<>();

        for (Invoice inv : list) {
            List<InvoiceItem> items = inv.getItems() == null ? Collections.emptyList() : inv.getItems();
            for (InvoiceItem it : items) {
                double gst = it.getGstPercent() == null ? 0.0 : it.getGstPercent();
                double taxable = it.getAmountWithoutTax() == null ? 0.0 : it.getAmountWithoutTax();
                double gstAmt = (it.getLineTotal() == null ? 0.0 : it.getLineTotal()) - taxable;

                GstSummaryItem gs = gstMap.get(gst);
                if (gs == null) {
                    gs = new GstSummaryItem();
                    gs.setGstPercent(gst);
                    gs.setTaxableValue(taxable);
                    gs.setGstAmount(gstAmt);
                    gstMap.put(gst, gs);
                } else {
                    gs.setTaxableValue(gs.getTaxableValue() + taxable);
                    gs.setGstAmount(gs.getGstAmount() + gstAmt);
                }
            }
        }

        FirmStatementResponse resp = new FirmStatementResponse();
        resp.setFrom(fromDate);
        resp.setTo(toDate);
        resp.setTotalBilled(totalBilled);
        resp.setTotalPaid(totalPaid);
        resp.setOutstanding(outstanding);
        resp.setTotalTax(totalTax);
        resp.setInvoiceCount(list.size());
        resp.setGstSummary(new ArrayList<>(gstMap.values()));

        return resp;
    }

    /* ============================================================
         FIRM STATEMENT PDF
    ============================================================ */
    @Override
    public byte[] generateFirmStatementPdf(LocalDate from, LocalDate to) throws Exception {

        FirmStatementResponse data = getFirmStatement(from, to);
        FirmDetails firm = firmRepo.findAll().stream().findFirst().orElse(null);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36,36,48,48);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        Font title = new Font(Font.HELVETICA, 18, Font.BOLD);
        Font h1 = new Font(Font.HELVETICA, 12, Font.BOLD);
        Font bold = new Font(Font.HELVETICA, 10, Font.BOLD);
        Font normal = new Font(Font.HELVETICA, 10);

        /* -------------------------------------------
           HEADER BLOCK WITH FIRM DETAILS
        ------------------------------------------- */
        Paragraph top = new Paragraph("FIRM STATEMENT", title);
        top.setAlignment(Element.ALIGN_CENTER);
        doc.add(top);
        doc.add(new Paragraph("\n"));

        if (firm != null) {
            Paragraph fName = new Paragraph(firm.getFirmName(), h1);
            fName.setAlignment(Element.ALIGN_LEFT);
            doc.add(fName);

            if (firm.getAddressLine1() != null)
                doc.add(new Paragraph(firm.getAddressLine1(), normal));

            if (firm.getAddressLine2() != null)
                doc.add(new Paragraph(firm.getAddressLine2(), normal));

            if (firm.getCity() != null || firm.getPincode() != null)
                doc.add(new Paragraph(
                    (firm.getCity() != null ? firm.getCity() : "") + 
                    (firm.getPincode() != null ? " - " + firm.getPincode() : ""),
                    normal
                ));

            if (firm.getPhone() != null)
                doc.add(new Paragraph("Phone: " + firm.getPhone(), normal));

            if (firm.getEmail() != null)
                doc.add(new Paragraph("Email: " + firm.getEmail(), normal));

            if (firm.getGstin() != null)
                doc.add(new Paragraph("GSTIN: " + firm.getGstin(), normal));

            doc.add(new Paragraph("\n"));
        }

        doc.add(new Paragraph(
            "Period: " + data.getFrom().format(DATE_FMT) + " to " + data.getTo().format(DATE_FMT),
            bold
        ));
        doc.add(new Paragraph("\n"));

        /* -------------------------------------------
           SUMMARY BLOCK (Card style)
        ------------------------------------------- */
        PdfPTable summary = new PdfPTable(new float[] {2f,2f,2f,2f});
        summary.setWidthPercentage(100);

        summary.addCell(summaryCell("Total Business", amount(data.getTotalBilled())));
        summary.addCell(summaryCell("Paid Amount", amount(data.getTotalPaid())));
        summary.addCell(summaryCell("Outstanding", amount(data.getOutstanding())));
        summary.addCell(summaryCell("Total GST", amount(data.getTotalTax())));

        doc.add(summary);
        doc.add(new Paragraph("\n"));

        /* -------------------------------------------
           GST SUMMARY TABLE
        ------------------------------------------- */
        Paragraph gstTitle = new Paragraph("GST Summary", h1);
        gstTitle.setAlignment(Element.ALIGN_LEFT);
        doc.add(gstTitle);
        doc.add(new Paragraph("\n"));

        PdfPTable gst = new PdfPTable(new float[]{1f,2f,2f});
        gst.setWidthPercentage(100);

        headerCell(gst, "GST %");
        headerCell(gst, "Taxable Value");
        headerCell(gst, "GST Amount");

        for (GstSummaryItem g : data.getGstSummary()) {
            gst.addCell(textCell(g.getGstPercent() + "%"));
            gst.addCell(textCell(amount(g.getTaxableValue())));
            gst.addCell(textCell(amount(g.getGstAmount())));
        }

        doc.add(gst);
        doc.add(new Paragraph("\n\n"));

        /* -------------------------------------------
           FOOTER – SIGNATURE
        ------------------------------------------- */
        Paragraph sig = new Paragraph(
            "\n\nAuthorised Signatory\n" + 
            (firm != null ? firm.getFirmName() : ""),
            bold
        );
        sig.setAlignment(Element.ALIGN_RIGHT);

        doc.add(sig);

        doc.close();
        return baos.toByteArray();
    }


    /* ============================================================
         HELPERS — FINAL CLEANED VERSION
    ============================================================ */
    private void headerCell(PdfPTable t, String label) {
        PdfPCell c = new PdfPCell(new Phrase(label, new Font(Font.HELVETICA, 10, Font.BOLD)));
        c.setBackgroundColor(Color.LIGHT_GRAY);
        c.setPadding(6);
        t.addCell(c);
    }

    private PdfPCell textCell(String t) {
        PdfPCell c = new PdfPCell(new Phrase(t == null ? "-" : t, new Font(Font.HELVETICA, 9)));
        c.setPadding(6);
        return c;
    }

    private String amount(Double v) {
        return String.format("%.2f", v == null ? 0.0 : v);
    }

    /** Utility: convert various invoice date types to LocalDate.
     *  If invoiceDate is LocalDate, return it; if LocalDateTime, convert.
     *  If null, return epoch (1970-01-01) — but callers check for null already.
     */
    private LocalDate toLocalDate(Object invoiceDate) {
        if (invoiceDate == null) return LocalDate.of(1970,1,1);
        if (invoiceDate instanceof LocalDate) return (LocalDate) invoiceDate;
        if (invoiceDate instanceof LocalDateTime) return ((LocalDateTime) invoiceDate).toLocalDate();
        // fallback: try toString parse (unlikely)
        try {
            return LocalDate.parse(invoiceDate.toString());
        } catch (Exception ex) {
            return LocalDate.of(1970,1,1);
        }
    }
    private PdfPCell summaryCell(String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(10);

        Paragraph t = new Paragraph(label + "\n" + value, new Font(Font.HELVETICA, 10, Font.BOLD));
        cell.addElement(t);
        return cell;
    }

}
