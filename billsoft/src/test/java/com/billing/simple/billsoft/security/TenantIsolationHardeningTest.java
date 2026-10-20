package com.billing.simple.billsoft.security;

import com.billing.simple.billsoft.controllers.AuthController;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.repositories.*;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DisplayName("Stage 1 — Tenant Isolation & Authorization Hardening Exploitation Tests")
public class TenantIsolationHardeningTest {

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
    private ExpenseRepository expenseRepo;

    @Autowired
    private NoteRepository noteRepo;

    @Autowired
    private ReminderRepository reminderRepo;

    @Autowired
    private EmployeeRepository employeeRepo;

    @Autowired
    private AppConfigRepository configRepo;

    @Autowired
    private AuthController authController;

    private Long firm1Id;
    private Long firm2Id;

    private Customer custFirm1;
    private Customer custFirm2;

    private Product prodFirm1;
    private Product prodFirm2;

    private Invoice invFirm1;
    private Invoice invFirm2;

    private Expense expFirm2;
    private Note noteFirm2;
    private Reminder remFirm2;
    private Employee empFirm2;

    private String tokenFirm1;
    private String tokenFirm2;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        configRepo.deleteAll();

        FirmDetails f1 = new FirmDetails();
        f1.setFirmName("Alpha Firm 1");
        f1 = firmRepo.save(f1);
        firm1Id = f1.getId();

        FirmDetails f2 = new FirmDetails();
        f2.setFirmName("Beta Firm 2");
        f2 = firmRepo.save(f2);
        firm2Id = f2.getId();

        // Customer
        custFirm1 = new Customer();
        custFirm1.setFirmId(firm1Id);
        custFirm1.setName("Customer Alpha");
        custFirm1.setPhone("1111111111");
        custFirm1 = customerRepo.save(custFirm1);

        custFirm2 = new Customer();
        custFirm2.setFirmId(firm2Id);
        custFirm2.setName("Customer Beta");
        custFirm2.setPhone("2222222222");
        custFirm2 = customerRepo.save(custFirm2);

        // Product
        prodFirm1 = new Product();
        prodFirm1.setFirmId(firm1Id);
        prodFirm1.setName("Product Alpha");
        prodFirm1.setPrice(new BigDecimal("100.00"));
        prodFirm1.setStockQuantity(new BigDecimal("50.000"));
        prodFirm1 = productRepo.save(prodFirm1);

        prodFirm2 = new Product();
        prodFirm2.setFirmId(firm2Id);
        prodFirm2.setName("Product Beta");
        prodFirm2.setPrice(new BigDecimal("200.00"));
        prodFirm2.setStockQuantity(new BigDecimal("30.000"));
        prodFirm2 = productRepo.save(prodFirm2);

        // Invoice
        invFirm1 = new Invoice();
        invFirm1.setFirmId(firm1Id);
        invFirm1.setInvoiceNumber("INV-A-001");
        invFirm1.setCustomer(custFirm1);
        invFirm1.setTotalAmount(new BigDecimal("100.00"));
        invFirm1 = invoiceRepo.save(invFirm1);

        invFirm2 = new Invoice();
        invFirm2.setFirmId(firm2Id);
        invFirm2.setInvoiceNumber("INV-B-001");
        invFirm2.setCustomer(custFirm2);
        invFirm2.setTotalAmount(new BigDecimal("200.00"));
        invFirm2 = invoiceRepo.save(invFirm2);

        // Expense
        expFirm2 = new Expense();
        expFirm2.setFirmId(firm2Id);
        expFirm2.setTitle("Office Rent");
        expFirm2.setCategory("Rent");
        expFirm2.setAmount(new BigDecimal("1500.00"));
        expFirm2.setExpenseDate(LocalDate.now());
        expFirm2 = expenseRepo.save(expFirm2);

        // Note
        noteFirm2 = new Note();
        noteFirm2.setFirmId(firm2Id);
        noteFirm2.setTitle("Beta Secret Note");
        noteFirm2.setContent("Confidential beta strategy");
        noteFirm2 = noteRepo.save(noteFirm2);

        // Reminder
        remFirm2 = new Reminder();
        remFirm2.setFirmId(firm2Id);
        remFirm2.setTitle("Beta Tax Filing");
        remFirm2.setDueDate(LocalDateTime.now().plusDays(5));
        remFirm2 = reminderRepo.save(remFirm2);

