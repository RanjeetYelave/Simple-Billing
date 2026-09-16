package com.billing.simple.billsoft.regression.system;

import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceRequestItem;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.repositories.*;
import com.billing.simple.billsoft.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Tag("regression")
@Tag("integration")
@DisplayName("Persistence, Data Durability, Atomicity & Multi-Business Isolation Regression Tests")
class PersistenceAndDataDurabilityRegressionTest {

    @Autowired
    private BackupService backupService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private ProductService productService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private PurchaseOrderService purchaseOrderService;

    @Autowired
    private PartyService partyService;

    @Autowired
    private FirmDetailsService firmService;

    @Autowired
    private EmployeeRepository employeeRepo;

    @Autowired
    private SalaryRecordRepository salaryRepo;

    @Autowired
    private ExpenseRepository expenseRepo;

    @Autowired
    private NoteRepository noteRepo;

    @Autowired
    private ReminderRepository reminderRepo;

    @Autowired
    private InvoiceRepository invoiceRepo;

    @Autowired
    private InvoicePaymentRepository invoicePaymentRepo;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private PartyRepository partyRepo;

    @Autowired
    private PurchaseOrderRepository poRepo;

    @Autowired
    private AppConfigRepository appConfigRepo;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long firmAId;
    private Long firmBId;

    @BeforeEach
    void setUp() {
        backupService.factoryReset();

        FirmDetails firmA = new FirmDetails();
        firmA.setFirmName("Alpha Enterprise Global");
        firmA.setGstin("27AAAAA0000A1Z5");
        firmA.setPhone("9111111111");
        firmA = firmService.create(firmA);
        firmAId = firmA.getId();

        FirmDetails firmB = new FirmDetails();
        firmB.setFirmName("Beta Solutions Ltd");
        firmB.setGstin("27BBBBB1111B2Z6");
        firmB.setPhone("9222222222");
        firmB = firmService.create(firmB);
        firmBId = firmB.getId();
    }

