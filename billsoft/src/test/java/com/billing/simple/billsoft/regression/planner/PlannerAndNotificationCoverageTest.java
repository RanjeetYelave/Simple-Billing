package com.billing.simple.billsoft.regression.planner;

import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceRequestItem;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.repositories.PurchaseOrderRepository;
import com.billing.simple.billsoft.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Tag("regression")
@Tag("unit")
@DisplayName("Planner, Notifications, Reminders, Notes, Expenses & Scheduler Coverage Tests")
class PlannerAndNotificationCoverageTest {

    @Autowired
    private PlannerNotificationScheduler scheduler;

    @Autowired
    private InboxMessageService inboxService;

    @Autowired
    private ReminderService reminderService;

    @Autowired
    private NoteService noteService;

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private SystemMetricsService metricsService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private ProductService productService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private PurchaseOrderService poService;

    @Autowired
    private PartyService partyService;

    @Autowired
    private FirmDetailsService firmService;

    @Autowired
    private EmployeeRepository employeeRepo;

    @Autowired
    private ReminderRepository reminderRepo;

    @Autowired
    private InboxMessageRepository inboxRepo;

    @Autowired
    private BackupService backupService;

    private final Long testFirmId = 1L;

    @BeforeEach
    void setUp() {
        backupService.factoryReset();

        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Omni Services Pvt Ltd");
        firm.setGstin("27AAACG4455D1Z6");
        firm.setAddressLine1("Highway IT Towers");
        firmService.create(firm);
    }

    @Test
    @DisplayName("Should test scheduler loops for reminders, stock alerts, overdue invoices, and PO deliveries")
    void testSchedulerRoutines() {
        // 1. Create a due reminder
        Reminder rem = new Reminder();
        rem.setTitle("File GST Return");
        rem.setDueDate(LocalDateTime.now().minusHours(1));
        rem.setCompleted(false);
        rem.setInboxNotified(false);
        rem.setFirmId(testFirmId);
        rem = reminderRepo.save(rem);

        // 2. Create Low Stock Product
        Product lowStock = productService.create(Product.builder()
                .name("Packing Carton Box")
                .price(BigDecimal.valueOf(25.0))
                .stockQuantity(BigDecimal.valueOf(2.0))
                .minStockLevel(BigDecimal.valueOf(10.0))
                .firmId(testFirmId)
                .build());

        // 3. Create Overdue Invoice
        Customer cust = customerService.create(Customer.builder()
                .name("Metro Traders")
                .phone("9822998877")
                .firmId(testFirmId)
                .build());

        InvoiceRequest invReq = new InvoiceRequest();
        invReq.setFirmId(testFirmId);
        invReq.setCustomerId(cust.getId());
        invReq.setStatus(InvoiceStatus.FINAL);
        invReq.setDueDate(LocalDate.now().minusDays(3));

        InvoiceRequestItem item = new InvoiceRequestItem();
        item.setProductId(lowStock.getId());
        item.setQty(1);
        item.setPricePerUnit(BigDecimal.valueOf(25.0));
        item.setGstPercent(BigDecimal.valueOf(18.0));
        invReq.setItems(List.of(item));

        Invoice inv = invoiceService.createInvoice(invReq);
        inv.setDueDate(LocalDate.now().minusDays(3));
        inv.setPaid(false);

        // 4. Create Overdue PO
        Party party = partyService.createParty(Party.builder()
                .name("Supreme Packaging")
                .phone("9822003344")
                .firmId(testFirmId)
                .build());

        PurchaseOrder po = poService.createPurchaseOrder(PurchaseOrder.builder()
                .party(party)
                .poDate(LocalDate.now().minusDays(5))
                .expectedDeliveryDate(LocalDate.now().minusDays(1))
                .status(PurchaseOrderStatus.ISSUED)
                .firmId(testFirmId)
                .build());

        // 5. Create Employee for payroll reminder
        Employee emp = new Employee();
        emp.setName("Suresh Raina");
        emp.setDateOfJoining(LocalDate.now().minusMonths(6));
        emp.setMonthlyBaseSalary(50000.0);
        emp.setFirmId(testFirmId);
        emp.setIsActive(true);
        employeeRepo.save(emp);

        // Run Scheduler Methods
        scheduler.checkDuePlannerItems();
        scheduler.checkLowStockAlerts();
        scheduler.checkOverdueInvoices();
        scheduler.checkPendingPurchaseOrderDeliveries();
        scheduler.checkPayrollMonthlyReminders();

        // Verify inbox notifications generated
        List<InboxMessage> msgs = inboxService.getMessagesByFirm(testFirmId);
        assertThat(msgs).isNotEmpty();
    }

    @Test
    @DisplayName("Should test Inbox, Reminder, Note, Expense and SystemMetrics services")
    void testPlannerCrudAndMetrics() {
        // 1. InboxMessageService
        InboxMessage msg = new InboxMessage();
        msg.setSubject("Test Alert");
        msg.setBody("Testing inbox notification body");
        msg.setSender("System");
        msg.setFirmId(testFirmId);
        msg = inboxService.createMessage(msg);
        assertThat(msg.getId()).isNotNull();

        inboxService.markAsRead(msg.getId());
        inboxService.deleteMessage(msg.getId());

        // 2. ReminderService
        Reminder r = new Reminder();
        r.setTitle("Follow up with client");
        r.setDueDate(LocalDateTime.now().plusDays(2));
        r.setFirmId(testFirmId);
        r = reminderService.create(r);
        assertThat(r.getId()).isNotNull();

        r.setCompleted(true);
        reminderService.update(r.getId(), r);
        reminderService.getActiveByFirm(testFirmId);
        reminderService.delete(r.getId());

        // 3. NoteService
        Note note = new Note();
        note.setTitle("Q3 Strategies");
        note.setContent("Expand retail distribution network.");
        note.setFirmId(testFirmId);
        note = noteService.create(note);
        assertThat(note.getId()).isNotNull();

        note.setTitle("Q3 Strategies - Revised");
        noteService.update(note.getId(), note);
        noteService.getByFirm(testFirmId);
        noteService.delete(note.getId());

        // 4. ExpenseService
        Expense exp = new Expense();
        exp.setTitle("Electricity Bill");
        exp.setAmount(BigDecimal.valueOf(4500.00));
        exp.setCategory("Utilities");
        exp.setExpenseDate(LocalDate.now());
        exp.setFirmId(testFirmId);
        exp = expenseService.createExpense(exp);
        assertThat(exp.getId()).isNotNull();

        expenseService.getExpensesByFirm(testFirmId);
        expenseService.deleteExpense(exp.getId());

        // 5. SystemMetricsService
        metricsService.recordRequest();
        metricsService.recordRequest();
        Map<String, Object> stats = metricsService.getMetricsSnapshot();
        assertThat(stats).containsKey("sessionRequestsCount");
        metricsService.flushMetrics();
    }
}
