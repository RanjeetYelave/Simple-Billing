package com.billing.simple.billsoft;

import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.repositories.*;
import com.billing.simple.billsoft.service.BackupService;
import org.junit.jupiter.api.BeforeEach;
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

@SpringBootTest
@ActiveProfiles("test")
public class BackupIntegrationTest {

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
    private BusinessLetterRepository businessLetterRepo;
    @Autowired
    private InboxMessageRepository inboxMessageRepo;

    @BeforeEach
    void cleanDb() {
        backupService.factoryReset();
    }

    @Test
    @Transactional
    void testCompleteExportAndImportLifecycle() {
        // 1. Create Firm
        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Acme Global");
        firm.setEmail("acme@example.com");
        firm = firmDetailsRepo.save(firm);
        Long firmId = firm.getId();

        // 2. Create Customer
        Customer customer = new Customer();
        customer.setFirmId(firmId);
        customer.setName("John Customer");
        customer.setPhone("9876543210");
        customer = customerRepo.save(customer);

        // 3. Create Product with full stock attributes
        Product product = Product.builder()
                .firmId(firmId)
                .name("Pro Laptop 15")
                .price(new BigDecimal("75000.00"))
                .costPrice(new BigDecimal("50000.00"))
                .stockQuantity(new BigDecimal("25.000"))
                .minStockLevel(new BigDecimal("5.000"))
                .sku("SKU-LAP-15")
                .barcode("8901234567890")
                .category("Electronics")
                .itemType("GOODS")
                .unit("pcs")
                .hsnCode("8471")
                .gstPercentage(new BigDecimal("18.00"))
                .build();
        product = productRepo.save(product);

        // 4. Create Stock Movement
        StockMovement movement = StockMovement.builder()
                .productId(product.getId())
                .productName(product.getName())
                .firmId(firmId)
                .movementType("INITIAL_STOCK")
                .quantityChange(new BigDecimal("25.000"))
                .previousStock(BigDecimal.ZERO)
                .newStock(new BigDecimal("25.000"))
                .referenceType("MANUAL")
                .referenceId("OPENING")
                .note("Opening balance")
                .createdAt(LocalDateTime.now())
                .build();
        stockMovementRepo.save(movement);

        // 5. Create Invoice
        Invoice invoice = new Invoice();
        invoice.setFirmId(firmId);
        invoice.setInvoiceNumber("INV-0001");
        invoice.setCustomer(customer);
        invoice.setInvoiceDate(LocalDateTime.now());
        invoice.setTotalAmount(new BigDecimal("88500.00"));
        invoice.setStatus(InvoiceStatus.FINAL);
        invoice.setItems(new ArrayList<>());

        InvoiceItem item = new InvoiceItem();
        item.setInvoice(invoice);
        item.setProduct(product);
        item.setQty(1);
        item.setPricePerUnit(new BigDecimal("75000.00"));
        item.setTaxableAmount(new BigDecimal("75000.00"));
        item.setGstPercent(new BigDecimal("18.00"));
        item.setGstAmount(new BigDecimal("13500.00"));
        item.setLineTotal(new BigDecimal("88500.00"));
        invoice.getItems().add(item);
        invoice = invoiceRepo.save(invoice);

        // 6. Create Vendor Party
        Party party = Party.builder()
                .firmId(firmId)
                .name("Dell Distributors")
                .phone("9988776655")
                .contactPerson("Alice Vendor")
                .build();
        party = partyRepo.save(party);

        // 7. Create Purchase Order
        PurchaseOrder po = PurchaseOrder.builder()
                .firmId(firmId)
                .poNumber("PO-2026-0001")
                .poDate(LocalDate.now())
                .party(party)
                .partyName(party.getName())
                .status(PurchaseOrderStatus.ISSUED)
                .totalAmount(new BigDecimal("50000.00"))
                .items(new ArrayList<>())
                .build();
        po = purchaseOrderRepo.save(po);

        // 8. Create Party Payment
        PartyPayment payment = PartyPayment.builder()
                .firmId(firmId)
                .partyId(party.getId())
                .purchaseOrderId(po.getId())
                .amount(new BigDecimal("50000.00"))
                .paymentDate(LocalDate.now())
                .paymentMode("Bank Transfer")
                .build();
        partyPaymentRepo.save(payment);

        // 9. Create Reminder & Note & Expense
        Reminder reminder = Reminder.builder()
                .firmId(firmId)
                .customerId(customer.getId())
                .title("Followup with John")
                .type("task")
                .status("TODO")
                .build();
        reminderRepo.save(reminder);

        Note note = Note.builder()
                .firmId(firmId)
                .customerId(customer.getId())
                .title("Special discount notes")
                .content("Agreed to 5% repeat discount")
                .build();
        noteRepo.save(note);

        Expense expense = Expense.builder()
                .firmId(firmId)
                .title("Office Internet")
                .amount(BigDecimal.valueOf(1200.0))
                .category("Utilities")
                .expenseDate(LocalDate.now())
                .build();
        expenseRepo.save(expense);

        // 10. Create Employee & Sub-records
        Employee emp = new Employee();
        emp.setFirmId(firmId);
        emp.setName("Robert Staff");
        emp.setDateOfJoining(LocalDate.of(2025, 1, 1));
        emp.setDesignation("Software Engineer");
        emp.setMonthlyBaseSalary(80000.0);
        emp = employeeRepo.save(emp);

        AttendanceRecord att = new AttendanceRecord();
        att.setEmployee(emp);
        att.setDate(LocalDate.now());
        att.setStatus("PRESENT");
        attendanceRecordRepo.save(att);

        SalaryRecord sal = new SalaryRecord();
        sal.setEmployee(emp);
        sal.setMonthYear("08-2026");
        sal.setBaseSalaryAtTime(80000.0);
        sal.setNetPaid(80000.0);
        sal.setPaymentDate(LocalDate.now());
        salaryRepo.save(sal);

        // 11. Create Business Letter
        BusinessLetter letter = BusinessLetter.builder()
                .firmId(firmId)
                .letterNumber("LTR-0001")
                .letterDate(LocalDate.now())
                .subject("Welcome Vendor")
                .content("Welcome to our authorized supplier network.")
                .partyId(party.getId())
                .recipientName(party.getName())
                .status(LetterStatus.ISSUED)
                .build();
        businessLetterRepo.save(letter);

        // 12. Create Inbox Message
        InboxMessage msg = InboxMessage.builder()
                .firmId(firmId)
                .subject("System Update")
                .body("Welcome to Simple Billing")
                .build();
        inboxMessageRepo.save(msg);

        // ─── EXECUTE EXPORT ───
        BackupDTO export = backupService.exportData(firmId);

        assertNotNull(export);
        assertEquals("Acme Global", export.getFirmDetails().getFirmName());
        assertEquals(1, export.getCustomers().size());
        assertEquals(1, export.getProducts().size());
        assertEquals(new BigDecimal("75000.00"), export.getProducts().get(0).getPrice());
        assertEquals(new BigDecimal("50000.00"), export.getProducts().get(0).getCostPrice());
        assertEquals("SKU-LAP-15", export.getProducts().get(0).getSku());
        assertEquals(1, export.getStockMovements().size());
        assertEquals(1, export.getInvoices().size());
        assertEquals(1, export.getParties().size());
        assertEquals(1, export.getPurchaseOrders().size());
        assertEquals(1, export.getPartyPayments().size());
        assertEquals(1, export.getReminders().size());
        assertEquals(1, export.getNotes().size());
        assertEquals(1, export.getExpenses().size());
        assertEquals(1, export.getEmployees().size());
        assertEquals(1, export.getAttendanceRecords().size());
        assertEquals(1, export.getSalaryRecords().size());
        assertEquals(1, export.getBusinessLetters().size());
        assertEquals(1, export.getInboxMessages().size());

        // ─── EXECUTE IMPORT AS NEW RESTORED FIRM ───
        backupService.importData(export, null, false);

        List<FirmDetails> allFirms = firmDetailsRepo.findAll();
        assertEquals(2, allFirms.size());
        FirmDetails restoredFirm = allFirms.stream().filter(f -> f.getFirmName().contains("Restored")).findFirst().orElse(null);
        assertNotNull(restoredFirm);

        Long restoredFirmId = restoredFirm.getId();

        // Verify restored customer
        List<Customer> restoredCustomers = customerRepo.findByFirmIdOrderByNameAsc(restoredFirmId);
        assertEquals(1, restoredCustomers.size());
        assertEquals("John Customer", restoredCustomers.get(0).getName());

        // Verify restored product
        List<Product> restoredProducts = productRepo.findByFirmId(restoredFirmId);
        assertEquals(1, restoredProducts.size());
        assertEquals("Pro Laptop 15", restoredProducts.get(0).getName());
        assertEquals("SKU-LAP-15", restoredProducts.get(0).getSku());
        assertEquals(new BigDecimal("25.000"), restoredProducts.get(0).getStockQuantity());
        assertEquals(new BigDecimal("50000.00"), restoredProducts.get(0).getCostPrice());

        // Verify restored invoices & product mapping
        List<Invoice> restoredInvoices = invoiceRepo.findAllByFirmId(restoredFirmId);
        assertEquals(1, restoredInvoices.size());
        assertEquals("INV-0001", restoredInvoices.get(0).getInvoiceNumber());
        assertEquals("John Customer", restoredInvoices.get(0).getCustomer().getName());
        assertEquals(1, restoredInvoices.get(0).getItems().size());
        assertEquals("Pro Laptop 15", restoredInvoices.get(0).getItems().get(0).getProduct().getName());

        // Verify restored party & PO
        List<Party> restoredParties = partyRepo.findByFirmIdOrderByNameAsc(restoredFirmId);
        assertEquals(1, restoredParties.size());
        assertEquals("Dell Distributors", restoredParties.get(0).getName());

        List<PurchaseOrder> restoredPos = purchaseOrderRepo.findByFirmIdOrderByPoDateDescIdDesc(restoredFirmId);
        assertEquals(1, restoredPos.size());
        assertEquals("Dell Distributors", restoredPos.get(0).getParty().getName());

        // Verify restored stock movements
        List<StockMovement> restoredMovements = stockMovementRepo.findByFirmIdOrderByCreatedAtDesc(restoredFirmId);
        assertEquals(1, restoredMovements.size());
        assertEquals(restoredProducts.get(0).getId(), restoredMovements.get(0).getProductId());

        // ─── EXECUTE FACTORY RESET ───
        backupService.factoryReset();

        assertEquals(0, firmDetailsRepo.count());
        assertEquals(0, customerRepo.count());
        assertEquals(0, productRepo.count());
        assertEquals(0, invoiceRepo.count());
        assertEquals(0, partyRepo.count());
        assertEquals(0, purchaseOrderRepo.count());
        assertEquals(0, stockMovementRepo.count());
    }
}
