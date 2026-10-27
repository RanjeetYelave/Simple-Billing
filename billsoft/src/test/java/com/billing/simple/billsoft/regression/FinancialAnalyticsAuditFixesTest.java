package com.billing.simple.billsoft.regression;

import com.billing.simple.billsoft.assistant.dto.OmnisearchQuickHelpDTOs.QuickHelpResponse;
import com.billing.simple.billsoft.assistant.dto.OmnisearchQuickHelpDTOs.SalesSummaryDTO;
import com.billing.simple.billsoft.assistant.service.OmnisearchQuickHelpService;
import com.billing.simple.billsoft.assistant.service.OmnisearchQuickHelpService.DateRangeWindow;
import com.billing.simple.billsoft.dtos.FirmAnalyticsResponse;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.repositories.PartyPaymentRepository;
import com.billing.simple.billsoft.repositories.PartyRepository;
import com.billing.simple.billsoft.repositories.PurchaseOrderRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.service.InvoiceService;
import com.billing.simple.billsoft.service.StatementServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regression and financial integrity tests verifying fixes for P0-P2 issues
 * documented in ANALYTICS_FINANCIAL_CORRECTNESS_AUDIT.md.
 */
class FinancialAnalyticsAuditFixesTest {

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");
    private static final Long FIRM_ID = 97L;

    @Mock
    private CustomerRepository customerRepo;
    @Mock
    private InvoiceRepository invoiceRepo;
    @Mock
    private InvoiceItemRepository invoiceItemRepo;
    @Mock
    private InvoicePaymentRepository invoicePaymentRepo;
    @Mock
    private PartyRepository partyRepo;
    @Mock
    private PartyPaymentRepository partyPaymentRepo;
    @Mock
    private PurchaseOrderRepository purchaseOrderRepo;
    @Mock
    private ExpenseRepository expenseRepo;
    @Mock
    private ProductRepository productRepo;
    @Mock
    private EmployeeRepository employeeRepo;
    @Mock
    private AttendanceRecordRepository attendanceRecordRepo;
    @Mock
    private SalesReturnRepository salesReturnRepo;
    @Mock
    private SalaryRecordRepository salaryRepo;
    @Mock
    private EmployeeAdvanceRepository advanceRepo;
    @Mock
    private StockMovementRepository stockMovementRepo;
    @Mock
    private AppConfigRepository appConfigRepo;

    @InjectMocks
    private OmnisearchQuickHelpService quickHelpService;

    @InjectMocks
    private StatementServiceImpl statementService;

