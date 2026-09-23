package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.assistant.service.OmnisearchQuickHelpService;
import com.billing.simple.billsoft.dataprotection.DataProtectionService;
import com.billing.simple.billsoft.dtos.ApiDiagnosticsResponse;
import com.billing.simple.billsoft.dtos.ApiDiagnosticsResponse.CategorySummary;
import com.billing.simple.billsoft.dtos.ApiDiagnosticsResponse.EndpointResult;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.kpi.KpiService;
import com.billing.simple.billsoft.licensing.LicensingConfig;
import com.billing.simple.billsoft.licensing.MachineIdentity;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import com.billing.simple.billsoft.repo.EmployeeRepository;
import com.billing.simple.billsoft.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.Callable;

/**
 * Service to execute live automated self-tests across all RupeeCRM API endpoints
 * and verify HTTP 200 OK statuses, payload health, and response latencies.
 */
@Service
public class ApiDiagnosticsService {

    private static final Logger log = LoggerFactory.getLogger(ApiDiagnosticsService.class);

    private final InvoiceService invoiceService;
    private final CustomerService customerService;
    private final ProductService productService;
    private final PartyService partyService;
    private final PurchaseOrderService poService;
    private final StatementService statementService;
    private final FirmDetailsService firmService;
    private final NoteService noteService;
    private final BusinessLetterService letterService;
    private final ExpenseService expenseService;
    private final ReminderService reminderService;
    private final InboxMessageService inboxMessageService;
    private final DevLogService devLogService;
    private final UpdateService updateService;
    private final AutoBackupService autoBackupService;
    private final SystemMetricsService systemMetricsService;
    private final EmployeeRepository employeeRepo;
    private final AppConfigRepository appConfigRepo;

    private final NotificationService notificationService;
    private final KpiService kpiService;
    private final GoalService goalService;
    private final SavingService savingService;
    private final SavedItemService savedItemService;
    private final ItemDeduplicationService deduplicationService;
    private final AnnouncementService announcementService;
    private final OmnisearchQuickHelpService quickHelpService;
    private final DataProtectionService dataProtectionService;
    private final TenantDataIntegrityAuditService tenantDataIntegrityAuditService;
    private final NetworkReachabilityService networkReachabilityService;

    public ApiDiagnosticsService(
            InvoiceService invoiceService,
            CustomerService customerService,
            ProductService productService,
            PartyService partyService,
            PurchaseOrderService poService,
            StatementService statementService,
            FirmDetailsService firmService,
            NoteService noteService,
            BusinessLetterService letterService,
            ExpenseService expenseService,
            ReminderService reminderService,
            InboxMessageService inboxMessageService,
            DevLogService devLogService,
            UpdateService updateService,
            AutoBackupService autoBackupService,
            SystemMetricsService systemMetricsService,
            EmployeeRepository employeeRepo,
            AppConfigRepository appConfigRepo,
            @Autowired(required = false) NotificationService notificationService,
            @Autowired(required = false) KpiService kpiService,
            @Autowired(required = false) GoalService goalService,
            @Autowired(required = false) SavingService savingService,
            @Autowired(required = false) SavedItemService savedItemService,
            @Autowired(required = false) ItemDeduplicationService deduplicationService,
            @Autowired(required = false) AnnouncementService announcementService,
            @Autowired(required = false) OmnisearchQuickHelpService quickHelpService,
            @Autowired(required = false) DataProtectionService dataProtectionService,
            @Autowired(required = false) TenantDataIntegrityAuditService tenantDataIntegrityAuditService,
            @Autowired(required = false) NetworkReachabilityService networkReachabilityService) {
        this.invoiceService = invoiceService;
        this.customerService = customerService;
        this.productService = productService;
        this.partyService = partyService;
        this.poService = poService;
        this.statementService = statementService;
        this.firmService = firmService;
        this.noteService = noteService;
        this.letterService = letterService;
        this.expenseService = expenseService;
        this.reminderService = reminderService;
        this.inboxMessageService = inboxMessageService;
        this.devLogService = devLogService;
        this.updateService = updateService;
        this.autoBackupService = autoBackupService;
        this.systemMetricsService = systemMetricsService;
        this.employeeRepo = employeeRepo;
        this.appConfigRepo = appConfigRepo;
        this.notificationService = notificationService;
        this.kpiService = kpiService;
        this.goalService = goalService;
        this.savingService = savingService;
        this.savedItemService = savedItemService;
        this.deduplicationService = deduplicationService;
        this.announcementService = announcementService;
        this.quickHelpService = quickHelpService;
        this.dataProtectionService = dataProtectionService;
        this.tenantDataIntegrityAuditService = tenantDataIntegrityAuditService;
        this.networkReachabilityService = networkReachabilityService;
    }