    @Test
    @DisplayName("Verify 100% Round-trip Data Durability via Export, Factory Reset, and Restore")
    void testCompleteBusinessDataBackupAndRestoreRoundTrip() {
        // 1. Create Business Entities for Firm A
        Customer cust = new Customer();
        cust.setName("Durable Customer Inc");
        cust.setPhone("9888877777");
        cust.setEmail("durable@customer.com");
        cust.setFirmId(firmAId);
        Customer savedCust = customerService.create(cust);

        Product prod = new Product();
        prod.setName("Industrial Server Rack");
        prod.setPrice(new BigDecimal("50000.00"));
        prod.setStockQuantity(new BigDecimal("20.00"));
        prod.setGstPercentage(new BigDecimal("18.00"));
        prod.setFirmId(firmAId);
        Product savedProd = productService.create(prod);

        // 2. Create and Pay Invoice
        InvoiceRequest invReq = new InvoiceRequest();
        invReq.setFirmId(firmAId);
        invReq.setCustomerId(savedCust.getId());
        invReq.setStatus(InvoiceStatus.FINAL);
        invReq.setInvoiceDate(LocalDateTime.now().toString());

        InvoiceRequestItem item = new InvoiceRequestItem();
        item.setProductId(savedProd.getId());
        item.setQty(2);
        item.setPricePerUnit(new BigDecimal("50000.00"));
        item.setGstPercent(new BigDecimal("18.00")); // 100,000 + 18,000 GST = 118,000
        invReq.setItems(List.of(item));

        Invoice inv = invoiceService.createInvoice(invReq);
        assertThat(inv.getTotalAmount()).isEqualByComparingTo(new BigDecimal("118000.00"));

        // Record Partial Payment
        InvoicePayment payment = invoiceService.recordPayment(
                inv.getId(), new BigDecimal("50000.00"), LocalDate.now(), "Bank Transfer", "TXN-BANK-101", "Advance"
        );
        assertThat(payment.getId()).isNotNull();

        // 3. Create Vendor & PO
        Party party = new Party();
        party.setName("Rack Component Suppliers");
        party.setFirmId(firmAId);
        party.setPhone("9333333333");
        Party savedParty = partyService.createParty(party);

        PurchaseOrder po = new PurchaseOrder();
        po.setFirmId(firmAId);
        po.setParty(savedParty);
        po.setPoDate(LocalDate.now());
        po.setStatus(PurchaseOrderStatus.ISSUED);
        PurchaseOrder savedPo = purchaseOrderService.createPurchaseOrder(po);

        // 4. Create Employee & Note & Reminder & Expense
        Employee emp = new Employee();
        emp.setName("Alice Engineer");
        emp.setFirmId(firmAId);
        emp.setMonthlyBaseSalary(80000.0);
        emp.setIsActive(true);
        Employee savedEmp = employeeRepo.save(emp);

        Expense exp = new Expense();
        exp.setFirmId(firmAId);
        exp.setTitle("Cloud Hosting Infrastructure");
        exp.setAmount(new BigDecimal("15000.00"));
        exp.setExpenseDate(LocalDate.now());
        expenseRepo.save(exp);

        Note note = new Note();
        note.setFirmId(firmAId);
        note.setTitle("Q3 Strategy");
        note.setContent("Expand enterprise client tier");
        noteRepo.save(note);

        Reminder rem = new Reminder();
        rem.setFirmId(firmAId);
        rem.setTitle("File GST Return");
        rem.setDueDate(LocalDateTime.now().plusDays(5));
        reminderRepo.save(rem);

        // 5. Export Backup
        BackupDTO backup = backupService.exportData(firmAId);
        assertThat(backup.getCustomers()).hasSize(1);
        assertThat(backup.getProducts()).hasSize(1);
        assertThat(backup.getInvoices()).hasSize(1);
        assertThat(backup.getInvoicePayments()).hasSize(1);
        assertThat(backup.getParties()).hasSize(1);
        assertThat(backup.getEmployees()).hasSize(1);
        assertThat(backup.getExpenses()).hasSize(1);
        assertThat(backup.getNotes()).hasSize(1);
        assertThat(backup.getReminders()).hasSize(1);

        // 6. Perform Hard Reset (Simulate clean machine restore)
        backupService.factoryReset();

        assertThat(customerRepo.count()).isEqualTo(0);
        assertThat(invoiceRepo.count()).isEqualTo(0);
        assertThat(invoicePaymentRepo.count()).isEqualTo(0);
        assertThat(productRepo.count()).isEqualTo(0);

        // 7. Restore Backup into New Cloned Firm
        backupService.importData(backup, null, false);

        // 8. Verify Complete Restoration Fidelity
        List<Customer> restoredCustomers = customerRepo.findAll();
        assertThat(restoredCustomers).hasSize(1);
        assertThat(restoredCustomers.get(0).getName()).isEqualTo("Durable Customer Inc");

        List<Product> restoredProducts = productRepo.findAll();
        assertThat(restoredProducts).hasSize(1);
        assertThat(restoredProducts.get(0).getName()).isEqualTo("Industrial Server Rack");
        assertThat(restoredProducts.get(0).getPrice()).isEqualByComparingTo(new BigDecimal("50000.00"));

        List<Invoice> restoredInvoices = invoiceRepo.findAll();
        assertThat(restoredInvoices).hasSize(1);
        assertThat(restoredInvoices.get(0).getTotalAmount()).isEqualByComparingTo(new BigDecimal("118000.00"));
        assertThat(restoredInvoices.get(0).getCustomer().getName()).isEqualTo("Durable Customer Inc");

        List<InvoicePayment> restoredPayments = invoicePaymentRepo.findAll();
        assertThat(restoredPayments).hasSize(1);
        assertThat(restoredPayments.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("50000.00"));
        assertThat(restoredPayments.get(0).getPaymentMode()).isEqualTo("Bank Transfer");

        List<Employee> restoredEmployees = employeeRepo.findAll();
        assertThat(restoredEmployees).hasSize(1);
        assertThat(restoredEmployees.get(0).getName()).isEqualTo("Alice Engineer");
    }

    @Test
    @DisplayName("Verify Transactional Atomicity: Crash during compound operation leaves zero partial state")
    void testTransactionRollbackLeavesNoOrphanRecords() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        long initialInvoiceCount = invoiceRepo.count();
        long initialProductCount = productRepo.count();

        // Simulate an atomic write failure: create product, create invoice, then simulate an unexpected JVM crash/exception
        assertThatThrownBy(() -> {
            txTemplate.execute(status -> {
                Product p = new Product();
                p.setName("Temporary Product");
                p.setPrice(new BigDecimal("200.00"));
                p.setFirmId(firmAId);
                Product savedP = productRepo.save(p);

                Invoice inv = new Invoice();
                inv.setFirmId(firmAId);
                inv.setInvoiceNumber("TEMP-999");
                inv.setTotalAmount(new BigDecimal("200.00"));
                invoiceRepo.save(inv);

                // Simulate abrupt crash / unhandled runtime exception
                throw new RuntimeException("Simulated JVM Crash / Transaction Abort during invoice persistence!");
            });
        }).hasMessageContaining("Simulated JVM Crash");

