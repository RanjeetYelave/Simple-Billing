package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dtos.ApiDiagnosticsResponse;
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

        apiDiagnosticsService = new ApiDiagnosticsService(
                invoiceService, customerService, productService, partyService, poService,
                statementService, firmService, noteService, letterService, expenseService,
                reminderService, inboxMessageService, devLogService, updateService,
                autoBackupService, systemMetricsService, employeeRepo, appConfigRepo
        );
    }

    @Test
    void testRunFullApiSuite_AllHealthy() {
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

        when(customerService.getAll(any())).thenReturn(Collections.emptyList());
        when(productService.getAll(any())).thenReturn(Collections.emptyList());
        when(productService.getInventorySummary(any())).thenReturn(Map.of("totalProducts", 0));
        when(productService.getCategories(any())).thenReturn(Collections.emptyList());
        when(productService.getStockMovements(any(), any())).thenReturn(Collections.emptyList());

        when(partyService.getPartiesByFirm(any())).thenReturn(Collections.emptyList());
        when(partyService.getAllPartiesWithFinancialSummaries(any())).thenReturn(Collections.emptyList());
        when(poService.getPurchaseOrdersByFirm(any())).thenReturn(Collections.emptyList());
        when(poService.generateNextPoNumber(any())).thenReturn("PO-001");

        when(expenseService.getExpensesByFirm(any())).thenReturn(Collections.emptyList());
        when(expenseService.getSummaryByFirm(any())).thenReturn(Map.of("total", 0));
        when(reminderService.getAll()).thenReturn(Collections.emptyList());
        when(reminderService.getActiveByFirm(any())).thenReturn(Collections.emptyList());
        when(inboxMessageService.getMessagesByFirm(any())).thenReturn(Collections.emptyList());

        when(letterService.getLettersByFirm(any(), any(), any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());
        when(letterService.generateNextLetterNumber(any())).thenReturn("LET-001");

        when(autoBackupService.getStatus()).thenReturn(Map.of("status", "OK"));
        when(systemMetricsService.getMetricsSnapshot()).thenReturn(Map.of("ramUsedMb", 120, "uptimeSeconds", 3600));
        when(devLogService.getStatus()).thenReturn(Map.of("enabled", false));
        when(updateService.checkUpdate()).thenReturn(Map.of("status", "OK"));
        when(firmService.list()).thenReturn(Collections.emptyList());
        when(noteService.getByFirm(any())).thenReturn(Collections.emptyList());

        ApiDiagnosticsResponse response = apiDiagnosticsService.runFullApiSuite(1L);

        assertNotNull(response);
        assertEquals("HEALTHY", response.getOverallStatus());
        assertEquals(0, response.getFailedEndpoints());
        assertTrue(response.getTotalEndpoints() >= 25, "Should test at least 25 endpoints");
        assertEquals(response.getTotalEndpoints(), response.getPassedEndpoints());
        assertEquals(100.0, response.getPassRatePercent());
    }
}
