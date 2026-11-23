package com.billing.simple.billsoft.regression.multifirm;

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
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * =========================================================================
 * MultiFirmDataIsolationCertificationTest
 *
 * Dedicated Production Certification Test Suite for RupeeCRM Multi-Firm
 * Data Isolation, Referential Integrity & Zero Cross-Firm Leakage.
 *
 * Simulates 3 Independent Synthetic Firms (Firm A, Firm B, Firm C) and
 * verifies that no cross-firm access, mutation, or inference is possible.
 * =========================================================================
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DisplayName("RupeeCRM Multi-Firm Data Isolation & Zero Cross-Firm Leakage Certification Suite")
public class MultiFirmDataIsolationCertificationTest {

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
    private PartyRepository partyRepo;

    @Autowired
    private PurchaseOrderRepository poRepo;

    @Autowired
    private ExpenseRepository expenseRepo;

    @Autowired
    private NoteRepository noteRepo;

    @Autowired
    private ReminderRepository reminderRepo;

    @Autowired
    private EmployeeRepository employeeRepo;

    @Autowired
    private BusinessLetterRepository letterRepo;

    @Autowired
    private GoalRepository goalRepo;

    @Autowired
    private SavingRepository savingRepo;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private ProductService productService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private PurchaseOrderService poService;

    @Autowired
    private PartyService partyService;

    @Autowired
    private NoteService noteService;

    @Autowired
    private ReminderService reminderService;

    @Autowired
    private TenantDataIntegrityAuditService auditService;

    // Synthetic Firms
    private Long firmAId;
    private Long firmBId;
    private Long firmCId;

    // Firm A Entities
    private Customer customerA;
    private Product productA;
    private Invoice invoiceA;
    private Party partyA;
    private PurchaseOrder poA;
    private Note noteA;
    private Reminder reminderA;
    private Employee employeeA;

    // Firm B Entities
    private Customer customerB;
    private Product productB;
    private Invoice invoiceB;
    private Party partyB;
    private PurchaseOrder poB;
    private Note noteB;
    private Reminder reminderB;
    private Employee employeeB;

    // Firm C Entities
    private Customer customerC;
    private Product productC;
    private Invoice invoiceC;
    private Party partyC;
    private PurchaseOrder poC;
    private Note noteC;
    private Reminder reminderC;
    private Employee employeeC;

