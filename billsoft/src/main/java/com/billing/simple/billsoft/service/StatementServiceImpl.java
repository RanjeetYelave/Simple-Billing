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
import com.billing.simple.billsoft.repo.InvoicePaymentRepository;
import com.billing.simple.billsoft.repo.InvoiceRepository;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.billing.simple.billsoft.dtos.*;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repositories.*;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StatementServiceImpl implements StatementService {

    private final InvoiceRepository invoiceRepo;
    private final CustomerRepository customerRepo;
    private final FirmDetailsRepository firmRepo;
    private final PartyRepository partyRepo;
    private final PartyPaymentRepository partyPaymentRepo;
    private final PurchaseOrderRepository purchaseOrderRepo;
    private final InvoicePaymentRepository invoicePaymentRepo;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DecimalFormat CURRENCY_FMT = new DecimalFormat("#,##,##0.00");

    public StatementServiceImpl(
            InvoiceRepository invoiceRepo,
            CustomerRepository customerRepo,
            FirmDetailsRepository firmRepo,
            PartyRepository partyRepo,
            PartyPaymentRepository partyPaymentRepo,
            PurchaseOrderRepository purchaseOrderRepo,
            InvoicePaymentRepository invoicePaymentRepo
    ) {
        this.invoiceRepo = invoiceRepo;
        this.customerRepo = customerRepo;
        this.firmRepo = firmRepo;
        this.partyRepo = partyRepo;
        this.partyPaymentRepo = partyPaymentRepo;
        this.purchaseOrderRepo = purchaseOrderRepo;
        this.invoicePaymentRepo = invoicePaymentRepo;
    }

    /* ============================================================================
        CUSTOMER STATEMENT (JSON)
    ============================================================================ */
    @Override
    public CustomerStatementResponse getCustomerStatement(Long firmId, Long customerId, LocalDate from, LocalDate to) {
        Customer customer = customerRepo.findById(customerId).orElse(null);
        if (customer == null) throw new RuntimeException("Customer not found: " + customerId);

        LocalDate toDate = (to == null) ? LocalDate.now() : to;
        LocalDate fromDate = (from == null) ? LocalDate.of(1970, 1, 1) : from;

        List<Invoice> all;
        if (firmId != null) {
            all = invoiceRepo.findByFirmIdAndCustomer_Id(firmId, customerId);
        } else {
            all = invoiceRepo.findByCustomer_Id(customerId);
        }

        // Only Invoices (skip estimates/drafts)
        List<Invoice> invoices = all.stream()
                .filter(i -> i.getInvoiceDate() != null)
                .filter(i -> i.getStatus() != InvoiceStatus.ESTIMATE)
                .filter(i -> i.getStatus() != InvoiceStatus.DRAFT)
                .collect(Collectors.toList());

        // Fetch all payments for customer
        List<InvoicePayment> paymentList = (firmId != null)
                ? invoicePaymentRepo.findByFirmIdAndCustomerIdOrderByPaymentDateAscIdAsc(firmId, customerId)
                : invoicePaymentRepo.findByCustomerIdOrderByPaymentDateAscIdAsc(customerId);

        Map<Long, Invoice> invoiceById = invoices.stream().collect(Collectors.toMap(Invoice::getId, i -> i, (a, b) -> a));
        Set<Long> invoicesWithPayments = paymentList.stream()
                .map(InvoicePayment::getInvoiceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Build unified list of ledger events
        class LedgerEvent {
            LocalDate date;
            String type;
            String ref;
            String description;
            BigDecimal debit;
            BigDecimal credit;
            int order; // 0 for invoice, 1 for payment
        }

        List<LedgerEvent> allEvents = new ArrayList<>();

        // Add invoice debits
        for (Invoice inv : invoices) {
            LedgerEvent e = new LedgerEvent();
            e.date = inv.getInvoiceDate().toLocalDate();
            e.type = "INVOICE";
            e.ref = inv.getInvoiceNumber() != null ? inv.getInvoiceNumber() : "INV-" + inv.getId();
            e.description = "Invoice";
            e.debit = nz(inv.getTotalAmount());
            e.credit = BigDecimal.ZERO;
            e.order = 0;
            allEvents.add(e);

            // Legacy fallback: if invoice was marked paid without InvoicePayment records
            if (Boolean.TRUE.equals(inv.getPaid()) && !invoicesWithPayments.contains(inv.getId())) {
                LedgerEvent pe = new LedgerEvent();
                pe.date = inv.getInvoiceDate().toLocalDate();
                pe.type = "PAYMENT";
                pe.ref = inv.getInvoiceNumber() != null ? inv.getInvoiceNumber() : "INV-" + inv.getId();
                pe.description = "Invoice Paid (Direct)";
                pe.debit = BigDecimal.ZERO;
                pe.credit = nz(inv.getTotalAmount());
                pe.order = 1;
                allEvents.add(pe);
            }
        }

        // Add real payment credits
        for (InvoicePayment p : paymentList) {
            LedgerEvent pe = new LedgerEvent();
            pe.date = p.getPaymentDate() != null ? p.getPaymentDate() : LocalDate.now();
            pe.type = "PAYMENT";
            String invNo = (p.getInvoiceId() != null && invoiceById.containsKey(p.getInvoiceId()))
                    ? invoiceById.get(p.getInvoiceId()).getInvoiceNumber() : null;
            pe.ref = (p.getReferenceNumber() != null && !p.getReferenceNumber().isBlank())
                    ? p.getReferenceNumber() : (invNo != null ? invNo : "PMT-" + p.getId());

            String modeStr = (p.getPaymentMode() != null && !p.getPaymentMode().isBlank()) ? p.getPaymentMode() : "Cash";
            String noteStr = (p.getNotes() != null && !p.getNotes().isBlank()) ? " (" + p.getNotes() + ")" : "";
            pe.description = "Payment via " + modeStr + noteStr;
            pe.debit = BigDecimal.ZERO;
            pe.credit = nz(p.getAmount());
            pe.order = 1;
            allEvents.add(pe);
        }

        // Compute opening balance before fromDate
        BigDecimal billedBefore = allEvents.stream()
                .filter(e -> e.date.isBefore(fromDate))
                .map(e -> e.debit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal paidBefore = allEvents.stream()
                .filter(e -> e.date.isBefore(fromDate))
                .map(e -> e.credit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal openingBalance = billedBefore.subtract(paidBefore);

        // Filter inside range and sort chronologically
        List<LedgerEvent> inRangeEvents = allEvents.stream()
                .filter(e -> !e.date.isBefore(fromDate) && !e.date.isAfter(toDate))
                .sorted(Comparator.comparing((LedgerEvent e) -> e.date)
                        .thenComparingInt(e -> e.order))
                .collect(Collectors.toList());

        List<StatementEntry> entries = new ArrayList<>();
        BigDecimal balance = openingBalance;
        BigDecimal totalBilled = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;

        for (LedgerEvent e : inRangeEvents) {
            balance = balance.add(e.debit).subtract(e.credit);
            totalBilled = totalBilled.add(e.debit);
            totalPaid = totalPaid.add(e.credit);

            StatementEntry entry = new StatementEntry();
            entry.setDate(e.date);
            entry.setType(e.type);
            entry.setRef(e.ref);
            entry.setDescription(e.description);
            entry.setDebit(e.debit.doubleValue());
            entry.setCredit(e.credit.doubleValue());
            entry.setBalance(balance.doubleValue());
            entries.add(entry);
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
    public byte[] generateCustomerStatementPdf(Long firmId, Long customerId, LocalDate from, LocalDate to) throws Exception {
        CustomerStatementResponse data = getCustomerStatement(firmId, customerId, from, to);
        FirmDetails firm = firmRepo.findById(firmId).orElse(null);

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
    public FirmStatementResponse getFirmStatement(Long firmId, LocalDate from, LocalDate to) {

        LocalDate toDate = (to == null) ? LocalDate.now() : to;
        LocalDate fromDate = (from == null) ? LocalDate.of(1970, 1, 1) : from;

        FirmDetails firm = (firmId != null) ? firmRepo.findById(firmId).orElse(null) : null;
        if (firm == null) {
            List<FirmDetails> allFirms = firmRepo.findAll();
            if (!allFirms.isEmpty()) firm = allFirms.get(0);
        }
        if (firm != null && firmId == null) {
            firmId = firm.getId();
        }

        // 1. Invoices
        List<Invoice> invoiceList;
        if (firmId != null) {
            invoiceList = invoiceRepo.findAllByFirmIdAndInvoiceDateBetweenOrderByInvoiceDateAsc(
                    firmId,
                    fromDate.atStartOfDay(),
                    toDate.plusDays(1).atStartOfDay()
            );
        } else {
            invoiceList = invoiceRepo.findAllByInvoiceDateBetweenOrderByInvoiceDateAsc(
                    fromDate.atStartOfDay(),
                    toDate.plusDays(1).atStartOfDay()
            );
        }
        invoiceList = invoiceList.stream()
                .filter(i -> i.getInvoiceDate() != null)
                .filter(i -> i.getStatus() != InvoiceStatus.ESTIMATE)
                .filter(i -> i.getStatus() != InvoiceStatus.DRAFT)
                .collect(Collectors.toList());

        BigDecimal totalBilled = invoiceList.stream()
                .map(i -> nz(i.getTotalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal taxableAmount = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        int paidCount = 0;
        int unpaidCount = 0;
        BigDecimal totalCollections = BigDecimal.ZERO;

        Map<BigDecimal, GstSummaryItem> gstMap = new LinkedHashMap<>();
        Map<String, FirmStatementResponse.PaymentModeSummary> modeMap = new LinkedHashMap<>();
        Map<Long, FirmStatementResponse.FirmAccountSummary> customerMap = new LinkedHashMap<>();
        List<FirmStatementResponse.FirmJournalEntry> journalEntries = new ArrayList<>();

        for (Invoice inv : invoiceList) {
            BigDecimal invTotal = nz(inv.getTotalAmount());
            BigDecimal invTax = nz(inv.getTotalTax());
            BigDecimal invDisc = nz(inv.getTotalDiscount());
            BigDecimal invTaxable = nz(inv.getSubtotalWithoutTax());

            taxableAmount = taxableAmount.add(invTaxable);
            totalTax = totalTax.add(invTax);
            totalDiscount = totalDiscount.add(invDisc);

            boolean isPaid = Boolean.TRUE.equals(inv.getPaid());
            if (isPaid) {
                paidCount++;
                totalCollections = totalCollections.add(invTotal);

                String mode = (inv.getPaymentMethod() != null && !inv.getPaymentMethod().trim().isEmpty())
                        ? inv.getPaymentMethod().trim() : "Cash";
                FirmStatementResponse.PaymentModeSummary pms = modeMap.computeIfAbsent(mode, k ->
                        FirmStatementResponse.PaymentModeSummary.builder()
                                .mode(k)
                                .count(0)
                                .totalAmount(0.0)
                                .build()
                );
                pms.setCount(pms.getCount() + 1);
                pms.setTotalAmount(pms.getTotalAmount() + invTotal.doubleValue());
            } else {
                unpaidCount++;
            }

            // Customer aggregation
            Long custId = (inv.getCustomer() != null) ? inv.getCustomer().getId() : 0L;
            String custName = (inv.getCustomer() != null && inv.getCustomer().getName() != null)
                    ? inv.getCustomer().getName() : "Walk-in Customer";
            String custPhone = (inv.getCustomer() != null) ? inv.getCustomer().getPhone() : "";

            FirmStatementResponse.FirmAccountSummary cas = customerMap.computeIfAbsent(custId, k ->
                    FirmStatementResponse.FirmAccountSummary.builder()
                            .id(k)
                            .name(custName)
                            .phone(custPhone)
                            .transactionCount(0)
                            .totalAmount(0.0)
                            .totalPaid(0.0)
                            .balanceDue(0.0)
                            .build()
            );
            cas.setTransactionCount(cas.getTransactionCount() + 1);
            cas.setTotalAmount(round2(cas.getTotalAmount() + invTotal.doubleValue()));
            if (isPaid) {
                cas.setTotalPaid(round2(cas.getTotalPaid() + invTotal.doubleValue()));
            } else {
                cas.setBalanceDue(round2(cas.getBalanceDue() + invTotal.doubleValue()));
            }

            // GST aggregation from line items
            if (inv.getItems() != null) {
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
                    gs.setTaxableValue(round2(gs.getTaxableValue() + taxable.doubleValue()));
                    gs.setGstAmount(round2(gs.getGstAmount() + gstAmt.doubleValue()));
                }
            }

            // Journal Entry
            journalEntries.add(FirmStatementResponse.FirmJournalEntry.builder()
                    .date(inv.getInvoiceDate())
                    .type("SALE_INVOICE")
                    .reference(inv.getInvoiceNumber() != null ? inv.getInvoiceNumber() : "INV-" + inv.getId())
                    .entityName(custName)
                    .entityType("CUSTOMER")
                    .paymentMethod(inv.getPaymentMethod())
                    .inflow(round2(invTotal))
                    .outflow(0.0)
                    .status(isPaid ? "PAID" : "UNPAID")
                    .notes(isPaid ? "Payment received" : "Outstanding balance")
                    .build()
            );
        }

        BigDecimal outstandingReceivables = totalBilled.subtract(totalCollections);

        // 2. Purchase Orders
        List<PurchaseOrder> poList = new ArrayList<>();
        if (firmId != null) {
            poList = purchaseOrderRepo.findByFirmIdAndPoDateBetweenOrderByPoDateAscIdAsc(firmId, fromDate, toDate);
        }
        poList = poList.stream()
                .filter(p -> p.getPoDate() != null)
                .filter(p -> p.getStatus() != PurchaseOrderStatus.CANCELLED)
                .collect(Collectors.toList());

        BigDecimal totalPurchases = poList.stream()
                .map(p -> nz(p.getTotalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPaidToVendorsFromPO = poList.stream()
                .map(p -> nz(p.getPaidAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<Long, FirmStatementResponse.FirmAccountSummary> vendorMap = new LinkedHashMap<>();
        for (PurchaseOrder po : poList) {
            Long partyId = (po.getParty() != null) ? po.getParty().getId() : 0L;
            String partyName = (po.getPartyName() != null && !po.getPartyName().trim().isEmpty())
                    ? po.getPartyName() : (po.getParty() != null ? po.getParty().getName() : "Vendor");
            String partyPhone = (po.getParty() != null) ? po.getParty().getPhone() : "";

            FirmStatementResponse.FirmAccountSummary fas = vendorMap.computeIfAbsent(partyId, k ->
                    FirmStatementResponse.FirmAccountSummary.builder()
                            .id(k)
                            .name(partyName)
                            .phone(partyPhone)
                            .transactionCount(0)
                            .totalAmount(0.0)
                            .totalPaid(0.0)
                            .balanceDue(0.0)
                            .build()
            );
            BigDecimal poTot = nz(po.getTotalAmount());
            BigDecimal poPaid = nz(po.getPaidAmount());
            fas.setTransactionCount(fas.getTransactionCount() + 1);
            fas.setTotalAmount(round2(fas.getTotalAmount() + poTot.doubleValue()));
            fas.setTotalPaid(round2(fas.getTotalPaid() + poPaid.doubleValue()));
            fas.setBalanceDue(round2(fas.getBalanceDue() + poTot.subtract(poPaid).doubleValue()));

            journalEntries.add(FirmStatementResponse.FirmJournalEntry.builder()
                    .date(po.getPoDate().atStartOfDay())
                    .type("PURCHASE_ORDER")
                    .reference(po.getPoNumber() != null ? po.getPoNumber() : "PO-" + po.getId())
                    .entityName(partyName)
                    .entityType("VENDOR")
                    .paymentMethod(po.getPaymentMethod())
                    .inflow(0.0)
                    .outflow(round2(poTot))
                    .status(po.getPaymentStatus() != null ? po.getPaymentStatus() : "ISSUED")
                    .notes(poPaid.compareTo(BigDecimal.ZERO) > 0 ? "Paid: " + CURRENCY_FMT.format(poPaid) : "Yet to pay")
                    .build()
            );
        }

        // 3. Direct Party Payments (if not tied to PO, add to vendor outflow)
        List<PartyPayment> partyPayments = new ArrayList<>();
        if (firmId != null) {
            partyPayments = partyPaymentRepo.findByFirmIdAndPaymentDateBetweenOrderByPaymentDateAscIdAsc(firmId, fromDate, toDate);
        }
        Map<Long, String> partyNameCache = new HashMap<>();
        BigDecimal directPayments = BigDecimal.ZERO;
        for (PartyPayment pp : partyPayments) {
            if (pp.getPurchaseOrderId() == null) {
                BigDecimal amt = nz(pp.getAmount());
                directPayments = directPayments.add(amt);

                String partyName = partyNameCache.computeIfAbsent(pp.getPartyId(), pid -> {
                    if (pid == null) return "Vendor";
                    return partyRepo.findById(pid).map(Party::getName).orElse("Vendor");
                });

                journalEntries.add(FirmStatementResponse.FirmJournalEntry.builder()
                        .date(pp.getPaymentDate().atStartOfDay())
                        .type("VENDOR_PAYMENT")
                        .reference(pp.getReferenceNumber() != null ? pp.getReferenceNumber() : "VPAY-" + pp.getId())
                        .entityName(partyName)
                        .entityType("VENDOR")
                        .paymentMethod(pp.getPaymentMode())
                        .inflow(0.0)
                        .outflow(round2(amt))
                        .status("PAID")
                        .notes(pp.getNotes() != null ? pp.getNotes() : "Direct vendor payment")
                        .build()
                );
            }
        }

        BigDecimal totalPaidToVendors = totalPaidToVendorsFromPO.add(directPayments);
        BigDecimal outstandingPayables = totalPurchases.subtract(totalPaidToVendorsFromPO);
        if (outstandingPayables.compareTo(BigDecimal.ZERO) < 0) outstandingPayables = BigDecimal.ZERO;

        BigDecimal netCashflow = totalCollections.subtract(totalPaidToVendors);
        BigDecimal netBusinessVolume = totalBilled.subtract(totalPurchases);

        // Sort journal entries chronologically
        journalEntries.sort(Comparator.comparing(FirmStatementResponse.FirmJournalEntry::getDate));

        List<FirmStatementResponse.FirmAccountSummary> topCustList = new ArrayList<>(customerMap.values());
        topCustList.sort((a, b) -> Double.compare(b.getTotalAmount(), a.getTotalAmount()));

        List<FirmStatementResponse.FirmAccountSummary> topVendList = new ArrayList<>(vendorMap.values());
        topVendList.sort((a, b) -> Double.compare(b.getTotalAmount(), a.getTotalAmount()));

        String firmAddr = "";
        if (firm != null) {
            StringBuilder sb = new StringBuilder();
            if (firm.getAddressLine1() != null && !firm.getAddressLine1().trim().isEmpty()) sb.append(firm.getAddressLine1()).append(", ");
            if (firm.getAddressLine2() != null && !firm.getAddressLine2().trim().isEmpty()) sb.append(firm.getAddressLine2()).append(", ");
            if (firm.getCity() != null && !firm.getCity().trim().isEmpty()) sb.append(firm.getCity());
            if (firm.getPincode() != null && !firm.getPincode().trim().isEmpty()) sb.append(" - ").append(firm.getPincode());
            firmAddr = sb.toString().trim();
        }

        return FirmStatementResponse.builder()
                .from(fromDate)
                .to(toDate)
                .firmId(firm != null ? firm.getId() : firmId)
                .firmName(firm != null ? firm.getFirmName() : "Billing Firm")
                .firmGstin(firm != null ? firm.getGstin() : null)
                .firmPhone(firm != null ? firm.getPhone() : null)
                .firmEmail(firm != null ? firm.getEmail() : null)
                .firmAddress(firmAddr)
                .totalBilled(round2(totalBilled))
                .taxableAmount(round2(taxableAmount))
                .totalTax(round2(totalTax))
                .totalDiscount(round2(totalDiscount))
                .invoiceCount(invoiceList.size())
                .paidInvoicesCount(paidCount)
                .unpaidInvoicesCount(unpaidCount)
                .totalPaid(round2(totalCollections))
                .outstanding(round2(outstandingReceivables))
                .totalPurchases(round2(totalPurchases))
                .purchaseOrderCount(poList.size())
                .totalPaidToVendors(round2(totalPaidToVendors))
                .outstandingPayables(round2(outstandingPayables))
                .netCashflow(round2(netCashflow))
                .netBusinessVolume(round2(netBusinessVolume))
                .gstSummary(new ArrayList<>(gstMap.values()))
                .paymentModeSummary(new ArrayList<>(modeMap.values()))
                .topCustomers(topCustList.stream().limit(10).collect(Collectors.toList()))
                .topVendors(topVendList.stream().limit(10).collect(Collectors.toList()))
                .entries(journalEntries)
                .build();
    }

    /* ============================================================================
         FIRM STATEMENT PDF (VECTOR MULTI-PAGE EXECUTIVE REPORT)
    ============================================================================ */
    @Override
    public byte[] generateFirmStatementPdf(Long firmId, LocalDate from, LocalDate to) throws Exception {

        FirmStatementResponse data = getFirmStatement(firmId, from, to);
        FirmDetails firm = (firmId != null) ? firmRepo.findById(firmId).orElse(null) : null;
        if (firm == null) {
            List<FirmDetails> all = firmRepo.findAll();
            if (!all.isEmpty()) firm = all.get(0);
        }

        Document doc = new Document(PageSize.A4, 28, 28, 32, 32);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter.getInstance(doc, baos);

        doc.open();

        Font headerTitleFont = new Font(Font.HELVETICA, 16, Font.BOLD, new Color(30, 41, 59));
        Font sectionTitleFont = new Font(Font.HELVETICA, 11, Font.BOLD, new Color(79, 70, 229));
        Font kpiLabelFont = new Font(Font.HELVETICA, 8, Font.NORMAL, new Color(100, 116, 139));
        Font kpiValFont = new Font(Font.HELVETICA, 11, Font.BOLD, new Color(15, 23, 42));
        Font tableHeaderFont = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);
        Font bold = new Font(Font.HELVETICA, 8, Font.BOLD, new Color(30, 41, 59));
        Font normal = new Font(Font.HELVETICA, 8, Font.NORMAL, new Color(51, 65, 85));
        Font subText = new Font(Font.HELVETICA, 8, Font.NORMAL, new Color(100, 116, 139));

        // ── Header Letterhead ──
        PdfPTable headerTable = new PdfPTable(new float[]{1.5f, 1f});
        headerTable.setWidthPercentage(100);

        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);
        leftCell.addElement(new Paragraph(data.getFirmName() != null ? data.getFirmName() : "FIRM STATEMENT", headerTitleFont));
        if (data.getFirmAddress() != null && !data.getFirmAddress().isEmpty()) {
            leftCell.addElement(new Paragraph(data.getFirmAddress(), subText));
        }
        if (data.getFirmGstin() != null && !data.getFirmGstin().isEmpty()) {
            leftCell.addElement(new Paragraph("GSTIN: " + data.getFirmGstin(), subText));
        }
        if (data.getFirmPhone() != null && !data.getFirmPhone().isEmpty()) {
            leftCell.addElement(new Paragraph("Phone: " + data.getFirmPhone() + (data.getFirmEmail() != null ? " | Email: " + data.getFirmEmail() : ""), subText));
        }
        headerTable.addCell(leftCell);

        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph repTitle = new Paragraph("FIRM SUMMARY STATEMENT", new Font(Font.HELVETICA, 12, Font.BOLD, new Color(79, 70, 229)));
        repTitle.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(repTitle);

        Paragraph per = new Paragraph("Period: " + data.getFrom().format(DATE_FMT) + " to " + data.getTo().format(DATE_FMT), bold);
        per.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(per);

        Paragraph gen = new Paragraph("Generated: " + LocalDate.now().format(DATE_FMT), subText);
        gen.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(gen);
        headerTable.addCell(rightCell);

        doc.add(headerTable);
        doc.add(new Paragraph("\n"));

        // ── 1. Executive Financial Summary KPIs ──
        PdfPTable kpiTable = new PdfPTable(3);
        kpiTable.setWidthPercentage(100);
        kpiTable.setSpacingBefore(4);
        kpiTable.setSpacingAfter(10);

        addKpiBox(kpiTable, "TOTAL SALES / REVENUE", "₹ " + CURRENCY_FMT.format(data.getTotalBilled() != null ? data.getTotalBilled() : 0), (data.getInvoiceCount() != null ? data.getInvoiceCount() : 0) + " Invoices Issued", new Color(240, 249, 255), kpiLabelFont, kpiValFont, subText);
        addKpiBox(kpiTable, "COLLECTIONS (INFLOW)", "₹ " + CURRENCY_FMT.format(data.getTotalPaid() != null ? data.getTotalPaid() : 0), (data.getPaidInvoicesCount() != null ? data.getPaidInvoicesCount() : 0) + " Invoices Paid", new Color(240, 253, 244), kpiLabelFont, new Font(Font.HELVETICA, 11, Font.BOLD, new Color(22, 101, 52)), subText);
        addKpiBox(kpiTable, "RECEIVABLES (MARKET DEBT)", "₹ " + CURRENCY_FMT.format(data.getOutstanding() != null ? data.getOutstanding() : 0), (data.getUnpaidInvoicesCount() != null ? data.getUnpaidInvoicesCount() : 0) + " Unpaid Invoices", new Color(254, 242, 242), kpiLabelFont, new Font(Font.HELVETICA, 11, Font.BOLD, new Color(185, 28, 28)), subText);

        addKpiBox(kpiTable, "TOTAL PURCHASES (ORDERS)", "₹ " + CURRENCY_FMT.format(data.getTotalPurchases() != null ? data.getTotalPurchases() : 0), (data.getPurchaseOrderCount() != null ? data.getPurchaseOrderCount() : 0) + " POs Issued", new Color(254, 249, 195), kpiLabelFont, kpiValFont, subText);
        addKpiBox(kpiTable, "PAID TO VENDORS (OUTFLOW)", "₹ " + CURRENCY_FMT.format(data.getTotalPaidToVendors() != null ? data.getTotalPaidToVendors() : 0), "Vendor Outflows", new Color(243, 244, 246), kpiLabelFont, kpiValFont, subText);
        addKpiBox(kpiTable, "NET OPERATING CASHFLOW", "₹ " + CURRENCY_FMT.format(data.getNetCashflow() != null ? data.getNetCashflow() : 0), "Inflow minus Outflow", new Color(238, 242, 255), kpiLabelFont, new Font(Font.HELVETICA, 11, Font.BOLD, new Color(79, 70, 229)), subText);

        doc.add(kpiTable);

        // ── 2. Side-by-Side: GST Breakdown & Payment Modes ──
        PdfPTable splitTable = new PdfPTable(new float[]{1.4f, 1f});
        splitTable.setWidthPercentage(100);
        splitTable.setSpacingAfter(10);

        // Left: GST Summary
        PdfPCell gstCell = new PdfPCell();
        gstCell.setBorder(Rectangle.NO_BORDER);
        gstCell.addElement(new Paragraph("GST Tax Slabs Breakdown", sectionTitleFont));

        PdfPTable gstInner = new PdfPTable(new float[]{1f, 1.5f, 1.5f});
        gstInner.setWidthPercentage(100);
        gstInner.setSpacingBefore(4);

        addPdfHeader(gstInner, "Slab", tableHeaderFont);
        addPdfHeader(gstInner, "Taxable Value (₹)", tableHeaderFont);
        addPdfHeader(gstInner, "GST Amount (₹)", tableHeaderFont);

        if (data.getGstSummary() == null || data.getGstSummary().isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("No GST tax data in period", normal));
            empty.setColspan(3); empty.setPadding(6); empty.setHorizontalAlignment(Element.ALIGN_CENTER);
            gstInner.addCell(empty);
        } else {
            for (GstSummaryItem g : data.getGstSummary()) {
                addPdfTextCell(gstInner, g.getGstPercent() + "%", normal, Element.ALIGN_LEFT);
                addPdfTextCell(gstInner, CURRENCY_FMT.format(g.getTaxableValue() != null ? g.getTaxableValue() : 0), normal, Element.ALIGN_RIGHT);
                addPdfTextCell(gstInner, CURRENCY_FMT.format(g.getGstAmount() != null ? g.getGstAmount() : 0), bold, Element.ALIGN_RIGHT);
            }
        }
        gstCell.addElement(gstInner);
        splitTable.addCell(gstCell);

        // Right: Payment Mode Summary
        PdfPCell payCell = new PdfPCell();
        payCell.setBorder(Rectangle.NO_BORDER);
        payCell.setPaddingLeft(10);
        payCell.addElement(new Paragraph("Collections by Payment Mode", sectionTitleFont));

        PdfPTable payInner = new PdfPTable(new float[]{1.5f, 0.8f, 1.5f});
        payInner.setWidthPercentage(100);
        payInner.setSpacingBefore(4);

        addPdfHeader(payInner, "Mode", tableHeaderFont);
        addPdfHeader(payInner, "Count", tableHeaderFont);
        addPdfHeader(payInner, "Amount (₹)", tableHeaderFont);

        if (data.getPaymentModeSummary() == null || data.getPaymentModeSummary().isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("No payment mode data", normal));
            empty.setColspan(3); empty.setPadding(6); empty.setHorizontalAlignment(Element.ALIGN_CENTER);
            payInner.addCell(empty);
        } else {
            for (FirmStatementResponse.PaymentModeSummary pm : data.getPaymentModeSummary()) {
                addPdfTextCell(payInner, pm.getMode(), normal, Element.ALIGN_LEFT);
                addPdfTextCell(payInner, String.valueOf(pm.getCount()), normal, Element.ALIGN_CENTER);
                addPdfTextCell(payInner, CURRENCY_FMT.format(pm.getTotalAmount() != null ? pm.getTotalAmount() : 0), bold, Element.ALIGN_RIGHT);
            }
        }
        payCell.addElement(payInner);
        splitTable.addCell(payCell);

        doc.add(splitTable);

        // ── 3. Top Accounts Summary ──
        if (data.getTopCustomers() != null && !data.getTopCustomers().isEmpty()) {
            Paragraph custHead = new Paragraph("Key Customer Accounts", sectionTitleFont);
            custHead.setSpacingBefore(4);
            doc.add(custHead);

            PdfPTable topCustTable = new PdfPTable(new float[]{2.5f, 0.8f, 1.5f, 1.5f, 1.5f});
            topCustTable.setWidthPercentage(100);
            topCustTable.setSpacingBefore(4);
            topCustTable.setSpacingAfter(10);

            addPdfHeader(topCustTable, "Customer Name", tableHeaderFont);
            addPdfHeader(topCustTable, "Invoices", tableHeaderFont);
            addPdfHeader(topCustTable, "Total Billed (₹)", tableHeaderFont);
            addPdfHeader(topCustTable, "Total Paid (₹)", tableHeaderFont);
            addPdfHeader(topCustTable, "Balance Due (₹)", tableHeaderFont);

            for (FirmStatementResponse.FirmAccountSummary c : data.getTopCustomers()) {
                addPdfTextCell(topCustTable, c.getName(), bold, Element.ALIGN_LEFT);
                addPdfTextCell(topCustTable, String.valueOf(c.getTransactionCount()), normal, Element.ALIGN_CENTER);
                addPdfTextCell(topCustTable, CURRENCY_FMT.format(c.getTotalAmount()), normal, Element.ALIGN_RIGHT);
                addPdfTextCell(topCustTable, CURRENCY_FMT.format(c.getTotalPaid()), normal, Element.ALIGN_RIGHT);
                addPdfTextCell(topCustTable, CURRENCY_FMT.format(c.getBalanceDue()), bold, Element.ALIGN_RIGHT);
            }
            doc.add(topCustTable);
        }

        // ── 4. Chronological Transactions Journal ──
        Paragraph jourHead = new Paragraph("Chronological Transaction Journal", sectionTitleFont);
        jourHead.setSpacingBefore(4);
        doc.add(jourHead);

        PdfPTable journalTable = new PdfPTable(new float[]{1.2f, 1.4f, 2.2f, 1.6f, 1.4f, 1.4f, 1.2f});
        journalTable.setWidthPercentage(100);
        journalTable.setSpacingBefore(4);

        addPdfHeader(journalTable, "Date", tableHeaderFont);
        addPdfHeader(journalTable, "Type", tableHeaderFont);
        addPdfHeader(journalTable, "Entity / Party", tableHeaderFont);
        addPdfHeader(journalTable, "Ref #", tableHeaderFont);
        addPdfHeader(journalTable, "Inflow (₹)", tableHeaderFont);
        addPdfHeader(journalTable, "Outflow (₹)", tableHeaderFont);
        addPdfHeader(journalTable, "Status", tableHeaderFont);

        if (data.getEntries() == null || data.getEntries().isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("No transactions recorded in this period", normal));
            empty.setColspan(7); empty.setPadding(8); empty.setHorizontalAlignment(Element.ALIGN_CENTER);
            journalTable.addCell(empty);
        } else {
            int idx = 0;
            for (FirmStatementResponse.FirmJournalEntry e : data.getEntries()) {
                Color bg = (idx++ % 2 == 0) ? Color.WHITE : new Color(248, 250, 252);

                addPdfJournalCell(journalTable, e.getDate() != null ? e.getDate().format(DATE_FMT) : "—", normal, Element.ALIGN_LEFT, bg);
                addPdfJournalCell(journalTable, e.getType() != null ? e.getType().replace("_", " ") : "—", normal, Element.ALIGN_LEFT, bg);
                addPdfJournalCell(journalTable, e.getEntityName() != null ? e.getEntityName() : "—", bold, Element.ALIGN_LEFT, bg);
                addPdfJournalCell(journalTable, e.getReference() != null ? e.getReference() : "—", normal, Element.ALIGN_LEFT, bg);
                addPdfJournalCell(journalTable, e.getInflow() != null && e.getInflow() > 0 ? CURRENCY_FMT.format(e.getInflow()) : "—", bold, Element.ALIGN_RIGHT, bg);
                addPdfJournalCell(journalTable, e.getOutflow() != null && e.getOutflow() > 0 ? CURRENCY_FMT.format(e.getOutflow()) : "—", bold, Element.ALIGN_RIGHT, bg);
                addPdfJournalCell(journalTable, e.getStatus() != null ? e.getStatus() : "—", normal, Element.ALIGN_CENTER, bg);
            }
        }
        doc.add(journalTable);

        // ── Signatory Footer ──
        doc.add(new Paragraph("\n"));
        Paragraph sig = new Paragraph("Authorised Signatory\n" + (data.getFirmName() != null ? data.getFirmName() : ""), bold);
        sig.setAlignment(Element.ALIGN_RIGHT);
        doc.add(sig);

        doc.close();
        return baos.toByteArray();
    }

    private void addKpiBox(PdfPTable table, String label, String value, String sub, Color bgColor, Font lFont, Font vFont, Font sFont) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(bgColor);
        cell.setPadding(6);
        cell.setBorderColor(new Color(226, 232, 240));

        Paragraph pLabel = new Paragraph(label, lFont);
        Paragraph pVal = new Paragraph(value, vFont);
        Paragraph pSub = new Paragraph(sub, sFont);

        cell.addElement(pLabel);
        cell.addElement(pVal);
        cell.addElement(pSub);
        table.addCell(cell);
    }

    private void addPdfHeader(PdfPTable table, String title, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(title, font));
        c.setBackgroundColor(new Color(79, 70, 229));
        c.setPadding(5);
        if (title.contains("₹") || title.contains("Count")) c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        else c.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.addCell(c);
    }

    private void addPdfTextCell(PdfPTable table, String text, Font font, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text != null ? text : "-", font));
        c.setPadding(4);
        c.setHorizontalAlignment(align);
        table.addCell(c);
    }

    private void addPdfJournalCell(PdfPTable table, String text, Font font, int align, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(text != null ? text : "-", font));
        c.setPadding(4);
        c.setBackgroundColor(bg);
        c.setHorizontalAlignment(align);
        table.addCell(c);
    }

    /* ============================================================================
        PARTY STATEMENT (JSON)
    ============================================================================ */
    @Override
    public PartyStatementResponse getPartyStatement(Long firmId, Long partyId, LocalDate from, LocalDate to) {
        Party party = partyRepo.findByIdAndFirmId(partyId, firmId)
                .orElseThrow(() -> new IllegalArgumentException("Party not found: " + partyId));

        LocalDate toDate = (to == null) ? LocalDate.now() : to;
        LocalDate fromDate = (from == null) ? LocalDate.of(1970, 1, 1) : from;

        // Initial party opening balance
        BigDecimal initialOpening = party.getOpeningBalance() != null ? party.getOpeningBalance() : BigDecimal.ZERO;
        String balType = party.getOpeningBalanceType() != null ? party.getOpeningBalanceType().toUpperCase() : "PAYABLE";
        BigDecimal netOpeningLiability = "ADVANCE".equals(balType) ? initialOpening.negate() : initialOpening;

        // Purchases before fromDate
        List<PurchaseOrder> posBefore = purchaseOrderRepo.findByFirmIdAndPartyIdAndPoDateBefore(firmId, partyId, fromDate);
        BigDecimal purchasesBefore = posBefore.stream()
                .filter(po -> po.getStatus() != PurchaseOrderStatus.CANCELLED)
                .map(po -> nz(po.getTotalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Payments before fromDate
        List<PartyPayment> paymentsBefore = partyPaymentRepo.findByFirmIdAndPartyIdAndPaymentDateBefore(firmId, partyId, fromDate);
        BigDecimal paidBefore = paymentsBefore.stream()
                .map(p -> nz(p.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal periodOpeningBalance = netOpeningLiability.add(purchasesBefore).subtract(paidBefore);

        // In range items
        List<PurchaseOrder> posInRange = purchaseOrderRepo.findByFirmIdAndPartyIdAndPoDateBetweenOrderByPoDateAscIdAsc(
                firmId, partyId, fromDate, toDate).stream()
                .filter(po -> po.getStatus() != PurchaseOrderStatus.CANCELLED)
                .collect(Collectors.toList());

        List<PartyPayment> paymentsInRange = partyPaymentRepo.findByFirmIdAndPartyIdAndPaymentDateBetweenOrderByPaymentDateAscIdAsc(
                firmId, partyId, fromDate, toDate);

        // Merge entries
        List<StatementEntry> entries = new ArrayList<>();

        for (PurchaseOrder po : posInRange) {
            StatementEntry entry = new StatementEntry();
            entry.setDate(po.getPoDate());
            entry.setType("PURCHASE_ORDER");
            entry.setRef(po.getPoNumber());
            entry.setDescription("Purchase Order (" + po.getStatus().name() + ")");
            entry.setDebit(po.getTotalAmount() != null ? po.getTotalAmount().doubleValue() : 0.0);
            entry.setCredit(0.0);
            entries.add(entry);
        }

        for (PartyPayment payment : paymentsInRange) {
            StatementEntry entry = new StatementEntry();
            entry.setDate(payment.getPaymentDate());
            entry.setType("PAYMENT");
            entry.setRef("PAY-" + payment.getId() + (payment.getPurchaseOrderId() != null ? "-PO-" + payment.getPurchaseOrderId() : "-ADV"));
            String targetLabel = payment.getPurchaseOrderId() != null ? "PO #" + payment.getPurchaseOrderId() : "Advance Payment";
            String refPart = payment.getReferenceNumber() != null && !payment.getReferenceNumber().isBlank() ? " [Ref: " + payment.getReferenceNumber() + "]" : "";
            entry.setDescription(targetLabel + " via " + payment.getPaymentMode() + refPart + (payment.getNotes() != null && !payment.getNotes().isEmpty() ? " • " + payment.getNotes() : ""));
            entry.setDebit(0.0);
            entry.setCredit(payment.getAmount() != null ? payment.getAmount().doubleValue() : 0.0);
            entries.add(entry);
        }

        // Sort entries by date
        entries.sort(Comparator.comparing(StatementEntry::getDate));

        // Calculate running balance
        BigDecimal running = periodOpeningBalance;
        BigDecimal totalPurchases = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;

        for (StatementEntry e : entries) {
            BigDecimal deb = BigDecimal.valueOf(e.getDebit() != null ? e.getDebit() : 0.0);
            BigDecimal cred = BigDecimal.valueOf(e.getCredit() != null ? e.getCredit() : 0.0);

            totalPurchases = totalPurchases.add(deb);
            totalPaid = totalPaid.add(cred);

            running = running.add(deb).subtract(cred);
            e.setBalance(running.setScale(2, RoundingMode.HALF_UP).doubleValue());
        }

        return PartyStatementResponse.builder()
                .partyId(party.getId())
                .partyName(party.getName())
                .phone(party.getPhone())
                .gstin(party.getGstin())
                .address(party.getAddress())
                .from(fromDate)
                .to(toDate)
                .openingBalance(periodOpeningBalance.setScale(2, RoundingMode.HALF_UP).doubleValue())
                .totalPurchases(totalPurchases.setScale(2, RoundingMode.HALF_UP).doubleValue())
                .totalPaid(totalPaid.setScale(2, RoundingMode.HALF_UP).doubleValue())
                .closingBalance(running.setScale(2, RoundingMode.HALF_UP).doubleValue())
                .entries(entries)
                .build();
    }

    /* ============================================================================
        PARTY STATEMENT (PDF)
    ============================================================================ */
    @Override
    public byte[] generatePartyStatementPdf(Long firmId, Long partyId, LocalDate from, LocalDate to) throws Exception {
        PartyStatementResponse data = getPartyStatement(firmId, partyId, from, to);
        FirmDetails firm = (firmId != null) ? firmRepo.findById(firmId).orElse(null) : firmRepo.findAll().stream().findFirst().orElse(null);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 28, 28, 28, 28);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD, new Color(79, 70, 229));
        Font firmFont = new Font(Font.HELVETICA, 13, Font.BOLD, Color.BLACK);
        Font bold = new Font(Font.HELVETICA, 9, Font.BOLD, Color.BLACK);
        Font normal = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, Color.BLACK);
        Font tableHeaderFont = new Font(Font.HELVETICA, 8.5f, Font.BOLD, Color.WHITE);

        // Header Table
        PdfPTable headerTable = new PdfPTable(new float[]{60, 40});
        headerTable.setWidthPercentage(100);

        PdfPCell fCell = new PdfPCell();
        fCell.setBorder(Rectangle.NO_BORDER);
        fCell.addElement(new Paragraph(firm != null && firm.getFirmName() != null ? firm.getFirmName() : "RupeeCRM", firmFont));
        if (firm != null) {
            if (firm.getAddressLine1() != null) fCell.addElement(new Paragraph(firm.getAddressLine1(), normal));
            if (firm.getGstin() != null) fCell.addElement(new Paragraph("GSTIN: " + firm.getGstin(), bold));
            if (firm.getPhone() != null) fCell.addElement(new Paragraph("Phone: " + firm.getPhone(), normal));
        }
        headerTable.addCell(fCell);

        PdfPCell tCell = new PdfPCell();
        tCell.setBorder(Rectangle.NO_BORDER);
        tCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph title = new Paragraph("VENDOR / PARTY STATEMENT", titleFont);
        title.setAlignment(Element.ALIGN_RIGHT);
        tCell.addElement(title);

        Paragraph pPeriod = new Paragraph("Period: " + data.getFrom().format(DATE_FMT) + " to " + data.getTo().format(DATE_FMT), normal);
        pPeriod.setAlignment(Element.ALIGN_RIGHT);
        tCell.addElement(pPeriod);
        headerTable.addCell(tCell);

        doc.add(headerTable);
        doc.add(new Paragraph("\n"));

        // Party Info Box
        PdfPTable pTable = new PdfPTable(1);
        pTable.setWidthPercentage(100);
        PdfPCell pBox = new PdfPCell();
        pBox.setBackgroundColor(new Color(243, 244, 246));
        pBox.setPadding(8);
        pBox.addElement(new Paragraph("PARTY / VENDOR: " + data.getPartyName(), bold));
        if (data.getGstin() != null && !data.getGstin().isEmpty()) pBox.addElement(new Paragraph("GSTIN: " + data.getGstin(), normal));
        if (data.getPhone() != null && !data.getPhone().isEmpty()) pBox.addElement(new Paragraph("Phone: " + data.getPhone(), normal));
        if (data.getAddress() != null && !data.getAddress().isEmpty()) pBox.addElement(new Paragraph("Address: " + data.getAddress(), normal));
        pTable.addCell(pBox);
        doc.add(pTable);
        doc.add(new Paragraph("\n"));

        // Summary Cards
        PdfPTable sumTable = new PdfPTable(4);
        sumTable.setWidthPercentage(100);
        sumTable.addCell(summaryCell("Opening Balance", "₹ " + CURRENCY_FMT.format(data.getOpeningBalance())));
        sumTable.addCell(summaryCell("Total Orders", "₹ " + CURRENCY_FMT.format(data.getTotalPurchases())));
        sumTable.addCell(summaryCell("Total Paid", "₹ " + CURRENCY_FMT.format(data.getTotalPaid())));
        sumTable.addCell(summaryCell("Closing Balance", "₹ " + CURRENCY_FMT.format(data.getClosingBalance())));
        doc.add(sumTable);
        doc.add(new Paragraph("\n"));

        // Ledger Entries Table
        PdfPTable entriesTable = new PdfPTable(new float[]{14, 20, 24, 14, 14, 14});
        entriesTable.setWidthPercentage(100);

        String[] headers = {"Date", "Type", "Ref / Notes", "Debit (₹)", "Credit (₹)", "Balance (₹)"};
        for (String h : headers) {
            PdfPCell c = new PdfPCell(new Phrase(h, tableHeaderFont));
            c.setBackgroundColor(new Color(79, 70, 229));
            c.setPadding(6);
            if (h.contains("₹")) c.setHorizontalAlignment(Element.ALIGN_RIGHT);
            entriesTable.addCell(c);
        }

        if (data.getEntries() == null || data.getEntries().isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("No transactions found in this period", normal));
            empty.setColspan(6);
            empty.setPadding(10);
            empty.setHorizontalAlignment(Element.ALIGN_CENTER);
            entriesTable.addCell(empty);
        } else {
            int idx = 0;
            for (StatementEntry e : data.getEntries()) {
                Color bg = (idx++ % 2 == 0) ? Color.WHITE : new Color(249, 250, 251);

                PdfPCell cDate = new PdfPCell(new Phrase(e.getDate() != null ? e.getDate().format(DATE_FMT) : "—", normal));
                cDate.setBackgroundColor(bg); cDate.setPadding(5);
                entriesTable.addCell(cDate);

                PdfPCell cType = new PdfPCell(new Phrase(e.getType() != null ? e.getType() : "—", normal));
                cType.setBackgroundColor(bg); cType.setPadding(5);
                entriesTable.addCell(cType);

                String refNotes = (e.getRef() != null ? e.getRef() : "") + (e.getDescription() != null && !e.getDescription().isEmpty() ? " " + e.getDescription() : "");
                PdfPCell cRef = new PdfPCell(new Phrase(refNotes, normal));
                cRef.setBackgroundColor(bg); cRef.setPadding(5);
                entriesTable.addCell(cRef);

                PdfPCell cDeb = new PdfPCell(new Phrase(e.getDebit() != null && e.getDebit() > 0 ? CURRENCY_FMT.format(e.getDebit()) : "—", normal));
                cDeb.setBackgroundColor(bg); cDeb.setPadding(5); cDeb.setHorizontalAlignment(Element.ALIGN_RIGHT);
                entriesTable.addCell(cDeb);

                PdfPCell cCred = new PdfPCell(new Phrase(e.getCredit() != null && e.getCredit() > 0 ? CURRENCY_FMT.format(e.getCredit()) : "—", normal));
                cCred.setBackgroundColor(bg); cCred.setPadding(5); cCred.setHorizontalAlignment(Element.ALIGN_RIGHT);
                entriesTable.addCell(cCred);

                PdfPCell cBal = new PdfPCell(new Phrase(e.getBalance() != null ? CURRENCY_FMT.format(e.getBalance()) : "0.00", bold));
                cBal.setBackgroundColor(bg); cBal.setPadding(5); cBal.setHorizontalAlignment(Element.ALIGN_RIGHT);
                entriesTable.addCell(cBal);
            }
        }

        doc.add(entriesTable);
        doc.add(new Paragraph("\n\n"));

        Paragraph sig = new Paragraph("Authorised Signatory\n" + (firm != null && firm.getFirmName() != null ? firm.getFirmName() : ""), bold);
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

    private static double round2(BigDecimal v) {
        if (v == null) return 0.0;
        return v.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
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
