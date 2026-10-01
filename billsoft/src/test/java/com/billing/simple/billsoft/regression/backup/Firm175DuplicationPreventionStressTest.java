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
public class Firm175DuplicationPreventionStressTest {

    @Autowired
    private BackupService backupService;

    @Autowired
    private FirmDetailsRepository firmDetailsRepo;
    @Autowired
    private CustomerRepository customerRepo;
    @Autowired
    private ProductRepository productRepo;
    @Autowired
    private StockMovementRepository stockMovementRepo;
    @Autowired
    private InvoiceRepository invoiceRepo;
    @Autowired
    private InvoicePaymentRepository invoicePaymentRepo;
    @Autowired
    private SalesReturnRepository salesReturnRepo;
    @Autowired
    private PartyRepository partyRepo;
    @Autowired
    private PartyPaymentRepository partyPaymentRepo;
    @Autowired
    private PurchaseOrderRepository purchaseOrderRepo;
    @Autowired
    private ReminderRepository reminderRepo;
    @Autowired
    private NoteRepository noteRepo;
    @Autowired
    private ExpenseRepository expenseRepo;
    @Autowired
    private EmployeeRepository employeeRepo;
    @Autowired
    private AttendanceRecordRepository attendanceRecordRepo;
    @Autowired
    private LeaveRecordRepository leaveRecordRepo;
    @Autowired
    private SalaryRecordRepository salaryRepo;
    @Autowired
    private EmployeeAdvanceRepository advanceRepo;
    @Autowired
    private PromotionRecordRepository promotionRepo;
    @Autowired
    private EmployeeDocumentRepository employeeDocumentRepo;
    @Autowired
    private BusinessLetterRepository businessLetterRepo;
    @Autowired
    private NotificationRepository notificationRepo;
    @Autowired
    private SavingRepository savingRepo;
    @Autowired
    private GoalRepository goalRepo;
    @Autowired
    private GoalLogRepository goalLogRepo;

    @BeforeEach
    void resetCleanDatabase() {
        backupService.factoryReset();
    }