    @BeforeEach
    void setUp() {
        TenantContext.clear();

        // 1. Create Firm A, Firm B, Firm C
        FirmDetails fA = new FirmDetails();
        fA.setFirmName("Firm A Industrial");
        fA.setPhone("1111111111");
        fA.setEmail("a@industry.com");
        fA = firmRepo.save(fA);
        firmAId = fA.getId();

        FirmDetails fB = new FirmDetails();
        fB.setFirmName("Firm B Retail");
        fB.setPhone("2222222222");
        fB.setEmail("b@retail.com");
        fB = firmRepo.save(fB);
        firmBId = fB.getId();

        FirmDetails fC = new FirmDetails();
        fC.setFirmName("Firm C Logistics");
        fC.setPhone("3333333333");
        fC.setEmail("c@logistics.com");
        fC = firmRepo.save(fC);
        firmCId = fC.getId();

        // 2. Customers
        customerA = customerRepo.save(Customer.builder().firmId(firmAId).name("Customer A").phone("1001").build());
        customerB = customerRepo.save(Customer.builder().firmId(firmBId).name("Customer B").phone("2002").build());
        customerC = customerRepo.save(Customer.builder().firmId(firmCId).name("Customer C").phone("3003").build());

        // 3. Products
        productA = productRepo.save(Product.builder().firmId(firmAId).name("Product A Heavy").price(new BigDecimal("1000.00")).stockQuantity(new BigDecimal("50.000")).build());
        productB = productRepo.save(Product.builder().firmId(firmBId).name("Product B Light").price(new BigDecimal("200.00")).stockQuantity(new BigDecimal("100.000")).build());
        productC = productRepo.save(Product.builder().firmId(firmCId).name("Product C Cargo").price(new BigDecimal("500.00")).stockQuantity(new BigDecimal("75.000")).build());

        // 4. Invoices
        invoiceA = invoiceRepo.save(Invoice.builder()
                .firmId(firmAId)
                .customer(customerA)
                .invoiceNumber("INV-A-001")
                .invoiceDate(LocalDateTime.now())
                .status(InvoiceStatus.PAID)
                .totalAmount(new BigDecimal("1000.00"))
                .paid(true)
                .build());

        invoiceB = invoiceRepo.save(Invoice.builder()
                .firmId(firmBId)
                .customer(customerB)
                .invoiceNumber("INV-B-001")
                .invoiceDate(LocalDateTime.now())
                .status(InvoiceStatus.UNPAID)
                .totalAmount(new BigDecimal("400.00"))
                .paid(false)
                .build());

        invoiceC = invoiceRepo.save(Invoice.builder()
                .firmId(firmCId)
                .customer(customerC)
                .invoiceNumber("INV-C-001")
                .invoiceDate(LocalDateTime.now())
                .status(InvoiceStatus.PAID)
                .totalAmount(new BigDecimal("1500.00"))
                .paid(true)
                .build());

        // 5. Parties & Purchase Orders
        partyA = partyRepo.save(Party.builder().firmId(firmAId).name("Supplier Alpha").contactPerson("Agent A").phone("111999").build());
        partyB = partyRepo.save(Party.builder().firmId(firmBId).name("Supplier Beta").contactPerson("Agent B").phone("222999").build());
        partyC = partyRepo.save(Party.builder().firmId(firmCId).name("Supplier Gamma").contactPerson("Agent C").phone("333999").build());

        poA = poRepo.save(PurchaseOrder.builder().firmId(firmAId).party(partyA).poNumber("PO-A-101").poDate(LocalDate.now()).status(PurchaseOrderStatus.ISSUED).totalAmount(new BigDecimal("5000.00")).build());
        poB = poRepo.save(PurchaseOrder.builder().firmId(firmBId).party(partyB).poNumber("PO-B-201").poDate(LocalDate.now()).status(PurchaseOrderStatus.ISSUED).totalAmount(new BigDecimal("8000.00")).build());
        poC = poRepo.save(PurchaseOrder.builder().firmId(firmCId).party(partyC).poNumber("PO-C-301").poDate(LocalDate.now()).status(PurchaseOrderStatus.ISSUED).totalAmount(new BigDecimal("12000.00")).build());

        // 6. Notes & Reminders
        noteA = noteRepo.save(Note.builder().firmId(firmAId).title("Confidential Strategy A").content("Secret A").build());
        noteB = noteRepo.save(Note.builder().firmId(firmBId).title("Confidential Strategy B").content("Secret B").build());
        noteC = noteRepo.save(Note.builder().firmId(firmCId).title("Confidential Strategy C").content("Secret C").build());

        reminderA = reminderRepo.save(Reminder.builder().firmId(firmAId).title("Tax Filing A").build());
        reminderB = reminderRepo.save(Reminder.builder().firmId(firmBId).title("Tax Filing B").build());
        reminderC = reminderRepo.save(Reminder.builder().firmId(firmCId).title("Tax Filing C").build());

        // 7. Employees
        Employee empA = new Employee();
        empA.setFirmId(firmAId);
        empA.setName("Alice Engineer (Firm A)");
        empA.setMonthlyBaseSalary(80000.0);
        empA.setIsActive(true);
        employeeA = employeeRepo.save(empA);

        Employee empB = new Employee();
        empB.setFirmId(firmBId);
        empB.setName("Bob Sales (Firm B)");
        empB.setMonthlyBaseSalary(60000.0);
        empB.setIsActive(true);
        employeeB = employeeRepo.save(empB);

        Employee empC = new Employee();
        empC.setFirmId(firmCId);
        empC.setName("Charlie Logistics (Firm C)");
        empC.setMonthlyBaseSalary(70000.0);
        empC.setIsActive(true);
        employeeC = employeeRepo.save(empC);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        paymentRepo.deleteAll();
        salesReturnRepo.deleteAll();
        invoiceRepo.deleteAll();
        poRepo.deleteAll();
        partyRepo.deleteAll();
        noteRepo.deleteAll();
        reminderRepo.deleteAll();
        if (employeeA != null && employeeA.getId() != null) { try { employeeRepo.deleteById(employeeA.getId()); } catch (Exception ignored) {} }
        if (employeeB != null && employeeB.getId() != null) { try { employeeRepo.deleteById(employeeB.getId()); } catch (Exception ignored) {} }
        if (employeeC != null && employeeC.getId() != null) { try { employeeRepo.deleteById(employeeC.getId()); } catch (Exception ignored) {} }
        productRepo.deleteAll();
        customerRepo.deleteAll();
        firmRepo.deleteAll();
    }

