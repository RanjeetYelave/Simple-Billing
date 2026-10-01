package com.billing.simple.billsoft.regression.backup;

import com.billing.simple.billsoft.BillsoftApplication;
import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.repositories.*;
import com.billing.simple.billsoft.service.BackupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = BillsoftApplication.class)
@ActiveProfiles("test")
public class PreResetNaturalKeyCollisionSafetyTest {

    @Autowired
    private BackupService backupService;

    @Autowired
    private FirmDetailsRepository firmDetailsRepo;
    @Autowired
    private CustomerRepository customerRepo;
    @Autowired
    private ProductRepository productRepo;
    @Autowired
    private InvoiceRepository invoiceRepo;
    @Autowired
    private InvoicePaymentRepository invoicePaymentRepo;
    @Autowired
    private ExpenseRepository expenseRepo;
    @Autowired
    private SavingRepository savingRepo;
    @Autowired
    private ReminderRepository reminderRepo;
    @Autowired
    private NoteRepository noteRepo;
    @Autowired
    private NotificationRepository notificationRepo;
    @Autowired
    private EmployeeRepository employeeRepo;
    @Autowired
    private EmployeeAdvanceRepository advanceRepo;
    @Autowired
    private PartyRepository partyRepo;
    @Autowired
    private PartyPaymentRepository partyPaymentRepo;
    @Autowired
    private PurchaseOrderRepository purchaseOrderRepo;
    @Autowired
    private StockMovementRepository stockMovementRepo;

    @BeforeEach
    void setupCleanDatabase() {
        backupService.factoryReset();
    }

    @Test
    @DisplayName("1. Expenses: Two legitimate distinct expenses with identical firm, date, amount, category survive restore and repeat imports")
    public void testCoexistingIdenticalExpensesSurviveRestore() {
        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Expense Collision Test Firm");
        firm = firmDetailsRepo.save(firm);
        Long fid = firm.getId();

        // Create 2 distinct legitimate expenses for the same date, amount, category (e.g. two ₹500 taxi rides or lunch bills)
        Expense exp1 = Expense.builder()
                .firmId(fid)
                .title("Taxi Receipt Morning")
                .expenseDate(LocalDate.of(2026, 9, 29))
                .amount(new BigDecimal("500.00"))
                .category("Travel")
                .notes("Morning commute")
                .build();
        exp1 = expenseRepo.save(exp1);

        Expense exp2 = Expense.builder()
                .firmId(fid)
                .title("Taxi Receipt Evening")
                .expenseDate(LocalDate.of(2026, 9, 29))
                .amount(new BigDecimal("500.00"))
                .category("Travel")
                .notes("Evening return")
                .build();
        exp2 = expenseRepo.save(exp2);

        assertEquals(2, expenseRepo.findByFirmIdOrderByExpenseDateDescIdDesc(fid).size());

        // Export backup
        BackupDTO backup = backupService.exportData(fid);
        assertEquals(2, backup.getExpenses().size());

        // Wipe and restore
        backupService.factoryReset();
        assertEquals(0, expenseRepo.findAll().size());

        // Restore once
        backupService.importData(backup, null, true);
        List<Expense> restored1 = expenseRepo.findAll();
        assertEquals(2, restored1.size(), "Both distinct expenses must survive the first restore without collapsing");

        // Repeated restore (10 times)
        for (int i = 0; i < 10; i++) {
            backupService.importData(backup, null, true);
        }
        List<Expense> restored10 = expenseRepo.findAll();
        assertEquals(2, restored10.size(), "Repeated restores must be idempotent and keep exactly 2 expenses");
    }

    @Test
    @DisplayName("2. Savings: Two legitimate distinct savings with identical firm, date, amount survive restore")
    public void testCoexistingIdenticalSavingsSurviveRestore() {
        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Savings Collision Test Firm");
        firm = firmDetailsRepo.save(firm);
        Long fid = firm.getId();

        SavingRecord s1 = SavingRecord.builder()
                .firmId(fid)
                .title("Recurring Deposit 1")
                .savingDate(LocalDate.of(2026, 9, 29))
                .amount(new BigDecimal("1000.00"))
                .category("Bank")
                .build();
        savingRepo.save(s1);

        SavingRecord s2 = SavingRecord.builder()
                .firmId(fid)
                .title("Recurring Deposit 2")
                .savingDate(LocalDate.of(2026, 9, 29))
                .amount(new BigDecimal("1000.00"))
                .category("Bank")
                .build();
        savingRepo.save(s2);

        assertEquals(2, savingRepo.findByFirmIdOrderBySavingDateDescIdDesc(fid).size());

        BackupDTO backup = backupService.exportData(fid);
        assertEquals(2, backup.getSavings().size());

        backupService.factoryReset();
        backupService.importData(backup, null, true);

        assertEquals(2, savingRepo.findAll().size(), "Both savings records must survive restore");

        for (int i = 0; i < 5; i++) {
            backupService.importData(backup, null, true);
        }
        assertEquals(2, savingRepo.findAll().size(), "Repeated imports must remain exactly 2");
    }

