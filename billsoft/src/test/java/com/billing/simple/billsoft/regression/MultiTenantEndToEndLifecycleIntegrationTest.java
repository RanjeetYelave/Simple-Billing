package com.billing.simple.billsoft.regression;

import com.billing.simple.billsoft.assistant.dto.OmnisearchQuickHelpDTOs.*;
import com.billing.simple.billsoft.assistant.service.OmnisearchQuickHelpService;
import com.billing.simple.billsoft.controllers.AuthController;
import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.dtos.*;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.forecast.dto.RestockRecommendationDto;
import com.billing.simple.billsoft.forecast.repository.ProductRestockRecommendationRepository;
import com.billing.simple.billsoft.forecast.service.RestockAdvisoryService;
import com.billing.simple.billsoft.kpi.KpiService;
import com.billing.simple.billsoft.kpi.dto.KpiDashboardResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * =========================================================================
 * GROUP 8 CANONICAL REGRESSION HARNESS: AUDIT-017
 * MultiTenantEndToEndLifecycleIntegrationTest
 *
 * Comprehensive, integrated multi-tenant regression harness proving that:
 * Firm A must never be able to read, modify, search, aggregate, restore,
 * download, or derive data belonging to Firm B, while intentionally global
 * functionality remains global where explicitly designed.
 * =========================================================================
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DisplayName("Group 8: Multi-Tenant Integration Regression Harness (AUDIT-017)")
public class MultiTenantEndToEndLifecycleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Repositories
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
    private PurchaseOrderItemRepository poItemRepo;

    @Autowired
    private ExpenseRepository expenseRepo;

    @Autowired
    private EmployeeRepository employeeRepo;

    @Autowired
    private NoteRepository noteRepo;

    @Autowired
    private ReminderRepository reminderRepo;

    @Autowired
    private AppConfigRepository configRepo;

    @Autowired
    private PricingProfileRepository profileRepo;

    @Autowired
    private CategoryPricingRuleRepository categoryRuleRepo;

    @Autowired
    private ProductRestockRecommendationRepository restockRepo;

    // Services
    @Autowired
    private BackupService backupService;

    @Autowired
    private CustomerBalanceService customerBalanceService;

    @Autowired
    private StatementService statementService;

    @Autowired
    private KpiService kpiService;

    @Autowired
    private ProductService productService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private PartyService partyService;

    @Autowired
    private PurchaseOrderService poService;

    @Autowired
    private OmnisearchQuickHelpService omnisearchService;

    @Autowired
    private RestockAdvisoryService restockAdvisoryService;

    @Autowired
    private com.billing.simple.billsoft.security.TenantInterceptor tenantInterceptor;

    @Autowired
    private AuthController authController;

    // Standard Two-Firm Fixture Identifiers
    private Long firmAId;
    private Long firmBId;

    private Customer custA;
    private Customer custB;

    private Product prodA;
    private Product prodB;

    private Party vendorA;
    private Party vendorB;

    private Invoice invA;
    private Invoice invB;

    private InvoicePayment paymentA;
    private InvoicePayment paymentB;

    private PurchaseOrder poA;
    private PurchaseOrder poB;

    private Expense expA;
    private Expense expB;

    private Employee empA;
    private Employee empB;

    private PricingProfile profileA;
    private PricingProfile profileB;

    private String tokenFirmA;
    private String tokenFirmB;

    @BeforeEach
    void setUp() {
        TenantContext.clear();

        // 1. Reset database state cleanly
        backupService.factoryReset();

        // 2. Establish Firm A ("Alpha Engineering Corp")
        FirmDetails fA = new FirmDetails();
        fA.setFirmName("Alpha Engineering Corp");
        fA.setGstin("27AAAAA1111A1Z1");
        fA.setPhone("9111111111");
        fA.setEmail("admin@alpha.com");
        fA.setAddressLine1("101 Alpha Industrial Estate");
        fA.setCity("Mumbai");
        fA.setState("Maharashtra");
        fA.setPincode("400001");
        fA = firmRepo.save(fA);
        firmAId = fA.getId();

        // 3. Establish Firm B ("Beta Manufacturing Ltd")
        FirmDetails fB = new FirmDetails();
        fB.setFirmName("Beta Manufacturing Ltd");
        fB.setGstin("27BBBBB2222B2Z2");
        fB.setPhone("9222222222");
        fB.setEmail("admin@beta.com");
        fB.setAddressLine1("202 Beta Commercial Zone");
        fB.setCity("Pune");
        fB.setState("Maharashtra");
        fB.setPincode("411001");
        fB = firmRepo.save(fB);
        firmBId = fB.getId();

        // 4. Authenticated Session Tokens
        tokenFirmA = "session_token_firm_alpha_" + System.currentTimeMillis();
        AppConfig sA = new AppConfig();
        sA.setConfigKey("session_" + tokenFirmA);
        sA.setConfigValue(LocalDateTime.now().plusDays(30).toString());
        configRepo.save(sA);

        tokenFirmB = "session_token_firm_beta_" + System.currentTimeMillis();
        AppConfig sB = new AppConfig();
        sB.setConfigKey("session_" + tokenFirmB);
        sB.setConfigValue(LocalDateTime.now().plusDays(30).toString());
        configRepo.save(sB);

        // 5. Customers (Distinguishable data)
        custA = customerRepo.save(Customer.builder()
                .firmId(firmAId)
                .name("Alpha Precision Works")
                .phone("9111000001")
                .address("Mumbai North")
                .build());

        custB = customerRepo.save(Customer.builder()
                .firmId(firmBId)
                .name("Beta Mega Corp")
                .phone("9222000002")
                .address("Pune East")
                .build());

        // 6. Products
        prodA = productRepo.save(Product.builder()
                .firmId(firmAId)
                .name("Alpha Drill Machine")
                .sku("ADM-100")
                .price(new BigDecimal("100.00"))
                .costPrice(new BigDecimal("40.00"))
                .stockQuantity(new BigDecimal("50.000"))
                .minStockLevel(new BigDecimal("10.000"))
                .isArchived(false)
                .build());

        prodB = productRepo.save(Product.builder()
                .firmId(firmBId)
                .name("Beta Hydraulic Pump")
                .sku("BHP-200")
                .price(new BigDecimal("1000.00"))
                .costPrice(new BigDecimal("400.00"))
                .stockQuantity(new BigDecimal("80.000"))
                .minStockLevel(new BigDecimal("5.000"))
                .isArchived(false)
                .build());

        // 7. Vendors / Parties
        vendorA = partyRepo.save(Party.builder()
                .firmId(firmAId)
                .name("Alpha Tooling Supply")
                .phone("9811111111")
                .city("Mumbai")
                .build());

        vendorB = partyRepo.save(Party.builder()
                .firmId(firmBId)
                .name("Beta Polymers Ltd")
                .phone("9822222222")
                .city("Pune")
                .build());

        // 8. Invoices (Deliberately different financial values)
        // Firm A: Total = ₹1,000.00
        invA = invoiceRepo.save(Invoice.builder()
                .firmId(firmAId)
                .customer(custA)
                .invoiceNumber("INV-ALPHA-101")
                .invoiceDate(LocalDateTime.now().minusDays(5))
                .status(InvoiceStatus.UNPAID)
                .totalAmount(new BigDecimal("1000.00"))
                .subtotalWithoutTax(new BigDecimal("1000.00"))
                .totalDiscount(BigDecimal.ZERO)
                .totalTax(BigDecimal.ZERO)
                .paid(false)
                .build());

        // Firm B: Total = ₹50,000.00
        invB = invoiceRepo.save(Invoice.builder()
                .firmId(firmBId)
                .customer(custB)
                .invoiceNumber("INV-BETA-201")
                .invoiceDate(LocalDateTime.now().minusDays(10))
                .status(InvoiceStatus.FINAL)
                .totalAmount(new BigDecimal("50000.00"))
                .subtotalWithoutTax(new BigDecimal("50000.00"))
                .totalDiscount(BigDecimal.ZERO)
                .totalTax(BigDecimal.ZERO)
                .paid(false)
                .build());

        // 9. Payments
        // Firm A payment: ₹200.00 -> remaining balance = ₹800.00
        paymentA = paymentRepo.save(InvoicePayment.builder()
                .firmId(firmAId)
                .invoiceId(invA.getId())
                .customerId(custA.getId())
                .amount(new BigDecimal("200.00"))
                .paymentDate(LocalDate.now().minusDays(2))
                .paymentMode("CASH")
                .build());

        // Firm B payment: ₹5,000.00 -> remaining balance = ₹45,000.00
        paymentB = paymentRepo.save(InvoicePayment.builder()
                .firmId(firmBId)
                .invoiceId(invB.getId())
                .customerId(custB.getId())
                .amount(new BigDecimal("5000.00"))
                .paymentDate(LocalDate.now().minusDays(3))
                .paymentMode("BANK_TRANSFER")
                .build());

        // 10. Purchase Orders
        poA = poRepo.save(PurchaseOrder.builder()
                .firmId(firmAId)
                .party(vendorA)
                .poNumber("PO-ALPHA-501")
                .poDate(LocalDate.now().minusDays(4))
                .status(PurchaseOrderStatus.ISSUED)
                .totalAmount(new BigDecimal("2500.00"))
                .build());

        poB = poRepo.save(PurchaseOrder.builder()
                .firmId(firmBId)
                .party(vendorB)
                .poNumber("PO-BETA-601")
                .poDate(LocalDate.now().minusDays(6))
                .status(PurchaseOrderStatus.ISSUED)
                .totalAmount(new BigDecimal("150000.00"))
                .build());

        // 11. Expenses
        expA = expenseRepo.save(Expense.builder()
                .firmId(firmAId)
                .title("Alpha Factory Power")
                .amount(new BigDecimal("1500.00"))
                .expenseDate(LocalDate.now())
                .build());

        expB = expenseRepo.save(Expense.builder()
                .firmId(firmBId)
                .title("Beta Logistics Freight")
                .amount(new BigDecimal("75000.00"))
                .expenseDate(LocalDate.now())
                .build());

        // 12. Employees
        empA = new Employee();
        empA.setFirmId(firmAId);
        empA.setName("Anand Alpha");
        empA.setMonthlyBaseSalary(40000.0);
        empA.setIsActive(true);
        empA = employeeRepo.save(empA);

        empB = new Employee();
        empB.setFirmId(firmBId);
        empB.setName("Bharat Beta");
        empB.setMonthlyBaseSalary(90000.0);
        empB.setIsActive(true);
        empB = employeeRepo.save(empB);

        // 13. Pricing Profiles
        profileA = profileRepo.save(PricingProfile.builder()
                .firmId(firmAId)
                .name("Alpha Retail Tier")
                .code("ALPHA_RETAIL")
                .build());

        profileB = profileRepo.save(PricingProfile.builder()
                .firmId(firmBId)
                .name("Beta Wholesale Tier")
                .code("BETA_WHOLESALE")
                .build());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        authController.invalidateToken(tokenFirmA);
        authController.invalidateToken(tokenFirmB);
    }

    // =========================================================================
    // MUST-HAVE TEST 1: CORE ENTITY READ ISOLATION
    // =========================================================================

    @Test
    @DisplayName("MUST-HAVE 1: Core entity read isolation across Product, Customer, Party, Invoice, PO")
    void test01_coreEntityReadIsolation() throws Exception {
        // --- FIRM A CONTEXT ---
        // 1. Products
        mockMvc.perform(get("/api/products/" + prodA.getId())
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alpha Drill Machine"));

        mockMvc.perform(get("/api/products/" + prodB.getId())
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isNotFound());

        // 2. Customers
        mockMvc.perform(get("/api/customers/" + custA.getId())
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alpha Precision Works"));

        mockMvc.perform(get("/api/customers/" + custB.getId())
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isNotFound());

        // 3. Parties / Vendors
        mockMvc.perform(get("/api/parties/" + vendorA.getId())
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alpha Tooling Supply"));

        mockMvc.perform(get("/api/parties/" + vendorB.getId())
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isNotFound());

        // 4. Invoices
        mockMvc.perform(get("/api/invoices/" + invA.getId())
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceNumber").value("INV-ALPHA-101"));

        mockMvc.perform(get("/api/invoices/" + invB.getId())
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isNotFound());

        // 5. Purchase Orders
        mockMvc.perform(get("/api/purchase-orders/" + poA.getId())
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.poNumber").value("PO-ALPHA-501"));

        mockMvc.perform(get("/api/purchase-orders/" + poB.getId())
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isNotFound());

        // --- FIRM B CONTEXT (Symmetric validation) ---
        mockMvc.perform(get("/api/products/" + prodB.getId())
                .header("X-Auth-Token", tokenFirmB)
                .header("X-Firm-Id", firmBId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Beta Hydraulic Pump"));

        mockMvc.perform(get("/api/products/" + prodA.getId())
                .header("X-Auth-Token", tokenFirmB)
                .header("X-Firm-Id", firmBId))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/invoices/" + invA.getId())
                .header("X-Auth-Token", tokenFirmB)
                .header("X-Firm-Id", firmBId))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // MUST-HAVE TEST 2: CORE ENTITY WRITE / DELETE ISOLATION + DB STATE PROOF
    // =========================================================================

    @Test
    @DisplayName("MUST-HAVE 2: Core entity write & delete isolation with database-level state verification")
    void test02_coreEntityWriteAndDeleteIsolation() throws Exception {
        // Record baseline state of Firm B entities
        Product initialProdB = productRepo.findById(prodB.getId()).orElseThrow();
        Customer initialCustB = customerRepo.findById(custB.getId()).orElseThrow();
        Party initialVendorB = partyRepo.findById(vendorB.getId()).orElseThrow();
        Invoice initialInvB = invoiceRepo.findById(invB.getId()).orElseThrow();
        PurchaseOrder initialPoB = poRepo.findById(poB.getId()).orElseThrow();

        // 1. Cross-firm Product Update Attempt
        Product maliciousProductUpdate = Product.builder()
                .name("Compromised Product")
                .price(new BigDecimal("1.00"))
                .build();
        mockMvc.perform(put("/api/products/" + prodB.getId())
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(maliciousProductUpdate)))
                .andExpect(status().is4xxClientError());

        // Direct DB Verification: Product B unchanged
        Product freshProdB = productRepo.findById(prodB.getId()).orElseThrow();
        assertEquals(initialProdB.getName(), freshProdB.getName(), "Product B name mutated by cross-firm PUT!");
        assertEquals(initialProdB.getPrice(), freshProdB.getPrice(), "Product B price mutated by cross-firm PUT!");

        // 2. Cross-firm Customer Update Attempt
        CustomerRequest maliciousCustUpdate = new CustomerRequest();
        maliciousCustUpdate.setName("Compromised Customer");
        mockMvc.perform(put("/api/customers/" + custB.getId())
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(maliciousCustUpdate)))
                .andExpect(status().is4xxClientError());

        // Direct DB Verification: Customer B unchanged
        Customer freshCustB = customerRepo.findById(custB.getId()).orElseThrow();
        assertEquals(initialCustB.getName(), freshCustB.getName(), "Customer B name mutated by cross-firm PUT!");

        // 3. Cross-firm Party Update Attempt
        Party maliciousPartyUpdate = Party.builder().name("Compromised Party").build();
        mockMvc.perform(put("/api/parties/" + vendorB.getId())
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(maliciousPartyUpdate)))
                .andExpect(status().is4xxClientError());

        // Direct DB Verification: Party B unchanged
        Party freshVendorB = partyRepo.findById(vendorB.getId()).orElseThrow();
        assertEquals(initialVendorB.getName(), freshVendorB.getName(), "Party B name mutated by cross-firm PUT!");

        // 4. Cross-firm Delete Attempts
        mockMvc.perform(delete("/api/products/" + prodB.getId())
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().is4xxClientError());
        assertTrue(productRepo.existsById(prodB.getId()), "Product B was deleted by Firm A!");

        mockMvc.perform(delete("/api/customers/" + custB.getId())
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().is4xxClientError());
        assertTrue(customerRepo.existsById(custB.getId()), "Customer B was deleted by Firm A!");

        mockMvc.perform(delete("/api/parties/" + vendorB.getId())
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().is4xxClientError());
        assertTrue(partyRepo.existsById(vendorB.getId()), "Party B was deleted by Firm A!");

        mockMvc.perform(delete("/api/purchase-orders/" + poB.getId())
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().is4xxClientError());
        assertTrue(poRepo.existsById(poB.getId()), "PO B was deleted by Firm A!");

        mockMvc.perform(delete("/api/invoices/" + invB.getId())
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().is4xxClientError());
        assertTrue(invoiceRepo.existsById(invB.getId()), "Invoice B was deleted by Firm A!");
    }

    // =========================================================================
    // MUST-HAVE TEST 3: FINANCIAL / BALANCE ISOLATION
    // =========================================================================

    @Test
    @DisplayName("MUST-HAVE 3: Financial & balance isolation (CustomerBalanceService, statements, KPIs, refunds)")
    void test03_financialAndBalanceIsolation() throws Exception {
        // 1. Authoritative CustomerBalanceService verification
        CustomerBalanceSummary balanceA = customerBalanceService.getCustomerBalance(firmAId, custA.getId());
        assertEquals(new BigDecimal("800.00"), balanceA.getClosingBalance(), "Firm A balance must be 1000.00 - 200.00 = 800.00");

        CustomerBalanceSummary balanceB = customerBalanceService.getCustomerBalance(firmBId, custB.getId());
        assertEquals(new BigDecimal("45000.00"), balanceB.getClosingBalance(), "Firm B balance must be 50000.00 - 5000.00 = 45000.00");

        // Cross-firm balance lookup throws TenantSecurityException
        assertThrows(TenantSecurityException.class, () -> {
            customerBalanceService.getCustomerBalance(firmAId, custB.getId());
        });

        // 2. Statements API
        // Firm A statement: billed must be exactly 1000.00 (strictly NOT 51,000.00)
        mockMvc.perform(get("/api/statements/firm?firmId=" + firmAId)
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firmName").value("Alpha Engineering Corp"))
                .andExpect(jsonPath("$.totalBilled").value(1000.0));

        // Customer statement for Customer A
        mockMvc.perform(get("/api/statements/customer/" + custA.getId() + "?firmId=" + firmAId)
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBilled").value(1000.0))
                .andExpect(jsonPath("$.totalPaid").value(200.0))
                .andExpect(jsonPath("$.closingBalance").value(800.0));

        // Attempt Customer statement for Customer B from Firm A context -> rejected with 400
        mockMvc.perform(get("/api/statements/customer/" + custB.getId() + "?firmId=" + firmAId)
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isBadRequest());

        // 3. KPI Dashboard API
        mockMvc.perform(get("/api/kpis/dashboard")
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBusiness").value(1000.0))
                .andExpect(jsonPath("$.totalPaid").value(200.0))
                .andExpect(jsonPath("$.totalPending").value(800.0))
                .andExpect(jsonPath("$.unpaidInvoiceCount").value(1));

        // 4. Sales Return Cross-Firm Isolation
        TenantContext.setCurrentFirmId(firmAId);
        SalesReturnRequest crossReturnReq = new SalesReturnRequest();
        crossReturnReq.setFirmId(firmAId);
        crossReturnReq.setInvoiceId(invB.getId());
        crossReturnReq.setRefundMode("CASH");

        assertThrows(TenantSecurityException.class, () -> {
            invoiceService.createSalesReturn(invB.getId(), crossReturnReq);
        });
    }

    // =========================================================================
    // MUST-HAVE TEST 4: SEARCH / OMNISEARCH ISOLATION
    // =========================================================================

    @Test
    @DisplayName("MUST-HAVE 4: OmniSearch isolation (/search, /expenses, /aging, /qr global)")
    void test04_searchAndOmniSearchIsolation() throws Exception {
        // 1. Full-text search as Firm A for 'Beta' keyword -> 0 results
        mockMvc.perform(get("/api/omnisearch/search")
                .param("query", "Beta")
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANSWER"))
                .andExpect(jsonPath("$.data.customers").isEmpty())
                .andExpect(jsonPath("$.data.products").isEmpty())
                .andExpect(jsonPath("$.data.vendors").isEmpty())
                .andExpect(jsonPath("$.data.employees").isEmpty());

        // 2. Full-text search as Firm A for 'Alpha' keyword -> returns Firm A records
        mockMvc.perform(get("/api/omnisearch/search")
                .param("query", "Alpha")
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANSWER"))
                .andExpect(jsonPath("$.data.customers[0].name").value("Alpha Precision Works"))
                .andExpect(jsonPath("$.data.products[0].name").value("Alpha Drill Machine"));

        // 3. Full-text search as Firm B for 'Alpha' keyword -> 0 results
        mockMvc.perform(get("/api/omnisearch/search")
                .param("query", "Alpha")
                .header("X-Auth-Token", tokenFirmB)
                .header("X-Firm-Id", firmBId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANSWER"))
                .andExpect(jsonPath("$.data.customers").isEmpty())
                .andExpect(jsonPath("$.data.products").isEmpty());

        // 4. Expenses summary under Firm A: ₹1,500.00 (strictly excluding Firm B's ₹75,000.00)
        mockMvc.perform(get("/api/omnisearch/expenses")
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANSWER"))
                .andExpect(jsonPath("$.data.totalExpense").value(1500.0));

        // 5. AR Aging summary under Firm A: ₹800.00 (strictly excluding Firm B's ₹45,000.00)
        mockMvc.perform(get("/api/omnisearch/aging")
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANSWER"))
                .andExpect(jsonPath("$.data.totalReceivable").value(800.0))
                .andExpect(jsonPath("$.data.netReceivable").value(800.0));

        // 6. Global QR Utility exemptions:
        // A) TenantInterceptor exempts /api/omnisearch/qr without requiring firm headers
        org.springframework.mock.web.MockHttpServletRequest reqQr = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/omnisearch/qr");
        org.springframework.mock.web.MockHttpServletResponse resQr = new org.springframework.mock.web.MockHttpServletResponse();
        assertTrue(tenantInterceptor.preHandle(reqQr, resQr, new Object()), "/api/omnisearch/qr must be exempt from tenant header requirement");

        // B) Licensing QR endpoint is globally accessible
        mockMvc.perform(get("/api/licensing/qr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app").value("RupeeCRM"));
    }

    // =========================================================================
    // MUST-HAVE TEST 5: INVENTORY / PRICING / FORECAST / SMART REORDER
    // =========================================================================

    @Test
    @DisplayName("MUST-HAVE 5: Inventory, pricing, forecasting & Smart Reorder derived-data isolation")
    void test05_inventoryPricingForecastSmartReorderIsolation() throws Exception {
        // 1. Stock Adjustment Isolation
        TenantContext.setCurrentFirmId(firmAId);
        assertThrows(TenantSecurityException.class, () -> {
            productService.adjustStock(prodB.getId(), new BigDecimal("10.000"), "MANUAL", "Cross adjustment");
        });
        Product freshProdB = productRepo.findById(prodB.getId()).orElseThrow();
        assertEquals(new BigDecimal("80.000"), freshProdB.getStockQuantity(), "Stock B was mutated by Firm A!");

        // 2. Pricing Profile Isolation
        mockMvc.perform(get("/api/pricing-profiles?firmId=" + firmAId)
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Alpha Retail Tier"))
                .andExpect(jsonPath("$[1]").doesNotExist());

        // Firm A cannot modify Firm B pricing profile
        PricingProfileDto maliciousProfile = PricingProfileDto.builder()
                .name("Compromised Tier")
                .code("COMPROMISED")
                .build();
        mockMvc.perform(put("/api/pricing-profiles/" + profileB.getId() + "?firmId=" + firmAId)
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(maliciousProfile)))
                .andExpect(status().is4xxClientError());

        // 3. Smart Reorder & Forecasting Isolation
        // Adjust Product A stock to 2.0 (below minStockLevel = 10.0) -> triggers restock recommendation
        productService.adjustStock(prodA.getId(), new BigDecimal("2.000"), "SET", "Inventory reduction");
        Product updatedProdA = productRepo.findById(prodA.getId()).orElseThrow();
        assertEquals(new BigDecimal("2.000"), updatedProdA.getStockQuantity());

        // Generate recommendations for Firm A
        restockAdvisoryService.generateCatalogRecommendations(firmAId);
        Page<RestockRecommendationDto> recsA = restockAdvisoryService.getActiveRecommendations(firmAId, null, PageRequest.of(0, 25));

        // Firm A recommendations must contain Product A, and strictly ZERO items for Product B
        assertFalse(recsA.isEmpty(), "Firm A must have restock recommendation for Product A");
        for (RestockRecommendationDto r : recsA.getContent()) {
            assertEquals(prodA.getId(), r.getProductId(), "Firm A restock recommendation returned non-Firm-A product!");
            assertNotEquals(prodB.getId(), r.getProductId(), "Firm B product leaked into Firm A Smart Reorder!");
        }

        // Verify incoming PO B does not increase Firm A stock
        PurchaseOrder freshPoB = poRepo.findById(poB.getId()).orElseThrow();
        assertEquals(firmBId, freshPoB.getFirmId());
    }

    // =========================================================================
    // MUST-HAVE TEST 6: DOWNLOAD / PDF / REPORT ISOLATION
    // =========================================================================

    @Test
    @DisplayName("MUST-HAVE 6: Document download isolation (Invoice PDF, PO PDF, batch PO PDF/ZIP, statements)")
    void test06_downloadAndPdfIsolation() throws Exception {
        // 1. Invoice PDF
        mockMvc.perform(get("/api/invoices/" + invA.getId() + "/pdf")
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));

        mockMvc.perform(get("/api/invoices/" + invB.getId() + "/pdf")
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isNotFound());

        // 2. Purchase Order PDF
        mockMvc.perform(get("/api/purchase-orders/" + poA.getId() + "/pdf")
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));

        mockMvc.perform(get("/api/purchase-orders/" + poB.getId() + "/pdf")
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isNotFound());

        // 3. Batch Purchase Order PDF: Requesting [poA.id, poB.id] under Firm A filters out poB
        mockMvc.perform(get("/api/purchase-orders/batch/pdf")
                .param("ids", poA.getId() + "," + poB.getId())
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));

        // 4. Batch Purchase Order ZIP: Unpack in-memory and assert exactly 1 file (poA), 0 files for poB
        byte[] zipBytes = mockMvc.perform(get("/api/purchase-orders/batch/zip")
                .param("ids", poA.getId() + "," + poB.getId())
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andReturn().getResponse().getContentAsByteArray();

        assertNotNull(zipBytes);
        assertTrue(zipBytes.length > 0);

        List<String> entryNames = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryNames.add(entry.getName());
                zis.closeEntry();
            }
        }

        assertEquals(1, entryNames.size(), "ZIP bundle must contain exactly 1 PO PDF");
        assertTrue(entryNames.get(0).contains(poA.getPoNumber()), "ZIP entry name must match PO A number");
        assertFalse(entryNames.get(0).contains(poB.getPoNumber()), "PO B leaked into Firm A ZIP bundle!");

        // 5. Customer Statement PDF
        mockMvc.perform(get("/api/statements/customer/" + custA.getId() + "/pdf?firmId=" + firmAId)
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));

        mockMvc.perform(get("/api/statements/customer/" + custB.getId() + "/pdf?firmId=" + firmAId)
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // MUST-HAVE TEST 7: BACKUP / RESTORE TENANT ISOLATION
    // =========================================================================

    @Test
    @DisplayName("MUST-HAVE 7: Single-firm backup payload strict exclusion and restore isolation")
    void test07_backupAndRestoreTenantIsolation() {
        // 1. Single-firm export for Firm A
        BackupDTO backupA = backupService.exportData(firmAId);
        assertNotNull(backupA);

        // Verify Firm A payload contains only Firm A entities
        assertEquals(1, backupA.getCustomers().size());
        assertEquals("Alpha Precision Works", backupA.getCustomers().get(0).getName());
        assertEquals(firmAId, backupA.getCustomers().get(0).getFirmId());

        assertEquals(1, backupA.getProducts().size());
        assertEquals("Alpha Drill Machine", backupA.getProducts().get(0).getName());
        assertEquals(firmAId, backupA.getProducts().get(0).getFirmId());

        assertEquals(1, backupA.getInvoices().size());
        assertEquals("INV-ALPHA-101", backupA.getInvoices().get(0).getInvoiceNumber());
        assertEquals(firmAId, backupA.getInvoices().get(0).getFirmId());

        assertEquals(1, backupA.getPurchaseOrders().size());
        assertEquals("PO-ALPHA-501", backupA.getPurchaseOrders().get(0).getPoNumber());
        assertEquals(firmAId, backupA.getPurchaseOrders().get(0).getFirmId());

        assertEquals(1, backupA.getParties().size());
        assertEquals("Alpha Tooling Supply", backupA.getParties().get(0).getName());
        assertEquals(firmAId, backupA.getParties().get(0).getFirmId());

        // Verify ZERO records belonging to Firm B in backupA
        assertTrue(backupA.getCustomers().stream().noneMatch(c -> firmBId.equals(c.getFirmId())));
        assertTrue(backupA.getProducts().stream().noneMatch(p -> firmBId.equals(p.getFirmId())));
        assertTrue(backupA.getInvoices().stream().noneMatch(i -> firmBId.equals(i.getFirmId())));
        assertTrue(backupA.getPurchaseOrders().stream().noneMatch(po -> firmBId.equals(po.getFirmId())));

        // 2. Single-firm export for Firm B
        BackupDTO backupB = backupService.exportData(firmBId);
        assertNotNull(backupB);
        assertEquals(1, backupB.getCustomers().size());
        assertEquals("Beta Mega Corp", backupB.getCustomers().get(0).getName());
        assertEquals(firmBId, backupB.getCustomers().get(0).getFirmId());
        assertTrue(backupB.getCustomers().stream().noneMatch(c -> firmAId.equals(c.getFirmId())));

        // 3. Restore Firm A in merge mode: Verify Firm B entities in DB remain 100% UNTOUCHED
        Customer liveCustA = customerRepo.findById(custA.getId()).orElseThrow();
        liveCustA.setName("Alpha Modified Prior Restore");
        customerRepo.save(liveCustA);

        backupService.importSelectiveData(backupA, null, "merge", firmAId);

        // Verify Firm B database entities remain unchanged
        Customer freshCustB = customerRepo.findById(custB.getId()).orElseThrow();
        assertEquals("Beta Mega Corp", freshCustB.getName());
        assertEquals(firmBId, freshCustB.getFirmId());

        Product freshProdB = productRepo.findById(prodB.getId()).orElseThrow();
        assertEquals("Beta Hydraulic Pump", freshProdB.getName());
        assertEquals(firmBId, freshProdB.getFirmId());

        Invoice freshInvB = invoiceRepo.findById(invB.getId()).orElseThrow();
        assertEquals("INV-BETA-201", freshInvB.getInvoiceNumber());
        assertEquals(firmBId, freshInvB.getFirmId());

        // 4. All-Firm export: Proves system-wide administrator capability handles both firms
        BackupDTO fullBackup = backupService.exportAllData();
        assertNotNull(fullBackup);
        assertTrue(fullBackup.getCustomers().size() >= 2);
        assertTrue(fullBackup.getProducts().size() >= 2);
        assertTrue(fullBackup.getInvoices().size() >= 2);
    }

    // =========================================================================
    // MUST-HAVE TEST 8: CROSS-FIRM IDOR SWEEP
    // =========================================================================

    @Test
    @DisplayName("MUST-HAVE 8: Systematic cross-firm IDOR sweep across key controllers")
    void test08_crossFirmIdorSweep() throws Exception {
        // Authenticated as Firm A, accessing Firm B entities by direct ID -> must return 404 or 403
        // 1. Product IDOR
        mockMvc.perform(get("/api/products/" + prodB.getId())
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isNotFound());

        // 2. Customer IDOR
        mockMvc.perform(get("/api/customers/" + custB.getId())
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isNotFound());

        // 3. Party IDOR & Financial Summary IDOR
        mockMvc.perform(get("/api/parties/" + vendorB.getId())
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/parties/" + vendorB.getId() + "/financial-summary")
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isNotFound());

        // 4. Invoice IDOR
        mockMvc.perform(get("/api/invoices/" + invB.getId())
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isNotFound());

        // 5. Purchase Order IDOR
        mockMvc.perform(get("/api/purchase-orders/" + poB.getId())
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isNotFound());

        // 6. Employee IDOR
        mockMvc.perform(get("/api/employees/" + empB.getId())
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId)
                .header("X-Employee-Pin", "0000"))
                .andExpect(status().isBadRequest());

        // 7. Firm Details IDOR
        mockMvc.perform(get("/api/firm/" + firmBId)
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isForbidden());

        // 8. Pricing Profile IDOR
        mockMvc.perform(get("/api/pricing-profiles/" + profileB.getId())
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().is4xxClientError());
    }

    // =========================================================================
    // MUST-HAVE TEST 9: END-TO-END TWO-FIRM BUSINESS LIFECYCLE
    // =========================================================================

    @Test
    @DisplayName("MUST-HAVE 9: Complete two-firm business lifecycle execution and boundary verification")
    void test09_endToEndTwoFirmLifecycle() throws Exception {
        // --- FIRM A LIFECYCLE ---
        TenantContext.setCurrentFirmId(firmAId);

        // 1. Invoicing: Sell 10 units of Product A
        InvoiceRequest reqA = new InvoiceRequest();
        reqA.setFirmId(firmAId);
        reqA.setCustomerId(custA.getId());
        reqA.setInvoiceNumber("INV-A-E2E-01");
        reqA.setInvoiceDate(LocalDate.now().toString());
        reqA.setItems(List.of(InvoiceRequestItem.builder()
                .productId(prodA.getId())
                .qty(10)
                .pricePerUnit(new BigDecimal("100.00"))
                .build()));
        Invoice createdInvA = invoiceService.createInvoice(reqA);
        assertNotNull(createdInvA.getId());

        // Stock decreased: 50 - 10 = 40
        Product stockAAfterSale = productRepo.findById(prodA.getId()).orElseThrow();
        assertEquals(new BigDecimal("40.000"), stockAAfterSale.getStockQuantity());

        // 2. Payment: Settle created invoice
        invoiceService.recordPayment(createdInvA.getId(), new BigDecimal("1000.00"), LocalDate.now(), "CASH", "TXN-A-01", "Full settlement");

        // 3. Sales Return: Customer returns 2 units
        SalesReturnRequest retReqA = new SalesReturnRequest();
        retReqA.setFirmId(firmAId);
        retReqA.setInvoiceId(createdInvA.getId());
        retReqA.setRefundMode("CASH");
        retReqA.setItems(List.of(SalesReturnRequest.SalesReturnItemRequest.builder()
                .productId(prodA.getId())
                .returnQty(2)
                .unitPrice(new BigDecimal("100.00"))
                .build()));
        invoiceService.createSalesReturn(createdInvA.getId(), retReqA);

        // Stock restored: 40 + 2 = 42
        Product stockAAfterReturn = productRepo.findById(prodA.getId()).orElseThrow();
        assertEquals(new BigDecimal("42.000"), stockAAfterReturn.getStockQuantity());

        // --- FIRM B LIFECYCLE (Distinct parameters) ---
        TenantContext.setCurrentFirmId(firmBId);

        InvoiceRequest reqB = new InvoiceRequest();
        reqB.setFirmId(firmBId);
        reqB.setCustomerId(custB.getId());
        reqB.setInvoiceNumber("INV-B-E2E-01");
        reqB.setInvoiceDate(LocalDate.now().toString());
        reqB.setItems(List.of(InvoiceRequestItem.builder()
                .productId(prodB.getId())
                .qty(20)
                .pricePerUnit(new BigDecimal("1000.00"))
                .build()));
        Invoice createdInvB = invoiceService.createInvoice(reqB);

        // Stock decreased: 80 - 20 = 60
        Product stockBAfterSale = productRepo.findById(prodB.getId()).orElseThrow();
        assertEquals(new BigDecimal("60.000"), stockBAfterSale.getStockQuantity());

        // Payment: Partial settlement of ₹10,000.00
        invoiceService.recordPayment(createdInvB.getId(), new BigDecimal("10000.00"), LocalDate.now(), "BANK_TRANSFER", "TXN-B-01", "Partial settlement");

        // --- ISOLATION ASSERTIONS FROM BOTH CONTEXTS ---
        // Firm A views
        TenantContext.setCurrentFirmId(firmAId);
        List<Invoice> firmAInvoices = invoiceRepo.findByFirmId(firmAId);
        assertEquals(2, firmAInvoices.size()); // invA + createdInvA

        Product finalProdA = productRepo.findById(prodA.getId()).orElseThrow();
        assertEquals(new BigDecimal("42.000"), finalProdA.getStockQuantity());

        // Firm B views
        TenantContext.setCurrentFirmId(firmBId);
        List<Invoice> firmBInvoices = invoiceRepo.findByFirmId(firmBId);
        assertEquals(2, firmBInvoices.size()); // invB + createdInvB

        Product finalProdB = productRepo.findById(prodB.getId()).orElseThrow();
        assertEquals(new BigDecimal("60.000"), finalProdB.getStockQuantity());
    }

    // =========================================================================
    // PHASE 4 TEST: GLOBAL VS TENANT-SCOPED BEHAVIOR
    // =========================================================================

    @Test
    @DisplayName("Phase 4: Global SNAKE_GAME_STATE shared globally vs. tenant-scoped HR PINs")
    void test10_globalVsTenantScopedBehavior() throws Exception {
        // 1. Global SNAKE_GAME_STATE: Saved without firm header
        String gameStatePayload = "{\"highScore\":1250,\"level\":5}";
        mockMvc.perform(put("/api/app-config/SNAKE_GAME_STATE")
                .header("X-Auth-Token", tokenFirmA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Collections.singletonMap("value", gameStatePayload))))
                .andExpect(status().isOk());

        // Readable under Firm A context
        mockMvc.perform(get("/api/app-config/SNAKE_GAME_STATE")
                .header("X-Auth-Token", tokenFirmA)
                .header("X-Firm-Id", firmAId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(gameStatePayload));

        // Readable under Firm B context (exact same global value)
        mockMvc.perform(get("/api/app-config/SNAKE_GAME_STATE")
                .header("X-Auth-Token", tokenFirmB)
                .header("X-Firm-Id", firmBId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(gameStatePayload));

        // 2. Tenant-scoped HR Security PIN: Firm A and Firm B PINs remain completely isolated
        AppConfig pinA = new AppConfig();
        pinA.setConfigKey("EMPLOYEE_MODULE_PIN_" + firmAId);
        pinA.setConfigValue("1111");
        configRepo.save(pinA);

        AppConfig pinB = new AppConfig();
        pinB.setConfigKey("EMPLOYEE_MODULE_PIN_" + firmBId);
        pinB.setConfigValue("2222");
        configRepo.save(pinB);

        assertEquals("1111", configRepo.findById("EMPLOYEE_MODULE_PIN_" + firmAId).orElseThrow().getConfigValue());
        assertEquals("2222", configRepo.findById("EMPLOYEE_MODULE_PIN_" + firmBId).orElseThrow().getConfigValue());
    }

    // =========================================================================
    // PHASE 6 TEST: CONCURRENCY / TENANT CONTEXT SAFETY
    // =========================================================================

    @Test
    @DisplayName("Phase 6: Multi-threaded interleaved execution (10 threads) maintains ThreadLocal isolation")
    void test11_concurrentThreadTenantContextSafety() throws Exception {
        int threadCount = 10;
        int taskCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<Void>> futures = new ArrayList<>();

        for (int i = 0; i < taskCount; i++) {
            final boolean isFirmA = (i % 2 == 0);
            final Long expectedFirmId = isFirmA ? firmAId : firmBId;
            final Long expectedCustId = isFirmA ? custA.getId() : custB.getId();
            final Long otherCustId = isFirmA ? custB.getId() : custA.getId();

            futures.add(executor.submit(() -> {
                try {
                    // 1. Thread enters with clean context
                    assertNull(TenantContext.getCurrentFirmId(), "Thread entered with dirty context");

                    // 2. Set authoritative firm ID
                    TenantContext.setCurrentFirmId(expectedFirmId);
                    assertEquals(expectedFirmId, TenantContext.getCurrentFirmId());

                    // 3. Query own firm records
                    Optional<Customer> ownCust = customerRepo.findByIdAndFirmId(expectedCustId, expectedFirmId);
                    assertTrue(ownCust.isPresent(), "Failed to read own firm customer");
                    assertEquals(expectedFirmId, ownCust.get().getFirmId());

                    // 4. Query other firm records -> must be absent
                    Optional<Customer> crossCust = customerRepo.findByIdAndFirmId(otherCustId, expectedFirmId);
                    assertTrue(crossCust.isEmpty(), "Cross-firm customer access succeeded unexpectedly!");

                    return null;
                } finally {
                    TenantContext.clear();
                    assertNull(TenantContext.getCurrentFirmId());
                }
            }));
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

        for (Future<Void> f : futures) {
            f.get();
        }
    }
}
