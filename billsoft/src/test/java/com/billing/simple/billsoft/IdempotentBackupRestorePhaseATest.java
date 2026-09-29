package com.billing.simple.billsoft;

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
public class IdempotentBackupRestorePhaseATest {

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
    @Autowired
    private AppConfigRepository appConfigRepo;

    @BeforeEach
    void cleanDb() {
        backupService.factoryReset();
    }

    @Test
    @Transactional
    @DisplayName("1. Repeated Same-Firm Restore Idempotency (1x, 2x, 3x)")
    public void testRepeatedBackupRestoreIdempotency1x2x3x() {
        // ── 1. Setup Baseline Firm with Rich Entities ──
        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Apex Global Systems");
        firm.setGstin("27AAAAA0000A1Z5");
        firm.setEmail("apex@example.com");
        firm = firmDetailsRepo.save(firm);
        Long firmId = firm.getId();

        // 2 Customers
        Customer c1 = new Customer();
        c1.setFirmId(firmId);
        c1.setName("Alice Corp");
        c1.setPhone("9811111111");
        c1 = customerRepo.save(c1);

        Customer c2 = new Customer();
        c2.setFirmId(firmId);
        c2.setName("Bob Traders");
        c2.setPhone("9822222222");
        c2 = customerRepo.save(c2);

        // 2 Products
        Product p1 = Product.builder()
                .firmId(firmId)
                .name("Industrial Server Rack")
                .sku("RACK-001")
                .price(new BigDecimal("15000.00"))
                .costPrice(new BigDecimal("10000.00"))
                .stockQuantity(new BigDecimal("100.000"))
                .minStockLevel(new BigDecimal("10.000"))
                .unit("NOS")
                .build();
        p1 = productRepo.save(p1);

        Product p2 = Product.builder()
                .firmId(firmId)
                .name("Cat6 Cable 305m")
                .sku("CBL-CAT6")
                .price(new BigDecimal("4500.00"))
                .costPrice(new BigDecimal("3000.00"))
                .stockQuantity(new BigDecimal("50.000"))
                .minStockLevel(new BigDecimal("5.000"))
                .unit("BOX")
                .build();
        p2 = productRepo.save(p2);

        // 1 Stock Movement
        StockMovement sm1 = StockMovement.builder()
                .firmId(firmId)
                .productId(p1.getId())
                .productName(p1.getName())
                .movementType("OPENING")
                .quantityChange(new BigDecimal("100.000"))
                .newStock(new BigDecimal("100.000"))
                .build();
        stockMovementRepo.save(sm1);

        // 1 Invoice (Total = ₹19,500.00) with 2 Items (₹15,000 + ₹4,500)
        Invoice inv1 = new Invoice();
        inv1.setFirmId(firmId);
        inv1.setCustomer(c1);
        inv1.setInvoiceNumber("INV-2026-001");
        inv1.setInvoiceDate(LocalDateTime.of(2026, 9, 21, 10, 0));
        inv1.setTotalAmount(new BigDecimal("19500.00"));
        inv1.setStatus(InvoiceStatus.UNPAID);
        inv1.setPaid(false);
        inv1.setItems(new ArrayList<>());

        InvoiceItem it1 = new InvoiceItem();
        it1.setInvoice(inv1);
        it1.setProduct(p1);
        it1.setQty(1);
        it1.setPricePerUnit(new BigDecimal("15000.00"));
        it1.setLineTotal(new BigDecimal("15000.00"));
        inv1.getItems().add(it1);

        InvoiceItem it2 = new InvoiceItem();
        it2.setInvoice(inv1);
        it2.setProduct(p2);
        it2.setQty(1);
        it2.setPricePerUnit(new BigDecimal("4500.00"));
        it2.setLineTotal(new BigDecimal("4500.00"));
        inv1.getItems().add(it2);

        inv1 = invoiceRepo.save(inv1);

        // 1 Invoice Payment (Amount = ₹10,000.00) -> Outstanding = ₹9,500.00
        InvoicePayment pmt1 = InvoicePayment.builder()
                .firmId(firmId)
                .customerId(c1.getId())
                .invoiceId(inv1.getId())
                .amount(new BigDecimal("10000.00"))
                .paymentDate(LocalDate.of(2026, 9, 22))
                .paymentMode("Bank Transfer")
                .referenceNumber("NEFT-99001")
                .build();
        invoicePaymentRepo.save(pmt1);

        // 1 Sales Return (Amount = ₹4,500.00)
        SalesReturn ret1 = SalesReturn.builder()
                .firmId(firmId)
                .customer(c1)
                .invoice(inv1)
                .returnNumber("RET-001")
                .returnDate(LocalDate.of(2026, 9, 23))
                .totalRefundAmount(new BigDecimal("4500.00"))
                .build();
        salesReturnRepo.save(ret1);

        // 1 Vendor & 1 Purchase Order (₹50,000.00)
        Party vendor = Party.builder()
                .firmId(firmId)
                .name("Global Logistics Co")
                .phone("9833333333")
                .build();
        vendor = partyRepo.save(vendor);

        PurchaseOrder po1 = PurchaseOrder.builder()
                .firmId(firmId)
                .party(vendor)
                .partyName(vendor.getName())
                .poNumber("PO-2026-001")
                .poDate(LocalDate.of(2026, 9, 15))
                .totalAmount(new BigDecimal("50000.00"))
                .status(PurchaseOrderStatus.ISSUED)
                .build();
        purchaseOrderRepo.save(po1);

        // 1 Employee, 1 Salary (₹40,000), 1 Advance (₹5,000)
        Employee emp = new Employee();
        emp.setFirmId(firmId);
        emp.setName("Rohan Deshmukh");
        emp.setPhone("9844444444");
        emp.setMonthlyBaseSalary(40000.0);
        emp = employeeRepo.save(emp);

        SalaryRecord sal = new SalaryRecord();
        sal.setEmployee(emp);
        sal.setMonthYear(String.format("%02d-%d", LocalDate.now().getMonthValue(), LocalDate.now().getYear()));
        sal.setNetPaid(40000.0);
        sal.setPaymentDate(LocalDate.now());
        salaryRepo.save(sal);

        EmployeeAdvance adv = new EmployeeAdvance();
        adv.setEmployee(emp);
        adv.setAmount(5000.0);
        adv.setDate(LocalDate.of(2026, 9, 10));
        advanceRepo.save(adv);

        // 1 Expense, 1 Reminder, 1 Note, 1 Goal, 1 Saving
        Expense exp1 = Expense.builder()
                .firmId(firmId)
                .title("Office Internet")
                .amount(new BigDecimal("1500.00"))
                .expenseDate(LocalDate.of(2026, 9, 18))
                .build();
        expenseRepo.save(exp1);

        Reminder rem1 = Reminder.builder()
                .firmId(firmId)
                .customerId(c1.getId())
                .title("Followup on pending dues")
                .dueDate(LocalDateTime.of(2026, 9, 30, 18, 0))
                .build();
        reminderRepo.save(rem1);

        Note note1 = Note.builder()
                .firmId(firmId)
                .customerId(c1.getId())
                .title("VIP client discount approved")
                .content("Approved 5% discount on bulk orders.")
                .build();
        noteRepo.save(note1);

        Goal goal1 = Goal.builder()
                .firmId(firmId)
                .title("Q3 Revenue Target")
                .goalType(GoalType.SAVINGS_TARGET)
                .targetValue(new BigDecimal("1000000.00"))
                .currentValue(new BigDecimal("250000.00"))
                .build();
        goal1 = goalRepo.save(goal1);

        SavingRecord sav1 = SavingRecord.builder()
                .firmId(firmId)
                .goalId(goal1.getId())
                .title("Reserve Fund Deposit")
                .amount(new BigDecimal("50000.00"))
                .savingDate(LocalDate.of(2026, 9, 20))
                .build();
        savingRepo.save(sav1);

        // ── 2. Export Backup B ──
        BackupDTO backupB = backupService.exportData(firmId);
        assertNotNull(backupB);
        assertEquals(2, backupB.getCustomers().size(), "Exact setup customers must be 2");
        assertEquals(2, backupB.getProducts().size(), "Exact setup products must be 2");
        assertEquals(1, backupB.getInvoices().size(), "Exact setup invoices must be 1");

        // ── 3. First Import (1x) ──
        backupService.importSelectiveData(backupB, null, "merge", firmId);

        long cCount1 = customerRepo.countByFirmId(firmId);
        long pCount1 = productRepo.countByFirmId(firmId);
        long invCount1 = invoiceRepo.countByFirmId(firmId);
        long pmtCount1 = invoicePaymentRepo.findByFirmIdOrderByPaymentDateDescIdDesc(firmId).size();
        long poCount1 = purchaseOrderRepo.findByFirmIdOrderByPoDateDescIdDesc(firmId).size();
        long empCount1 = employeeRepo.findByFirmId(firmId).size();
        long expCount1 = expenseRepo.countByFirmId(firmId);
        long retCount1 = salesReturnRepo.findByFirmIdOrderByReturnDateDescIdDesc(firmId).size();

        BigDecimal totalInvoiced1 = invoiceRepo.findAllByFirmId(firmId).stream().map(Invoice::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaid1 = invoicePaymentRepo.findByFirmIdOrderByPaymentDateDescIdDesc(firmId).stream().map(InvoicePayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstanding1 = totalInvoiced1.subtract(totalPaid1);

        assertEquals(2, cCount1, "Customers after 1x must be 2");
        assertEquals(2, pCount1, "Products after 1x must be 2");
        assertEquals(1, invCount1, "Invoices after 1x must be 1");
        assertEquals(1, pmtCount1, "Payments after 1x must be 1");
        assertEquals(1, poCount1, "POs after 1x must be 1");
        assertEquals(1, empCount1, "Employees after 1x must be 1");
        assertEquals(1, expCount1, "Expenses after 1x must be 1");
        assertEquals(1, retCount1, "Returns after 1x must be 1");
        assertEquals(new BigDecimal("19500.00"), totalInvoiced1, "Total invoiced after 1x must be ₹19,500.00");
        assertEquals(new BigDecimal("10000.00"), totalPaid1, "Total paid after 1x must be ₹10,000.00");
        assertEquals(new BigDecimal("9500.00"), outstanding1, "Outstanding after 1x must be ₹9,500.00");

        // ── 4. Second Import (2x) ──
        backupService.importSelectiveData(backupB, null, "merge", firmId);

        long cCount2 = customerRepo.countByFirmId(firmId);
        long pCount2 = productRepo.countByFirmId(firmId);
        long invCount2 = invoiceRepo.countByFirmId(firmId);
        long pmtCount2 = invoicePaymentRepo.findByFirmIdOrderByPaymentDateDescIdDesc(firmId).size();
        long poCount2 = purchaseOrderRepo.findByFirmIdOrderByPoDateDescIdDesc(firmId).size();
        long empCount2 = employeeRepo.findByFirmId(firmId).size();
        long expCount2 = expenseRepo.countByFirmId(firmId);
        long retCount2 = salesReturnRepo.findByFirmIdOrderByReturnDateDescIdDesc(firmId).size();

        BigDecimal totalInvoiced2 = invoiceRepo.findAllByFirmId(firmId).stream().map(Invoice::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaid2 = invoicePaymentRepo.findByFirmIdOrderByPaymentDateDescIdDesc(firmId).stream().map(InvoicePayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstanding2 = totalInvoiced2.subtract(totalPaid2);

        assertEquals(cCount1, cCount2, "Customer count must remain strictly invariant after 2x restore");
        assertEquals(pCount1, pCount2, "Product count must remain strictly invariant after 2x restore");
        assertEquals(invCount1, invCount2, "Invoice count must remain strictly invariant after 2x restore");
        assertEquals(pmtCount1, pmtCount2, "Payment count must remain strictly invariant after 2x restore");
        assertEquals(poCount1, poCount2, "PO count must remain strictly invariant after 2x restore");
        assertEquals(empCount1, empCount2, "Employee count must remain strictly invariant after 2x restore");
        assertEquals(expCount1, expCount2, "Expense count must remain strictly invariant after 2x restore");
        assertEquals(retCount1, retCount2, "Return count must remain strictly invariant after 2x restore");
        assertEquals(totalInvoiced1, totalInvoiced2, "Total invoiced amount must remain strictly invariant after 2x restore");
        assertEquals(totalPaid1, totalPaid2, "Total paid amount must remain strictly invariant after 2x restore");
        assertEquals(outstanding1, outstanding2, "Outstanding amount must remain strictly invariant after 2x restore");

        // ── 5. Third Import (3x) ──
        backupService.importSelectiveData(backupB, null, "merge", firmId);

        long cCount3 = customerRepo.countByFirmId(firmId);
        long pCount3 = productRepo.countByFirmId(firmId);
        long invCount3 = invoiceRepo.countByFirmId(firmId);
        long pmtCount3 = invoicePaymentRepo.findByFirmIdOrderByPaymentDateDescIdDesc(firmId).size();

        BigDecimal totalInvoiced3 = invoiceRepo.findAllByFirmId(firmId).stream().map(Invoice::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaid3 = invoicePaymentRepo.findByFirmIdOrderByPaymentDateDescIdDesc(firmId).stream().map(InvoicePayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstanding3 = totalInvoiced3.subtract(totalPaid3);

        assertEquals(cCount1, cCount3, "Customer count must remain strictly invariant after 3x restore");
        assertEquals(pCount1, pCount3, "Product count must remain strictly invariant after 3x restore");
        assertEquals(invCount1, invCount3, "Invoice count must remain strictly invariant after 3x restore");
        assertEquals(pmtCount1, pmtCount3, "Payment count must remain strictly invariant after 3x restore");
        assertEquals(totalInvoiced1, totalInvoiced3, "Total invoiced amount must remain strictly invariant after 3x restore");
        assertEquals(totalPaid1, totalPaid3, "Total paid amount must remain strictly invariant after 3x restore");
        assertEquals(outstanding1, outstanding3, "Outstanding amount must remain strictly invariant after 3x restore");
    }

    @Test
    @Transactional
    @DisplayName("2. Primary Source Identity Design Prevents ID Collisions with Unrelated Target Entities")
    public void testSourceIdentityPreventsIdCollisionWithUnrelatedTargetEntity() {
        // Setup Source Firm A
        FirmDetails firmA = new FirmDetails();
        firmA.setFirmName("Source Firm A");
        firmA = firmDetailsRepo.save(firmA);
        Long firmAId = firmA.getId();

        Customer custA = new Customer();
        custA.setFirmId(firmAId);
        custA.setName("Customer Alpha (From Firm A)");
        custA.setPhone("9111111111");
        custA = customerRepo.save(custA);
        Long sourceCustId = custA.getId();

        // Export Firm A
        BackupDTO backupA = backupService.exportData(firmAId);

        // Setup Target Firm B
        FirmDetails firmB = new FirmDetails();
        firmB.setFirmName("Target Firm B");
        firmB = firmDetailsRepo.save(firmB);
        Long firmBId = firmB.getId();

        // Create an unrelated customer in Firm B whose DB ID happens to be something existing
        Customer unrelatedCustB = new Customer();
        unrelatedCustB.setFirmId(firmBId);
        unrelatedCustB.setName("Unrelated Target Customer Beta");
        unrelatedCustB.setPhone("9222222222");
        unrelatedCustB = customerRepo.save(unrelatedCustB);
        Long unrelatedCustBId = unrelatedCustB.getId();

        // Import Backup A into Firm B
        backupService.importSelectiveData(backupA, null, "merge", firmBId);

        // Verify: Unrelated target customer remains UNTOUCHED
        Customer reloadedUnrelated = customerRepo.findById(unrelatedCustBId).orElse(null);
        assertNotNull(reloadedUnrelated);
        assertEquals("Unrelated Target Customer Beta", reloadedUnrelated.getName());
        assertEquals("9222222222", reloadedUnrelated.getPhone());
        assertEquals(firmBId, reloadedUnrelated.getFirmId());

        // Verify: Total customers in Firm B is now 2 (unrelated + imported Alpha)
        List<Customer> firmBCustomers = customerRepo.findByFirmIdOrderByNameAsc(firmBId);
        assertEquals(2, firmBCustomers.size(), "Firm B must contain exactly 2 customers: the original unrelated customer + imported customer");
        assertTrue(firmBCustomers.stream().anyMatch(c -> c.getName().equals("Customer Alpha (From Firm A)")));
        assertTrue(firmBCustomers.stream().anyMatch(c -> c.getName().equals("Unrelated Target Customer Beta")));

        // Re-import 2x and 3x into Firm B
        backupService.importSelectiveData(backupA, null, "merge", firmBId);
        backupService.importSelectiveData(backupA, null, "merge", firmBId);

        List<Customer> firmBCustomersAfter3x = customerRepo.findByFirmIdOrderByNameAsc(firmBId);
        assertEquals(2, firmBCustomersAfter3x.size(), "Idempotent re-import must NOT create duplicates or touch unrelated customer");
    }

    @Test
    @Transactional
    @DisplayName("3. Cross-Firm Restore Isolation & Relational Integrity (1x, 2x, 3x)")
    public void testCrossFirmRestoreIsolationAndRelationalIntegrity1x2x3x() {
        // ── Firm A (Source) ──
        FirmDetails firmA = new FirmDetails();
        firmA.setFirmName("Firm A Source");
        firmA = firmDetailsRepo.save(firmA);
        Long firmAId = firmA.getId();

        Customer custA = new Customer();
        custA.setFirmId(firmAId);
        custA.setName("Customer A1");
        custA.setPhone("9333333333");
        custA = customerRepo.save(custA);

        Product prodA = Product.builder()
                .firmId(firmAId)
                .name("Product A1")
                .sku("SKU-A1")
                .price(new BigDecimal("1000.00"))
                .build();
        prodA = productRepo.save(prodA);

        Invoice invA = new Invoice();
        invA.setFirmId(firmAId);
        invA.setCustomer(custA);
        invA.setInvoiceNumber("INV-A-100");
        invA.setInvoiceDate(LocalDateTime.now());
        invA.setTotalAmount(new BigDecimal("1000.00"));
        invA.setItems(new ArrayList<>());
        InvoiceItem itemA = new InvoiceItem();
        itemA.setInvoice(invA);
        itemA.setProduct(prodA);
        itemA.setQty(1);
        itemA.setPricePerUnit(new BigDecimal("1000.00"));
        itemA.setLineTotal(new BigDecimal("1000.00"));
        invA.getItems().add(itemA);
        invA = invoiceRepo.save(invA);

        InvoicePayment pmtA = InvoicePayment.builder()
                .firmId(firmAId)
                .invoiceId(invA.getId())
                .customerId(custA.getId())
                .amount(new BigDecimal("1000.00"))
                .paymentDate(LocalDate.now())
                .build();
        invoicePaymentRepo.save(pmtA);

        Employee empA = new Employee();
        empA.setFirmId(firmAId);
        empA.setName("Employee A1");
        empA.setMonthlyBaseSalary(30000.0);
        empA = employeeRepo.save(empA);

        SalaryRecord salA = new SalaryRecord();
        salA.setEmployee(empA);
        salA.setMonthYear("09-2026");
        salA.setNetPaid(30000.0);
        salA.setPaymentDate(LocalDate.now());
        salaryRepo.save(salA);

        // Export Firm A
        BackupDTO backupA = backupService.exportData(firmAId);

        // ── Firm B (Target with Pre-existing Data) ──
        FirmDetails firmB = new FirmDetails();
        firmB.setFirmName("Firm B Target");
        firmB = firmDetailsRepo.save(firmB);
        Long firmBId = firmB.getId();

        Customer custB_pre = new Customer();
        custB_pre.setFirmId(firmBId);
        custB_pre.setName("Pre-existing Customer B0");
        custB_pre.setPhone("9444444444");
        custB_pre = customerRepo.save(custB_pre);

        // ── Import Firm A backup into Firm B (1x) ──
        backupService.importSelectiveData(backupA, null, "merge", firmBId);

        // Assertions after 1st import
        assertEquals(1, customerRepo.countByFirmId(firmAId), "Firm A customer count must remain untouched");
        assertEquals(1, invoiceRepo.countByFirmId(firmAId), "Firm A invoice count must remain untouched");

        List<Customer> bCusts1 = customerRepo.findByFirmIdOrderByNameAsc(firmBId);
        assertEquals(2, bCusts1.size(), "Firm B must have 2 customers (1 pre-existing + 1 imported)");

        List<Invoice> bInvs1 = invoiceRepo.findAllByFirmId(firmBId);
        assertEquals(1, bInvs1.size(), "Firm B must have 1 imported invoice");
        Invoice bInv1 = bInvs1.get(0);
        assertEquals(firmBId, bInv1.getFirmId(), "Imported invoice must belong strictly to Firm B");
        assertNotNull(bInv1.getCustomer(), "Imported invoice customer must be non-null");
        assertEquals(firmBId, bInv1.getCustomer().getFirmId(), "Imported invoice must point to Firm B's mapped customer, NEVER cross-firm");
        assertEquals("Customer A1", bInv1.getCustomer().getName());

        List<InvoicePayment> bPmts1 = invoicePaymentRepo.findByFirmIdOrderByPaymentDateDescIdDesc(firmBId);
        assertEquals(1, bPmts1.size(), "Firm B must have 1 imported payment");
        assertEquals(firmBId, bPmts1.get(0).getFirmId());
        assertEquals(bInv1.getId(), bPmts1.get(0).getInvoiceId(), "Payment must reference Firm B's newly mapped invoice ID");

        List<SalaryRecord> bSals1 = salaryRepo.findAll().stream().filter(s -> s.getEmployee().getFirmId().equals(firmBId)).toList();
        assertEquals(1, bSals1.size(), "Firm B must have 1 salary record");
        assertEquals(firmBId, bSals1.get(0).getEmployee().getFirmId(), "Salary must link to Firm B mapped employee");

        // ── Repeat Restore (2x, 3x) into Firm B ──
        backupService.importSelectiveData(backupA, null, "merge", firmBId);
        backupService.importSelectiveData(backupA, null, "merge", firmBId);

        List<Customer> bCusts3 = customerRepo.findByFirmIdOrderByNameAsc(firmBId);
        List<Invoice> bInvs3 = invoiceRepo.findAllByFirmId(firmBId);
        List<InvoicePayment> bPmts3 = invoicePaymentRepo.findByFirmIdOrderByPaymentDateDescIdDesc(firmBId);

        assertEquals(2, bCusts3.size(), "State after 3x must equal State after 1x (No duplicate customers)");
        assertEquals(1, bInvs3.size(), "State after 3x must equal State after 1x (No duplicate invoices)");
        assertEquals(1, bPmts3.size(), "State after 3x must equal State after 1x (No duplicate payments)");
        assertEquals(1, customerRepo.countByFirmId(firmAId), "Firm A remains completely unchanged");
    }

    @Test
    @Transactional
    @DisplayName("4. Distinct Customers with Same Phone or Name Are Preserved")
    public void testCriticalIdentityDistinctCustomersWithSamePhoneOrName() {
        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Shared Identity Test Firm");
        firm = firmDetailsRepo.save(firm);
        Long firmId = firm.getId();

        // 2 distinct customers with same phone number
        Customer c1 = new Customer();
        c1.setFirmId(firmId);
        c1.setName("Rahul Sharma");
        c1.setPhone("9999999999");
        c1 = customerRepo.save(c1);

        Customer c2 = new Customer();
        c2.setFirmId(firmId);
        c2.setName("Pooja Sharma");
        c2.setPhone("9999999999");
        c2 = customerRepo.save(c2);

        // 2 distinct customers with same name
        Customer c3 = new Customer();
        c3.setFirmId(firmId);
        c3.setName("Amit Patil");
        c3.setPhone("8888888888");
        c3 = customerRepo.save(c3);

        Customer c4 = new Customer();
        c4.setFirmId(firmId);
        c4.setName("Amit Patil");
        c4.setPhone("7777777777");
        c4 = customerRepo.save(c4);

        // Export and restore
        BackupDTO backup = backupService.exportData(firmId);
        assertEquals(4, backup.getCustomers().size());

        List<FirmDetails> restoredFirms = backupService.importSelectiveData(backup, null, "clone", null);
        assertFalse(restoredFirms.isEmpty());
        Long clonedFirmId = restoredFirms.get(0).getId();

        List<Customer> clonedCustomers = customerRepo.findByFirmIdOrderByNameAsc(clonedFirmId);
        assertEquals(4, clonedCustomers.size(), "All 4 distinct customers must remain separate and NEVER be merged by phone/name heuristics");
    }

    @Test
    @Transactional
    @DisplayName("5. Conflict Test Matrix: Cases A through F")
    public void testConflictMatrixCasesAThroughF() {
        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Conflict Matrix Firm");
        firm = firmDetailsRepo.save(firm);
        Long firmId = firm.getId();

        // Customer Setup
        Customer cust = new Customer();
        cust.setFirmId(firmId);
        cust.setName("Matrix Enterprise");
        cust.setEmail("initial@matrix.com");
        cust.setAddress("Old Address 1");
        cust = customerRepo.save(cust);

        // Product Setup
        Product prod = Product.builder()
                .firmId(firmId)
                .name("Matrix Widget")
                .sku("WIDGET-01")
                .price(new BigDecimal("100.00"))
                .costPrice(new BigDecimal("60.00"))
                .stockQuantity(new BigDecimal("50.000"))
                .build();
        prod = productRepo.save(prod);

        // Stock Movement Setup
        StockMovement sm = StockMovement.builder()
                .firmId(firmId)
                .productId(prod.getId())
                .productName(prod.getName())
                .movementType("OPENING")
                .quantityChange(new BigDecimal("50.000"))
                .newStock(new BigDecimal("50.000"))
                .build();
        stockMovementRepo.save(sm);

        // Invoice Setup
        Invoice inv = new Invoice();
        inv.setFirmId(firmId);
        inv.setCustomer(cust);
        inv.setInvoiceNumber("INV-CM-001");
        inv.setInvoiceDate(LocalDateTime.of(2026, 9, 20, 10, 0));
        inv.setTotalAmount(new BigDecimal("5000.00"));
        inv.setStatus(InvoiceStatus.UNPAID);
        inv.setPaid(false);
        inv = invoiceRepo.save(inv);

        // Payment Setup
        InvoicePayment pmt = InvoicePayment.builder()
                .firmId(firmId)
                .customerId(cust.getId())
                .invoiceId(inv.getId())
                .amount(new BigDecimal("2000.00"))
                .paymentDate(LocalDate.of(2026, 9, 21))
                .build();
        invoicePaymentRepo.save(pmt);

        // Capture Older Backup State
        BackupDTO olderBackup = backupService.exportData(firmId);

        // ── Simulate Newer Live State in Database ──
        // Case A: Newer Live Invoice (Total changed from ₹5,000 to ₹7,500, marked PAID)
        inv.setTotalAmount(new BigDecimal("7500.00"));
        inv.setStatus(InvoiceStatus.PAID);
        inv.setPaid(true);
        invoiceRepo.save(inv);

        // Case C: Newer Customer (Email changed to updated@matrix.com)
        cust.setEmail("updated@matrix.com");
        customerRepo.save(cust);

        // Case D: Newer Product (Price changed from ₹100 to ₹120)
        prod.setPrice(new BigDecimal("120.00"));
        productRepo.save(prod);

        // Case E: Additional Live Payment (Added second payment of ₹5,500)
        InvoicePayment pmt2 = InvoicePayment.builder()
                .firmId(firmId)
                .customerId(cust.getId())
                .invoiceId(inv.getId())
                .amount(new BigDecimal("5500.00"))
                .paymentDate(LocalDate.of(2026, 9, 22))
                .build();
        invoicePaymentRepo.save(pmt2);

        // Case F: Newer Stock State (Added stock movement +10)
        StockMovement sm2 = StockMovement.builder()
                .firmId(firmId)
                .productId(prod.getId())
                .productName(prod.getName())
                .movementType("RESTOCK")
                .quantityChange(new BigDecimal("10.000"))
                .newStock(new BigDecimal("60.000"))
                .build();
        stockMovementRepo.save(sm2);

        // Capture Newer Backup State
        BackupDTO newerBackup = backupService.exportData(firmId);

        // ── TEST 1: Restore Older Backup against Newer Live State ──
        // Policy: No silent rollback of financial, customer, product, or inventory records
        backupService.importSelectiveData(olderBackup, null, "merge", firmId);

        // Verify Case A: Live Invoice not rolled back
        Invoice reloadedInvA = invoiceRepo.findByInvoiceNumberAndFirmId("INV-CM-001", firmId).orElse(null);
        assertNotNull(reloadedInvA);
        assertEquals(new BigDecimal("7500.00"), reloadedInvA.getTotalAmount(), "Case A: Live invoice total must remain ₹7,500.00");
        assertEquals(InvoiceStatus.PAID, reloadedInvA.getStatus(), "Case A: Live invoice status must remain PAID");

        // Verify Case C: Live Customer email preserved
        Customer reloadedCustC = customerRepo.findById(cust.getId()).orElse(null);
        assertNotNull(reloadedCustC);
        assertEquals("updated@matrix.com", reloadedCustC.getEmail(), "Case C: Newer customer email must be preserved");

        // Verify Case D: Live Product price preserved
        Product reloadedProdD = productRepo.findById(prod.getId()).orElse(null);
        assertNotNull(reloadedProdD);
        assertEquals(new BigDecimal("120.00"), reloadedProdD.getPrice(), "Case D: Newer product price must be preserved");

        // Verify Case E: Live Payments not deleted or duplicated
        List<InvoicePayment> livePmts = invoicePaymentRepo.findByFirmIdOrderByPaymentDateDescIdDesc(firmId);
        assertEquals(2, livePmts.size(), "Case E: Both live payments must be preserved without silent loss or duplication");
        BigDecimal totalPaid = livePmts.stream().map(InvoicePayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("7500.00"), totalPaid, "Case E: Total paid must remain ₹7,500.00");

        // Verify Case F: Newer Stock movements preserved
        List<StockMovement> liveMovements = stockMovementRepo.findByFirmIdOrderByCreatedAtDesc(firmId);
        assertEquals(2, liveMovements.size(), "Case F: Newer stock movements must be preserved");

        // ── TEST 2: Restore Newer Backup against Older Live State (Case B) ──
        // Policy: Newer backup updates missing/safe non-conflicting fields into mapping
        backupService.importSelectiveData(newerBackup, null, "merge", firmId);
        assertEquals(1, invoiceRepo.countByFirmId(firmId), "Case B: Invoices remain invariant");
        assertEquals(2, invoicePaymentRepo.findByFirmIdOrderByPaymentDateDescIdDesc(firmId).size(), "Case B: Payments invariant");
    }

    @Test
    @Transactional
    @DisplayName("6. Comprehensive Verification of All 19 Entity Types Exported by BackupDTO")
    public void testAll19EntityTypesImportIdempotency1x2x3x() {
        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Matrix 19-Entity Firm");
        firm.setGstin("27ZZZZZ9999Z1Z1");
        firm = firmDetailsRepo.save(firm);
        Long firmId = firm.getId();

        // 1. Customer
        Customer c = new Customer();
        c.setFirmId(firmId);
        c.setName("Omni Customer");
        c.setPhone("9555555555");
        c = customerRepo.save(c);

        // 2. Product
        Product p = Product.builder()
                .firmId(firmId)
                .name("Omni Product")
                .sku("OMNI-001")
                .price(new BigDecimal("500.00"))
                .build();
        p = productRepo.save(p);

        // 3. Stock Movement
        StockMovement sm = StockMovement.builder()
                .firmId(firmId)
                .productId(p.getId())
                .productName("Omni Product")
                .movementType("OPENING")
                .quantityChange(new BigDecimal("10.000"))
                .previousStock(BigDecimal.ZERO)
                .newStock(new BigDecimal("10.000"))
                .build();
        stockMovementRepo.save(sm);

        // 4. Invoice & Items
        Invoice inv = new Invoice();
        inv.setFirmId(firmId);
        inv.setCustomer(c);
        inv.setInvoiceNumber("INV-OMNI-01");
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

        // 5. Invoice Payment
        InvoicePayment ip = InvoicePayment.builder()
                .firmId(firmId)
                .invoiceId(inv.getId())
                .customerId(c.getId())
                .amount(new BigDecimal("500.00"))
                .paymentDate(LocalDate.now())
                .build();
        invoicePaymentRepo.save(ip);

        // 6. Sales Return
        SalesReturn sr = SalesReturn.builder()
                .firmId(firmId)
                .invoice(inv)
                .customer(c)
                .returnNumber("RET-OMNI-01")
                .returnDate(LocalDate.now())
                .totalRefundAmount(new BigDecimal("500.00"))
                .build();
        salesReturnRepo.save(sr);

        // 7. Party (Vendor)
        Party party = Party.builder()
                .firmId(firmId)
                .name("Omni Vendor")
                .build();
        party = partyRepo.save(party);

        // 8. Purchase Order
        PurchaseOrder po = PurchaseOrder.builder()
                .firmId(firmId)
                .party(party)
                .poNumber("PO-OMNI-01")
                .poDate(LocalDate.now())
                .totalAmount(new BigDecimal("1000.00"))
                .build();
        purchaseOrderRepo.save(po);

        // 9. Party Payment
        PartyPayment pp = PartyPayment.builder()
                .firmId(firmId)
                .partyId(party.getId())
                .amount(new BigDecimal("1000.00"))
                .paymentDate(LocalDate.now())
                .build();
        partyPaymentRepo.save(pp);

        // 10. Reminder
        Reminder rem = Reminder.builder()
                .firmId(firmId)
                .title("Omni Reminder")
                .dueDate(LocalDateTime.now())
                .build();
        reminderRepo.save(rem);

        // 11. Note
        Note note = Note.builder()
                .firmId(firmId)
                .title("Omni Note")
                .content("Omni Content")
                .build();
        noteRepo.save(note);

        // 12. Expense
        Expense exp = Expense.builder()
                .firmId(firmId)
                .title("Omni Expense")
                .amount(new BigDecimal("250.00"))
                .expenseDate(LocalDate.now())
                .build();
        expenseRepo.save(exp);

        // 13. Goal
        Goal goal = Goal.builder()
                .firmId(firmId)
                .title("Omni Goal")
                .goalType(GoalType.SAVINGS_TARGET)
                .targetValue(new BigDecimal("10000.00"))
                .build();
        goal = goalRepo.save(goal);

        // 14. Saving
        SavingRecord sav = SavingRecord.builder()
                .firmId(firmId)
                .goalId(goal.getId())
                .title("Omni Saving")
                .amount(new BigDecimal("2000.00"))
                .savingDate(LocalDate.now())
                .build();
        savingRepo.save(sav);

        // 15. Goal Log
        GoalLog gl = GoalLog.builder()
                .firmId(firmId)
                .goalId(goal.getId())
                .actionType("DEPOSIT")
                .deltaValue(new BigDecimal("2000.00"))
                .resultingValue(new BigDecimal("2000.00"))
                .logDate(LocalDate.now())
                .createdAt(LocalDateTime.now())
                .build();
        goalLogRepo.save(gl);

        // 16. Employee
        Employee emp = new Employee();
        emp.setFirmId(firmId);
        emp.setName("Omni Employee");
        emp.setMonthlyBaseSalary(35000.0);
        emp = employeeRepo.save(emp);

        // 17. Attendance Record
        AttendanceRecord att = new AttendanceRecord();
        att.setEmployee(emp);
        att.setDate(LocalDate.now());
        att.setStatus("PRESENT");
        attendanceRecordRepo.save(att);

        // 18. Leave Record
        LeaveRecord lr = new LeaveRecord();
        lr.setEmployee(emp);
        lr.setStartDate(LocalDate.now());
        lr.setEndDate(LocalDate.now());
        lr.setType("CASUAL");
        lr.setStatus("APPROVED");
        lr.setTotalDays(1);
        leaveRecordRepo.save(lr);

        // 19. Salary Record
        SalaryRecord sal = new SalaryRecord();
        sal.setEmployee(emp);
        sal.setMonthYear("09-2026");
        sal.setNetPaid(35000.0);
        sal.setPaymentDate(LocalDate.now());
        salaryRepo.save(sal);

        // 20. Employee Advance
        EmployeeAdvance adv = new EmployeeAdvance();
        adv.setEmployee(emp);
        adv.setAmount(3000.0);
        adv.setDate(LocalDate.now());
        advanceRepo.save(adv);

        // 21. Promotion Record
        PromotionRecord pr = new PromotionRecord();
        pr.setEmployee(emp);
        pr.setEffectiveDate(LocalDate.now());
        pr.setType("INCREMENT");
        pr.setPreviousSalary(30000.0);
        pr.setNewSalary(35000.0);
        promotionRepo.save(pr);

        // 22. Employee Document
        EmployeeDocument doc = new EmployeeDocument();
        doc.setEmployee(emp);
        doc.setType("ID_PROOF");
        doc.setFileName("aadhaar.pdf");
        doc.setDataBase64("JVBERi0xLjQK...");
        doc.setUploadedAt(LocalDateTime.now());
        employeeDocumentRepo.save(doc);

        // 23. Business Letter
        BusinessLetter bl = BusinessLetter.builder()
                .firmId(firmId)
                .letterNumber("LET-001")
                .letterDate(LocalDate.now())
                .recipientName("Omni Partner")
                .subject("Omni Offer")
                .content("Letter Content")
                .build();
        businessLetterRepo.save(bl);

        // 24. Inbox Message
        InboxMessage msg = InboxMessage.builder()
                .firmId(firmId)
                .subject("Omni Message")
                .body("Hello Omni")
                .sender("System")
                .build();
        inboxMessageRepo.save(msg);

        // 25. App Config
        AppConfig ac = new AppConfig();
        ac.setConfigKey("app.theme.mode");
        ac.setConfigValue("DARK");
        appConfigRepo.save(ac);

        // Export Firm
        BackupDTO backup19 = backupService.exportData(firmId);
        assertNotNull(backup19);

        // Execute 1x, 2x, 3x Imports into the same Firm
        backupService.importSelectiveData(backup19, null, "merge", firmId);
        backupService.importSelectiveData(backup19, null, "merge", firmId);
        backupService.importSelectiveData(backup19, null, "merge", firmId);

        // Verify count invariants for every single entity type
        assertEquals(1, customerRepo.countByFirmId(firmId), "Customer count = 1");
        assertEquals(1, productRepo.countByFirmId(firmId), "Product count = 1");
        assertEquals(1, stockMovementRepo.findByFirmIdOrderByCreatedAtDesc(firmId).size(), "StockMovement count = 1");
        assertEquals(1, invoiceRepo.countByFirmId(firmId), "Invoice count = 1");
        assertEquals(1, invoicePaymentRepo.findByFirmIdOrderByPaymentDateDescIdDesc(firmId).size(), "InvoicePayment count = 1");
        assertEquals(1, salesReturnRepo.findByFirmIdOrderByReturnDateDescIdDesc(firmId).size(), "SalesReturn count = 1");
        assertEquals(1, partyRepo.findByFirmIdOrderByNameAsc(firmId).size(), "Party count = 1");
        assertEquals(1, purchaseOrderRepo.findByFirmIdOrderByPoDateDescIdDesc(firmId).size(), "PurchaseOrder count = 1");
        assertEquals(1, partyPaymentRepo.findByFirmIdOrderByPaymentDateDescIdDesc(firmId).size(), "PartyPayment count = 1");
        assertEquals(1, reminderRepo.findByFirmId(firmId).size(), "Reminder count = 1");
        assertEquals(1, noteRepo.findByFirmId(firmId).size(), "Note count = 1");
        assertEquals(1, expenseRepo.countByFirmId(firmId), "Expense count = 1");
        assertEquals(1, goalRepo.findByFirmIdOrderByCreatedAtDesc(firmId).size(), "Goal count = 1");
        assertEquals(1, savingRepo.findByFirmIdOrderBySavingDateDescIdDesc(firmId).size(), "Saving count = 1");
        assertEquals(1, goalLogRepo.findByFirmIdOrderByLogDateAscCreatedAtAsc(firmId).size(), "GoalLog count = 1");
        assertEquals(1, employeeRepo.findByFirmId(firmId).size(), "Employee count = 1");
        assertEquals(1, attendanceRecordRepo.count(), "Attendance count = 1");
        assertEquals(1, leaveRecordRepo.count(), "Leave count = 1");
        assertEquals(1, salaryRepo.count(), "Salary count = 1");
        assertEquals(1, advanceRepo.count(), "Advance count = 1");
        assertEquals(1, promotionRepo.count(), "Promotion count = 1");
        assertEquals(1, employeeDocumentRepo.count(), "Document count = 1");
        assertEquals(1, businessLetterRepo.findByFirmIdOrderByLetterDateDescIdDesc(firmId).size(), "BusinessLetter count = 1");
        assertEquals(1, inboxMessageRepo.findByFirmIdOrderByCreatedAtDesc(firmId).size(), "InboxMessage count = 1");
    }
}
