package com.billing.simple.billsoft.telegram;

import com.billing.simple.billsoft.BillsoftApplication;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.repositories.PartyRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.telegram.entities.TelegramAuthorizedUser;
import com.billing.simple.billsoft.telegram.entities.TelegramDraft;
import com.billing.simple.billsoft.telegram.entities.TelegramUserSession;
import com.billing.simple.billsoft.telegram.repo.TelegramAuthorizedUserRepository;
import com.billing.simple.billsoft.telegram.repo.TelegramDocumentDeliveryRepository;
import com.billing.simple.billsoft.telegram.repo.TelegramDraftRepository;
import com.billing.simple.billsoft.telegram.repo.TelegramUserSessionRepository;
import com.billing.simple.billsoft.telegram.service.TelegramDraftService;
import com.billing.simple.billsoft.telegram.service.TelegramReadService;
import com.billing.simple.billsoft.telegram.service.TelegramSecurityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = BillsoftApplication.class)
@ActiveProfiles("test")
@Transactional
public class TelegramSixFunctionsTest {

    @Autowired
    private TelegramDraftService draftService;

    @Autowired
    private TelegramReadService readService;

    @Autowired
    private TelegramSecurityManager securityManager;

    @Autowired
    private TelegramDraftRepository draftRepo;

    @Autowired
    private TelegramDocumentDeliveryRepository deliveryRepo;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private PartyRepository partyRepo;

    @Autowired
    private EmployeeRepository employeeRepo;

    @Autowired
    private LeaveRecordRepository leaveRepo;

    @Autowired
    private EmployeeAdvanceRepository advanceRepo;

    @Autowired
    private SalaryRecordRepository salaryRepo;

    @Autowired
    private TelegramAuthorizedUserRepository userRepo;

    private final Long testFirmId = 1L;
    private final Long testUserId = 778899L;
    private final Long testChatId = 123456L;

    private Customer customer;
    private Product product;
    private Party party;
    private Employee employee;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentFirmId(testFirmId);

        customer = new Customer();
        customer.setName("Acme Corp");
        customer.setPhone("9988776655");
        customer.setFirmId(testFirmId);
        customer = customerRepo.save(customer);

        product = new Product();
        product.setName("Industrial Valve 40mm");
        product.setPrice(BigDecimal.valueOf(1250.00));
        product.setUnit("Piece");
        product.setFirmId(testFirmId);
        product.setInInventory(true);
        product.setStockQuantity(BigDecimal.valueOf(50));
        product = productRepo.save(product);

        party = new Party();
        party.setName("Apex Steel Suppliers");
        party.setPhone("9123456780");
        party.setFirmId(testFirmId);
        party = partyRepo.save(party);

        employee = new Employee();
        employee.setName("Vikram Singh");
        employee.setRole("Technician");
        employee.setPhone("9876501234");
        employee.setMonthlyBaseSalary(35000.0);
        employee.setCurrentAdvanceBalance(0.0);
        employee.setFirmId(testFirmId);
        employee = employeeRepo.save(employee);

