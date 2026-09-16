package com.billing.simple.billsoft.kpi;

import com.billing.simple.billsoft.dtos.FirmAnalyticsResponse;
import com.billing.simple.billsoft.dtos.PartyFinancialSummary;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoicePayment;
import com.billing.simple.billsoft.entities.InvoiceStatus;
import com.billing.simple.billsoft.entities.SalesReturn;
import com.billing.simple.billsoft.kpi.dto.CustomerKpiSummary;
import com.billing.simple.billsoft.kpi.dto.KpiDashboardResponse;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repo.InvoicePaymentRepository;
import com.billing.simple.billsoft.repo.InvoiceRepository;
import com.billing.simple.billsoft.repo.SalesReturnRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.service.ExpenseService;
import com.billing.simple.billsoft.service.InvoiceService;
import com.billing.simple.billsoft.service.PartyService;
import com.billing.simple.billsoft.service.ProductService;
import com.billing.simple.billsoft.service.SavingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KpiServiceTest {

    @Mock
    private InvoiceService invoiceService;

    @Mock
    private PartyService partyService;

    @Mock
    private ProductService productService;

    @Mock
    private ExpenseService expenseService;

    @Mock
    private SavingService savingService;

    @Mock
    private CustomerRepository customerRepo;

    @Mock
    private InvoiceRepository invoiceRepo;

    @Mock
    private InvoicePaymentRepository invoicePaymentRepo;

    @Mock
    private SalesReturnRepository salesReturnRepo;

    @InjectMocks
    private KpiServiceImpl kpiService;

    private final Long FIRM_A = 101L;
    private final Long FIRM_B = 202L;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentFirmId(FIRM_A);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testDashboardKpisAggregation() {
        // Setup InvoiceService analytics
        FirmAnalyticsResponse analytics = new FirmAnalyticsResponse();
        analytics.setTotalBusiness(150000.0);
        analytics.setBusinessToday(25000.0);
        analytics.setBusinessThisWeek(45000.0);
        analytics.setBusinessThisMonth(110000.0);
        analytics.setBusinessThisYear(150000.0);
        analytics.setTotalPaid(100000.0);
        analytics.setTotalPending(50000.0);
        analytics.setUnpaidInvoiceCount(5L);
        analytics.setOverdueInvoiceCount(2L);
        analytics.setQuotePipelineValue(30000.0);
        analytics.setQuoteCount(3L);
        when(invoiceService.getFirmAnalytics(FIRM_A)).thenReturn(analytics);

        // Setup PartyService summaries
        PartyFinancialSummary ps1 = PartyFinancialSummary.builder()
                .partyId(1L)
                .netBalance(new BigDecimal("12000.00")) // Payable
                .pendingPurchaseOrders(2L)
                .build();
        PartyFinancialSummary ps2 = PartyFinancialSummary.builder()
                .partyId(2L)
                .netBalance(new BigDecimal("-3000.00")) // Advance (not payable)
                .pendingPurchaseOrders(0L)
                .build();
        PartyFinancialSummary ps3 = PartyFinancialSummary.builder()
                .partyId(3L)
                .netBalance(new BigDecimal("8000.00")) // Payable
                .pendingPurchaseOrders(1L)
                .build();
        when(partyService.getAllPartiesWithFinancialSummaries(FIRM_A)).thenReturn(List.of(ps1, ps2, ps3));

        // Setup ProductService inventory summary
        Map<String, Object> invSummary = Map.of(
                "totalProducts", 45L,
                "totalCostValue", new BigDecimal("450000.00"),
                "totalRetailValue", new BigDecimal("600000.00"),
                "lowStockCount", 4L,
                "outOfStockCount", 1L
        );
        when(productService.getInventorySummary(FIRM_A)).thenReturn(invSummary);

        // Setup ExpenseService summary
        Map<String, Object> expSummary = Map.of(
                "totalAllTime", new BigDecimal("85000.00"),
                "totalCurrentMonth", new BigDecimal("12000.00")
        );
        when(expenseService.getSummaryByFirm(FIRM_A)).thenReturn(expSummary);

        // Setup SavingService summary
        Map<String, Object> savSummary = Map.of(
                "totalAllTime", new BigDecimal("35000.00"),
                "totalCurrentMonth", new BigDecimal("5000.00")
        );
        when(savingService.getSummaryByFirm(FIRM_A)).thenReturn(savSummary);

        // Execute
        KpiDashboardResponse resp = kpiService.getDashboardKpis(FIRM_A);

        // Verify
        assertNotNull(resp);
        assertEquals(150000.0, resp.getTotalBusiness());
        assertEquals(25000.0, resp.getBusinessToday());
        assertEquals(45000.0, resp.getBusinessThisWeek());
        assertEquals(110000.0, resp.getBusinessThisMonth());
        assertEquals(150000.0, resp.getBusinessThisYear());
        assertEquals(100000.0, resp.getTotalPaid());
        assertEquals(50000.0, resp.getTotalPending());
        assertEquals(5L, resp.getUnpaidInvoiceCount());
        assertEquals(2L, resp.getOverdueInvoiceCount());
        assertEquals(30000.0, resp.getQuotePipelineValue());
        assertEquals(3L, resp.getQuoteCount());

        // Vendor payables: 12000 + 8000 = 20000 (ignoring negative balance/advance)
        assertEquals(20000.0, resp.getVendorPayables());
        assertEquals(3L, resp.getOpenPoCount());
        assertEquals(3L, resp.getTotalVendors());

        // Inventory valuation
        assertEquals(450000.0, resp.getTotalInventoryCostValue());
        assertEquals(600000.0, resp.getTotalInventoryRetailValue());
        assertEquals(4L, resp.getLowStockCount());
        assertEquals(1L, resp.getOutOfStockCount());
        assertEquals(45L, resp.getTotalProducts());

        // Expenses & Savings
        assertEquals(85000.0, resp.getTotalExpenses());
        assertEquals(12000.0, resp.getCurrentMonthExpenses());
        assertEquals(35000.0, resp.getTotalSavings());
        assertEquals(5000.0, resp.getCurrentMonthSavings());
    }

    @Test
    void testCustomerKpiSummaryAccountingSemantics() {
        // Customer 1: Billed 10,000, Returns 2,000, Paid 3,000 -> Net Dues = 5,000
        Customer c1 = new Customer();
        c1.setId(1L);
        c1.setName("Alice Traders");
        c1.setPhone("9876543210");
        c1.setEmail("alice@test.com");
        c1.setGstin("27AAAAA0000A1Z5");
        c1.setAddress("Mumbai");
        c1.setFirmId(FIRM_A);

        // Customer 2: Billed 5,000, Paid 5,000 -> Net Dues = 0 (Settled)
        Customer c2 = new Customer();
        c2.setId(2L);
        c2.setName("Bob Enterprises");
        c2.setPhone("9876543211");
        c2.setFirmId(FIRM_A);

        when(customerRepo.findByFirmIdOrderByNameAsc(FIRM_A)).thenReturn(List.of(c1, c2));

        // Invoices for Alice: Inv 1 (6000, UNPAID, Overdue), Inv 2 (4000, UNPAID, future due date)
        Invoice inv1 = new Invoice();
        inv1.setId(101L);
        inv1.setCustomer(c1);
        inv1.setFirmId(FIRM_A);
        inv1.setTotalAmount(new BigDecimal("6000.00"));
        inv1.setStatus(InvoiceStatus.UNPAID);
        inv1.setInvoiceDate(LocalDateTime.now().minusDays(10));
        inv1.setDueDate(LocalDate.now().minusDays(2)); // Overdue

        Invoice inv2 = new Invoice();
        inv2.setId(102L);
        inv2.setCustomer(c1);
        inv2.setFirmId(FIRM_A);
        inv2.setTotalAmount(new BigDecimal("4000.00"));
        inv2.setStatus(InvoiceStatus.UNPAID);
        inv2.setInvoiceDate(LocalDateTime.now().minusDays(3));
        inv2.setDueDate(LocalDate.now().plusDays(5)); // Not overdue

        // Invoice for Bob: Inv 3 (5000, PAID)
        Invoice inv3 = new Invoice();
        inv3.setId(103L);
        inv3.setCustomer(c2);
        inv3.setFirmId(FIRM_A);
        inv3.setTotalAmount(new BigDecimal("5000.00"));
        inv3.setStatus(InvoiceStatus.PAID);
        inv3.setPaid(true);

        when(invoiceRepo.findInvoicesWithCustomerForAnalytics(eq(FIRM_A), any()))
                .thenReturn(List.of(inv1, inv2, inv3));

        // Payments: Alice paid 3000, Bob paid 5000
        InvoicePayment p1 = new InvoicePayment();
        p1.setId(201L);
        p1.setCustomerId(1L);
        p1.setInvoiceId(101L);
        p1.setAmount(new BigDecimal("3000.00"));
        p1.setFirmId(FIRM_A);

        InvoicePayment p2 = new InvoicePayment();
        p2.setId(202L);
        p2.setCustomerId(2L);
        p2.setInvoiceId(103L);
        p2.setAmount(new BigDecimal("5000.00"));
        p2.setFirmId(FIRM_A);

        when(invoicePaymentRepo.findByFirmIdOrderByPaymentDateDescIdDesc(FIRM_A))
                .thenReturn(List.of(p1, p2));

        // Sales return for Alice: 2000 refund credit
        SalesReturn sr1 = new SalesReturn();
        sr1.setId(301L);
        sr1.setCustomer(c1);
        sr1.setTotalRefundAmount(new BigDecimal("2000.00"));
        sr1.setFirmId(FIRM_A);

        when(salesReturnRepo.findByFirmIdOrderByReturnDateDescIdDesc(FIRM_A))
                .thenReturn(List.of(sr1));

        // Execute
        List<CustomerKpiSummary> summaries = kpiService.getCustomerKpiSummaries(FIRM_A);

        // Verify
        assertEquals(2, summaries.size());

        // Alice's KPI
        CustomerKpiSummary s1 = summaries.stream().filter(s -> s.getCustomerId().equals(1L)).findFirst().orElseThrow();
        assertEquals("Alice Traders", s1.getCustomerName());
        assertEquals(10000.0, s1.getTotalBilled());
        assertEquals(3000.0, s1.getTotalPaid());
        assertEquals(2000.0, s1.getTotalReturns());
        // Net Balance = (10000 - 2000) - 3000 = 5000
        assertEquals(5000.0, s1.getNetBalance());
        assertEquals(2L, s1.getInvoiceCount());
        assertEquals(2L, s1.getUnpaidInvoiceCount());
        assertEquals(1L, s1.getOverdueInvoiceCount());

        // Bob's KPI
        CustomerKpiSummary s2 = summaries.stream().filter(s -> s.getCustomerId().equals(2L)).findFirst().orElseThrow();
        assertEquals("Bob Enterprises", s2.getCustomerName());
        assertEquals(5000.0, s2.getTotalBilled());
        assertEquals(5000.0, s2.getTotalPaid());
        assertEquals(0.0, s2.getTotalReturns());
        assertEquals(0.0, s2.getNetBalance());
        assertEquals(1L, s2.getInvoiceCount());
        assertEquals(0L, s2.getUnpaidInvoiceCount());
        assertEquals(0L, s2.getOverdueInvoiceCount());
    }

    @Test
    void testFirmIsolation_FirmBDataDoesNotLeakIntoFirmA() {
        Customer cA = new Customer();
        cA.setId(10L);
        cA.setName("Firm A Customer");
        cA.setFirmId(FIRM_A);

        when(customerRepo.findByFirmIdOrderByNameAsc(FIRM_A)).thenReturn(List.of(cA));
        when(invoiceRepo.findInvoicesWithCustomerForAnalytics(eq(FIRM_A), any())).thenReturn(Collections.emptyList());
        when(invoicePaymentRepo.findByFirmIdOrderByPaymentDateDescIdDesc(FIRM_A)).thenReturn(Collections.emptyList());
        when(salesReturnRepo.findByFirmIdOrderByReturnDateDescIdDesc(FIRM_A)).thenReturn(Collections.emptyList());

        List<CustomerKpiSummary> summariesA = kpiService.getCustomerKpiSummaries(FIRM_A);
        assertEquals(1, summariesA.size());
        assertEquals("Firm A Customer", summariesA.get(0).getCustomerName());
    }

    @Test
    void testEmptyFirmScenario() {
        when(customerRepo.findByFirmIdOrderByNameAsc(FIRM_B)).thenReturn(Collections.emptyList());

        List<CustomerKpiSummary> summaries = kpiService.getCustomerKpiSummaries(FIRM_B);
        assertNotNull(summaries);
        assertTrue(summaries.isEmpty());

        when(invoiceService.getFirmAnalytics(FIRM_B)).thenReturn(new FirmAnalyticsResponse());
        when(partyService.getAllPartiesWithFinancialSummaries(FIRM_B)).thenReturn(Collections.emptyList());
        when(productService.getInventorySummary(FIRM_B)).thenReturn(Collections.emptyMap());
        when(expenseService.getSummaryByFirm(FIRM_B)).thenReturn(Collections.emptyMap());
        when(savingService.getSummaryByFirm(FIRM_B)).thenReturn(Collections.emptyMap());

        KpiDashboardResponse dash = kpiService.getDashboardKpis(FIRM_B);
        assertNotNull(dash);
        assertEquals(0.0, dash.getTotalBusiness());
        assertEquals(0.0, dash.getVendorPayables());
        assertEquals(0.0, dash.getTotalInventoryCostValue());
    }
}