    @Test
    @DisplayName("Firm 175 Stress Test: 5 Cycles of Full System Export and Merge Import Produce 0 Duplicates")
    public void testFirm175MultiCycleDuplicationStress() {
        // ── Step 1: Initialize Multi-Tenant Environment ──
        FirmDetails firm1 = new FirmDetails();
        firm1.setFirmName("Primary Firm One");
        firm1.setGstin("27AAAAA1111A1Z1");
        firm1 = firmDetailsRepo.save(firm1);
        Long firm1Id = firm1.getId();

        FirmDetails firm2 = new FirmDetails();
        firm2.setFirmName("Secondary Firm Two");
        firm2.setGstin("27BBBBB2222B2Z2");
        firm2 = firmDetailsRepo.save(firm2);
        Long firm2Id = firm2.getId();

        FirmDetails firm175 = new FirmDetails();
        firm175.setFirmName("Auto Firm");
        firm175.setGstin("27CCCCC3333C3Z3");
        firm175.setPhone("9876543210");
        firm175.setEmail("autofirm@example.com");
        firm175 = firmDetailsRepo.save(firm175);
        Long firm175Id = firm175.getId();

        // ── Step 2: Populate Realistic Data for Firm 175 across all 19 entities ──
        // Customers
        Customer c175_1 = new Customer();
        c175_1.setFirmId(firm175Id);
        c175_1.setName("Auto Client Alpha");
        c175_1.setPhone("9988776655");
        c175_1.setEmail("alpha@autoclient.com");
        c175_1 = customerRepo.save(c175_1);

        Customer c175_2 = new Customer();
        c175_2.setFirmId(firm175Id);
        c175_2.setName("Auto Client Beta");
        c175_2.setPhone("9988776644");
        c175_2.setEmail("beta@autoclient.com");
        c175_2 = customerRepo.save(c175_2);

        // Products
        Product p175_1 = Product.builder()
                .firmId(firm175Id)
                .name("Synthetic Brake Fluid 500ml")
                .sku("BRK-FLD-01")
                .price(new BigDecimal("450.00"))
                .costPrice(new BigDecimal("280.00"))
                .stockQuantity(new BigDecimal("100.000"))
                .minStockLevel(new BigDecimal("20.000"))
                .unit("BTL")
                .build();
        p175_1 = productRepo.save(p175_1);

        Product p175_2 = Product.builder()
                .firmId(firm175Id)
                .name("Ceramic Brake Pads Set")
                .sku("PAD-CRM-02")
                .price(new BigDecimal("2400.00"))
                .costPrice(new BigDecimal("1600.00"))
                .stockQuantity(new BigDecimal("40.000"))
                .minStockLevel(new BigDecimal("5.000"))
                .unit("SET")
                .build();
        p175_2 = productRepo.save(p175_2);

        // Stock Movements
        StockMovement sm175_1 = StockMovement.builder()
                .firmId(firm175Id)
                .productId(p175_1.getId())
                .productName(p175_1.getName())
                .movementType("OPENING")
                .quantityChange(new BigDecimal("100.000"))
                .previousStock(BigDecimal.ZERO)
                .newStock(new BigDecimal("100.000"))
                .createdAt(LocalDateTime.of(2026, 9, 1, 9, 0))
                .build();
        stockMovementRepo.save(sm175_1);

        // Invoices
        Invoice inv175 = new Invoice();
        inv175.setFirmId(firm175Id);
        inv175.setCustomer(c175_1);
        inv175.setInvoiceNumber("INV-AF-0001");
        inv175.setInvoiceDate(LocalDateTime.of(2026, 9, 10, 11, 0));
        inv175.setTotalAmount(new BigDecimal("5250.00"));
        inv175.setStatus(InvoiceStatus.UNPAID);
        inv175.setPaid(false);
        inv175.setItems(new ArrayList<>());

        InvoiceItem itm1 = new InvoiceItem();
        itm1.setInvoice(inv175);
        itm1.setProduct(p175_1);
        itm1.setQty(1);
        itm1.setPricePerUnit(new BigDecimal("450.00"));
        itm1.setLineTotal(new BigDecimal("450.00"));
        inv175.getItems().add(itm1);

        InvoiceItem itm2 = new InvoiceItem();
        itm2.setInvoice(inv175);
        itm2.setProduct(p175_2);
        itm2.setQty(2);
        itm2.setPricePerUnit(new BigDecimal("2400.00"));
        itm2.setLineTotal(new BigDecimal("4800.00"));
        inv175.getItems().add(itm2);

        inv175 = invoiceRepo.save(inv175);

        // Invoice Payments
        InvoicePayment pmt175 = InvoicePayment.builder()
                .firmId(firm175Id)
                .customerId(c175_1.getId())
                .invoiceId(inv175.getId())
                .amount(new BigDecimal("3000.00"))
                .paymentDate(LocalDate.of(2026, 9, 12))
                .paymentMode("UPI")
                .referenceNumber("UPI-AF-9001")
                .build();
        invoicePaymentRepo.save(pmt175);

        // Sales Return
        SalesReturn ret175 = SalesReturn.builder()
                .firmId(firm175Id)
                .customer(c175_1)
                .invoice(inv175)
                .returnNumber("RET-AF-001")
                .returnDate(LocalDate.of(2026, 9, 14))
                .totalRefundAmount(new BigDecimal("450.00"))
                .build();
        salesReturnRepo.save(ret175);

        // Vendor & PO
        Party vendor175 = Party.builder()
                .firmId(firm175Id)
                .name("Auto Parts Depot")
                .phone("9911223344")
                .email("sales@autodepot.com")
                .build();
        vendor175 = partyRepo.save(vendor175);

        PurchaseOrder po175 = PurchaseOrder.builder()
                .firmId(firm175Id)
                .party(vendor175)
                .partyName(vendor175.getName())
                .poNumber("PO-AF-001")
                .poDate(LocalDate.of(2026, 9, 5))
                .totalAmount(new BigDecimal("25000.00"))
                .status(PurchaseOrderStatus.ISSUED)
                .build();
        purchaseOrderRepo.save(po175);

        // Party Payment
        PartyPayment pp175 = PartyPayment.builder()
                .firmId(firm175Id)
                .partyId(vendor175.getId())
                .purchaseOrderId(po175.getId())
                .amount(new BigDecimal("10000.00"))
                .paymentDate(LocalDate.of(2026, 9, 6))
                .paymentMode("NEFT")
                .build();
        partyPaymentRepo.save(pp175);

        // Employee, Salary, Advance, Attendance, Leave, Promotion, Document
        Employee emp175 = new Employee();
        emp175.setFirmId(firm175Id);
        emp175.setName("Vikram Shinde");
        emp175.setPhone("9822110099");
        emp175.setMonthlyBaseSalary(35000.0);
        emp175 = employeeRepo.save(emp175);

        SalaryRecord sal175 = new SalaryRecord();
        sal175.setEmployee(emp175);
        sal175.setMonthYear(String.format("%02d-%d", LocalDate.now().getMonthValue(), LocalDate.now().getYear()));
        sal175.setNetPaid(35000.0);
        sal175.setPaymentDate(LocalDate.now());
        salaryRepo.save(sal175);

        EmployeeAdvance adv175 = new EmployeeAdvance();
        adv175.setEmployee(emp175);
        adv175.setAmount(4000.0);
        adv175.setDate(LocalDate.of(2026, 9, 7));
        advanceRepo.save(adv175);

        AttendanceRecord att175 = new AttendanceRecord();
        att175.setEmployee(emp175);
        att175.setDate(LocalDate.of(2026, 9, 8));
        att175.setStatus("PRESENT");
        attendanceRecordRepo.save(att175);

        LeaveRecord lr175 = new LeaveRecord();
        lr175.setEmployee(emp175);
        lr175.setStartDate(LocalDate.of(2026, 9, 15));
        lr175.setEndDate(LocalDate.of(2026, 9, 16));
        lr175.setType("CASUAL");
        lr175.setStatus("APPROVED");
        lr175.setTotalDays(2);
        leaveRecordRepo.save(lr175);

        PromotionRecord pr175 = new PromotionRecord();
        pr175.setEmployee(emp175);
        pr175.setType("PROMOTION");
        pr175.setEffectiveDate(LocalDate.of(2026, 9, 1));
        pr175.setNewRole("Senior Lead Technician");
        pr175.setIsApplied(true);
        promotionRepo.save(pr175);

        EmployeeDocument doc175 = new EmployeeDocument();
        doc175.setEmployee(emp175);
        doc175.setFileName("aadhaar_vikram.pdf");
        doc175.setType("ID_PROOF");
        doc175.setUploadedAt(LocalDateTime.of(2026, 9, 1, 10, 0));
        employeeDocumentRepo.save(doc175);

        // Expense, Reminder, Note, Goal, Saving, GoalLog, BusinessLetter, Notification
        Expense exp175 = Expense.builder()
                .firmId(firm175Id)
                .title("Workshop Electricity")
                .amount(new BigDecimal("3200.00"))
                .category("Utilities")
                .expenseDate(LocalDate.of(2026, 9, 10))
                .build();
        expenseRepo.save(exp175);

        Reminder rem175 = Reminder.builder()
                .firmId(firm175Id)
                .customerId(c175_1.getId())
                .title("Service due notification")
                .dueDate(LocalDateTime.of(2026, 9, 30, 10, 0))
                .inboxNotified(true)
                .build();
        reminderRepo.save(rem175);

        Note note175 = Note.builder()
                .firmId(firm175Id)
                .customerId(c175_1.getId())
                .title("Customer Preferences")
                .content("Prefers synthetic oil only.")
                .build();
        noteRepo.save(note175);

        Goal goal175 = Goal.builder()
                .firmId(firm175Id)
                .title("Monthly Workshop Sales")
                .goalType(GoalType.SAVINGS_TARGET)
                .targetValue(new BigDecimal("500000.00"))
                .currentValue(new BigDecimal("150000.00"))
                .build();
        goal175 = goalRepo.save(goal175);

        SavingRecord sav175 = SavingRecord.builder()
                .firmId(firm175Id)
                .goalId(goal175.getId())
                .title("Equipment Fund")
                .amount(new BigDecimal("25000.00"))
                .savingDate(LocalDate.of(2026, 9, 15))
                .build();
        savingRepo.save(sav175);

        GoalLog gl175 = GoalLog.builder()
                .firmId(firm175Id)
                .goalId(goal175.getId())
                .actionType("DEPOSIT")
                .deltaValue(new BigDecimal("25000.00"))
                .resultingValue(new BigDecimal("150000.00"))
                .logDate(LocalDate.of(2026, 9, 15))
                .build();
        goalLogRepo.save(gl175);

        BusinessLetter bl175 = BusinessLetter.builder()
                .firmId(firm175Id)
                .letterNumber("LET-AF-2026-01")
                .letterDate(LocalDate.of(2026, 9, 12))
                .subject("Service Warranty Confirmation")
                .content("We confirm full service warranty for the next 12 months.")
                .recipientName("Auto Client Alpha")
                .signatoryName("Auto Firm Manager")
                .customerId(c175_1.getId())
                .build();
        businessLetterRepo.save(bl175);

        Notification notif175 = Notification.builder()
                .firmId(firm175Id)
                .category(NotificationCategory.SYSTEM)
                .eventKey("system:update:175")
                .title("System Update Notification")
                .body("All automated services operational.")
                .build();
        notificationRepo.save(notif175);

        // Also add 1 customer and 1 invoice to Firm 1 to ensure multi-tenant baseline
        Customer c1_1 = new Customer();
        c1_1.setFirmId(firm1Id);
        c1_1.setName("Tenant 1 Cust");
        c1_1.setPhone("9100000001");
        customerRepo.save(c1_1);

        // ── Baseline Fingerprint Capture ──
        long baseFirm175Custs = customerRepo.countByFirmId(firm175Id);
        long baseFirm175Prods = productRepo.countByFirmId(firm175Id);
        long baseFirm175Invs = invoiceRepo.countByFirmId(firm175Id);
        long baseFirm175Pmts = invoicePaymentRepo.findByFirmIdOrderByPaymentDateDescIdDesc(firm175Id).size();
        long baseFirm175Rets = salesReturnRepo.findByFirmIdOrderByReturnDateDescIdDesc(firm175Id).size();
        long baseFirm175POs = purchaseOrderRepo.findByFirmIdOrderByPoDateDescIdDesc(firm175Id).size();
        long baseFirm175Emps = employeeRepo.findByFirmId(firm175Id).size();
        long baseFirm175Exps = expenseRepo.countByFirmId(firm175Id);
        long baseFirm175Rems = reminderRepo.findByFirmId(firm175Id).size();
        long baseFirm175Notes = noteRepo.findByFirmId(firm175Id).size();
        long baseFirm175Letters = businessLetterRepo.findByFirmIdOrderByLetterDateDescIdDesc(firm175Id).size();
        long baseFirm175Notifications = notificationRepo.findByFirmId(firm175Id).size();

        assertEquals(2, baseFirm175Custs, "Baseline Firm 175 customer count must be 2");
        assertEquals(2, baseFirm175Prods, "Baseline Firm 175 product count must be 2");
        assertEquals(1, baseFirm175Invs, "Baseline Firm 175 invoice count must be 1");
        assertEquals(1, baseFirm175Pmts, "Baseline Firm 175 payment count must be 1");

        // ── Step 3: Execute 5 Repeated Cycles of Export-All + Merge Import ──
        for (int cycle = 1; cycle <= 5; cycle++) {
            // Full system export
            BackupDTO fullBackup = backupService.exportAllData();
            assertNotNull(fullBackup);

            // Import back with mode "merge" (Simulating automated test runs / repeated restore)
            backupService.importSelectiveData(fullBackup, null, "merge", null);

            // Verify Firm 175 counts remain exactly identical
            assertEquals(baseFirm175Custs, customerRepo.countByFirmId(firm175Id), "Cycle " + cycle + ": Customer count mismatch for Firm 175");
            assertEquals(baseFirm175Prods, productRepo.countByFirmId(firm175Id), "Cycle " + cycle + ": Product count mismatch for Firm 175");
            assertEquals(baseFirm175Invs, invoiceRepo.countByFirmId(firm175Id), "Cycle " + cycle + ": Invoice count mismatch for Firm 175");
            assertEquals(baseFirm175Pmts, invoicePaymentRepo.findByFirmIdOrderByPaymentDateDescIdDesc(firm175Id).size(), "Cycle " + cycle + ": Payment count mismatch");
            assertEquals(baseFirm175Rets, salesReturnRepo.findByFirmIdOrderByReturnDateDescIdDesc(firm175Id).size(), "Cycle " + cycle + ": Return count mismatch");
            assertEquals(baseFirm175POs, purchaseOrderRepo.findByFirmIdOrderByPoDateDescIdDesc(firm175Id).size(), "Cycle " + cycle + ": PO count mismatch");
            assertEquals(baseFirm175Emps, employeeRepo.findByFirmId(firm175Id).size(), "Cycle " + cycle + ": Employee count mismatch");
            assertEquals(baseFirm175Exps, expenseRepo.countByFirmId(firm175Id), "Cycle " + cycle + ": Expense count mismatch");
            assertEquals(baseFirm175Rems, reminderRepo.findByFirmId(firm175Id).size(), "Cycle " + cycle + ": Reminder count mismatch");
            assertEquals(baseFirm175Notes, noteRepo.findByFirmId(firm175Id).size(), "Cycle " + cycle + ": Note count mismatch");
            assertEquals(baseFirm175Letters, businessLetterRepo.findByFirmIdOrderByLetterDateDescIdDesc(firm175Id).size(), "Cycle " + cycle + ": Letter count mismatch");
            assertEquals(baseFirm175Notifications, notificationRepo.findByFirmId(firm175Id).size(), "Cycle " + cycle + ": Notification message count mismatch");

            // Verify Financial Integrity
            BigDecimal invoiced = invoiceRepo.findAllByFirmId(firm175Id).stream().map(Invoice::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal paid = invoicePaymentRepo.findByFirmIdOrderByPaymentDateDescIdDesc(firm175Id).stream().map(InvoicePayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(new BigDecimal("5250.00"), invoiced, "Cycle " + cycle + ": Total invoiced amount corrupted");
            assertEquals(new BigDecimal("3000.00"), paid, "Cycle " + cycle + ": Total paid amount corrupted");

            // Verify Multi-Tenant Isolation
            assertEquals(1, customerRepo.countByFirmId(firm1Id), "Cycle " + cycle + ": Firm 1 corrupted");
            assertEquals(3, firmDetailsRepo.count(), "Cycle " + cycle + ": Total firms changed");
        }
    }
}