    @Test
    @DisplayName("3. Reminders & Notes: Two distinct records with identical firm and title survive restore")
    public void testCoexistingRemindersAndNotesWithSameTitleSurviveRestore() {
        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Reminders & Notes Test Firm");
        firm = firmDetailsRepo.save(firm);
        Long fid = firm.getId();

        Reminder r1 = Reminder.builder().firmId(fid).title("Follow Up").note("Follow up on Proposal A").dueDate(LocalDateTime.of(2026, 10, 1, 10, 0)).build();
        Reminder r2 = Reminder.builder().firmId(fid).title("Follow Up").note("Follow up on Payment B").dueDate(LocalDateTime.of(2026, 10, 15, 10, 0)).build();
        reminderRepo.save(r1);
        reminderRepo.save(r2);

        Note n1 = Note.builder().firmId(fid).title("Meeting Notes").content("Discussed Q3 budget").build();
        Note n2 = Note.builder().firmId(fid).title("Meeting Notes").content("Discussed hiring plan").build();
        noteRepo.save(n1);
        noteRepo.save(n2);

        BackupDTO backup = backupService.exportData(fid);
        assertEquals(2, backup.getReminders().size());
        assertEquals(2, backup.getNotes().size());

        backupService.factoryReset();
        backupService.importData(backup, null, true);

        assertEquals(2, reminderRepo.findAll().size(), "Both reminders must survive");
        assertEquals(2, noteRepo.findAll().size(), "Both notes must survive");

        for (int i = 0; i < 3; i++) {
            backupService.importData(backup, null, true);
        }
        assertEquals(2, reminderRepo.findAll().size());
        assertEquals(2, noteRepo.findAll().size());
    }

    @Test
    @DisplayName("4. Notifications: Two distinct notifications with identical firm and title survive restore")
    public void testCoexistingNotificationsWithSameTitleSurviveRestore() {
        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Notification Test Firm");
        firm = firmDetailsRepo.save(firm);
        Long fid = firm.getId();

        Notification m1 = Notification.builder().firmId(fid).category(NotificationCategory.SYSTEM).eventKey("sys:alert:1").title("System Alert").body("Disk check completed").sender("System").build();
        Notification m2 = Notification.builder().firmId(fid).category(NotificationCategory.SYSTEM).eventKey("sys:alert:2").title("System Alert").body("Backup check completed").sender("Cron").build();
        notificationRepo.save(m1);
        notificationRepo.save(m2);

        BackupDTO backup = backupService.exportData(fid);
        assertEquals(2, backup.getNotifications().size());

        backupService.factoryReset();
        backupService.importData(backup, null, true);

        assertEquals(2, notificationRepo.findAll().size(), "Both notifications must survive restore");

        for (int i = 0; i < 3; i++) {
            backupService.importData(backup, null, true);
        }
        assertEquals(2, notificationRepo.findAll().size());
    }

    @Test
    @DisplayName("5. Employee Advances: Two distinct advances with identical employee, date, amount survive restore")
    public void testCoexistingEmployeeAdvancesSurviveRestore() {
        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Employee Advance Firm");
        firm = firmDetailsRepo.save(firm);
        Long fid = firm.getId();

        Employee emp = new Employee();
        emp.setFirmId(fid);
        emp.setName("Rohan Deshmukh");
        emp.setPhone("9876543210");
        emp = employeeRepo.save(emp);

        EmployeeAdvance adv1 = new EmployeeAdvance();
        adv1.setEmployee(emp);
        adv1.setDate(LocalDate.of(2026, 9, 29));
        adv1.setAmount(2000.0);
        adv1.setDescription("Morning advance");
        advanceRepo.save(adv1);

        EmployeeAdvance adv2 = new EmployeeAdvance();
        adv2.setEmployee(emp);
        adv2.setDate(LocalDate.of(2026, 9, 29));
        adv2.setAmount(2000.0);
        adv2.setDescription("Evening emergency advance");
        advanceRepo.save(adv2);

        BackupDTO backup = backupService.exportData(fid);
        assertEquals(2, backup.getAdvances().size());

        backupService.factoryReset();
        backupService.importData(backup, null, true);

        assertEquals(2, advanceRepo.findAll().size(), "Both advances must survive restore");

        for (int i = 0; i < 3; i++) {
            backupService.importData(backup, null, true);
        }
        assertEquals(2, advanceRepo.findAll().size());
    }