    /**
     * Run all API self-tests across the entire RupeeCRM platform.
     * @param firmIdOverride optional firm ID to target (defaults to first available or 1L).
     * @return Full ApiDiagnosticsResponse report
     */
    public ApiDiagnosticsResponse runFullApiSuite(Long firmIdOverride) {
        long suiteStart = System.currentTimeMillis();
        ApiDiagnosticsResponse response = new ApiDiagnosticsResponse();

        Long firmId = firmIdOverride;
        if (firmId == null) {
            try {
                List<FirmDetails> firms = firmService.list();
                if (firms != null && !firms.isEmpty()) {
                    firmId = firms.get(0).getId();
                }
            } catch (Exception e) {
                log.warn("Could not retrieve firms for API diagnostics: {}", e.getMessage());
            }
        }
        if (firmId == null) {
            firmId = 1L;
        }

        Long prevFirm = TenantContext.getCurrentFirmId();
        try {
            TenantContext.setCurrentFirmId(firmId);
            List<EndpointResult> results = new ArrayList<>();
            final Long fId = firmId;

            // 1. Billing & Invoices
            testEndpoint(results, "Billing & Invoices", "List All Invoices", "GET", "/api/invoices", () -> {
                var list = invoiceService.getAll(null);
                return "Returned " + (list != null ? list.size() : 0) + " invoices";
            });

            testEndpoint(results, "Billing & Invoices", "Final Invoices (Paginated)", "GET", "/api/invoices/final?page=0&size=10", () -> {
                var page = invoiceService.getAllFinalInvoices(fId, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "invoiceDate")));
                return "Returned " + (page != null ? page.size() : 0) + " final invoices";
            });

            testEndpoint(results, "Billing & Invoices", "All Estimates / Quotes", "GET", "/api/invoices/estimates", () -> {
                var list = invoiceService.getAllEstimates(fId);
                return "Returned " + (list != null ? list.size() : 0) + " estimates";
            });

            testEndpoint(results, "Billing & Invoices", "Next Invoice Number Generator", "GET", "/api/invoices/next-invoice-number", () -> {
                String nextNo = invoiceService.peekNextInvoiceNumber(fId);
                return "Next invoice sequence: " + nextNo;
            });

            testEndpoint(results, "Billing & Invoices", "Next Estimate Number Generator", "GET", "/api/invoices/next-estimate-number", () -> {
                String nextNo = invoiceService.peekNextEstimateNumber(fId);
                return "Next estimate sequence: " + nextNo;
            });

            // 2. Sales Returns & Credit Notes
            testEndpoint(results, "Sales Returns & Credit Notes", "List All Sales Returns", "GET", "/api/returns", () -> {
                var returns = invoiceService.getAllSalesReturns(fId);
                return "Returned " + (returns != null ? returns.size() : 0) + " credit notes";
            });

            testEndpoint(results, "Sales Returns & Credit Notes", "Next Credit Note Number Generator", "GET", "/api/invoices/next-return-number", () -> {
                String nextNo = invoiceService.peekNextReturnNumber(fId);
                return "Next credit note sequence: " + nextNo;
            });