    @InjectMocks
    private InvoiceService invoiceService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TenantContext.setCurrentFirmId(FIRM_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // P1 Fix 2: Omniseach period / range Parameter Normalization & Validation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("P1 Fix 2: DateRangeWindow resolves standard canonical periods correctly")
    void testCanonicalPeriodResolution() {
        LocalDate todayIST = LocalDate.now(IST_ZONE);

        // 1. Today
        DateRangeWindow todayWin = quickHelpService.resolveDateRange("today", null, null);
        assertEquals(todayIST, todayWin.fromDate);
        assertEquals(todayIST, todayWin.toDate);

        // 2. Yesterday
        DateRangeWindow yestWin = quickHelpService.resolveDateRange("yesterday", null, null);
        assertEquals(todayIST.minusDays(1), yestWin.fromDate);
        assertEquals(todayIST.minusDays(1), yestWin.toDate);

        // 3. This Week (spaced and underscored)
        DateRangeWindow weekWin1 = quickHelpService.resolveDateRange("this week", null, null);
        DateRangeWindow weekWin2 = quickHelpService.resolveDateRange("this_week", null, null);
        assertEquals(weekWin1.fromDate, weekWin2.fromDate);
        assertEquals(todayIST, weekWin1.toDate);

        // 4. This Month (spaced and underscored)
        DateRangeWindow monthWin1 = quickHelpService.resolveDateRange("this month", null, null);
        DateRangeWindow monthWin2 = quickHelpService.resolveDateRange("this_month", null, null);
        assertEquals(todayIST.withDayOfMonth(1), monthWin1.fromDate);
        assertEquals(todayIST, monthWin1.toDate);

        // 5. This Year / FY (spaced and underscored)
        DateRangeWindow yearWin1 = quickHelpService.resolveDateRange("this year", null, null);
        DateRangeWindow yearWin2 = quickHelpService.resolveDateRange("this_fy", null, null);
        assertEquals(yearWin1.fromDate, yearWin2.fromDate);
        int expectedFyStartYear = todayIST.getMonthValue() >= 4 ? todayIST.getYear() : todayIST.getYear() - 1;
        assertEquals(LocalDate.of(expectedFyStartYear, 4, 1), yearWin1.fromDate);

        // 6. Null / empty parameter defaults safely to today
        DateRangeWindow nullWin = quickHelpService.resolveDateRange(null, null, null);
        assertEquals("Today", nullWin.label);
        DateRangeWindow emptyWin = quickHelpService.resolveDateRange("   ", null, null);
        assertEquals("Today", emptyWin.label);
    }

    @Test
    @DisplayName("P1 Fix 2: Unsupported periods throw IllegalArgumentException rather than silently returning today")
    void testUnsupportedPeriodRejection() {
        assertThrows(IllegalArgumentException.class, () -> {
            quickHelpService.resolveDateRange("invalid_period_foo", null, null);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            quickHelpService.resolveDateRange("tomorrow_unknown", null, null);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // P1 Fix 3: UTC-vs-IST Date Boundary Semantics
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("P1 Fix 3: Business zone is explicitly Asia/Kolkata across all queries")
    void testBusinessZoneConstant() {
        assertEquals("Asia/Kolkata", OmnisearchQuickHelpService.BUSINESS_ZONE.getId());
        LocalDate nowKolkata = LocalDate.now(IST_ZONE);
        DateRangeWindow win = quickHelpService.resolveDateRange("today", null, null);
        assertEquals(nowKolkata, win.fromDate);
        assertEquals(nowKolkata, win.toDate);
    }

    @Test
    @DisplayName("P1 Fix 3: Deterministic instants around Indian date boundary (00:00, 05:29, 05:30, 23:59 IST) resolve correct business date")
    void testDeterministicInstantsAroundIndianDateBoundary() {
        LocalDate expectedToday = LocalDate.of(2026, 10, 26);
        LocalDate expectedYesterday = LocalDate.of(2026, 10, 25);

        // 4 boundary instants in Asia/Kolkata:
        // 1. 00:00:00 IST (18:30 UTC previous day)
        // 2. 05:29:59 IST (23:59:59 UTC previous day - critical UTC drift boundary)
        // 3. 05:30:00 IST (00:00:00 UTC same day)
        // 4. 23:59:59 IST (18:29:59 UTC same day)
        java.time.ZonedDateTime[] boundaryInstants = {
                java.time.ZonedDateTime.of(2026, 10, 26, 0, 0, 0, 0, IST_ZONE),
                java.time.ZonedDateTime.of(2026, 10, 26, 5, 29, 59, 999999999, IST_ZONE),
                java.time.ZonedDateTime.of(2026, 10, 26, 5, 30, 0, 0, IST_ZONE),
                java.time.ZonedDateTime.of(2026, 10, 26, 23, 59, 59, 999999999, IST_ZONE)
        };

        for (java.time.ZonedDateTime instant : boundaryInstants) {
            java.time.Clock fixedClock = java.time.Clock.fixed(instant.toInstant(), IST_ZONE);

            DateRangeWindow todayWin = quickHelpService.resolveDateRange("today", null, null, fixedClock);
            DateRangeWindow yestWin = quickHelpService.resolveDateRange("yesterday", null, null, fixedClock);

            // Business date must always be 2026-10-26 in IST regardless of early-morning UTC offset
            assertEquals(expectedToday, todayWin.fromDate, "Today fromDate failed at instant " + instant);
            assertEquals(expectedToday, todayWin.toDate, "Today toDate failed at instant " + instant);
            assertEquals(expectedYesterday, yestWin.fromDate, "Yesterday fromDate failed at instant " + instant);
            assertEquals(expectedYesterday, yestWin.toDate, "Yesterday toDate failed at instant " + instant);

            // Assert contiguous, non-overlapping date boundaries
            assertEquals(expectedYesterday.plusDays(1), todayWin.fromDate);
            assertTrue(yestWin.toDate.isBefore(todayWin.fromDate));
            assertTrue(todayWin.getStartDateTime().isAfter(yestWin.getEndDateTime()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // P1 Fix 4: Align Omniseach Sales Calculations With Dashboard Rules
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("P1 Fix 4: SalesSummary deducts sales returns and skips non-final documents")
    void testSalesSummaryDeductsReturnsAndSkipsDrafts() {
        // Arrange
        Invoice invEligible = new Invoice();
        invEligible.setId(101L);
        invEligible.setFirmId(FIRM_ID);
        invEligible.setStatus(InvoiceStatus.UNPAID);
        invEligible.setTotalAmount(new BigDecimal("5000.00"));
        invEligible.setTotalTax(new BigDecimal("900.00"));
        invEligible.setInvoiceDate(LocalDateTime.now());

        Invoice invDraft = new Invoice();
        invDraft.setId(102L);
        invDraft.setFirmId(FIRM_ID);
        invDraft.setStatus(InvoiceStatus.DRAFT);
        invDraft.setTotalAmount(new BigDecimal("2000.00"));
        invDraft.setInvoiceDate(LocalDateTime.now());

        Invoice invEstimate = new Invoice();
        invEstimate.setId(103L);
        invEstimate.setFirmId(FIRM_ID);
        invEstimate.setStatus(InvoiceStatus.ESTIMATE);
        invEstimate.setTotalAmount(new BigDecimal("3000.00"));
        invEstimate.setInvoiceDate(LocalDateTime.now());

        Invoice invCancelled = new Invoice();
        invCancelled.setId(104L);
        invCancelled.setFirmId(FIRM_ID);
        invCancelled.setStatus(InvoiceStatus.CANCELLED);
        invCancelled.setTotalAmount(new BigDecimal("4000.00"));
        invCancelled.setInvoiceDate(LocalDateTime.now());

        when(invoiceRepo.findAllByFirmIdAndInvoiceDateBetweenOrderByInvoiceDateAsc(eq(FIRM_ID), any(), any()))
                .thenReturn(List.of(invEligible, invDraft, invEstimate, invCancelled));

        // Mock a sales return of ₹1,000 against eligible invoice 101
        List<Object[]> returnAggs = new ArrayList<>();
        returnAggs.add(new Object[]{101L, new BigDecimal("1000.00")});
        when(salesReturnRepo.sumRefundTotalsByInvoiceForFirm(FIRM_ID)).thenReturn(returnAggs);

        when(invoicePaymentRepo.findByFirmIdAndPaymentDateBetweenOrderByPaymentDateAscIdAsc(eq(FIRM_ID), any(), any()))
                .thenReturn(Collections.emptyList());

        // Act
        QuickHelpResponse<SalesSummaryDTO> response = quickHelpService.getSalesSummary("today", null, null);

        // Assert: 5000 - 1000 return = 4000 net revenue. Non-final invoices (draft, estimate, cancelled) excluded.
        assertNotNull(response.getData());
        assertEquals(new BigDecimal("4000.00"), response.getData().getTotalRevenue());
        assertEquals(1, response.getData().getInvoiceCount());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // P2 Fix 5: Draft Purchase Orders Excluded From Vendor Liabilities
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("P2 Fix 5: Draft purchase orders do not create vendor statement liabilities")
    void testDraftPurchaseOrdersExcludedFromVendorStatements() {
        Party party = new Party();
        party.setId(50L);
        party.setFirmId(FIRM_ID);
        party.setName("Test Vendor");
        party.setOpeningBalance(BigDecimal.ZERO);
        party.setOpeningBalanceType("PAYABLE");

        when(partyRepo.findById(50L)).thenReturn(Optional.of(party));
        when(partyRepo.findByIdAndFirmId(50L, FIRM_ID)).thenReturn(Optional.of(party));

        PurchaseOrder draftPO = new PurchaseOrder();
        draftPO.setId(201L);
        draftPO.setParty(party);
        draftPO.setFirmId(FIRM_ID);
        draftPO.setTotalAmount(new BigDecimal("15000.00"));
        draftPO.setStatus(PurchaseOrderStatus.DRAFT);
        draftPO.setPoDate(LocalDate.now());

        PurchaseOrder issuedPO = new PurchaseOrder();
        issuedPO.setId(202L);
        issuedPO.setPoNumber("PO-202");
        issuedPO.setParty(party);
        issuedPO.setFirmId(FIRM_ID);
        issuedPO.setTotalAmount(new BigDecimal("25000.00"));
        issuedPO.setStatus(PurchaseOrderStatus.ISSUED);
        issuedPO.setPoDate(LocalDate.now());

        PurchaseOrder cancelledPO = new PurchaseOrder();
        cancelledPO.setId(203L);
        cancelledPO.setParty(party);
        cancelledPO.setFirmId(FIRM_ID);
        cancelledPO.setTotalAmount(new BigDecimal("10000.00"));
        cancelledPO.setStatus(PurchaseOrderStatus.CANCELLED);
        cancelledPO.setPoDate(LocalDate.now());

        when(purchaseOrderRepo.findByFirmIdAndPartyIdAndPoDateBefore(eq(FIRM_ID), eq(50L), any()))
                .thenReturn(Collections.emptyList());
        when(partyPaymentRepo.findByFirmIdAndPartyIdAndPaymentDateBefore(eq(FIRM_ID), eq(50L), any()))
                .thenReturn(Collections.emptyList());
        when(purchaseOrderRepo.findByFirmIdAndPartyIdAndPoDateBetweenOrderByPoDateAscIdAsc(eq(FIRM_ID), eq(50L), any(), any()))
                .thenReturn(List.of(draftPO, issuedPO, cancelledPO));
        when(partyPaymentRepo.findByFirmIdAndPartyIdAndPaymentDateBetweenOrderByPaymentDateAscIdAsc(eq(FIRM_ID), eq(50L), any(), any()))
                .thenReturn(Collections.emptyList());

        // Act
        var statement = statementService.getPartyStatement(FIRM_ID, 50L, LocalDate.now().minusDays(30), LocalDate.now());

        // Assert: Only issuedPO (₹25,000) contributes to liability. Draft (₹15,000) and Cancelled (₹10,000) excluded.
        assertEquals(25000.00, statement.getTotalPurchases(), 0.001);
        assertEquals(25000.00, statement.getClosingBalance(), 0.001);
        assertEquals(1, statement.getEntries().size());
        assertEquals("PO-202", statement.getEntries().get(0).getRef());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // P2 Fix 6: Indian Financial Year Calculations (April 1 to March 31)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("P2 Fix 6: Dashboard businessThisYear evaluates Indian FY boundaries")
    void testIndianFinancialYearAnalytics() {
        LocalDate today = LocalDate.now(IST_ZONE);
        int currentFyStartYear = today.getMonthValue() >= 4 ? today.getYear() : today.getYear() - 1;

        // Invoice within current Indian FY
        Invoice invCurrentFy = new Invoice();
        invCurrentFy.setId(301L);
        invCurrentFy.setFirmId(FIRM_ID);
        invCurrentFy.setStatus(InvoiceStatus.PAID);
        invCurrentFy.setTotalAmount(new BigDecimal("10000.00"));
        invCurrentFy.setInvoiceDate(LocalDateTime.of(currentFyStartYear, 6, 15, 12, 0));

        // Invoice from previous Indian FY (e.g. March 15 of previous FY)
        Invoice invPriorFy = new Invoice();
        invPriorFy.setId(302L);
        invPriorFy.setFirmId(FIRM_ID);
        invPriorFy.setStatus(InvoiceStatus.PAID);
        invPriorFy.setTotalAmount(new BigDecimal("8000.00"));
        invPriorFy.setInvoiceDate(LocalDateTime.of(currentFyStartYear - 1, 8, 10, 12, 0));

        when(invoiceRepo.findInvoicesWithCustomerForAnalytics(eq(FIRM_ID), anyList()))
                .thenReturn(new ArrayList<>(List.of(invCurrentFy, invPriorFy)));
        when(invoiceRepo.findAllByFirmIdAndStatusOrderByInvoiceDateAsc(eq(FIRM_ID), eq(InvoiceStatus.ESTIMATE)))
                .thenReturn(Collections.emptyList());
        when(invoicePaymentRepo.findByFirmIdOrderByPaymentDateDescIdDesc(FIRM_ID))
                .thenReturn(Collections.emptyList());
        when(salesReturnRepo.sumRefundTotalsByInvoiceForFirm(FIRM_ID))
                .thenReturn(Collections.emptyList());

        FirmAnalyticsResponse analytics = invoiceService.getFirmAnalytics(FIRM_ID);

        // Total business includes all non-cancelled invoices (10000 + 8000 = 18000)
        assertEquals(18000.00, analytics.getTotalBusiness(), 0.001);
        // businessThisYear must ONLY include invoices in the current Indian FY (10000.00), not prior FY
        assertEquals(10000.00, analytics.getBusinessThisYear(), 0.001);
    }

    @Test
    @DisplayName("P2 Fix 6: Strict April 1 boundary, March 31 prior FY exclusion, and January in-FY recognition")
    void testIndianFinancialYearEdgeBoundaries() {
        LocalDate today = LocalDate.now(IST_ZONE);
        int currentFyStartYear = today.getMonthValue() >= 4 ? today.getYear() : today.getYear() - 1;

        // 1. Invoice exactly on start boundary: April 1 00:00:00 of current FY
        Invoice invStartBoundary = new Invoice();
        invStartBoundary.setId(401L);
        invStartBoundary.setFirmId(FIRM_ID);
        invStartBoundary.setStatus(InvoiceStatus.FINAL);
        invStartBoundary.setTotalAmount(new BigDecimal("5000.00"));
        invStartBoundary.setInvoiceDate(LocalDateTime.of(currentFyStartYear, 4, 1, 0, 0, 0));

        // 2. Invoice immediately before start boundary: March 31 23:59:59 of prior FY (must be EXCLUDED from current FY)
        Invoice invPriorFyEnd = new Invoice();
        invPriorFyEnd.setId(402L);
        invPriorFyEnd.setFirmId(FIRM_ID);
        invPriorFyEnd.setStatus(InvoiceStatus.FINAL);
        invPriorFyEnd.setTotalAmount(new BigDecimal("7000.00"));
        invPriorFyEnd.setInvoiceDate(LocalDateTime.of(currentFyStartYear, 3, 31, 23, 59, 59));

        // 3. Invoice in January (calendar new year, but same Indian FY)
        // If current date is >= April, this was from previous calendar year portion or January before April
        // We test an invoice within the current Indian FY window
        Invoice invInCurrentFy = new Invoice();
        invInCurrentFy.setId(403L);
        invInCurrentFy.setFirmId(FIRM_ID);
        invInCurrentFy.setStatus(InvoiceStatus.PAID);
        invInCurrentFy.setTotalAmount(new BigDecimal("3000.00"));
        // Pick a date guaranteed to be within current FY and <= today
        invInCurrentFy.setInvoiceDate(LocalDateTime.of(currentFyStartYear, 5, 1, 10, 0, 0));

        when(invoiceRepo.findInvoicesWithCustomerForAnalytics(eq(FIRM_ID), anyList()))
                .thenReturn(new ArrayList<>(List.of(invStartBoundary, invPriorFyEnd, invInCurrentFy)));
        when(invoiceRepo.findAllByFirmIdAndStatusOrderByInvoiceDateAsc(eq(FIRM_ID), eq(InvoiceStatus.ESTIMATE)))
                .thenReturn(Collections.emptyList());
        when(invoicePaymentRepo.findByFirmIdOrderByPaymentDateDescIdDesc(FIRM_ID))
                .thenReturn(Collections.emptyList());
        when(salesReturnRepo.sumRefundTotalsByInvoiceForFirm(FIRM_ID))
                .thenReturn(Collections.emptyList());

        FirmAnalyticsResponse analytics = invoiceService.getFirmAnalytics(FIRM_ID);

        // Total business: 5000 + 7000 + 3000 = 15000
        assertEquals(15000.00, analytics.getTotalBusiness(), 0.001);
        // businessThisYear: 5000 (April 1 start boundary) + 3000 (in-FY) = 8000.
        // March 31 (₹7,000) of prior FY is strictly excluded.
        assertEquals(8000.00, analytics.getBusinessThisYear(), 0.001);
    }
}