    @Test
    @DisplayName("6. Invoice Payments: Two distinct payments with identical invoice, amount, date survive restore")
    public void testCoexistingInvoicePaymentsSurviveRestore() {
        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Invoice Payment Firm");
        firm = firmDetailsRepo.save(firm);
        Long fid = firm.getId();

        Customer c = new Customer();
        c.setFirmId(fid);
        c.setName("Payer Customer");
        c.setPhone("9876543211");
        c = customerRepo.save(c);

        Invoice inv = new Invoice();
        inv.setFirmId(fid);
        inv.setInvoiceNumber("INV-PAY-001");
        inv.setCustomer(c);
        inv.setTotalAmount(new BigDecimal("10000.00"));
        inv = invoiceRepo.save(inv);

        InvoicePayment p1 = InvoicePayment.builder()
                .firmId(fid)
                .invoiceId(inv.getId())
                .customerId(c.getId())
                .amount(new BigDecimal("2500.00"))
                .paymentDate(LocalDate.of(2026, 9, 29))
                .notes("Part payment 1 via UPI")
                .build();
        invoicePaymentRepo.save(p1);

        InvoicePayment p2 = InvoicePayment.builder()
                .firmId(fid)
                .invoiceId(inv.getId())
                .customerId(c.getId())
                .amount(new BigDecimal("2500.00"))
                .paymentDate(LocalDate.of(2026, 9, 29))
                .notes("Part payment 2 via Cash")
                .build();
        invoicePaymentRepo.save(p2);

        BackupDTO backup = backupService.exportData(fid);
        assertEquals(2, backup.getInvoicePayments().size());

        backupService.factoryReset();
        backupService.importData(backup, null, true);

        assertEquals(2, invoicePaymentRepo.findAll().size(), "Both payments must survive restore");

        for (int i = 0; i < 3; i++) {
            backupService.importData(backup, null, true);
        }
        assertEquals(2, invoicePaymentRepo.findAll().size());
    }

    @Test
    @DisplayName("7. Scenario A & D: Export X, create legitimate Y with same Tier-3 values in live DB, import backup -> X and Y remain distinct, Y untouched")
    public void testScenarioAandD_ExportX_CreateY_RestoreX_PreservesBothDistinct() {
        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Scenario A Test Firm");
        firm = firmDetailsRepo.save(firm);
        Long fid = firm.getId();

        // Create Entity X (Expense X)
        Expense expX = Expense.builder()
                .firmId(fid)
                .title("Expense X")
                .expenseDate(LocalDate.of(2026, 9, 29))
                .amount(new BigDecimal("750.00"))
                .category("Office Supplies")
                .notes("Original X")
                .build();
        expX = expenseRepo.save(expX);

        BackupDTO backup = backupService.exportData(fid);

        // Now create a legitimate distinct Entity Y in the live DB with identical Tier-3 keys (date, amount, category)
        Expense expY = Expense.builder()
                .firmId(fid)
                .title("Expense Y")
                .expenseDate(LocalDate.of(2026, 9, 29))
                .amount(new BigDecimal("750.00"))
                .category("Office Supplies")
                .notes("Newly created live Y")
                .build();
        expY = expenseRepo.save(expY);

        assertEquals(2, expenseRepo.findAll().size(), "Live DB has X and Y before import");

        // Import the backup containing X
        backupService.importData(backup, fid, true);

        List<Expense> allExp = expenseRepo.findAll();
        assertEquals(2, allExp.size(), "X must resolve to X via Tier-1/mapping without touching Y or duplicating X");

        // Verify Y is untouched
        Expense liveY = expenseRepo.findById(expY.getId()).orElse(null);
        assertNotNull(liveY);
        assertEquals("Expense Y", liveY.getTitle());
        assertEquals("Newly created live Y", liveY.getNotes());
    }

    @Test
    @DisplayName("8. Scenario B: Export X, Import X repeatedly 10 times -> exactly one X")
    public void testScenarioB_RepeatedImport10Times_ExactlyOneX() {
        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Scenario B Test Firm");
        firm = firmDetailsRepo.save(firm);
        Long fid = firm.getId();

        Customer c = new Customer();
        c.setFirmId(fid);
        c.setName("Unique Client X");
        c.setPhone("9988776655");
        customerRepo.save(c);

        Product p = Product.builder().firmId(fid).name("Unique Product X").sku("UPX-01").price(new BigDecimal("500.00")).build();
        productRepo.save(p);

        BackupDTO backup = backupService.exportData(fid);

        // Repeat import 10 times
        for (int i = 0; i < 10; i++) {
            backupService.importData(backup, fid, true);
        }

        assertEquals(1, customerRepo.findAll().size(), "Customer count must be exactly 1");
        assertEquals(1, productRepo.findAll().size(), "Product count must be exactly 1");
    }