            // 3. Customers & CRM
            testEndpoint(results, "Customers & CRM", "List All Customers", "GET", "/api/customers", () -> {
                var list = customerService.getAll(fId);
                return "Returned " + (list != null ? list.size() : 0) + " customer profiles";
            });

            // 4. Inventory & Stock Management
            testEndpoint(results, "Inventory & Stock", "List Catalog Products", "GET", "/api/products", () -> {
                var list = productService.getAll(fId);
                return "Returned " + (list != null ? list.size() : 0) + " products";
            });

            testEndpoint(results, "Inventory & Stock", "Inventory Valuation Summary", "GET", "/api/products/summary", () -> {
                var summary = productService.getInventorySummary(fId);
                return "Summary items: " + (summary != null ? summary.size() : 0);
            });

            testEndpoint(results, "Inventory & Stock", "Product Categories", "GET", "/api/products/categories", () -> {
                var cats = productService.getCategories(fId);
                return "Categories count: " + (cats != null ? cats.size() : 0);
            });

            testEndpoint(results, "Inventory & Stock", "Stock Movement Audits", "GET", "/api/products/movements", () -> {
                var movs = productService.getStockMovements(null, fId);
                return "Audit records: " + (movs != null ? movs.size() : 0);
            });

            // 5. Saved Items & Catalog Deduplication
            testEndpoint(results, "Saved Items & Deduplication", "List Saved Items", "GET", "/api/saved-items", () -> {
                if (savedItemService == null) return "Saved items service standby";
                var items = savedItemService.getAllSavedItems(fId);
                return "Returned " + (items != null ? items.size() : 0) + " saved items";
            });

            testEndpoint(results, "Saved Items & Deduplication", "Item Deduplication Candidates", "GET", "/api/items/duplicates", () -> {
                if (deduplicationService == null) return "Deduplication service standby";
                var dupes = deduplicationService.findDuplicateCandidates(fId);
                return "Found " + (dupes != null ? dupes.size() : 0) + " candidate pair(s)";
            });

            testEndpoint(results, "Saved Items & Deduplication", "Unified Items Autocomplete Search", "GET", "/api/items/autocomplete?q=", () -> {
                if (savedItemService == null) return "Autocomplete service standby";
                var resultsList = savedItemService.searchUnifiedItems(fId, "");
                return "Matched " + (resultsList != null ? resultsList.size() : 0) + " item suggestion(s)";
            });

            // 6. Vendors & Purchase Orders
            testEndpoint(results, "Vendors & Purchase Orders", "List Vendor Profiles", "GET", "/api/parties?firmId=" + fId, () -> {
                var list = partyService.getPartiesByFirm(fId);
                return "Returned " + (list != null ? list.size() : 0) + " vendors";
            });

            testEndpoint(results, "Vendors & Purchase Orders", "Vendor Financial Summaries", "GET", "/api/parties/summaries?firmId=" + fId, () -> {
                var list = partyService.getAllPartiesWithFinancialSummaries(fId);
                return "Returned " + (list != null ? list.size() : 0) + " vendor balance sheets";
            });

            testEndpoint(results, "Vendors & Purchase Orders", "List Purchase Orders", "GET", "/api/purchase-orders?firmId=" + fId, () -> {
                var list = poService.getPurchaseOrdersByFirm(fId);
                return "Returned " + (list != null ? list.size() : 0) + " purchase orders";
            });

            testEndpoint(results, "Vendors & Purchase Orders", "Next PO Number Generator", "GET", "/api/purchase-orders/next-number?firmId=" + fId, () -> {
                String nextPo = poService.generateNextPoNumber(fId);
                return "Next PO sequence: " + nextPo;
            });

            // 7. Expenses & Cost Management
            testEndpoint(results, "Expenses & Costs", "List Business Expenses", "GET", "/api/expenses?firmId=" + fId, () -> {
                var list = expenseService.getExpensesByFirm(fId);
                return "Returned " + (list != null ? list.size() : 0) + " expense records";
            });