        // Employee
        empFirm2 = new Employee();
        empFirm2.setFirmId(firm2Id);
        empFirm2.setName("Bob Beta");
        empFirm2.setMonthlyBaseSalary(50000.0);
        empFirm2.setIsActive(true);
        empFirm2 = employeeRepo.save(empFirm2);

        // Generate session token
        tokenFirm1 = "session_token_app_master";
        AppConfig sessionConfig = new AppConfig();
        sessionConfig.setConfigKey("session_" + tokenFirm1);
        sessionConfig.setConfigValue(LocalDateTime.now().plusDays(30).toString());
        configRepo.save(sessionConfig);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        authController.invalidateToken(tokenFirm1);
        authController.invalidateToken(tokenFirm2);
        invoiceRepo.deleteAll();
        expenseRepo.deleteAll();
        noteRepo.deleteAll();
        reminderRepo.deleteAll();
        employeeRepo.deleteAll();
        productRepo.deleteAll();
        customerRepo.deleteAll();
        firmRepo.deleteAll();
        configRepo.deleteAll();
    }

    // =========================================================================
    // EXPLOIT 1: Firm-Scoped Path Without Header / Auth (Previously Leaked)
    // =========================================================================
    @Test
    @DisplayName("Exploit 1: Accessing /firm/{id} path without active firm context must fail closed (403)")
    void exploit1_accessFirmPathWithoutFirmContextFailsClosed() throws Exception {
        // GET /api/expenses/firm/2 with mismatching or missing header context
        mockMvc.perform(get("/api/expenses/firm/" + firm2Id)
                .header("X-Firm-Id", firm1Id))
                .andExpect(status().isForbidden());

        // GET /api/notes/firm/2 with mismatching header context
        mockMvc.perform(get("/api/notes/firm/" + firm2Id)
                .header("X-Firm-Id", firm1Id))
                .andExpect(status().isForbidden());

        // GET /api/reminders/firm/2 with mismatching header context
        mockMvc.perform(get("/api/reminders/firm/" + firm2Id)
                .header("X-Firm-Id", firm1Id))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // EXPLOIT 2: Cross-Firm Mutating Operations Fail Closed
    // =========================================================================
    @Test
    @DisplayName("Exploit 2: Cross-firm mutating operations must fail closed (400 or 404)")
    void exploit2_crossFirmMutatingOperationsFailClosed() throws Exception {
        // DELETE /api/invoices/{id} of Firm 2 under Firm 1 context
        mockMvc.perform(delete("/api/invoices/" + invFirm2.getId())
                .header("X-Firm-Id", firm1Id))
                .andExpect(status().is4xxClientError());

        // Ensure invoice was not deleted
        assertTrue(invoiceRepo.existsById(invFirm2.getId()));

        // DELETE /api/customers/{id} of Firm 2 under Firm 1 context
        mockMvc.perform(delete("/api/customers/" + custFirm2.getId())
                .header("X-Firm-Id", firm1Id))
                .andExpect(status().is4xxClientError());
        assertTrue(customerRepo.existsById(custFirm2.getId()));

        // DELETE /api/products/{id} of Firm 2 under Firm 1 context
        mockMvc.perform(delete("/api/products/" + prodFirm2.getId())
                .header("X-Firm-Id", firm1Id))
                .andExpect(status().is4xxClientError());
        assertTrue(productRepo.existsById(prodFirm2.getId()));

        // DELETE /api/reminders/{id} of Firm 2 under Firm 1 context
        mockMvc.perform(delete("/api/reminders/" + remFirm2.getId())
                .header("X-Firm-Id", firm1Id))
                .andExpect(status().is4xxClientError());
        assertTrue(reminderRepo.existsById(remFirm2.getId()));
    }

    // =========================================================================
    // EXPLOIT 3: Dynamic Multi-Firm Switching & Isolation by Authenticated User
    // =========================================================================
    @Test
    @DisplayName("Exploit 3: Authenticated session can switch firms via X-Firm-Id and sees strictly isolated data")
    void exploit3_multiFirmSwitchingAndIsolation() throws Exception {
        // Querying under Firm 1
        mockMvc.perform(get("/api/invoices")
                .header("X-Auth-Token", tokenFirm1)
                .header("X-Firm-Id", firm1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(invFirm1.getId()));

        // Switch to Firm 2 with same session token
        mockMvc.perform(get("/api/invoices")
                .header("X-Auth-Token", tokenFirm1)
                .header("X-Firm-Id", firm2Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(invFirm2.getId()));

        // Accessing /api/expenses/firm/2 with matching X-Firm-Id
        mockMvc.perform(get("/api/expenses/firm/" + firm2Id)
                .header("X-Auth-Token", tokenFirm1)
                .header("X-Firm-Id", firm2Id))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // EXPLOIT 4: Cross-Firm Direct Entity Access (Previously Leaked via findById)
    // =========================================================================
    @Test
    @DisplayName("Exploit 4: Authenticated Firm 1 user reading Firm 2 entity by ID must return 404 (not Firm 2 data)")
    void exploit4_directEntityAccessDoesNotLeakCrossTenant() throws Exception {
        // Firm 1 reads Firm 2's invoice
        mockMvc.perform(get("/api/invoices/" + invFirm2.getId())
                .header("X-Auth-Token", tokenFirm1)
                .header("X-Firm-Id", firm1Id))
                .andExpect(status().isNotFound());

        // Firm 1 reads Firm 2's customer
        mockMvc.perform(get("/api/customers/" + custFirm2.getId())
                .header("X-Auth-Token", tokenFirm1)
                .header("X-Firm-Id", firm1Id))
                .andExpect(status().isNotFound());

        // Firm 1 reads Firm 2's product
        mockMvc.perform(get("/api/products/" + prodFirm2.getId())
                .header("X-Auth-Token", tokenFirm1)
                .header("X-Firm-Id", firm1Id))
                .andExpect(status().isNotFound());

        // Firm 1 reads Firm 2's employee
        mockMvc.perform(get("/api/employees/" + empFirm2.getId())
                .header("X-Auth-Token", tokenFirm1)
                .header("X-Firm-Id", firm1Id)
                .header("X-Employee-Pin", "0000"))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // EXPLOIT 5: Cross-Firm Request Body Injection
    // =========================================================================
    @Test
    @DisplayName("Exploit 5: Cross-firm entity creation in request body must be prohibited")
    void exploit5_crossFirmBodyInjectionProhibited() throws Exception {
        // Authenticated as Firm 1, but body specifies firmId: 2
        Customer spoofCust = new Customer();
        spoofCust.setFirmId(firm2Id);
        spoofCust.setName("Spoofed Cust");
        spoofCust.setPhone("9999999999");

        mockMvc.perform(post("/api/customers")
                .header("X-Auth-Token", tokenFirm1)
                .header("X-Firm-Id", firm1Id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(spoofCust)))
                .andExpect(status().isBadRequest());

        // Authenticated as Firm 1, create invoice referencing Firm 2 customer
        com.billing.simple.billsoft.dtos.InvoiceRequest spoofInv = new com.billing.simple.billsoft.dtos.InvoiceRequest();
        spoofInv.setFirmId(firm1Id);
        spoofInv.setCustomerId(custFirm2.getId()); // referencing Firm 2 customer
        spoofInv.setInvoiceNumber("INV-SPOOF-01");

        mockMvc.perform(post("/api/invoices")
                .header("X-Auth-Token", tokenFirm1)
                .header("X-Firm-Id", firm1Id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(spoofInv)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // LEGITIMATE USAGE: Authenticated User Operations Succeed Within Tenant
    // =========================================================================
    @Test
    @DisplayName("Legitimate Usage: Authenticated requests succeed within own tenant")
    void legitimateUsage_ownTenantOperationsSucceed() throws Exception {
        // Firm 1 reads own invoice
        mockMvc.perform(get("/api/invoices/" + invFirm1.getId())
                .header("X-Auth-Token", tokenFirm1)
                .header("X-Firm-Id", firm1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(invFirm1.getId()))
                .andExpect(jsonPath("$.firmId").value(firm1Id));

        // Firm 1 lists own invoices
        mockMvc.perform(get("/api/invoices")
                .header("X-Auth-Token", tokenFirm1)
                .header("X-Firm-Id", firm1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(invFirm1.getId()));

        // Firm 2 reads own invoice using same session token
        mockMvc.perform(get("/api/invoices/" + invFirm2.getId())
                .header("X-Auth-Token", tokenFirm1)
                .header("X-Firm-Id", firm2Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(invFirm2.getId()))
                .andExpect(jsonPath("$.firmId").value(firm2Id));
    }

    // =========================================================================
    // EXPLOIT 6: Concurrent Multi-Threaded Tenant Isolation (10+ Threads)
    // =========================================================================
    @Test
    @DisplayName("Exploit 6: Multi-threaded interleaved execution (10 threads) maintains strict tenant isolation")
    void exploit6_multiThreadedTenantIsolation_noCrossContamination() throws Exception {
        int threadCount = 10;
        int taskCount = 60;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.List<java.util.concurrent.Future<Void>> futures = new java.util.ArrayList<>();

        for (int i = 0; i < taskCount; i++) {
            final boolean isFirm1 = (i % 2 == 0);
            final Long expectedFirmId = isFirm1 ? firm1Id : firm2Id;
            final Long otherCustId = isFirm1 ? custFirm2.getId() : custFirm1.getId();

            futures.add(executor.submit(() -> {
                try {
                    // 1. Verify thread entering task has clean context (or previous cleanup was honored)
                    assertNull(TenantContext.getCurrentFirmId(), "Thread entered with uncleared tenant context");

                    // 2. Set authoritative firm context
                    TenantContext.setCurrentFirmId(expectedFirmId);
                    assertEquals(expectedFirmId, TenantContext.getCurrentFirmId());

                    // 3. Query own firm records
                    java.util.List<Customer> ownCusts = customerRepo.findByFirmIdOrderByNameAsc(expectedFirmId);
                    assertFalse(ownCusts.isEmpty());
                    for (Customer c : ownCusts) {
                        assertEquals(expectedFirmId, c.getFirmId(), "Returned customer has mismatched firmId");
                    }

                    // 4. Query other firm records -> must be empty / inaccessible
                    assertTrue(customerRepo.findByIdAndFirmId(otherCustId, expectedFirmId).isEmpty(),
                            "Cross-firm customer access succeeded unexpectedly");

                    java.util.List<Invoice> ownInvoices = invoiceRepo.findByFirmId(expectedFirmId);
                    assertFalse(ownInvoices.isEmpty());
                    for (Invoice inv : ownInvoices) {
                        assertEquals(expectedFirmId, inv.getFirmId(), "Returned invoice has mismatched firmId");
                    }

                    return null;
                } finally {
                    // 5. Clean up unconditionally
                    TenantContext.clear();
                    assertNull(TenantContext.getCurrentFirmId(), "TenantContext.clear() failed to remove firm context");
                }
            }));
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS));

        // Propagate any task failure
        for (java.util.concurrent.Future<Void> f : futures) {
            f.get();
        }
    }

    @Test
    @DisplayName("Exploit 7: Thread reuse cannot inherit previous tenant context")
    void exploit7_threadReuse_cannotInheritPreviousTenantContext() throws Exception {
        java.util.concurrent.ExecutorService singleThreadExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            // Task 1: sets firm 1 context and cleans up
            singleThreadExecutor.submit(() -> {
                TenantContext.setCurrentFirmId(firm1Id);
                assertEquals(firm1Id, TenantContext.getCurrentFirmId());
                TenantContext.clear();
                return null;
            }).get();

            // Task 2 on the exact same thread: must have null firm context
            singleThreadExecutor.submit(() -> {
                assertNull(TenantContext.getCurrentFirmId(), "Reused thread inherited firm context from prior execution");
                return null;
            }).get();
        } finally {
            singleThreadExecutor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Exploit 8: Invalid tenant IDs (0, negative, null) fail closed and cannot establish context")
    void exploit8_invalidTenantIds_cannotEstablishContext() {
        TenantContext.setCurrentFirmId(0L);
        assertNull(TenantContext.getCurrentFirmId(), "firmId=0 unexpectedly established tenant context");

        TenantContext.setCurrentFirmId(-1L);
        assertNull(TenantContext.getCurrentFirmId(), "negative firmId unexpectedly established tenant context");

        TenantContext.setCurrentFirmId(null);
        assertNull(TenantContext.getCurrentFirmId(), "null firmId unexpectedly established tenant context");

        assertThrows(TenantSecurityException.class, () -> TenantContext.getRequiredFirmId(),
                "getRequiredFirmId() did not fail closed on empty context");
    }
}
