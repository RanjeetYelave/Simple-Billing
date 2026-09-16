package com.billing.simple.billsoft.regression.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.billing.simple.billsoft.dtos.CustomerStatementResponse;
import com.billing.simple.billsoft.dtos.PartyFinancialSummary;
import com.billing.simple.billsoft.dtos.PartyStatementResponse;
import com.billing.simple.billsoft.dtos.StatementEntry;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoiceItem;
import com.billing.simple.billsoft.entities.InvoicePayment;
import com.billing.simple.billsoft.entities.InvoiceStatus;
import com.billing.simple.billsoft.entities.Party;
import com.billing.simple.billsoft.entities.PartyPayment;
import com.billing.simple.billsoft.entities.PurchaseOrder;
import com.billing.simple.billsoft.entities.PurchaseOrderItem;
import com.billing.simple.billsoft.entities.PurchaseOrderStatus;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;
import com.billing.simple.billsoft.repo.InvoicePaymentRepository;
import com.billing.simple.billsoft.repo.InvoiceRepository;
import com.billing.simple.billsoft.repositories.PartyPaymentRepository;
import com.billing.simple.billsoft.repositories.PartyRepository;
import com.billing.simple.billsoft.repositories.PurchaseOrderRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.service.PartyService;
import com.billing.simple.billsoft.service.StatementService;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class StatementsAndLedgerLargeDatasetRegressionTest {

    @Autowired
    private StatementService statementService;

    @Autowired
    private PartyService partyService;

    @Autowired
    private com.billing.simple.billsoft.service.PurchaseOrderService poService;

    @Autowired
    private InvoiceRepository invoiceRepo;

    @Autowired
    private InvoicePaymentRepository invoicePaymentRepo;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private PartyRepository partyRepo;

    @Autowired
    private PurchaseOrderRepository poRepo;

    @Autowired
    private PartyPaymentRepository partyPaymentRepo;

    @Autowired
    private FirmDetailsRepository firmRepo;

    private Long firmId;
    private Customer testCustomer;
    private Party testVendor;

    @BeforeEach
    void setup() {
        FirmDetails firm = firmRepo.findAll().stream().findFirst().orElseGet(() -> {
            FirmDetails f = new FirmDetails();
            f.setFirmName("Statement Scale Test Firm");
            return firmRepo.save(f);
        });
        firmId = firm.getId();
        TenantContext.setCurrentFirmId(firmId);

        testCustomer = customerRepo.save(Customer.builder().name("Ledger Test Customer").firmId(firmId).phone("9111111111").build());
        testVendor = partyRepo.save(Party.builder().name("Ledger Test Vendor").firmId(firmId).openingBalance(new BigDecimal("5000.00")).openingBalanceType("PAYABLE").build());
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testCustomerStatement_RunningBalanceAndDateFiltering() {
        LocalDate baseDate = LocalDate.of(2026, 1, 1);

        // Generate 30 historical invoices and 20 historical payments
        for (int i = 1; i <= 30; i++) {
            Invoice inv = new Invoice();
            inv.setFirmId(firmId);
            inv.setCustomer(testCustomer);
            inv.setInvoiceNumber("INV-LEDGER-" + i);
            inv.setInvoiceDate(baseDate.plusDays(i * 3).atStartOfDay());
            inv.setStatus(InvoiceStatus.UNPAID);
            inv.setTotalAmount(new BigDecimal("1000.00"));
            inv.setSubtotalWithoutTax(new BigDecimal("1000.00"));
            inv.setTotalDiscount(BigDecimal.ZERO);
            inv.setTotalTax(BigDecimal.ZERO);
            invoiceRepo.save(inv);

            if (i % 2 == 0) {
                InvoicePayment pmt = InvoicePayment.builder()
                        .firmId(firmId)
                        .invoiceId(inv.getId())
                        .customerId(testCustomer.getId())
                        .amount(new BigDecimal("500.00"))
                        .paymentDate(baseDate.plusDays(i * 3 + 1))
                        .paymentMode("Bank Transfer")
                        .build();
                invoicePaymentRepo.save(pmt);
            }
        }

        // 1. Full Statement
        CustomerStatementResponse fullStmt = statementService.getCustomerStatement(firmId, testCustomer.getId(), baseDate, baseDate.plusDays(100));
        assertThat(fullStmt.getEntries()).isNotEmpty();
        assertThat(fullStmt.getTotalBilled()).isEqualTo(30000.0);
        assertThat(fullStmt.getTotalPaid()).isEqualTo(7500.0);
        assertThat(fullStmt.getClosingBalance()).isEqualTo(22500.0);

        // 2. Date-filtered statement (starting mid-way) -> prior transactions carry forward into openingBalance
        LocalDate filterStart = baseDate.plusDays(31);
        CustomerStatementResponse partialStmt = statementService.getCustomerStatement(firmId, testCustomer.getId(), filterStart, baseDate.plusDays(100));
        assertThat(partialStmt.getOpeningBalance()).isGreaterThan(0.0);
        assertThat(partialStmt.getClosingBalance()).isEqualTo(22500.0);

        // Verify running balance continuously accumulates
        double running = partialStmt.getOpeningBalance();
        for (StatementEntry e : partialStmt.getEntries()) {
            running = running + e.getDebit() - e.getCredit();
            assertThat(e.getBalance()).isEqualTo(Math.round(running * 100.0) / 100.0);
        }
    }

    @Test
    void testVendorStatement_OpeningBalanceAndPOReconciliation() {
        LocalDate baseDate = LocalDate.of(2026, 2, 1);

        // Add 10 POs and 5 payments
        for (int i = 1; i <= 10; i++) {
            PurchaseOrder po = new PurchaseOrder();
            po.setFirmId(firmId);
            po.setParty(testVendor);
            po.setPoNumber("PO-LEDGER-" + i);
            po.setPoDate(baseDate.plusDays(i * 2));
            po.setStatus(PurchaseOrderStatus.RECEIVED);
            
            PurchaseOrderItem it = new PurchaseOrderItem();
            it.setProductName("Test Item " + i);
            it.setQuantity(new BigDecimal("10.00"));
            it.setUnitPrice(new BigDecimal("200.00"));
            it.setPurchaseOrder(po);
            po.setItems(new ArrayList<>(List.of(it)));
            poService.createPurchaseOrder(po);

            if (i <= 5) {
                PartyPayment p = new PartyPayment();
                p.setFirmId(firmId);
                p.setPartyId(testVendor.getId());
                p.setPurchaseOrderId(po.getId());
                p.setAmount(new BigDecimal("1500.00"));
                p.setPaymentDate(baseDate.plusDays(i * 2 + 1));
                p.setPaymentMode("UPI");
                partyPaymentRepo.save(p);
            }
        }

        PartyStatementResponse stmt = statementService.getPartyStatement(firmId, testVendor.getId(), baseDate, baseDate.plusDays(60));
        assertThat(stmt.getOpeningBalance()).isEqualTo(5000.0); // Vendor initial opening liability
        assertThat(stmt.getTotalPurchases()).isEqualTo(20000.0);
        assertThat(stmt.getTotalPaid()).isEqualTo(7500.0);
        assertThat(stmt.getClosingBalance()).isEqualTo(5000.0 + 20000.0 - 7500.0); // 17500.0

        // Financial Summary batch aggregation must match exactly
        PartyFinancialSummary summary = partyService.getFinancialSummary(testVendor.getId(), firmId);
        assertThat(summary.getNetBalance()).isEqualByComparingTo(new BigDecimal("17500.00"));
        assertThat(summary.getBalanceStatus()).isEqualTo("PAYABLE");
    }

    @Test
    void testCustomerStatement1000RowsPaginationContinuity() throws Exception {
        LocalDate baseDate = LocalDate.of(2025, 1, 1);
        Customer scaleCustomer = customerRepo.save(Customer.builder()
                .name("Statement Scale Customer 1000")
                .firmId(firmId)
                .phone("9222222222")
                .build());

        // 1. Construct 1,000 actual ledger entries: 600 invoices (debits) and 400 payments (credits)
        List<Invoice> invoices = new ArrayList<>();
        for (int i = 1; i <= 600; i++) {
            Invoice inv = new Invoice();
            inv.setFirmId(firmId);
            inv.setCustomer(scaleCustomer);
            inv.setInvoiceNumber(String.format("INV-STMT-%04d", i));
            inv.setInvoiceDate(baseDate.plusDays(i).atStartOfDay());
            inv.setStatus(InvoiceStatus.UNPAID);
            inv.setTotalAmount(new BigDecimal("1000.00"));
            inv.setSubtotalWithoutTax(new BigDecimal("1000.00"));
            inv.setTotalDiscount(BigDecimal.ZERO);
            inv.setTotalTax(BigDecimal.ZERO);
            invoices.add(inv);
        }
        invoiceRepo.saveAll(invoices);

        List<InvoicePayment> payments = new ArrayList<>();
        for (int i = 1; i <= 400; i++) {
            Invoice inv = invoices.get(i - 1);
            InvoicePayment pmt = InvoicePayment.builder()
                    .firmId(firmId)
                    .invoiceId(inv.getId())
                    .customerId(scaleCustomer.getId())
                    .amount(new BigDecimal("400.00"))
                    .paymentDate(baseDate.plusDays(i))
                    .paymentMode("Bank Transfer")
                    .referenceNumber(String.format("PMT-STMT-%04d", i))
                    .build();
            payments.add(pmt);
        }
        invoicePaymentRepo.saveAll(payments);

        // 2 & 3. Fetch customer statement using real service path for all 1000 entries
        LocalDate filterEnd = baseDate.plusDays(700);
        CustomerStatementResponse fullStmt = statementService.getCustomerStatement(firmId, scaleCustomer.getId(), baseDate, filterEnd);
        List<StatementEntry> entries = fullStmt.getEntries();
        assertThat(entries).hasSize(1000);

        // 4. Chronological ordering
        for (int i = 1; i < entries.size(); i++) {
            assertThat(entries.get(i).getDate()).isAfterOrEqualTo(entries.get(i - 1).getDate());
        }

        // 5 & 6. Zero duplicate entries, zero missing entries
        Set<String> uniqueRefs = entries.stream().map(StatementEntry::getRef).collect(Collectors.toSet());
        assertThat(uniqueRefs).hasSize(1000);

        // 7, 8, 9. Independently calculate opening balance and every running balance
        double runningBalance = fullStmt.getOpeningBalance();
        assertThat(runningBalance).isEqualTo(0.0);

        double totalDebit = 0.0;
        double totalCredit = 0.0;
        for (StatementEntry entry : entries) {
            totalDebit += entry.getDebit();
            totalCredit += entry.getCredit();
            runningBalance = runningBalance + entry.getDebit() - entry.getCredit();
            assertThat(entry.getBalance()).isEqualTo(Math.round(runningBalance * 100.0) / 100.0);
        }

        // 12. Verify final closing balance
        // 600 * 1000 = 600,000.0 debit; 400 * 400 = 160,000.0 credit; closing = 440,000.0
        assertThat(fullStmt.getTotalBilled()).isEqualTo(600000.0);
        assertThat(fullStmt.getTotalPaid()).isEqualTo(160000.0);
        assertThat(fullStmt.getClosingBalance()).isEqualTo(440000.0);
        assertThat(runningBalance).isEqualTo(440000.0);

        // 10 & 11. Pagination continuity across simulated pages (e.g. pageSize = 25 -> 40 pages)
        int pageSize = 25;
        int totalPages = (int) Math.ceil((double) entries.size() / pageSize);
        assertThat(totalPages).isEqualTo(40);

        double lastPageClosingBalance = fullStmt.getOpeningBalance();
        for (int pageIdx = 0; pageIdx < totalPages; pageIdx++) {
            int fromIdx = pageIdx * pageSize;
            int toIdx = Math.min(fromIdx + pageSize, entries.size());
            List<StatementEntry> pageEntries = entries.subList(fromIdx, toIdx);

            assertThat(pageEntries).hasSize(25);

            // Page 2 and subsequent pages must continue from previous page's closing balance
            double firstEntryExpectedBalance = lastPageClosingBalance + pageEntries.get(0).getDebit() - pageEntries.get(0).getCredit();
            assertThat(pageEntries.get(0).getBalance()).isEqualTo(Math.round(firstEntryExpectedBalance * 100.0) / 100.0);

            if (pageIdx > 0) {
                // Page 2+ must NOT reset to 0 or opening balance
                assertThat(pageEntries.get(0).getBalance()).isGreaterThan(0.0);
            }

            lastPageClosingBalance = pageEntries.get(pageEntries.size() - 1).getBalance();
        }
        assertThat(lastPageClosingBalance).isEqualTo(440000.0);

        // Also test Date-Filtered Opening Balance carry-forward
        // Filter starting from day 101 -> first 100 invoices (100 * 1000 = 100k) and 100 payments (100 * 400 = 40k) carry forward
        LocalDate filterMid = baseDate.plusDays(101);
        CustomerStatementResponse midStmt = statementService.getCustomerStatement(firmId, scaleCustomer.getId(), filterMid, filterEnd);
        assertThat(midStmt.getOpeningBalance()).isEqualTo(60000.0); // 100k - 40k
        assertThat(midStmt.getClosingBalance()).isEqualTo(440000.0);
        assertThat(midStmt.getEntries()).hasSize(800); // 500 invoices + 300 payments in range

        // Generate Statement PDF
        byte[] pdfBytes = statementService.generateCustomerStatementPdf(firmId, scaleCustomer.getId(), baseDate, filterEnd);
        assertThat(pdfBytes).isNotEmpty();
        String header = new String(pdfBytes, 0, Math.min(pdfBytes.length, 8), StandardCharsets.US_ASCII);
        assertThat(header).startsWith("%PDF-");
    }
}