        // Verify that database completely rolled back all writes
        assertThat(invoiceRepo.count()).isEqualTo(initialInvoiceCount);
        assertThat(productRepo.count()).isEqualTo(initialProductCount);
    }

    @Test
    @DisplayName("Verify Immutability of Historical Financial Invoices: Catalog edits never mutate past invoices")
    void testHistoricalInvoicesAreImmutableAgainstProductAndCustomerEdits() {
        Customer cust = new Customer();
        cust.setName("Original Customer Name");
        cust.setFirmId(firmAId);
        Customer savedCust = customerService.create(cust);

        Product prod = new Product();
        prod.setName("Original Hardware Item");
        prod.setPrice(new BigDecimal("1000.00"));
        prod.setGstPercentage(new BigDecimal("10.00"));
        prod.setFirmId(firmAId);
        Product savedProd = productService.create(prod);

        // Create Final Invoice
        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(firmAId);
        req.setCustomerId(savedCust.getId());
        req.setStatus(InvoiceStatus.FINAL);
        req.setInvoiceDate(LocalDateTime.now().minusMonths(3).toString());

        InvoiceRequestItem item = new InvoiceRequestItem();
        item.setProductId(savedProd.getId());
        item.setQty(5);
        item.setPricePerUnit(new BigDecimal("1000.00"));
        item.setGstPercent(new BigDecimal("10.00")); // Total 5,500
        req.setItems(List.of(item));

        Invoice finalizedInvoice = invoiceService.createInvoice(req);
        Long invoiceId = finalizedInvoice.getId();
        BigDecimal originalTotal = finalizedInvoice.getTotalAmount();
        assertThat(originalTotal).isEqualByComparingTo(new BigDecimal("5500.00"));

        // Now mutate the catalog: Increase product price to 5000.00 and rename
        savedProd.setPrice(new BigDecimal("5000.00"));
        savedProd.setName("Renamed High-End Hardware Item");
        productRepo.save(savedProd);

        // Mutate the customer
        savedCust.setName("Altered Customer Name");
        customerRepo.save(savedCust);

        // Fetch historical invoice and assert financial immutability
        Invoice reloadedInvoice = invoiceService.getById(invoiceId);
        assertThat(reloadedInvoice.getTotalAmount()).isEqualByComparingTo(new BigDecimal("5500.00"));
        assertThat(reloadedInvoice.getItems().get(0).getPricePerUnit()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(reloadedInvoice.getItems().get(0).getLineTotal()).isEqualByComparingTo(new BigDecimal("5500.00"));
    }

    @Test
    @DisplayName("Verify Multi-Business Tenant Isolation: Zero cross-firm data leakage")
    void testMultiBusinessTenantDataIsolation() {
        // Business A Data
        Customer custA = customerService.create(Customer.builder().name("Firm A Client").firmId(firmAId).build());
        Product prodA = productService.create(Product.builder().name("Firm A Widget").firmId(firmAId).price(BigDecimal.TEN).build());
        Employee empA = new Employee();
        empA.setName("Firm A Worker");
        empA.setFirmId(firmAId);
        empA.setIsActive(true);
        employeeRepo.save(empA);

        // Business B Data
        Customer custB = customerService.create(Customer.builder().name("Firm B Client").firmId(firmBId).build());
        Product prodB = productService.create(Product.builder().name("Firm B Gadget").firmId(firmBId).price(BigDecimal.valueOf(20)).build());
        Employee empB = new Employee();
        empB.setName("Firm B Worker");
        empB.setFirmId(firmBId);
        empB.setIsActive(true);
        employeeRepo.save(empB);

        // Assert Firm A Queries see ONLY Firm A
        List<Customer> firmACustomers = customerService.getAll(firmAId);
        assertThat(firmACustomers).extracting(Customer::getName).containsExactly("Firm A Client");

        List<Product> firmAProducts = productService.getAll(firmAId);
        assertThat(firmAProducts).extracting(Product::getName).containsExactly("Firm A Widget");

        List<Employee> firmAEmployees = employeeRepo.findByFirmId(firmAId);
        assertThat(firmAEmployees).extracting(Employee::getName).containsExactly("Firm A Worker");

        // Assert Firm B Queries see ONLY Firm B
        List<Customer> firmBCustomers = customerService.getAll(firmBId);
        assertThat(firmBCustomers).extracting(Customer::getName).containsExactly("Firm B Client");

        List<Product> firmBProducts = productService.getAll(firmBId);
        assertThat(firmBProducts).extracting(Product::getName).containsExactly("Firm B Gadget");

        List<Employee> firmBEmployees = employeeRepo.findByFirmId(firmBId);
        assertThat(firmBEmployees).extracting(Employee::getName).containsExactly("Firm B Worker");
    }
}