    @Test
    @DisplayName("9. Scenario C: Export X, Modify X in live DB, Import old backup -> live DB retained, no duplicate")
    public void testScenarioC_ExportX_ModifyInLiveDB_ImportOldBackup_RetainsLiveValues() {
        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Scenario C Test Firm");
        firm = firmDetailsRepo.save(firm);
        Long fid = firm.getId();

        Customer c = new Customer();
        c.setFirmId(fid);
        c.setName("Customer C");
        c.setPhone("9900000001");
        c.setAddress("Old Address In Backup");
        c = customerRepo.save(c);

        BackupDTO backup = backupService.exportData(fid);

        // Modify X in live DB
        c.setAddress("New Live Updated Address");
        customerRepo.save(c);

        // Import old backup
        backupService.importData(backup, fid, true);

        Customer liveC = customerRepo.findById(c.getId()).orElse(null);
        assertNotNull(liveC);
        assertEquals(1, customerRepo.findAll().size(), "Must not duplicate customer");
        assertEquals("New Live Updated Address", liveC.getAddress(), "Live DB update must not be overwritten by older backup");
    }

    @Test
    @DisplayName("10. Scenario E: Export Firm A and Firm B with matching natural-key values, restore both -> zero cross-firm merging")
    public void testScenarioE_MultiFirmMatchingNaturalKeys_ZeroCrossFirmMerging() {
        // Firm A
        FirmDetails firmA = new FirmDetails();
        firmA.setFirmName("Firm Alpha Safety");
        firmA.setGstin("27AAAAA0000A1Z5");
        firmA = firmDetailsRepo.save(firmA);
        Long fAId = firmA.getId();

        Customer cA = new Customer();
        cA.setFirmId(fAId);
        cA.setName("Common Client Name");
        cA.setPhone("9000000000");
        customerRepo.save(cA);

        Expense expA = Expense.builder().firmId(fAId).title("Office Rent").expenseDate(LocalDate.of(2026, 9, 29)).amount(new BigDecimal("15000.00")).category("Rent").build();
        expenseRepo.save(expA);

        // Firm B
        FirmDetails firmB = new FirmDetails();
        firmB.setFirmName("Firm Beta Safety");
        firmB.setGstin("27BBBBB0000B1Z5");
        firmB = firmDetailsRepo.save(firmB);
        Long fBId = firmB.getId();

        Customer cB = new Customer();
        cB.setFirmId(fBId);
        cB.setName("Common Client Name"); // Identical name & phone in Firm B
        cB.setPhone("9000000000");
        customerRepo.save(cB);

        Expense expB = Expense.builder().firmId(fBId).title("Office Rent").expenseDate(LocalDate.of(2026, 9, 29)).amount(new BigDecimal("15000.00")).category("Rent").build();
        expenseRepo.save(expB);

        // Export system backup with all firms
        BackupDTO fullBackup = backupService.exportAllData();
        assertEquals(2, fullBackup.getCustomers().size());
        assertEquals(2, fullBackup.getExpenses().size());

        // Factory reset
        backupService.factoryReset();
        assertEquals(0, customerRepo.findAll().size());
        assertEquals(0, expenseRepo.findAll().size());

        // Restore full backup 5 times
        for (int i = 0; i < 5; i++) {
            backupService.importData(fullBackup, null, true);
        }

        // Verify total and firm-scoped counts
        assertEquals(2, firmDetailsRepo.findAll().size(), "Both firms must exist");
        assertEquals(2, customerRepo.findAll().size(), "Both customers must exist across the 2 firms");
        assertEquals(2, expenseRepo.findAll().size(), "Both expenses must exist across the 2 firms");

        List<FirmDetails> restoredFirms = firmDetailsRepo.findAll();
        for (FirmDetails f : restoredFirms) {
            assertEquals(1, customerRepo.findByFirmIdOrderByNameAsc(f.getId()).size(), "Each firm must have exactly 1 customer");
            assertEquals(1, expenseRepo.findByFirmIdOrderByExpenseDateDescIdDesc(f.getId()).size(), "Each firm must have exactly 1 expense");
        }
    }
}
