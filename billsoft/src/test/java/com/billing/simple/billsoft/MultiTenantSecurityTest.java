package com.billing.simple.billsoft;

import com.billing.simple.billsoft.dtos.*;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.repositories.*;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import com.billing.simple.billsoft.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@AutoConfigureMockMvc
public class MultiTenantSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FirmDetailsRepository firmRepo;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private InvoiceRepository invoiceRepo;

    @Autowired
    private InvoicePaymentRepository paymentRepo;

    @Autowired
    private SalesReturnRepository salesReturnRepo;

    @Autowired
    private ExpenseRepository expenseRepo;

    @Autowired
    private EmployeeRepository employeeRepo;

    @Autowired
    private NoteRepository noteRepo;

    @Autowired
    private ReminderRepository reminderRepo;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private ProductService productService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private NoteService noteService;

    @Autowired
    private ReminderService reminderService;

    private Long firmAId;
    private Long firmBId;

    private Customer customerA;
    private Customer customerB;

    private Product productA;
    private Product productB;

    private Invoice invoiceA;
    private Invoice invoiceB;

    private Invoice quoteA;
    private Invoice quoteB;

    private Expense expenseA;
    private Expense expenseB;

    private Employee employeeA;
    private Employee employeeB;

    private Note noteA;
    private Note noteB;

    private Reminder reminderA;
    private Reminder reminderB;

    @BeforeEach
    void setUp() {
        TenantContext.clear();

        // 1. Create Firm A and Firm B
        FirmDetails firmA = new FirmDetails();
        firmA.setFirmName("Alpha Corp (Firm A)");
        firmA.setPhone("1111111111");
        firmA.setEmail("alpha@firm.com");
        firmA = firmRepo.save(firmA);
        firmAId = firmA.getId();

        FirmDetails firmB = new FirmDetails();
        firmB.setFirmName("Beta Corp (Firm B)");
        firmB.setPhone("2222222222");
        firmB.setEmail("beta@firm.com");
        firmB = firmRepo.save(firmB);
        firmBId = firmB.getId();

        // 2. Customers
        customerA = customerRepo.save(Customer.builder().firmId(firmAId).name("Customer A").phone("1111").build());
        customerB = customerRepo.save(Customer.builder().firmId(firmBId).name("Customer B").phone("2222").build());

        // 3. Products
        productA = productRepo.save(Product.builder()
                .firmId(firmAId)
                .name("Product A")
                .price(new BigDecimal("100.00"))
                .stockQuantity(new BigDecimal("50.000"))
                .build());

        productB = productRepo.save(Product.builder()
                .firmId(firmBId)
                .name("Product B")
                .price(new BigDecimal("200.00"))
                .stockQuantity(new BigDecimal("80.000"))
                .build());

        // 4. Invoices
        invoiceA = invoiceRepo.save(Invoice.builder()
                .firmId(firmAId)
                .customer(customerA)
                .invoiceNumber("INV-A-101")
                .invoiceDate(LocalDateTime.now())
                .status(InvoiceStatus.PAID)
                .totalAmount(new BigDecimal("100.00"))
                .paid(true)
                .build());

        invoiceB = invoiceRepo.save(Invoice.builder()
                .firmId(firmBId)
                .customer(customerB)
                .invoiceNumber("INV-B-201")
                .invoiceDate(LocalDateTime.now())
                .status(InvoiceStatus.UNPAID)
                .totalAmount(new BigDecimal("200.00"))
                .paid(false)
                .build());

        // 5. Quotes / Estimates
        quoteA = invoiceRepo.save(Invoice.builder()
                .firmId(firmAId)
                .customer(customerA)
                .invoiceNumber("EST-A-001")
                .invoiceDate(LocalDateTime.now())
                .status(InvoiceStatus.ESTIMATE)
                .totalAmount(new BigDecimal("500.00"))
                .paid(false)
                .build());

        quoteB = invoiceRepo.save(Invoice.builder()
                .firmId(firmBId)
                .customer(customerB)
                .invoiceNumber("EST-B-001")
                .invoiceDate(LocalDateTime.now())
                .status(InvoiceStatus.ESTIMATE)
                .totalAmount(new BigDecimal("700.00"))
                .paid(false)
                .build());

        // 6. Expenses
        expenseA = expenseRepo.save(Expense.builder()
                .firmId(firmAId)
                .title("Expense A")
                .amount(new BigDecimal("50.00"))
                .expenseDate(LocalDate.now())
                .build());

        expenseB = expenseRepo.save(Expense.builder()
                .firmId(firmBId)
                .title("Expense B")
                .amount(new BigDecimal("90.00"))
                .expenseDate(LocalDate.now())
                .build());

        // 7. Employees
        Employee empA = new Employee();
        empA.setFirmId(firmAId);
        empA.setName("Employee Alpha");
        empA.setMonthlyBaseSalary(50000.0);
        empA.setIsActive(true);
        employeeA = employeeRepo.save(empA);

        Employee empB = new Employee();
        empB.setFirmId(firmBId);
        empB.setName("Employee Beta");
        empB.setMonthlyBaseSalary(60000.0);
        empB.setIsActive(true);
        employeeB = employeeRepo.save(empB);

        // 8. Notes & Reminders
        noteA = noteRepo.save(Note.builder()
                .firmId(firmAId)
                .title("Note A")
                .content("Firm A confidential")
                .build());

        noteB = noteRepo.save(Note.builder()
                .firmId(firmBId)
                .title("Note B")
                .content("Firm B confidential")
                .build());

        reminderA = reminderRepo.save(Reminder.builder()
                .firmId(firmAId)
                .title("Reminder A")
                .build());

        reminderB = reminderRepo.save(Reminder.builder()
                .firmId(firmBId)
                .title("Reminder B")
                .build());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ─── 1. Read Isolation ───
    @Test
    @DisplayName("1. Firm A cannot read Firm B records")
    void testFirmACannotReadFirmBRecords() throws Exception {
        // Customer
        mockMvc.perform(get("/api/customers/" + customerB.getId())
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isNotFound());

        // Product
        mockMvc.perform(get("/api/products/" + productB.getId())
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isNotFound());

        // Invoice
        mockMvc.perform(get("/api/invoices/" + invoiceB.getId())
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isNotFound());
    }

    // ─── 2. Update Isolation ───
    @Test
    @DisplayName("2. Firm A cannot update Firm B records")
    void testFirmACannotUpdateFirmBRecords() throws Exception {
        CustomerRequest updateReq = new CustomerRequest();
        updateReq.setName("Hacked Name");

        mockMvc.perform(put("/api/customers/" + customerB.getId())
                .header("X-Firm-Id", firmAId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isNotFound());

        // Verify Firm B record unchanged
        Customer freshB = customerRepo.findById(customerB.getId()).orElseThrow();
        assertEquals("Customer B", freshB.getName());
    }

    // ─── 3. Delete Isolation ───
    @Test
    @DisplayName("3. Firm A cannot delete Firm B records")
    void testFirmACannotDeleteFirmBRecords() throws Exception {
        mockMvc.perform(delete("/api/customers/" + customerB.getId())
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isNotFound());

        assertTrue(customerRepo.existsById(customerB.getId()));
    }

    // ─── 4. Cross-Entity ID References ───
    @Test
    @DisplayName("4. Firm A cannot use Firm B IDs in related entities")
    void testFirmACannotUseFirmBIDsInRelatedEntities() {
        TenantContext.setCurrentFirmId(firmAId);

        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(firmAId);
        req.setCustomerId(customerB.getId()); // Cross-firm customer!
        req.setItems(List.of(
                InvoiceRequestItem.builder()
                        .productId(productA.getId())
                        .qty(1)
                        .pricePerUnit(BigDecimal.TEN)
                        .build()
        ));

        assertThrows(TenantSecurityException.class, () -> invoiceService.createInvoice(req));
    }

    // ─── 5. Quote Conversion Isolation & Atomic Rollback ───
    @Test
    @DisplayName("5. Firm B cannot convert Firm A's quote (atomic rollback verified)")
    void testQuoteConversionStrictTenantIsolationAndRollback() {
        TenantContext.setCurrentFirmId(firmBId);

        BigDecimal productAInitialStock = productRepo.findById(productA.getId()).orElseThrow().getStockQuantity();

        // Attempt converting Firm A quote under Firm B context
        assertThrows(TenantSecurityException.class, () -> {
            invoiceService.convertEstimateToInvoice(quoteA.getId(), null);
        });

        // Verify quote status was NOT mutated
        Invoice freshQuoteA = invoiceRepo.findById(quoteA.getId()).orElseThrow();
        assertEquals(InvoiceStatus.ESTIMATE, freshQuoteA.getStatus());

        // Verify stock of Firm A product was NOT mutated
        BigDecimal productACurrentStock = productRepo.findById(productA.getId()).orElseThrow().getStockQuantity();
        assertEquals(productAInitialStock, productACurrentStock);

        // Verify no invoice was created
        List<Invoice> invoices = invoiceRepo.findByFirmIdAndStatus(firmBId, InvoiceStatus.PAID, org.springframework.data.domain.Pageable.unpaged()).getContent();
        assertTrue(invoices.isEmpty());
    }

    // ─── 6. Modify Stock Isolation ───
    @Test
    @DisplayName("6. Firm A cannot modify Firm B stock")
    void testFirmACannotModifyFirmBStock() {
        TenantContext.setCurrentFirmId(firmAId);

        assertThrows(TenantSecurityException.class, () -> {
            productService.adjustStock(productB.getId(), new BigDecimal("10.000"), "MANUAL", "Malicious adjustment");
        });

        Product freshB = productRepo.findById(productB.getId()).orElseThrow();
        assertEquals(new BigDecimal("80.000"), freshB.getStockQuantity());
    }

    // ─── 7. Payment Isolation ───
    @Test
    @DisplayName("7. Firm A cannot create payments against Firm B invoices")
    void testFirmACannotCreatePaymentsAgainstFirmBInvoices() {
        TenantContext.setCurrentFirmId(firmAId);

        assertThrows(TenantSecurityException.class, () -> {
            invoiceService.recordPayment(invoiceB.getId(), new BigDecimal("50.00"), LocalDate.now(), "CASH", "TXN123", "Payment note");
        });

        List<InvoicePayment> payments = paymentRepo.findByInvoiceIdOrderByPaymentDateAscIdAsc(invoiceB.getId());
        assertTrue(payments.isEmpty());
    }

    // ─── 8. Sales Return Isolation ───
    @Test
    @DisplayName("8. Firm A cannot create returns against Firm B invoices")
    void testFirmACannotCreateReturnsAgainstFirmBInvoices() {
        TenantContext.setCurrentFirmId(firmAId);

        SalesReturnRequest req = new SalesReturnRequest();
        req.setFirmId(firmAId);
        req.setInvoiceId(invoiceB.getId()); // Firm B invoice
        req.setRefundMode("CASH");
        req.setItems(List.of(
                SalesReturnRequest.SalesReturnItemRequest.builder()
                        .productId(productA.getId())
                        .returnQty(1)
                        .unitPrice(BigDecimal.TEN)
                        .build()
        ));

        assertThrows(TenantSecurityException.class, () -> {
            invoiceService.createSalesReturn(invoiceB.getId(), req);
        });

        List<SalesReturn> returns = salesReturnRepo.findByInvoiceIdOrderByCreatedAtDesc(invoiceB.getId());
        assertTrue(returns.isEmpty());
    }

    // ─── 9. PDF Scoping ───
    @Test
    @DisplayName("9. Firm A cannot access Firm B PDFs")
    void testFirmACannotAccessFirmBPdfs() throws Exception {
        mockMvc.perform(get("/api/invoices/" + invoiceB.getId() + "/pdf")
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isNotFound());
    }

    // ─── 10. Employee / Payroll Isolation ───
    @Test
    @DisplayName("10. Firm A cannot access Firm B employee/payroll information")
    void testFirmACannotAccessFirmBEmployeePayroll() throws Exception {
        mockMvc.perform(get("/api/employees/" + employeeB.getId() + "/salaries")
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isBadRequest());
    }

    // ─── 11. Analytics & Statements Isolation ───
    @Test
    @DisplayName("11. Firm A cannot access Firm B analytics/statements")
    void testFirmACannotAccessFirmBAnalyticsStatements() throws Exception {
        mockMvc.perform(get("/api/statements/customer/" + customerB.getId())
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isBadRequest());
    }

    // ─── 12. Missing Tenant Context Fails Safely ───
    @Test
    @DisplayName("12. Missing tenant context fails safely on business endpoints")
    void testMissingTenantContextFailsSafely() throws Exception {
        mockMvc.perform(get("/api/invoices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    // ─── 13. DTO FirmId Manipulation ───
    @Test
    @DisplayName("13. Manipulating firmId in DTO does not change ownership on update")
    void testManipulatingFirmIdInDtoDoesNotChangeOwnership() {
        TenantContext.setCurrentFirmId(firmAId);

        Expense updated = new Expense();
        updated.setTitle("Altered Title");
        updated.setAmount(new BigDecimal("999.00"));
        updated.setFirmId(firmBId); // Malicious firmId attempt

        expenseService.updateExpense(expenseA.getId(), updated);

        Expense freshA = expenseRepo.findById(expenseA.getId()).orElseThrow();
        assertEquals(firmAId, freshA.getFirmId(), "Firm ID ownership must remain immutable!");
        assertEquals("Altered Title", freshA.getTitle());
    }

    // ─── 14. Header FirmId Validation ───
    @Test
    @DisplayName("14. Manipulating X-Firm-Id with invalid non-existent ID fails safely with empty results")
    void testManipulatingXFirmIdCannotBypassAuthorization() throws Exception {
        mockMvc.perform(get("/api/invoices")
                .header("X-Firm-Id", 999999L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    // ─── 15. Same-Firm Operations Continue to Work ───
    @Test
    @DisplayName("15. Same-firm operations continue to work smoothly")
    void testSameFirmOperationsContinueToWork() throws Exception {
        mockMvc.perform(get("/api/customers/" + customerA.getId())
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/products/" + productA.getId())
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/invoices/" + invoiceA.getId())
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isOk());
    }

    // ─── 16. Tenant Data Integrity Audit Diagnostic Endpoint ───
    @Test
    @DisplayName("16. Diagnostics Tenant Integrity Audit endpoint executes and returns audit status")
    void testTenantIntegrityAuditEndpoint() throws Exception {
        mockMvc.perform(get("/api/diagnostics/tenant-integrity-audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLEAN"))
                .andExpect(jsonPath("$.auditTimestamp").exists())
                .andExpect(jsonPath("$.totalViolationsCount").value(0))
                .andExpect(jsonPath("$.scannedCounts").exists());
    }

    // ─── 17. H-01 Customer Analytics Tenant Isolation ───
    @Test
    @DisplayName("17. H-01: Firm A can access its own customer analytics, Firm B receives tenant security rejection")
    void testCustomerAnalyticsTenantIsolation() throws Exception {
        // 1. Firm A can access its own customer analytics
        mockMvc.perform(get("/api/invoices/analytics/customer/" + customerA.getId())
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(customerA.getId()))
                .andExpect(jsonPath("$.customerName").value("Customer A"));

        // 2. Firm B cannot access Firm A's customer analytics
        mockMvc.perform(get("/api/invoices/analytics/customer/" + customerA.getId())
                .header("X-Firm-Id", firmBId))
                .andExpect(status().isBadRequest());

        // 3. Search analytics scoped by firm: Firm B cannot find Firm A's customer analytics by name
        mockMvc.perform(get("/api/invoices/analytics/search")
                .param("name", "Customer A")
                .header("X-Firm-Id", firmBId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ─── 18. H-02 Print Preferences Tenant Authorization ───
    @Test
    @DisplayName("18. H-02: Firm A can update own print preferences, but cannot update Firm B's print preferences")
    void testPrintPreferencesTenantAuthorization() throws Exception {
        // Record Firm B's initial preferences
        FirmDetails initialB = firmRepo.findById(firmBId).orElseThrow();
        String initialBTheme = initialB.getInvoicePrintTheme();

        // 1. Firm A updates its own preferences -> OK
        PrintPreferencesRequest reqA = new PrintPreferencesRequest();
        reqA.setTheme("MODERN");
        reqA.setColor("EMERALD");
        reqA.setFormat("A5");

        mockMvc.perform(patch("/api/firm/" + firmAId + "/print-preferences")
                .header("X-Firm-Id", firmAId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoicePrintTheme").value("MODERN"))
                .andExpect(jsonPath("$.invoicePrintThemeColor").value("EMERALD"))
                .andExpect(jsonPath("$.invoicePrintFormat").value("A5"));

        // 2. Firm A attempts to update Firm B's preferences -> Rejected (400 Bad Request / TenantSecurityException)
        PrintPreferencesRequest reqMalicious = new PrintPreferencesRequest();
        reqMalicious.setTheme("MINIMAL");
        reqMalicious.setColor("SLATE");
        reqMalicious.setFormat("THERMAL_80MM");

        mockMvc.perform(patch("/api/firm/" + firmBId + "/print-preferences")
                .header("X-Firm-Id", firmAId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqMalicious)))
                .andExpect(status().isBadRequest());

        // 3. Confirm Firm B's preferences remain strictly unchanged
        FirmDetails freshB = firmRepo.findById(firmBId).orElseThrow();
        assertEquals(initialBTheme, freshB.getInvoicePrintTheme(), "Firm B print preferences must not be mutated by Firm A!");
    }
}