        TelegramAuthorizedUser authUser = new TelegramAuthorizedUser();
        authUser.setUserId(testUserId);
        authUser.setDisplayName("Vikram");
        authUser.setStatus("ACTIVE");
        authUser.setAuthorizedAt(LocalDateTime.now());
        userRepo.save(authUser);
    }

    // Function 1: Create Invoice / Quotation
    @Test
    void testFunction1_CreateInvoiceAndQuotation() {
        // Create Tax Invoice
        TelegramDraft invDraft = draftService.startDraft(testChatId, testUserId, testFirmId, "INVOICE");
        draftService.setCustomer(invDraft.getDraftId(), customer.getId(), customer.getName(), customer.getPhone());
        draftService.addItem(invDraft.getDraftId(), product.getId(), product.getName(), null, product.getUnit(), BigDecimal.valueOf(2), product.getPrice(), BigDecimal.ZERO);

        Invoice preview = draftService.previewDraft(invDraft.getDraftId());
        assertEquals(BigDecimal.valueOf(2500.00).setScale(2), preview.getTotalAmount().setScale(2));

        Invoice executed = draftService.executeDraft(invDraft.getDraftId(), 4, "TEST-INSTALLATION");
        assertNotNull(executed.getId());
        assertNotNull(executed.getInvoiceNumber());

        // Create Quotation / Estimate
        TelegramDraft estDraft = draftService.startDraft(testChatId, testUserId, testFirmId, "ESTIMATE");
        draftService.setCustomer(estDraft.getDraftId(), customer.getId(), customer.getName(), customer.getPhone());
        draftService.addItem(estDraft.getDraftId(), product.getId(), product.getName(), null, product.getUnit(), BigDecimal.valueOf(1), product.getPrice(), BigDecimal.ZERO);
        draftService.previewDraft(estDraft.getDraftId());
        Invoice executedEst = draftService.executeDraft(estDraft.getDraftId(), 4, "TEST-INSTALLATION");
        assertNotNull(executedEst.getId());
        assertNotNull(executedEst.getEstimateNumber());
    }

    // Function 2: Download Invoice / Quotation
    @Test
    void testFunction2_DownloadInvoiceAndQuotation() {
        // Query recent invoices and estimates via read service
        var recentInvoices = readService.getRecentInvoices(testFirmId, 5);
        assertNotNull(recentInvoices);

        var recentEstimates = readService.getRecentEstimates(testFirmId, 5);
        assertNotNull(recentEstimates);
    }

    // Function 3: Statements & Business Reports
    @Test
    void testFunction3_StatementsAndReports() throws Exception {
        // Test generating stock movement vector PDF
        byte[] stockPdf = readService.generateStockMovementPdf(testFirmId);
        assertNotNull(stockPdf);
        assertTrue(stockPdf.length > 500);

        // Test party lookup
        List<Party> parties = readService.searchParties(testFirmId, "Apex");
        assertFalse(parties.isEmpty());
        assertEquals("Apex Steel Suppliers", parties.get(0).getName());
    }

    // Function 4: Create / Download Purchase Order
    @Test
    void testFunction4_CreateAndDownloadPurchaseOrder() {
        TelegramDraft poDraft = draftService.startDraft(testChatId, testUserId, testFirmId, "PURCHASE_ORDER");
        draftService.setCustomer(poDraft.getDraftId(), party.getId(), party.getName(), party.getPhone());
        draftService.addItem(poDraft.getDraftId(), product.getId(), product.getName(), null, product.getUnit(), BigDecimal.valueOf(10), BigDecimal.valueOf(1000.00), BigDecimal.ZERO);

        PurchaseOrder poPreview = draftService.previewPoDraft(poDraft.getDraftId());
        assertEquals(BigDecimal.valueOf(10000.00).setScale(2), poPreview.getTotalAmount().setScale(2));

        PurchaseOrder savedPo = draftService.executePoDraft(poDraft.getDraftId(), 4, "TEST-INSTALLATION");
        assertNotNull(savedPo.getId());
        assertNotNull(savedPo.getPoNumber());
        assertEquals("Apex Steel Suppliers", savedPo.getPartyName());

        List<PurchaseOrder> recentPos = readService.getRecentPurchaseOrders(testFirmId, 5);
        assertFalse(recentPos.isEmpty());
    }

    // Function 5: Employee Leaves, Advances & Payroll
    @Test
    void testFunction5_EmployeeLeavesAdvancesAndPayroll() {
        // 1. Record Leave
        LeaveRecord leave = draftService.recordLeave(testFirmId, employee.getId(), LocalDate.now(), LocalDate.now().plusDays(2), "CASUAL", "Family Event");
        assertNotNull(leave.getId());
        assertEquals("APPROVED", leave.getStatus());

        // 2. Record Advance
        EmployeeAdvance adv = draftService.recordAdvance(testFirmId, employee.getId(), 5000.0, "Festival Advance");
        assertNotNull(adv.getId());
        Employee updatedEmp = readService.getEmployeeById(testFirmId, employee.getId()).orElseThrow();
        assertEquals(5000.0, updatedEmp.getCurrentAdvanceBalance());

        // 3. Process Salary
        String monthYear = "11-2026";
        SalaryRecord sal = draftService.processSalary(testFirmId, employee.getId(), monthYear, 2000.0, "TEST-INSTALLATION", testUserId, testChatId);
        assertNotNull(sal.getId());
        assertEquals(35000.0, sal.getBaseSalaryAtTime());
        assertEquals(2000.0, sal.getAdvanceDeducted());
        assertEquals(33000.0, sal.getNetPaid());

        Employee postSalaryEmp = readService.getEmployeeById(testFirmId, employee.getId()).orElseThrow();
        assertEquals(3000.0, postSalaryEmp.getCurrentAdvanceBalance());
    }

    // Function 6: Record Expenses / Savings
    @Test
    void testFunction6_RecordExpensesAndSavings() {
        // Record Expense
        Expense exp = draftService.recordExpense(testFirmId, BigDecimal.valueOf(450.00), "Vehicle Diesel", "Travel");
        assertNotNull(exp.getId());
        assertEquals(BigDecimal.valueOf(450.00).setScale(2), exp.getAmount().setScale(2));

        // Record Savings
        SavingRecord sav = draftService.recordSaving(testFirmId, BigDecimal.valueOf(5000.00), "Emergency Fund Reserve");
        assertNotNull(sav.getId());
        assertEquals(BigDecimal.valueOf(5000.00).setScale(2), sav.getAmount().setScale(2));
    }
}
