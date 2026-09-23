package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.assistant.dto.OmnisearchQuickHelpDTOs.*;
import com.billing.simple.billsoft.assistant.service.OmnisearchQuickHelpService;
import com.billing.simple.billsoft.dataprotection.DataProtectionService;
import com.billing.simple.billsoft.dto.NotificationPreferencesDto;
import com.billing.simple.billsoft.dto.NotificationSummaryResponse;
import com.billing.simple.billsoft.dtos.ApiDiagnosticsResponse;
import com.billing.simple.billsoft.kpi.KpiService;
import com.billing.simple.billsoft.kpi.dto.CustomerKpiSummary;
import com.billing.simple.billsoft.kpi.dto.KpiDashboardResponse;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import com.billing.simple.billsoft.repo.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class ApiDiagnosticsServiceTest {

    private InvoiceService invoiceService;
    private CustomerService customerService;
    private ProductService productService;
    private PartyService partyService;
    private PurchaseOrderService poService;
    private StatementService statementService;
    private FirmDetailsService firmService;
    private NoteService noteService;
    private BusinessLetterService letterService;
    private ExpenseService expenseService;
    private ReminderService reminderService;
    private InboxMessageService inboxMessageService;
    private DevLogService devLogService;
    private UpdateService updateService;
    private AutoBackupService autoBackupService;
    private SystemMetricsService systemMetricsService;
    private EmployeeRepository employeeRepo;
    private AppConfigRepository appConfigRepo;

    private NotificationService notificationService;
    private KpiService kpiService;
    private GoalService goalService;
    private SavingService savingService;
    private SavedItemService savedItemService;
    private ItemDeduplicationService deduplicationService;
    private AnnouncementService announcementService;
    private OmnisearchQuickHelpService quickHelpService;
    private DataProtectionService dataProtectionService;
    private TenantDataIntegrityAuditService tenantDataIntegrityAuditService;
    private NetworkReachabilityService networkReachabilityService;

    private ApiDiagnosticsService apiDiagnosticsService;

    @BeforeEach
    void setUp() {
        invoiceService = Mockito.mock(InvoiceService.class);
        customerService = Mockito.mock(CustomerService.class);
        productService = Mockito.mock(ProductService.class);
        partyService = Mockito.mock(PartyService.class);
        poService = Mockito.mock(PurchaseOrderService.class);
        statementService = Mockito.mock(StatementService.class);
        firmService = Mockito.mock(FirmDetailsService.class);
        noteService = Mockito.mock(NoteService.class);
        letterService = Mockito.mock(BusinessLetterService.class);
        expenseService = Mockito.mock(ExpenseService.class);
        reminderService = Mockito.mock(ReminderService.class);
        inboxMessageService = Mockito.mock(InboxMessageService.class);
        devLogService = Mockito.mock(DevLogService.class);
        updateService = Mockito.mock(UpdateService.class);
        autoBackupService = Mockito.mock(AutoBackupService.class);
        systemMetricsService = Mockito.mock(SystemMetricsService.class);
        employeeRepo = Mockito.mock(EmployeeRepository.class);
        appConfigRepo = Mockito.mock(AppConfigRepository.class);

        notificationService = Mockito.mock(NotificationService.class);
        kpiService = Mockito.mock(KpiService.class);
        goalService = Mockito.mock(GoalService.class);
        savingService = Mockito.mock(SavingService.class);
        savedItemService = Mockito.mock(SavedItemService.class);
        deduplicationService = Mockito.mock(ItemDeduplicationService.class);
        announcementService = Mockito.mock(AnnouncementService.class);
        quickHelpService = Mockito.mock(OmnisearchQuickHelpService.class);
        dataProtectionService = Mockito.mock(DataProtectionService.class);
        tenantDataIntegrityAuditService = Mockito.mock(TenantDataIntegrityAuditService.class);
        networkReachabilityService = Mockito.mock(NetworkReachabilityService.class);

        apiDiagnosticsService = new ApiDiagnosticsService(
                invoiceService, customerService, productService, partyService, poService,
                statementService, firmService, noteService, letterService, expenseService,
                reminderService, inboxMessageService, devLogService, updateService,
                autoBackupService, systemMetricsService, employeeRepo, appConfigRepo,
                notificationService, kpiService, goalService, savingService,
                savedItemService, deduplicationService, announcementService,
                quickHelpService, dataProtectionService, tenantDataIntegrityAuditService,
                networkReachabilityService
        );
    }

    @Test
    void testRunFullApiSuite_AllHealthy() {
        // Billing & Invoices
        when(invoiceService.getAll(null)).thenReturn(Collections.emptyList());
        when(invoiceService.getAllFinalInvoices(any(), any(Pageable.class))).thenReturn(Collections.emptyList());
        when(invoiceService.getAllEstimates(any())).thenReturn(Collections.emptyList());
        when(invoiceService.peekNextInvoiceNumber(any())).thenReturn("INV-001");
        when(invoiceService.peekNextEstimateNumber(any())).thenReturn("EST-001");
        when(invoiceService.generateInvoiceNumber(any())).thenReturn("INV-001");
        when(invoiceService.generateEstimateNumber(any())).thenReturn("EST-001");
        when(invoiceService.getAllSalesReturns(any())).thenReturn(Collections.emptyList());
        when(invoiceService.peekNextReturnNumber(any())).thenReturn("RET-001");
        when(invoiceService.generateReturnNumber(any())).thenReturn("RET-001");
        when(invoiceService.getFirmAnalytics(any())).thenReturn(null);

        // Customers & Products
        when(customerService.getAll(any())).thenReturn(Collections.emptyList());
        when(productService.getAll(any())).thenReturn(Collections.emptyList());
        when(productService.getInventorySummary(any())).thenReturn(Map.of("totalProducts", 0));
        when(productService.getCategories(any())).thenReturn(Collections.emptyList());
        when(productService.getStockMovements(any(), any())).thenReturn(Collections.emptyList());

        // Saved Items & Deduplication
        when(savedItemService.getAllSavedItems(any())).thenReturn(Collections.emptyList());
        when(savedItemService.searchUnifiedItems(any(), anyString())).thenReturn(Collections.emptyList());
        when(deduplicationService.findDuplicateCandidates(any())).thenReturn(Collections.emptyList());

        // Vendors & POs
        when(partyService.getPartiesByFirm(any())).thenReturn(Collections.emptyList());
        when(partyService.getAllPartiesWithFinancialSummaries(any())).thenReturn(Collections.emptyList());
        when(poService.getPurchaseOrdersByFirm(any())).thenReturn(Collections.emptyList());
        when(poService.generateNextPoNumber(any())).thenReturn("PO-001");

        // Expenses & Savings & Goals
        when(expenseService.getExpensesByFirm(any())).thenReturn(Collections.emptyList());
        when(expenseService.getSummaryByFirm(any())).thenReturn(Map.of("total", 0));
        when(goalService.getGoalsByFirm(any())).thenReturn(Collections.emptyList());
        when(savingService.getSavingsByFirm(any())).thenReturn(Collections.emptyList());
        when(savingService.getSummaryByFirm(any())).thenReturn(Map.of("total", 0));
        when(savingService.getCategoriesByFirm(any())).thenReturn(Collections.emptyList());

        // Reminders, Messages, Notifications
        when(reminderService.getAll()).thenReturn(Collections.emptyList());
        when(reminderService.getActiveByFirm(any())).thenReturn(Collections.emptyList());
        when(inboxMessageService.getMessagesByFirm(any())).thenReturn(Collections.emptyList());
        when(notificationService.listNotifications(any(), anyString(), any(), anyInt())).thenReturn(Collections.emptyList());
        when(notificationService.getSummary(any())).thenReturn(NotificationSummaryResponse.builder().unreadCount(0).build());
        when(notificationService.getPreferences(any())).thenReturn(NotificationPreferencesDto.builder().enabled(true).build());

        // KPIs
        when(kpiService.getDashboardKpis(any())).thenReturn(KpiDashboardResponse.builder().totalBusiness(0.0).build());
        when(kpiService.getCustomerKpiSummaries(any())).thenReturn(Collections.emptyList());

        // Letters & Announcements
        when(letterService.getLettersByFirm(any(), any(), any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());
        when(letterService.generateNextLetterNumber(any())).thenReturn("LET-001");
        when(announcementService.getAnnouncements(anyBoolean())).thenReturn(Collections.emptyList());

        // OmniSearch Assistant
        QuickHelpResponse<MacroBusinessSummaryDTO> macroResp = new QuickHelpResponse<>();
        macroResp.setData(new MacroBusinessSummaryDTO());
        when(quickHelpService.getMacroBusinessSummary()).thenReturn(macroResp);

        QuickHelpResponse<SalesSummaryDTO> salesResp = new QuickHelpResponse<>();
        salesResp.setData(new SalesSummaryDTO());
        when(quickHelpService.getSalesSummary(anyString(), any(), any())).thenReturn(salesResp);

        QuickHelpResponse<InventorySummaryDTO> invResp = new QuickHelpResponse<>();
        invResp.setData(new InventorySummaryDTO());
        when(quickHelpService.getInventoryQuickHelp(anyString(), anyBoolean())).thenReturn(invResp);

        QuickHelpResponse<ExpenseSummaryDTO> expResp = new QuickHelpResponse<>();
        expResp.setData(new ExpenseSummaryDTO());
        when(quickHelpService.getExpenseSummary(anyString(), any(), any(), any())).thenReturn(expResp);

        QuickHelpResponse<UnifiedSearchResultDTO> searchResp = new QuickHelpResponse<>();
        searchResp.setData(new UnifiedSearchResultDTO());
        when(quickHelpService.searchAllEntities(anyString())).thenReturn(searchResp);

        // System, Backup, Health, Data Protection
        when(autoBackupService.getStatus()).thenReturn(Map.of("status", "OK"));
        when(dataProtectionService.getStatusMap()).thenReturn(Map.of("state", "OK"));
        when(tenantDataIntegrityAuditService.performFullAudit()).thenReturn(Map.of("status", "HEALTHY"));
        when(systemMetricsService.getMetricsSnapshot()).thenReturn(Map.of("ramUsedMb", 120, "uptimeSeconds", 3600));
        when(networkReachabilityService.getStatus(anyBoolean())).thenReturn(new NetworkReachabilityService.NetworkStatusDTO(true, true, 20L, null));
        when(devLogService.getStatus()).thenReturn(Map.of("enabled", false));
        when(updateService.checkUpdate()).thenReturn(Map.of("status", "OK"));
        when(firmService.list()).thenReturn(Collections.emptyList());
        when(noteService.getByFirm(any())).thenReturn(Collections.emptyList());

        ApiDiagnosticsResponse response = apiDiagnosticsService.runFullApiSuite(1L);

        assertNotNull(response);
        assertEquals("HEALTHY", response.getOverallStatus());
        assertEquals(0, response.getFailedEndpoints());
        assertTrue(response.getTotalEndpoints() >= 50, "Should test at least 50 endpoints across all subsystems");
        assertEquals(response.getTotalEndpoints(), response.getPassedEndpoints());
        assertEquals(100.0, response.getPassRatePercent());
    }
}