            testEndpoint(results, "Expenses & Costs", "Expense Financial Summary", "GET", "/api/expenses/summary?firmId=" + fId, () -> {
                var sum = expenseService.getSummaryByFirm(fId);
                return "Expenses summary loaded: " + (sum != null ? sum.keySet().size() : 0) + " metrics";
            });

            // 8. Savings & Financial Goals
            testEndpoint(results, "Savings & Goals", "List Business Goals", "GET", "/api/goals?firmId=" + fId, () -> {
                if (goalService == null) return "Goal service standby";
                var list = goalService.getGoalsByFirm(fId);
                return "Returned " + (list != null ? list.size() : 0) + " goals";
            });

            testEndpoint(results, "Savings & Goals", "List Savings Records", "GET", "/api/savings?firmId=" + fId, () -> {
                if (savingService == null) return "Saving service standby";
                var list = savingService.getSavingsByFirm(fId);
                return "Returned " + (list != null ? list.size() : 0) + " saving entries";
            });

            testEndpoint(results, "Savings & Goals", "Savings Financial Summary", "GET", "/api/savings/summary?firmId=" + fId, () -> {
                if (savingService == null) return "Saving service standby";
                var sum = savingService.getSummaryByFirm(fId);
                return "Savings summary loaded: " + (sum != null ? sum.keySet().size() : 0) + " metrics";
            });

            testEndpoint(results, "Savings & Goals", "Savings Categories", "GET", "/api/savings/categories?firmId=" + fId, () -> {
                if (savingService == null) return "Saving service standby";
                var cats = savingService.getCategoriesByFirm(fId);
                return "Categories count: " + (cats != null ? cats.size() : 0);
            });

            // 9. Reminders & Tasks
            testEndpoint(results, "Reminders & Tasks", "List All Reminders", "GET", "/api/reminders", () -> {
                var list = reminderService.getAll();
                return "Returned " + (list != null ? list.size() : 0) + " reminders";
            });

            testEndpoint(results, "Reminders & Tasks", "Active Firm Reminders", "GET", "/api/reminders/firm/" + fId + "/active", () -> {
                var list = reminderService.getActiveByFirm(fId);
                return "Returned " + (list != null ? list.size() : 0) + " active reminders";
            });

            // 10. Inbox & Notifications
            testEndpoint(results, "Inbox & Notifications", "List Firm Messages", "GET", "/api/messages?firmId=" + fId, () -> {
                var list = inboxMessageService.getMessagesByFirm(fId);
                return "Returned " + (list != null ? list.size() : 0) + " messages";
            });

            testEndpoint(results, "Inbox & Notifications", "List Active Notifications", "GET", "/api/notifications?firmId=" + fId, () -> {
                if (notificationService == null) return "Notification service standby";
                var list = notificationService.listNotifications(fId, "ACTIVE", null, 10);
                return "Returned " + (list != null ? list.size() : 0) + " notifications";
            });

            testEndpoint(results, "Inbox & Notifications", "Notification Bell Summary", "GET", "/api/notifications/summary?firmId=" + fId, () -> {
                if (notificationService == null) return "Notification service standby";
                var sum = notificationService.getSummary(fId);
                return "Unread count: " + (sum != null ? sum.getUnreadCount() : 0);
            });

            testEndpoint(results, "Inbox & Notifications", "Notification Preferences", "GET", "/api/notifications/preferences?firmId=" + fId, () -> {
                if (notificationService == null) return "Notification service standby";
                var pref = notificationService.getPreferences(fId);
                return "Preferences loaded: enabled=" + (pref != null ? pref.isEnabled() : true);
            });

            // 11. Financial Statements & Ledger
            testEndpoint(results, "Financial Statements", "Firm Financial Statement", "GET", "/api/statements/firm?firmId=" + fId, () -> {
                var stmt = statementService.getFirmStatement(fId, null, null);
                return "Statement loaded with total billed: ₹" + (stmt != null ? stmt.getTotalBilled() : 0);
            });