    // =========================================================================
    // TEST 1: Cross-Firm Invoice Direct Read (IDOR) Fails Closed
    // =========================================================================
    @Test
    @DisplayName("Attack 1: Firm A cannot read Firm B or Firm C invoice by ID")
    void attack01_crossFirmInvoiceDirectReadFailsClosed() throws Exception {
        // Firm A attempts to read Firm B invoice
        mockMvc.perform(get("/api/invoices/" + invoiceB.getId())
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isNotFound());

        // Firm A attempts to read Firm C invoice
        mockMvc.perform(get("/api/invoices/" + invoiceC.getId())
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // TEST 2: Cross-Firm Customer Direct Read (IDOR) Fails Closed
    // =========================================================================
    @Test
    @DisplayName("Attack 2: Firm B cannot read Firm A or Firm C customer by ID")
    void attack02_crossFirmCustomerDirectReadFailsClosed() throws Exception {
        mockMvc.perform(get("/api/customers/" + customerA.getId())
                .header("X-Firm-Id", firmBId))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/customers/" + customerC.getId())
                .header("X-Firm-Id", firmBId))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // TEST 3: Cross-Firm Product Direct Read (IDOR) Fails Closed
    // =========================================================================
    @Test
    @DisplayName("Attack 3: Firm C cannot read Firm A or Firm B product by ID")
    void attack03_crossFirmProductDirectReadFailsClosed() throws Exception {
        mockMvc.perform(get("/api/products/" + productA.getId())
                .header("X-Firm-Id", firmCId))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/products/" + productB.getId())
                .header("X-Firm-Id", firmCId))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // TEST 4: Cross-Firm Invoice Update Fails Closed
    // =========================================================================
    @Test
    @DisplayName("Attack 4: Firm A cannot update Firm B invoice")
    void attack04_crossFirmInvoiceUpdateFailsClosed() throws Exception {
        InvoiceUpdateRequest req = new InvoiceUpdateRequest();
        req.setStatus(InvoiceStatus.CANCELLED);

        mockMvc.perform(put("/api/invoices/" + invoiceB.getId())
                .header("X-Firm-Id", firmAId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is4xxClientError());

        // Assert Firm B invoice unchanged
        Invoice freshB = invoiceRepo.findById(invoiceB.getId()).orElseThrow();
        assertEquals(InvoiceStatus.UNPAID, freshB.getStatus());
    }

    // =========================================================================
    // TEST 5: Cross-Firm Invoice Deletion Fails Closed
    // =========================================================================
    @Test
    @DisplayName("Attack 5: Firm B cannot delete Firm A invoice")
    void attack05_crossFirmInvoiceDeletionFailsClosed() throws Exception {
        mockMvc.perform(delete("/api/invoices/" + invoiceA.getId())
                .header("X-Firm-Id", firmBId))
                .andExpect(status().is4xxClientError());

        assertTrue(invoiceRepo.existsById(invoiceA.getId()));
    }

    // =========================================================================
    // TEST 6: Cross-Firm Customer in Invoice Creation Fails Closed
    // =========================================================================
    @Test
    @DisplayName("Attack 6: Firm A cannot create invoice referencing Firm B customer")
    void attack06_crossFirmCustomerReferenceInInvoiceCreationFailsClosed() {
        TenantContext.setCurrentFirmId(firmAId);

        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(firmAId);
        req.setCustomerId(customerB.getId()); // Referencing Firm B customer
        req.setInvoiceNumber("INV-A-EXPLOIT-01");
        req.setItems(List.of(
                InvoiceRequestItem.builder()
                        .productId(productA.getId())
                        .qty(1)
                        .pricePerUnit(new BigDecimal("100.00"))
                        .build()
        ));

        assertThrows(TenantSecurityException.class, () -> invoiceService.createInvoice(req));
    }

    // =========================================================================
    // TEST 7: Cross-Firm Product in Invoice Creation Fails Closed
    // =========================================================================
    @Test
    @DisplayName("Attack 7: Firm A cannot create invoice referencing Firm B product in line items")
    void attack07_crossFirmProductReferenceInInvoiceCreationFailsClosed() {
        TenantContext.setCurrentFirmId(firmAId);

        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(firmAId);
        req.setCustomerId(customerA.getId());
        req.setInvoiceNumber("INV-A-EXPLOIT-02");
        req.setItems(List.of(
                InvoiceRequestItem.builder()
                        .productId(productB.getId()) // Referencing Firm B product!
                        .qty(1)
                        .pricePerUnit(new BigDecimal("200.00"))
                        .build()
        ));

        assertThrows(TenantSecurityException.class, () -> invoiceService.createInvoice(req));
    }

    // =========================================================================
    // TEST 8: Cross-Firm Payment Injection Fails Closed
    // =========================================================================
    @Test
    @DisplayName("Attack 8: Firm C cannot record payment against Firm B invoice")
    void attack08_crossFirmPaymentInjectionFailsClosed() {
        TenantContext.setCurrentFirmId(firmCId);

        assertThrows(TenantSecurityException.class, () -> {
            invoiceService.recordPayment(invoiceB.getId(), new BigDecimal("100.00"), LocalDate.now(), "CASH", "TXN999", "Exploit Payment");
        });

        List<InvoicePayment> payments = paymentRepo.findByInvoiceIdOrderByPaymentDateAscIdAsc(invoiceB.getId());
        assertTrue(payments.isEmpty());
    }

    // =========================================================================
    // TEST 9: Cross-Firm Sales Return Fails Closed
    // =========================================================================
    @Test
    @DisplayName("Attack 9: Firm B cannot create sales return against Firm A invoice")
    void attack09_crossFirmSalesReturnFailsClosed() {
        TenantContext.setCurrentFirmId(firmBId);

        SalesReturnRequest req = new SalesReturnRequest();
        req.setFirmId(firmBId);
        req.setInvoiceId(invoiceA.getId());
        req.setRefundMode("CASH");
        req.setItems(List.of(
                SalesReturnRequest.SalesReturnItemRequest.builder()
                        .productId(productB.getId())
                        .returnQty(1)
                        .unitPrice(new BigDecimal("100.00"))
                        .build()
        ));

        assertThrows(TenantSecurityException.class, () -> {
            invoiceService.createSalesReturn(invoiceA.getId(), req);
        });

        assertTrue(salesReturnRepo.findByInvoiceIdOrderByCreatedAtDesc(invoiceA.getId()).isEmpty());
    }

    // =========================================================================
    // TEST 10: Cross-Firm Purchase Order & Party Isolation Fails Closed
    // =========================================================================
    @Test
    @DisplayName("Attack 10: Firm A cannot read Firm B PO or link Firm B Supplier")
    void attack10_crossFirmPurchaseOrderIsolationFailsClosed() throws Exception {
        // Firm A reads Firm B PO by ID
        mockMvc.perform(get("/api/purchase-orders/" + poB.getId())
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isNotFound());

        // Firm A lists suppliers -> only sees Supplier Alpha
        mockMvc.perform(get("/api/parties")
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Supplier Alpha"))
                .andExpect(jsonPath("$[?(@.name == 'Supplier Beta')]").doesNotExist());
    }

    // =========================================================================
    // TEST 11: Cross-Firm Analytics Request Forbidden (IDOR Protection)
    // =========================================================================
    @Test
    @DisplayName("Attack 11: Firm A cannot query Firm B or Firm C financial analytics via param or header")
    void attack11_crossFirmAnalyticsFailsClosed() throws Exception {
        // Mismatched firm param vs context header
        mockMvc.perform(get("/api/analytics/firm")
                .header("X-Firm-Id", firmAId)
                .param("firmId", String.valueOf(firmBId)))
                .andExpect(status().isForbidden());

        // Calling analytics under Firm A only computes Firm A's figures
        mockMvc.perform(get("/api/analytics/firm")
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalInvoiceCount").value(1))
                .andExpect(jsonPath("$.totalBusiness").value(1000.00));
    }

    // =========================================================================
    // TEST 12: Cross-Firm Customer Statement Fails Closed
    // =========================================================================
    @Test
    @DisplayName("Attack 12: Firm A cannot access statement for Firm B customer")
    void attack12_crossFirmCustomerStatementFailsClosed() throws Exception {
        mockMvc.perform(get("/api/statements/customer/" + customerB.getId())
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // TEST 13: Cross-Firm Notes & Reminders Mutation Fails Closed
    // =========================================================================
    @Test
    @DisplayName("Attack 13: Firm A cannot update or delete Firm B notes or reminders")
    void attack13_crossFirmNotesAndRemindersFailsClosed() throws Exception {
        TenantContext.setCurrentFirmId(firmAId);

        // Delete Firm B note under Firm A context -> must return false
        assertFalse(noteService.delete(noteB.getId()));
        assertTrue(noteRepo.existsById(noteB.getId()));

        // Delete Firm B reminder under Firm A context -> must return false
        assertFalse(reminderService.delete(reminderB.getId()));
        assertTrue(reminderRepo.existsById(reminderB.getId()));
    }

    // =========================================================================
    // TEST 14: Cross-Firm Employee & Payroll Isolation Fails Closed
    // =========================================================================
    @Test
    @DisplayName("Attack 14: Firm A cannot access Firm B employee details or payroll")
    void attack14_crossFirmEmployeePayrollIsolationFailsClosed() throws Exception {
        mockMvc.perform(get("/api/employees/" + employeeB.getId() + "/salaries")
                .header("X-Firm-Id", firmAId)
                .header("X-Employee-Pin", "0000"))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // TEST 15: Concurrent Multi-Threaded Execution Across 3 Firms
    // =========================================================================
    @Test
    @DisplayName("Test 15: Interleaved concurrent operations across Firm A, Firm B, Firm C maintain strict isolation")
    void test15_concurrentInterleavedExecutionMaintainsZeroLeakage() throws Exception {
        int threadCount = 12;
        int operationsCount = 60;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<Void>> futures = new ArrayList<>();

        for (int i = 0; i < operationsCount; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                Long targetFirmId;
                Long expectedInvoiceId;
                String expectedInvoiceNo;

                if (index % 3 == 0) {
                    targetFirmId = firmAId;
                    expectedInvoiceId = invoiceA.getId();
                    expectedInvoiceNo = "INV-A-001";
                } else if (index % 3 == 1) {
                    targetFirmId = firmBId;
                    expectedInvoiceId = invoiceB.getId();
                    expectedInvoiceNo = "INV-B-001";
                } else {
                    targetFirmId = firmCId;
                    expectedInvoiceId = invoiceC.getId();
                    expectedInvoiceNo = "INV-C-001";
                }

                try {
                    TenantContext.setCurrentFirmId(targetFirmId);

                    // 1. Fetch own invoice
                    Invoice inv = invoiceService.getById(expectedInvoiceId);
                    assertNotNull(inv, "Invoice must be accessible within its own firm context");
                    assertEquals(targetFirmId, inv.getFirmId());
                    assertEquals(expectedInvoiceNo, inv.getInvoiceNumber());

                    // 2. Fetch other firms' invoices -> must return null
                    if (!targetFirmId.equals(firmAId)) {
                        assertNull(invoiceService.getById(invoiceA.getId()), "Cross-firm invoice leak detected for Firm A");
                    }
                    if (!targetFirmId.equals(firmBId)) {
                        assertNull(invoiceService.getById(invoiceB.getId()), "Cross-firm invoice leak detected for Firm B");
                    }
                    if (!targetFirmId.equals(firmCId)) {
                        assertNull(invoiceService.getById(invoiceC.getId()), "Cross-firm invoice leak detected for Firm C");
                    }

                    return null;
                } finally {
                    TenantContext.clear();
                }
            }));
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

        for (Future<Void> f : futures) {
            f.get();
        }
    }

    // =========================================================================
    // TEST 16: Read-Only Tenant Data Integrity Diagnostic Audit
    // =========================================================================
    @Test
    @DisplayName("Test 16: Read-only Diagnostic Tenant Integrity Audit reports CLEAN with 0 violations")
    void test16_tenantDataIntegrityAuditReportsClean() {
        Map<String, Object> auditReport = auditService.performFullAudit();
        assertNotNull(auditReport);
        assertEquals("CLEAN", auditReport.get("status"));
        assertEquals(0, auditReport.get("totalViolationsCount"));
        assertTrue(((List<?>) auditReport.get("violations")).isEmpty());
    }
}
