package com.billing.simple.billsoft.service;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.billing.simple.billsoft.dtos.CustomerStatementResponse;
import com.billing.simple.billsoft.dtos.FirmStatementResponse;
import com.billing.simple.billsoft.dtos.GstSummaryItem;
import com.billing.simple.billsoft.dtos.StatementEntry;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoiceItem;
import com.billing.simple.billsoft.entities.InvoiceStatus;
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

    /* ============================================================================
        CUSTOMER STATEMENT (JSON)
    ============================================================================ */
    @Override
    public CustomerStatementResponse getCustomerStatement(Long customerId, LocalDate from, LocalDate to) {
        Customer customer = customerRepo.findById(customerId).orElse(null);
        if (customer == null) throw new RuntimeException("Customer not found: " + customerId);

        LocalDate toDate = (to == null) ? LocalDate.now() : to;
        LocalDate fromDate = (from == null) ? LocalDate.of(1970, 1, 1) : from;

        List<Invoice> all = invoiceRepo.findByCustomer_Id(customerId);

        // Only Invoices (skip estimates/drafts)
        List<Invoice> invoices = all.stream()
                .filter(i -> i.getInvoiceDate() != null)
                .filter(i -> i.getStatus() != InvoiceStatus.ESTIMATE)
                .filter(i -> i.getStatus() != InvoiceStatus.DRAFT)
                .collect(Collectors.toList());

        // Opening balance = Billed - Paid before period
        BigDecimal billedBefore = invoices.stream()
                .filter(i -> i.getInvoiceDate().toLocalDate().isBefore(fromDate))
                .map(i -> nz(i.getTotalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal paidBefore = invoices.stream()
                .filter(i -> i.getInvoiceDate().toLocalDate().isBefore(fromDate))
                .filter(i -> Boolean.TRUE.equals(i.getPaid()))
                .map(i -> nz(i.getTotalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal openingBalance = billedBefore.subtract(paidBefore);

        // Invoices inside range
        List<Invoice> inRange = invoices.stream()
                .filter(i -> {
                    LocalDate d = i.getInvoiceDate().toLocalDate();
                    return (!d.isBefore(fromDate) && !d.isAfter(toDate));
                })
                .sorted(Comparator.comparing(i -> i.getInvoiceDate().toLocalDate()))
                .collect(Collectors.toList());

        List<StatementEntry> entries = new ArrayList<>();
        BigDecimal balance = openingBalance;
        BigDecimal totalBilled = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;

        for (Invoice inv : inRange) {
            LocalDate invDate = inv.getInvoiceDate().toLocalDate();
            BigDecimal amt = nz(inv.getTotalAmount());

            // Invoice row
            StatementEntry invoiceEntry = new StatementEntry();
            invoiceEntry.setDate(invDate);
            invoiceEntry.setType("INVOICE");
            invoiceEntry.setRef(inv.getInvoiceNumber());
            invoiceEntry.setDescription("Invoice");
            invoiceEntry.setDebit(amt.doubleValue());
            invoiceEntry.setCredit(0.0);

            balance = balance.add(amt);
            invoiceEntry.setBalance(balance.doubleValue());
            entries.add(invoiceEntry);
            totalBilled = totalBilled.add(amt);

            // If invoice is paid → add payment row
            if (Boolean.TRUE.equals(inv.getPaid())) {
                StatementEntry pay = new StatementEntry();
                pay.setDate(invDate);
                pay.setType("PAYMENT");
                pay.setRef(inv.getInvoiceNumber());
                pay.setDescription("Invoice Paid");
                pay.setDebit(0.0);
                pay.setCredit(amt.doubleValue());

                balance = balance.subtract(amt);
                pay.setBalance(balance.doubleValue());
                entries.add(pay);
                totalPaid = totalPaid.add(amt);
            }
        }

        CustomerStatementResponse resp = new CustomerStatementResponse();
        resp.setCustomerId(customerId);
        resp.setCustomerName(customer.getName());
        resp.setFrom(fromDate);
        resp.setTo(toDate);
        resp.setOpeningBalance(openingBalance.doubleValue());
        resp.setTotalBilled(totalBilled.doubleValue());
        resp.setTotalPaid(totalPaid.doubleValue());
        resp.setClosingBalance(balance.doubleValue());
        resp.setEntries(entries);

        return resp;
    }

    /* ============================================================================
         CUSTOMER STATEMENT PDF
    ============================================================================ */
    @Override
    public byte[] generateCustomerStatementPdf(Long customerId, LocalDate from, LocalDate to) throws Exception {
        CustomerStatementResponse data = getCustomerStatement(customerId, from, to);
        FirmDetails firm = firmRepo.findAll().stream().findFirst().orElse(null);

        Document doc = new Document(PageSize.A4, 36, 36, 48, 48);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter.getInstance(doc, baos);
        doc.open();

        Font title = new Font(Font.HELVETICA, 16, Font.BOLD);
        Font bold = new Font(Font.HELVETICA, 10, Font.BOLD);
        Font normal = new Font(Font.HELVETICA, 10);

        // Title
        Paragraph head = new Paragraph("Customer Statement", title);
        head.setAlignment(Element.ALIGN_CENTER);
        doc.add(head);
        doc.add(new Paragraph("\n"));

        // Firm details
        if (firm != null) {
            doc.add(new Paragraph(firm.getFirmName(), bold));
            if (firm.getAddressLine1() != null) doc.add(new Paragraph(firm.getAddressLine1(), normal));
            if (firm.getCity() != null) doc.add(new Paragraph(firm.getCity(), normal));
            doc.add(new Paragraph("\n"));
        }

        doc.add(new Paragraph("Customer: " + data.getCustomerName(), bold));
        doc.add(new Paragraph("Period: " + data.getFrom().format(DATE_FMT)
                + " to " + data.getTo().format(DATE_FMT), normal));
        doc.add(new Paragraph("\n"));

        doc.add(new Paragraph("Opening Balance: " + fmt(data.getOpeningBalance()), bold));
        doc.add(new Paragraph("\n"));

        // Table
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
            table.addCell(textCell(e.getDate().format(DATE_FMT)));
            table.addCell(textCell(e.getType()));
            table.addCell(textCell(e.getRef()));
            table.addCell(textCell(e.getDescription()));
            table.addCell(textCell(fmt(e.getDebit())));
            table.addCell(textCell(fmt(e.getCredit())));
            table.addCell(textCell(fmt(e.getBalance())));
        }

        doc.add(table);
        doc.add(new Paragraph("\n"));

        // Totals
        doc.add(new Paragraph("Total Billed: " + fmt(data.getTotalBilled()), bold));
        doc.add(new Paragraph("Total Paid: " + fmt(data.getTotalPaid()), bold));
        doc.add(new Paragraph("Closing Balance: " + fmt(data.getClosingBalance()), bold));

        doc.close();
        return baos.toByteArray();
    }

    /* ============================================================================
         FIRM STATEMENT (JSON)
    ============================================================================ */
    @Override
    public FirmStatementResponse getFirmStatement(LocalDate from, LocalDate to) {

        LocalDate toDate = (to == null) ? LocalDate.now() : to;
        LocalDate fromDate = (from == null) ? LocalDate.of(1970, 1, 1) : from;

        List<Invoice> list = invoiceRepo.findAll().stream()
                .filter(i -> i.getInvoiceDate() != null)
                .filter(i -> {
                    LocalDate d = i.getInvoiceDate().toLocalDate();
                    return (!d.isBefore(fromDate) && !d.isAfter(toDate));
                })
                .collect(Collectors.toList());

        BigDecimal totalBilled = list.stream()
                .map(i -> nz(i.getTotalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPaid = list.stream()
                .filter(i -> Boolean.TRUE.equals(i.getPaid()))
                .map(i -> nz(i.getTotalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal outstanding = totalBilled.subtract(totalPaid);

        BigDecimal totalTax = list.stream()
                .map(i -> nz(i.getTotalTax()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // GST summary using stored taxableAmount + gstAmount
        Map<BigDecimal, GstSummaryItem> gstMap = new LinkedHashMap<>();

        for (Invoice inv : list) {
            for (InvoiceItem it : inv.getItems()) {
                BigDecimal gstPct = nz(it.getGstPercent());
                BigDecimal taxable = nz(it.getTaxableAmount());
                BigDecimal gstAmt = nz(it.getGstAmount());

                GstSummaryItem gs = gstMap.computeIfAbsent(gstPct, k -> {
                    GstSummaryItem item = new GstSummaryItem();
                    item.setGstPercent(gstPct.doubleValue());
                    item.setTaxableValue(0.0);
                    item.setGstAmount(0.0);
                    return item;
                });

                gs.setTaxableValue(gs.getTaxableValue() + taxable.doubleValue());
                gs.setGstAmount(gs.getGstAmount() + gstAmt.doubleValue());
            }
        }

        FirmStatementResponse resp = new FirmStatementResponse();
        resp.setFrom(fromDate);
        resp.setTo(toDate);
        resp.setTotalBilled(totalBilled.doubleValue());
        resp.setTotalPaid(totalPaid.doubleValue());
        resp.setOutstanding(outstanding.doubleValue());
        resp.setTotalTax(totalTax.doubleValue());
        resp.setInvoiceCount(list.size());
        resp.setGstSummary(new ArrayList<>(gstMap.values()));

        return resp;
    }

    /* ============================================================================
         FIRM STATEMENT PDF
    ============================================================================ */
    @Override
    public byte[] generateFirmStatementPdf(LocalDate from, LocalDate to) throws Exception {

        FirmStatementResponse data = getFirmStatement(from, to);
        FirmDetails firm = firmRepo.findAll().stream().findFirst().orElse(null);

        Document doc = new Document(PageSize.A4, 36, 36, 48, 48);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter.getInstance(doc, baos);

        doc.open();

        Font title = new Font(Font.HELVETICA, 18, Font.BOLD);
        Font h1 = new Font(Font.HELVETICA, 12, Font.BOLD);
        Font bold = new Font(Font.HELVETICA, 10, Font.BOLD);
        Font normal = new Font(Font.HELVETICA, 10);

        // Header
        Paragraph top = new Paragraph("FIRM STATEMENT", title);
        top.setAlignment(Element.ALIGN_CENTER);
        doc.add(top);
        doc.add(new Paragraph("\n"));

        if (firm != null) {
            doc.add(new Paragraph(firm.getFirmName(), h1));
            if (firm.getAddressLine1() != null) doc.add(new Paragraph(firm.getAddressLine1(), normal));
            if (firm.getAddressLine2() != null) doc.add(new Paragraph(firm.getAddressLine2(), normal));
            if (firm.getCity() != null || firm.getPincode() != null)
                doc.add(new Paragraph(
                        (firm.getCity() != null ? firm.getCity() : "") +
                        (firm.getPincode() != null ? " - " + firm.getPincode() : ""),
                        normal
                ));
            if (firm.getPhone() != null) doc.add(new Paragraph("Phone: " + firm.getPhone(), normal));
            if (firm.getEmail() != null) doc.add(new Paragraph("Email: " + firm.getEmail(), normal));
            if (firm.getGstin() != null) doc.add(new Paragraph("GSTIN: " + firm.getGstin(), normal));
            doc.add(new Paragraph("\n"));
        }

        doc.add(new Paragraph(
                "Period: " + data.getFrom().format(DATE_FMT) + " to " + data.getTo().format(DATE_FMT),
                bold
        ));
        doc.add(new Paragraph("\n"));

        // Summary row
        PdfPTable summary = new PdfPTable(new float[]{2f, 2f, 2f, 2f});
        summary.setWidthPercentage(100);

        summary.addCell(summaryCell("Total Business", fmt(data.getTotalBilled())));
        summary.addCell(summaryCell("Paid Amount", fmt(data.getTotalPaid())));
        summary.addCell(summaryCell("Outstanding", fmt(data.getOutstanding())));
        summary.addCell(summaryCell("Total GST", fmt(data.getTotalTax())));

        doc.add(summary);
        doc.add(new Paragraph("\n"));

        // GST summary table
        Paragraph gstTitle = new Paragraph("GST Summary", h1);
        gstTitle.setAlignment(Element.ALIGN_LEFT);
        doc.add(gstTitle);
        doc.add(new Paragraph("\n"));

        PdfPTable gst = new PdfPTable(new float[]{1f, 2f, 2f});
        gst.setWidthPercentage(100);

        headerCell(gst, "GST %");
        headerCell(gst, "Taxable Value");
        headerCell(gst, "GST Amount");

        for (GstSummaryItem g : data.getGstSummary()) {
            gst.addCell(textCell(g.getGstPercent() + "%"));
            gst.addCell(textCell(fmt(g.getTaxableValue())));
            gst.addCell(textCell(fmt(g.getGstAmount())));
        }

        doc.add(gst);
        doc.add(new Paragraph("\n\n"));

        Paragraph sig = new Paragraph("\n\nAuthorised Signatory\n" +
                (firm != null ? firm.getFirmName() : ""), bold);
        sig.setAlignment(Element.ALIGN_RIGHT);
        doc.add(sig);

        doc.close();
        return baos.toByteArray();
    }

    /* ============================================================================
        HELPERS
    ============================================================================ */
    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String fmt(Double v) {
        if (v == null) return "0.00";
        return String.format("%.2f", v);
    }

    private void headerCell(PdfPTable t, String label) {
        PdfPCell c = new PdfPCell(new Phrase(label, new Font(Font.HELVETICA, 10, Font.BOLD)));
        c.setBackgroundColor(Color.LIGHT_GRAY);
        c.setPadding(6);
        t.addCell(c);
    }

    private PdfPCell textCell(String v) {
        PdfPCell c = new PdfPCell(new Phrase(v == null ? "-" : v, new Font(Font.HELVETICA, 9)));
        c.setPadding(6);
        return c;
    }

    private PdfPCell summaryCell(String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(10);
        Paragraph p = new Paragraph(label + "\n" + value, new Font(Font.HELVETICA, 10, Font.BOLD));
        cell.addElement(p);
        return cell;
    }

}