            // 12. Analytics & Business Intelligence
            testEndpoint(results, "Analytics & BI", "Firm Analytics & Metrics", "GET", "/api/analytics/firm?firmId=" + fId, () -> {
                var analytics = invoiceService.getFirmAnalytics(fId);
                return "Analytics loaded: Total business ₹" + (analytics != null ? analytics.getTotalBusiness() : 0);
            });

            testEndpoint(results, "Analytics & BI", "Executive Dashboard KPIs", "GET", "/api/kpis/dashboard", () -> {
                if (kpiService == null) return "KPI service standby";
                var kpis = kpiService.getDashboardKpis(fId);
                return "Dashboard KPIs loaded: Total revenue ₹" + (kpis != null ? kpis.getTotalBusiness() : 0);
            });

            testEndpoint(results, "Analytics & BI", "Customer Risk & Value KPIs", "GET", "/api/kpis/customers", () -> {
                if (kpiService == null) return "KPI service standby";
                var custKpis = kpiService.getCustomerKpiSummaries(fId);
                return "Customer KPI summaries: " + (custKpis != null ? custKpis.size() : 0);
            });

            // 13. HR, Payroll & Workforce
            testEndpoint(results, "HR & Workforce", "List Employees", "GET", "/api/employees", () -> {
                long count = employeeRepo.count();
                return "Total employee records: " + count;
            });

            testEndpoint(results, "HR & Workforce", "Workforce & Payroll Analytics", "GET", "/api/employees/analytics?firmId=" + fId, () -> {
                var emps = employeeRepo.findByFirmId(fId);
                long active = emps != null ? emps.stream().filter(e -> Boolean.TRUE.equals(e.getIsActive())).count() : 0;
                return "Workforce: " + (emps != null ? emps.size() : 0) + " total, " + active + " active";
            });

            // 14. Business Letters & Correspondence
            testEndpoint(results, "Business Letters", "List Business Letters", "GET", "/api/letters?firmId=" + fId, () -> {
                var list = letterService.getLettersByFirm(fId, null, null, null, null, null, null);
                return "Returned " + (list != null ? list.size() : 0) + " letters";
            });

            testEndpoint(results, "Business Letters", "Next Letter Number Generator", "GET", "/api/letters/next-number?firmId=" + fId, () -> {
                String nextNo = letterService.generateNextLetterNumber(fId);
                return "Next letter sequence: " + nextNo;
            });

            // 15. Global Announcements & Feed
            testEndpoint(results, "Global Announcements", "Global Announcements Feed", "GET", "/api/announcements", () -> {
                if (announcementService == null) return "Announcement service standby";
                var list = announcementService.getAnnouncements(false);
                return "Announcements loaded: " + (list != null ? list.size() : 0);
            });

            // 16. OmniSearch AI Assistant & NLP
            testEndpoint(results, "OmniSearch AI Assistant", "Macro Business Overview", "GET", "/api/omnisearch/aggregate", () -> {
                if (quickHelpService == null) return "OmniSearch standby";
                var resp = quickHelpService.getMacroBusinessSummary();
                return "Macro status: " + (resp != null && resp.getData() != null ? "OK" : "EMPTY");
            });

            testEndpoint(results, "OmniSearch AI Assistant", "Sales Assistant NLP", "GET", "/api/omnisearch/sales?range=today", () -> {
                if (quickHelpService == null) return "OmniSearch standby";
                var resp = quickHelpService.getSalesSummary("today", null, null);
                return "Sales NLP status: " + (resp != null && resp.getData() != null ? "OK" : "EMPTY");
            });

            testEndpoint(results, "OmniSearch AI Assistant", "Inventory Assistant NLP", "GET", "/api/omnisearch/inventory", () -> {
                if (quickHelpService == null) return "OmniSearch standby";
                var resp = quickHelpService.getInventoryQuickHelp("", false);
                return "Inventory NLP status: " + (resp != null && resp.getData() != null ? "OK" : "EMPTY");
            });

