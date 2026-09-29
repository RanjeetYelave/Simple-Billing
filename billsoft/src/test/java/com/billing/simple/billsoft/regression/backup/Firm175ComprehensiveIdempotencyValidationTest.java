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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = BillsoftApplication.class)
@ActiveProfiles("test")
public class Firm175ComprehensiveIdempotencyValidationTest {

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
    private InvoiceItemRepository invoiceItemRepo;
    @Autowired
    private InvoicePaymentRepository invoicePaymentRepo;
    @Autowired
    private SalesReturnRepository salesReturnRepo;
    @Autowired
    private SalesReturnItemRepository salesReturnItemRepo;
    @Autowired
    private PartyRepository partyRepo;
    @Autowired
    private PartyPaymentRepository partyPaymentRepo;
    @Autowired
    private PurchaseOrderRepository purchaseOrderRepo;
    @Autowired
    private PurchaseOrderItemRepository purchaseOrderItemRepo;
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
    private InboxMessageRepository inboxMessageRepo;
    @Autowired
    private SavingRepository savingRepo;
    @Autowired
    private GoalRepository goalRepo;
    @Autowired
    private GoalLogRepository goalLogRepo;

    @BeforeEach
    void setupCleanDatabase() {
        backupService.factoryReset();
    }

    @Test
    @DisplayName("Tests 1-4 & 7-11: 10x Repeated Import Idempotency Across All 19 Entities & Financial Totals")
    public void testTenConsecutiveMergeImportsProduceZeroDuplicates() {
        // Setup Firm 175
        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Auto Firm 175");
        firm.setGstin("27ABCDE1234F1Z5");
        firm = firmDetailsRepo.save(firm);
        Long firmId = firm.getId();

        // Customer
        Customer c1 = new Customer();
        c1.setFirmId(firmId);
        c1.setName("Ramesh Motors");
        c1.setPhone("9811002200");
        c1 = customerRepo.save(c1);

        // Product
        Product p1 = Product.builder()
                .firmId(firmId)
                .name("Engine Oil 5W30 4L")
                .sku("OIL-5W30-4L")
                .price(new BigDecimal("2200.00"))
                .costPrice(new BigDecimal("1500.00"))
                .stockQuantity(new BigDecimal("50.000"))
                .unit("CAN")
                .build();
        p1 = productRepo.save(p1);

        // Stock Movement
        StockMovement sm1 = StockMovement.builder()
                .firmId(firmId)
                .productId(p1.getId())
                .productName(p1.getName())
                .movementType("OPENING")
                .quantityChange(new BigDecimal("50.000"))
                .newStock(new BigDecimal("50.000"))
                .build();
        stockMovementRepo.save(sm1);

        // Invoice + Item
        Invoice inv1 = new Invoice();
        inv1.setFirmId(firmId);
        inv1.setCustomer(c1);
        inv1.setInvoiceNumber("INV-2026-101");
        inv1.setInvoiceDate(LocalDateTime.of(2026, 9, 20, 10, 0));
        inv1.setTotalAmount(new BigDecimal("4400.00"));
        inv1.setStatus(InvoiceStatus.UNPAID);
        inv1.setPaid(false);
        inv1.setItems(new ArrayList<>());

        InvoiceItem it1 = new InvoiceItem();
        it1.setInvoice(inv1);
        it1.setProduct(p1);
        it1.setQty(2);
        it1.setPricePerUnit(new BigDecimal("2200.00"));
        it1.setLineTotal(new BigDecimal("4400.00"));
        inv1.getItems().add(it1);
        inv1 = invoiceRepo.save(inv1);

        // Payment
        InvoicePayment pmt1 = InvoicePayment.builder()
                .firmId(firmId)
                .customerId(c1.getId())
                .invoiceId(inv1.getId())
                .amount(new BigDecimal("2000.00"))
                .paymentDate(LocalDate.of(2026, 9, 21))
                .build();
        invoicePaymentRepo.save(pmt1);

        // Sales Return + Item
        SalesReturn ret1 = SalesReturn.builder()
                .firmId(firmId)
                .customer(c1)
                .invoice(inv1)
                .returnNumber("RET-2026-101")
                .returnDate(LocalDate.of(2026, 9, 22))
                .totalRefundAmount(new BigDecimal("2200.00"))
                .items(new ArrayList<>())
                .build();
        SalesReturnItem retIt1 = SalesReturnItem.builder()
                .salesReturn(ret1)
                .product(p1)
                .productName(p1.getName())
                .returnQty(1)
                .unitPrice(new BigDecimal("2200.00"))
                .refundTotal(new BigDecimal("2200.00"))
                .build();
        ret1.getItems().add(retIt1);
        salesReturnRepo.save(ret1);

        // Party (Vendor) + PO + PO Item + Party Payment
        Party vendor = Party.builder()
                .firmId(firmId)
                .name("Lubricants Distribution Corp")
                .phone("9822334455")
                .build();
        vendor = partyRepo.save(vendor);

        PurchaseOrder po = PurchaseOrder.builder()
                .firmId(firmId)
                .party(vendor)
                .partyName(vendor.getName())
                .poNumber("PO-2026-101")
                .poDate(LocalDate.of(2026, 9, 10))
                .totalAmount(new BigDecimal("30000.00"))
                .status(PurchaseOrderStatus.ISSUED)
                .items(new ArrayList<>())
                .build();
        PurchaseOrderItem poIt = PurchaseOrderItem.builder()
                .purchaseOrder(po)
                .productId(p1.getId())
                .productName(p1.getName())
                .quantity(new BigDecimal("20.000"))
                .unitPrice(new BigDecimal("1500.00"))
                .totalAmount(new BigDecimal("30000.00"))
                .build();
        po.getItems().add(poIt);
        purchaseOrderRepo.save(po);

        PartyPayment pp = PartyPayment.builder()
                .firmId(firmId)
                .partyId(vendor.getId())
                .purchaseOrderId(po.getId())
                .amount(new BigDecimal("15000.00"))
                .paymentDate(LocalDate.of(2026, 9, 12))
                .build();
        partyPaymentRepo.save(pp);

        // Employee + Salary + Advance
        Employee emp = new Employee();
        emp.setFirmId(firmId);
        emp.setName("Sunil Kadam");
        emp.setPhone("9833445566");
        emp.setMonthlyBaseSalary(32000.0);
        emp = employeeRepo.save(emp);

        SalaryRecord sal = new SalaryRecord();
        sal.setEmployee(emp);
        sal.setMonthYear(String.format("%02d-%d", LocalDate.now().getMonthValue(), LocalDate.now().getYear()));
        sal.setNetPaid(32000.0);
        sal.setPaymentDate(LocalDate.now());
        salaryRepo.save(sal);

        EmployeeAdvance adv = new EmployeeAdvance();
        adv.setEmployee(emp);
        adv.setAmount(3000.0);
        adv.setDate(LocalDate.of(2026, 9, 5));
        advanceRepo.save(adv);

        // Expense, Reminder, Note, Goal, Saving, GoalLog
        Expense exp = Expense.builder()
                .firmId(firmId)
                .title("Shop Cleaning")
                .amount(new BigDecimal("800.00"))
                .category("Maintenance")
                .expenseDate(LocalDate.of(2026, 9, 15))
                .build();
        expenseRepo.save(exp);

        Reminder rem = Reminder.builder()
                .firmId(firmId)
                .customerId(c1.getId())
                .title("Quarterly Service Callback")
                .dueDate(LocalDateTime.of(2026, 9, 30, 10, 0))
                .build();
        reminderRepo.save(rem);

        Note note = Note.builder()
                .firmId(firmId)
                .customerId(c1.getId())
                .title("Customer fleet info")
                .content("Fleet of 5 commercial vehicles.")
                .build();
        noteRepo.save(note);

        Goal goal = Goal.builder()
                .firmId(firmId)
                .title("Lube Sales Target")
                .goalType(GoalType.SAVINGS_TARGET)
                .targetValue(new BigDecimal("100000.00"))
                .currentValue(new BigDecimal("30000.00"))
                .build();
        goal = goalRepo.save(goal);

        SavingRecord sav = SavingRecord.builder()
                .firmId(firmId)
                .goalId(goal.getId())
                .title("Lube Reserve")
                .amount(new BigDecimal("10000.00"))
                .savingDate(LocalDate.of(2026, 9, 18))
                .build();
        savingRepo.save(sav);

        GoalLog gl = GoalLog.builder()
                .firmId(firmId)
                .goalId(goal.getId())
                .actionType("DEPOSIT")
                .deltaValue(new BigDecimal("10000.00"))
                .resultingValue(new BigDecimal("30000.00"))
                .logDate(LocalDate.of(2026, 9, 18))
                .build();
        goalLogRepo.save(gl);

        // Business Letter + Inbox
        BusinessLetter bl = BusinessLetter.builder()
                .firmId(firmId)
                .letterNumber("LET-101")
                .letterDate(LocalDate.of(2026, 9, 20))
                .subject("Fleet Maintenance Agreement")
                .content("Agreement terms...")
                .recipientName("Ramesh Motors")
                .customerId(c1.getId())
                .build();
        businessLetterRepo.save(bl);

        InboxMessage msg = InboxMessage.builder()
                .firmId(firmId)
                .subject("Inventory Alert: Oil stock updated")
                .body("Stock updated to 50 cans.")
                .build();
        inboxMessageRepo.save(msg);

        // Baseline verification
        assertEquals(1, customerRepo.countByFirmId(firmId));
        assertEquals(1, productRepo.countByFirmId(firmId));
        assertEquals(1, invoiceRepo.countByFirmId(firmId));
        assertEquals(1, invoicePaymentRepo.findByFirmIdOrderByPaymentDateDescIdDesc(firmId).size());
        assertEquals(1, salesReturnRepo.findByFirmIdOrderByReturnDateDescIdDesc(firmId).size());
        assertEquals(1, purchaseOrderRepo.findByFirmIdOrderByPoDateDescIdDesc(firmId).size());
        assertEquals(1, employeeRepo.findByFirmId(firmId).size());

        // Export once
        BackupDTO backup = backupService.exportData(firmId);
        assertNotNull(backup);

        // Perform 10 consecutive merge imports
        for (int i = 1; i <= 10; i++) {
            backupService.importSelectiveData(backup, null, "merge", firmId);

            // Assert counts remain strictly 1
            assertEquals(1, customerRepo.countByFirmId(firmId), "Iteration " + i + ": Customer count changed");
            assertEquals(1, productRepo.countByFirmId(firmId), "Iteration " + i + ": Product count changed");
            assertEquals(1, invoiceRepo.countByFirmId(firmId), "Iteration " + i + ": Invoice count changed");
            assertEquals(1, invoicePaymentRepo.findByFirmIdOrderByPaymentDateDescIdDesc(firmId).size(), "Iteration " + i + ": Payment count changed");
            assertEquals(1, salesReturnRepo.findByFirmIdOrderByReturnDateDescIdDesc(firmId).size(), "Iteration " + i + ": Sales return count changed");
            assertEquals(1, purchaseOrderRepo.findByFirmIdOrderByPoDateDescIdDesc(firmId).size(), "Iteration " + i + ": PO count changed");
            assertEquals(1, partyRepo.findByFirmIdOrderByNameAsc(firmId).size(), "Iteration " + i + ": Vendor count changed");
            assertEquals(1, partyPaymentRepo.findByFirmIdOrderByPaymentDateDescIdDesc(firmId).size(), "Iteration " + i + ": Vendor payment count changed");
            assertEquals(1, employeeRepo.findByFirmId(firmId).size(), "Iteration " + i + ": Employee count changed");
            assertEquals(1, expenseRepo.countByFirmId(firmId), "Iteration " + i + ": Expense count changed");
            assertEquals(1, reminderRepo.findByFirmId(firmId).size(), "Iteration " + i + ": Reminder count changed");
            assertEquals(1, noteRepo.findByFirmId(firmId).size(), "Iteration " + i + ": Note count changed");
            assertEquals(1, goalRepo.findByFirmIdOrderByCreatedAtDesc(firmId).size(), "Iteration " + i + ": Goal count changed");
            assertEquals(1, savingRepo.findByFirmIdOrderBySavingDateDescIdDesc(firmId).size(), "Iteration " + i + ": Saving count changed");
            assertEquals(1, goalLogRepo.findByFirmIdOrderByLogDateAscCreatedAtAsc(firmId).size(), "Iteration " + i + ": Goal log count changed");
            assertEquals(1, businessLetterRepo.findByFirmIdOrderByLetterDateDescIdDesc(firmId).size(), "Iteration " + i + ": Letter count changed");
            assertEquals(1, inboxMessageRepo.findByFirmIdOrderByCreatedAtDesc(firmId).size(), "Iteration " + i + ": Inbox count changed");

            // Verify invoice item child list count is still 1
            Invoice checkInv = invoiceRepo.findAllByFirmId(firmId).get(0);
            assertEquals(1, checkInv.getItems().size(), "Iteration " + i + ": Invoice item count changed");

            // Verify financial totals
            BigDecimal totalInvoiced = invoiceRepo.findAllByFirmId(firmId).stream().map(Invoice::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalPaid = invoicePaymentRepo.findByFirmIdOrderByPaymentDateDescIdDesc(firmId).stream().map(InvoicePayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(new BigDecimal("4400.00"), totalInvoiced, "Iteration " + i + ": Invoice total distorted");
            assertEquals(new BigDecimal("2000.00"), totalPaid, "Iteration " + i + ": Payment total distorted");
        }
    }

    @Test
    @DisplayName("Test 12 (Section 12): Legitimate New Data Is Accurately Imported Without Duplicating Existing Data")
    public void testLegitimateNewDataImportAlongWithExistingData() {
        // 1. Initial State A
        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Auto Firm Initial");
        firm = firmDetailsRepo.save(firm);
        Long firmId = firm.getId();

        Customer cX = new Customer();
        cX.setFirmId(firmId);
        cX.setName("Customer X");
        cX.setPhone("9100000001");
        cX = customerRepo.save(cX);

        Product pX = Product.builder()
                .firmId(firmId)
                .name("Product X")
                .sku("SKU-X")
                .price(new BigDecimal("100.00"))
                .build();
        pX = productRepo.save(pX);

        Invoice invX = new Invoice();
        invX.setFirmId(firmId);
        invX.setCustomer(cX);
        invX.setInvoiceNumber("INV-X-001");
        invX.setInvoiceDate(LocalDateTime.now());
        invX.setTotalAmount(new BigDecimal("100.00"));
        invX.setItems(new ArrayList<>());
        InvoiceItem itX = new InvoiceItem();
        itX.setInvoice(invX);
        itX.setProduct(pX);
        itX.setQty(1);
        itX.setPricePerUnit(new BigDecimal("100.00"));
        itX.setLineTotal(new BigDecimal("100.00"));
        invX.getItems().add(itX);
        invX = invoiceRepo.save(invX);

        // Export Backup A
        BackupDTO backupA = backupService.exportData(firmId);

        // 2. Add Legitimate New Data Y
        Customer cY = new Customer();
        cY.setFirmId(firmId);
        cY.setName("Customer Y");
        cY.setPhone("9200000002");
        cY = customerRepo.save(cY);

        Product pY = Product.builder()
                .firmId(firmId)
                .name("Product Y")
                .sku("SKU-Y")
                .price(new BigDecimal("200.00"))
                .build();
        pY = productRepo.save(pY);

        Invoice invY = new Invoice();
        invY.setFirmId(firmId);
        invY.setCustomer(cY);
        invY.setInvoiceNumber("INV-Y-002");
        invY.setInvoiceDate(LocalDateTime.now());
        invY.setTotalAmount(new BigDecimal("200.00"));
        invY.setItems(new ArrayList<>());
        InvoiceItem itY = new InvoiceItem();
        itY.setInvoice(invY);
        itY.setProduct(pY);
        itY.setQty(1);
        itY.setPricePerUnit(new BigDecimal("200.00"));
        itY.setLineTotal(new BigDecimal("200.00"));
        invY.getItems().add(itY);
        invY = invoiceRepo.save(invY);

        // Export Backup B (Contains both X and Y)
        BackupDTO backupB = backupService.exportData(firmId);
        assertEquals(2, backupB.getCustomers().size());
        assertEquals(2, backupB.getProducts().size());
        assertEquals(2, backupB.getInvoices().size());

        // 3. Reset to State A (Only X exists in DB)
        backupService.factoryReset();
        firm = new FirmDetails();
        firm.setFirmName("Auto Firm Restored");
        firm = firmDetailsRepo.save(firm);
        Long targetFirmId = firm.getId();

        // Restore Backup A first
        backupService.importSelectiveData(backupA, null, "merge", targetFirmId);
        assertEquals(1, customerRepo.countByFirmId(targetFirmId));
        assertEquals(1, productRepo.countByFirmId(targetFirmId));
        assertEquals(1, invoiceRepo.countByFirmId(targetFirmId));

        // 4. Import Backup B (Contains X + new Y)
        backupService.importSelectiveData(backupB, null, "merge", targetFirmId);

        // Verification:
        // - X was NOT duplicated
        // - Y was newly imported
        // - Total customers = 2, Total products = 2, Total invoices = 2
        assertEquals(2, customerRepo.countByFirmId(targetFirmId), "Must contain exactly 2 customers (X + Y)");
        assertEquals(2, productRepo.countByFirmId(targetFirmId), "Must contain exactly 2 products (X + Y)");
        assertEquals(2, invoiceRepo.countByFirmId(targetFirmId), "Must contain exactly 2 invoices (X + Y)");

        List<Customer> targetCusts = customerRepo.findByFirmIdOrderByNameAsc(targetFirmId);
        assertTrue(targetCusts.stream().anyMatch(c -> c.getName().equals("Customer X")));
        assertTrue(targetCusts.stream().anyMatch(c -> c.getName().equals("Customer Y")));

        List<Invoice> targetInvs = invoiceRepo.findAllByFirmId(targetFirmId);
        Invoice resolvedInvY = targetInvs.stream().filter(inv -> "INV-Y-002".equals(inv.getInvoiceNumber())).findFirst().orElse(null);
        assertNotNull(resolvedInvY);
        assertNotNull(resolvedInvY.getCustomer());
        assertEquals("Customer Y", resolvedInvY.getCustomer().getName(), "Invoice Y must reference resolved Customer Y");
        assertEquals(1, resolvedInvY.getItems().size());
        assertEquals("Product Y", resolvedInvY.getItems().get(0).getProduct().getName(), "Invoice item must reference resolved Product Y");
    }

    @Test
    @DisplayName("Test 5: Round-Trip Export/Import onto Fresh Clean Database")
    public void testRoundTripExportImportToCleanDatabase() {
        // Firm 1 with rich data
        FirmDetails firm1 = new FirmDetails();
        firm1.setFirmName("Round Trip Source Firm");
        firm1.setGstin("27ROUNDTRIP01");
        firm1 = firmDetailsRepo.save(firm1);
        Long firm1Id = firm1.getId();

        Customer c = new Customer();
        c.setFirmId(firm1Id);
        c.setName("Round Trip Customer");
        c.setPhone("9988001122");
        c = customerRepo.save(c);

        Product p = Product.builder()
                .firmId(firm1Id)
                .name("Round Trip Product")
                .sku("RT-PROD-01")
                .price(new BigDecimal("500.00"))
                .build();
        p = productRepo.save(p);

        Invoice inv = new Invoice();
        inv.setFirmId(firm1Id);
        inv.setCustomer(c);
        inv.setInvoiceNumber("INV-RT-01");
        inv.setInvoiceDate(LocalDateTime.now());
        inv.setTotalAmount(new BigDecimal("500.00"));
        inv.setItems(new ArrayList<>());
        InvoiceItem item = new InvoiceItem();
        item.setInvoice(inv);
        item.setProduct(p);
        item.setQty(1);
        item.setPricePerUnit(new BigDecimal("500.00"));
        item.setLineTotal(new BigDecimal("500.00"));
        inv.getItems().add(item);
        inv = invoiceRepo.save(inv);

        // Export full system
        BackupDTO exportBackup = backupService.exportAllData();
        assertNotNull(exportBackup);

        // Clean wipe DB
        backupService.factoryReset();
        assertEquals(0, firmDetailsRepo.count());
        assertEquals(0, customerRepo.count());
        assertEquals(0, productRepo.count());
        assertEquals(0, invoiceRepo.count());

        // Restore into clean DB
        List<FirmDetails> restoredFirms = backupService.importSelectiveData(exportBackup, null, "clone", null);
        assertEquals(1, restoredFirms.size());
        Long restoredFirmId = restoredFirms.get(0).getId();

        assertEquals(1, customerRepo.countByFirmId(restoredFirmId));
        assertEquals(1, productRepo.countByFirmId(restoredFirmId));
        assertEquals(1, invoiceRepo.countByFirmId(restoredFirmId));

        Customer restoredCust = customerRepo.findByFirmIdOrderByNameAsc(restoredFirmId).get(0);
        assertEquals("Round Trip Customer", restoredCust.getName());

        Invoice restoredInv = invoiceRepo.findAllByFirmId(restoredFirmId).get(0);
        assertEquals("INV-RT-01", restoredInv.getInvoiceNumber());
        assertEquals(restoredCust.getId(), restoredInv.getCustomer().getId(), "FK to customer must match new restored ID");
        assertEquals(1, restoredInv.getItems().size());
        assertEquals("Round Trip Product", restoredInv.getItems().get(0).getProduct().getName());
    }
}