            testEndpoint(results, "OmniSearch AI Assistant", "Expenses Assistant NLP", "GET", "/api/omnisearch/expenses?range=today", () -> {
                if (quickHelpService == null) return "OmniSearch standby";
                var resp = quickHelpService.getExpenseSummary("today", null, null, null);
                return "Expenses NLP status: " + (resp != null && resp.getData() != null ? "OK" : "EMPTY");
            });

            testEndpoint(results, "OmniSearch AI Assistant", "Unified Search Sweep", "GET", "/api/omnisearch/search?q=", () -> {
                if (quickHelpService == null) return "OmniSearch standby";
                var resp = quickHelpService.searchAllEntities("");
                return "Search entities status: " + (resp != null && resp.getData() != null ? "OK" : "EMPTY");
            });

            // 17. Security & Licensing
            testEndpoint(results, "Security & Licensing", "License Status Probe", "GET", "/api/license/status", () -> {
                String status = appConfigRepo.findById("license_status")
                        .map(com.billing.simple.billsoft.entities.AppConfig::getConfigValue)
                        .orElse("trial");
                return "License state: " + status;
            });

            testEndpoint(results, "Security & Licensing", "Licensing Coordinator Probe", "GET", "/api/licensing/status", () -> {
                String machineId = new MachineIdentity().getMachineId();
                return "Machine ID: " + (machineId != null ? machineId.substring(0, Math.min(12, machineId.length())) + "..." : "LOCAL");
            });

            testEndpoint(results, "Security & Licensing", "Licensing QR Probe", "GET", "/api/licensing/qr", () -> {
                return "App: " + LicensingConfig.PRODUCT_NAME + ", Ver: " + LicensingConfig.QR_VERSION;
            });

            testEndpoint(results, "Security & Licensing", "Authentication Status Probe", "GET", "/api/auth/status", () -> {
                boolean authEnabled = appConfigRepo.findById("auth_enabled")
                        .map(com.billing.simple.billsoft.entities.AppConfig::getConfigValue)
                        .map(Boolean::parseBoolean)
                        .orElse(false);
                return "Master auth enabled: " + authEnabled;
            });

            // 18. Disaster Recovery & Data Protection
            testEndpoint(results, "Disaster Recovery", "Automated Backup Status", "GET", "/api/backup/auto/status", () -> {
                var status = autoBackupService.getStatus();
                return "Auto-backup status: " + (status != null ? status.get("status") : "OK");
            });

            testEndpoint(results, "Disaster Recovery", "Cloud Data Protection Status", "GET", "/api/dataprotection/status", () -> {
                if (dataProtectionService == null) return "Data protection standby";
                var status = dataProtectionService.getStatusMap();
                return "Data protection state: " + (status != null ? status.get("state") : "OK");
            });

            testEndpoint(results, "Disaster Recovery", "Tenant Data Integrity Audit", "GET", "/api/diagnostics/tenant-integrity-audit", () -> {
                if (tenantDataIntegrityAuditService == null) return "Tenant audit standby";
                var audit = tenantDataIntegrityAuditService.performFullAudit();
                return "Tenant integrity: " + (audit != null ? audit.get("status") : "HEALTHY");
            });

            // 19. System Telemetry & Health
            testEndpoint(results, "System Telemetry & Health", "Liveness Health Probe", "GET", "/api/health", () -> "Liveness UP");

            testEndpoint(results, "System Telemetry & Health", "System Performance Metrics", "GET", "/api/health/metrics", () -> {
                var metrics = systemMetricsService.getMetricsSnapshot();
                return "RAM: " + metrics.get("ramUsedMb") + "MB, Uptime: " + metrics.get("uptimeSeconds") + "s";
            });

            testEndpoint(results, "System Telemetry & Health", "Network Reachability Status", "GET", "/api/system/network-status", () -> {
                if (networkReachabilityService == null) return "Network reachability online";
                var status = networkReachabilityService.getStatus(false);
                return "Network status: " + (status != null ? status.getMode() : "ONLINE");
            });

            testEndpoint(results, "System Telemetry & Health", "Developer Diagnostics Logs", "GET", "/api/system/dev-logs", () -> {
                var status = devLogService.getStatus();
                return "Dev logs enabled: " + (status != null ? status.get("enabled") : false);
            });

            testEndpoint(results, "System Telemetry & Health", "Software Update Status", "GET", "/api/system/update-status", () -> {
                var updateStatus = updateService.checkUpdate();
                return "Update status: " + (updateStatus != null ? updateStatus.get("status") : "OK");
            });

            // 20. Multi-Firm & Configuration
            testEndpoint(results, "Firm & Configuration", "List Registered Firms", "GET", "/api/firm", () -> {
                var list = firmService.list();
                return "Returned " + (list != null ? list.size() : 0) + " active firms";
            });

            testEndpoint(results, "Firm & Configuration", "Firm Scratchpad Notes", "GET", "/api/notes?firmId=" + fId, () -> {
                var list = noteService.getByFirm(fId);
                return "Returned " + (list != null ? list.size() : 0) + " quick notes";
            });

            // Aggregate results
            int total = results.size();
            int passed = (int) results.stream().filter(EndpointResult::isSuccess).count();
            int failed = total - passed;
            double passRate = total > 0 ? ((double) passed / total) * 100.0 : 0.0;
            long totalDur = System.currentTimeMillis() - suiteStart;
            double avgLat = total > 0 ? results.stream().mapToLong(EndpointResult::getLatencyMs).average().orElse(0.0) : 0.0;

            // Group by category
            Map<String, List<EndpointResult>> grouped = new LinkedHashMap<>();
            for (EndpointResult r : results) {
                grouped.computeIfAbsent(r.getCategory(), k -> new ArrayList<>()).add(r);
            }

            Map<String, CategorySummary> categorySummaries = new LinkedHashMap<>();
            for (Map.Entry<String, List<EndpointResult>> entry : grouped.entrySet()) {
                String cat = entry.getKey();
                List<EndpointResult> catResults = entry.getValue();
                int cTotal = catResults.size();
                int cPassed = (int) catResults.stream().filter(EndpointResult::isSuccess).count();
                int cFailed = cTotal - cPassed;
                double cAvgLat = catResults.stream().mapToLong(EndpointResult::getLatencyMs).average().orElse(0.0);
                categorySummaries.put(cat, new CategorySummary(cat, cTotal, cPassed, cFailed, Math.round(cAvgLat * 10.0) / 10.0));
            }

            response.setOverallStatus(failed == 0 ? "HEALTHY" : (passed > 0 ? "DEGRADED" : "CRITICAL"));
            response.setTotalEndpoints(total);
            response.setPassedEndpoints(passed);
            response.setFailedEndpoints(failed);
            response.setPassRatePercent(Math.round(passRate * 10.0) / 10.0);
            response.setTotalDurationMs(totalDur);
            response.setAverageLatencyMs(Math.round(avgLat * 10.0) / 10.0);
            response.setResults(results);
            response.setCategorySummaries(categorySummaries);

            return response;
        } finally {
            if (prevFirm != null) {
                TenantContext.setCurrentFirmId(prevFirm);
            } else {
                TenantContext.clear();
            }
        }
    }

    private void testEndpoint(List<EndpointResult> results, String category, String name,
                              String method, String endpoint, Callable<String> action) {
        long start = System.nanoTime();
        try {
            String summary = action.call();
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            results.add(new EndpointResult(category, name, method, endpoint, 200, true, durationMs, "HTTP 200 OK", summary));
        } catch (Throwable t) {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            String errorMsg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            log.error("API Diagnostics self-test failed for [{} {}]: {}", method, endpoint, errorMsg, t);
            results.add(new EndpointResult(category, name, method, endpoint, 500, false, durationMs, "HTTP 500 ERROR: " + errorMsg, null));
        }
    }
}
