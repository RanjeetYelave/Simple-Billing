package com.billing.simple.billsoft.security;

import com.billing.simple.billsoft.controllers.AuthController;
import com.billing.simple.billsoft.dtos.CustomerRequest;
import com.billing.simple.billsoft.dtos.PricingProfileDto;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.repositories.PartyRepository;
import com.billing.simple.billsoft.repositories.PurchaseOrderRepository;
import com.billing.simple.billsoft.service.*;
import com.billing.simple.billsoft.service.impl.PartyServiceImpl;
import com.billing.simple.billsoft.service.impl.PurchaseOrderServiceImpl;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DisplayName("Group 1 Security Foundation Integration & Hardening Tests")
public class Group1SecurityFoundationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppConfigRepository appConfigRepo;

    @Autowired
    private FirmDetailsRepository firmRepo;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private PartyRepository partyRepo;

    @Autowired
    private PurchaseOrderRepository poRepo;

    @Autowired
    private PricingProfileRepository profileRepo;

    @Autowired
    private StockMovementRepository stockMovementRepo;

    @Autowired
    private InvoiceRepository invoiceRepo;

    @Autowired
    private AuthController authController;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private ProductService productService;

    @Autowired
    private PartyService partyService;

    @Autowired
    private PurchaseOrderService poService;

    @Autowired
    private ProductPricingService pricingService;

    @Autowired
    private InvoiceMigrationService invoiceMigrationService;

    private Long firm1Id;
    private Long firm2Id;
    private String authToken;

    @BeforeEach
    void setUp() {
        appConfigRepo.deleteAll();
        invoiceRepo.deleteAll();
        stockMovementRepo.deleteAll();
        poRepo.deleteAll();
        partyRepo.deleteAll();
        productRepo.deleteAll();
        customerRepo.deleteAll();
        profileRepo.deleteAll();
        firmRepo.deleteAll();

        // Setup Firm 1
        FirmDetails f1 = new FirmDetails();
        f1.setFirmName("Firm One");
        f1 = firmRepo.save(f1);
        firm1Id = f1.getId();

        // Setup Firm 2
        FirmDetails f2 = new FirmDetails();
        f2.setFirmName("Firm Two");
        f2 = firmRepo.save(f2);
        firm2Id = f2.getId();

        // Setup authenticated session
        Map<String, String> enableReq = new HashMap<>();
        enableReq.put("password", "Test@1234");
        authToken = (String) authController.enableAuth(enableReq).getBody().get("token");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        authController.invalidateAllSessions();
        invoiceRepo.deleteAll();
        stockMovementRepo.deleteAll();
        poRepo.deleteAll();
        partyRepo.deleteAll();
        productRepo.deleteAll();
        customerRepo.deleteAll();
        profileRepo.deleteAll();
        firmRepo.deleteAll();
        appConfigRepo.deleteAll();
    }

    // =========================================================================
    // AUDIT-035: AppConfig Security & SNAKE_GAME_STATE Global Exception
    // =========================================================================

    @Test
    @DisplayName("AUDIT-035: Sensitive keys are blocked from generic read/write/delete")
    void audit035_sensitiveKeysBlocked() throws Exception {
        // 1. global_password
        mockMvc.perform(get("/api/app-config/global_password")
                .header("X-Auth-Token", authToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());

        mockMvc.perform(put("/api/app-config/global_password")
                .header("X-Auth-Token", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":\"hacked\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/app-config/global_password")
                .header("X-Auth-Token", authToken))
                .andExpect(status().isForbidden());

        // 2. session_* keys
        mockMvc.perform(get("/api/app-config/session_" + authToken)
                .header("X-Auth-Token", authToken))
                .andExpect(status().isForbidden());

        // 3. backup_cloud_credentials
        mockMvc.perform(get("/api/app-config/backup_cloud_credentials")
                .header("X-Auth-Token", authToken))
                .andExpect(status().isForbidden());

        // 4. PIN keys
        mockMvc.perform(get("/api/app-config/EMPLOYEE_MODULE_PIN")
                .header("X-Auth-Token", authToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AUDIT-035: auth_enabled cannot be modified or deleted via generic AppConfig endpoint")
    void audit035_authEnabledGenericMutationBlocked() throws Exception {
        mockMvc.perform(put("/api/app-config/auth_enabled")
                .header("X-Auth-Token", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":\"false\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("auth_enabled cannot be modified via generic configuration endpoint; use /api/auth/enable or /api/auth/disable"));

        mockMvc.perform(delete("/api/app-config/auth_enabled")
                .header("X-Auth-Token", authToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AUDIT-035: Sequence counters are blocked from generic mutation")
    void audit035_sequenceCountersBlocked() throws Exception {
        mockMvc.perform(put("/api/app-config/LAST_INVOICE_SEQ_1")
                .header("X-Auth-Token", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":\"999\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/app-config/LAST_INVOICE_SEQ_1")
                .header("X-Auth-Token", authToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AUDIT-035: SNAKE_GAME_STATE is accessible without X-Firm-Id and shared globally")
    void audit035_snakeGameStateIsGlobalAndAccessibleWithoutFirmId() throws Exception {
        // Save Snake state without X-Firm-Id
        String gameState = "{\"highScore\":250,\"longestSnake\":15}";
        mockMvc.perform(put("/api/app-config/SNAKE_GAME_STATE")
                .header("X-Auth-Token", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Collections.singletonMap("value", gameState))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true));

        // Read under Firm 1 context
        mockMvc.perform(get("/api/app-config/SNAKE_GAME_STATE")
                .header("X-Auth-Token", authToken)
                .header("X-Firm-Id", firm1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(gameState));

        // Read under Firm 2 context -> must be the exact same global state
        mockMvc.perform(get("/api/app-config/SNAKE_GAME_STATE")
                .header("X-Auth-Token", authToken)
                .header("X-Firm-Id", firm2Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(gameState));

        // Read without any firm header
        mockMvc.perform(get("/api/app-config/SNAKE_GAME_STATE")
                .header("X-Auth-Token", authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(gameState));
    }

    // =========================================================================
    // AUDIT-034: Firm Details Isolation & Bootstrap
    // =========================================================================

    @Test
    @DisplayName("AUDIT-034: Cross-firm CRUD on FirmDetails is strictly prohibited")
    void audit034_crossFirmProtection() throws Exception {
        // 1. Cross-firm GET /api/firm/{id}
        mockMvc.perform(get("/api/firm/" + firm2Id)
                .header("X-Auth-Token", authToken)
                .header("X-Firm-Id", firm1Id))
                .andExpect(status().isForbidden());

        // 2. Same-firm GET /api/firm/{id}
        mockMvc.perform(get("/api/firm/" + firm1Id)
                .header("X-Auth-Token", authToken)
                .header("X-Firm-Id", firm1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firmName").value("Firm One"));

        // 3. Cross-firm PUT /api/firm/{id}
        FirmDetails updatePayload = new FirmDetails();
        updatePayload.setFirmName("Firm One Hacked");
        mockMvc.perform(put("/api/firm/" + firm2Id)
                .header("X-Auth-Token", authToken)
                .header("X-Firm-Id", firm1Id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatePayload)))
                .andExpect(status().isForbidden());

        // 4. Same-firm PUT /api/firm/{id}
        updatePayload.setFirmName("Firm One Renamed");
        mockMvc.perform(put("/api/firm/" + firm1Id)
                .header("X-Auth-Token", authToken)
                .header("X-Firm-Id", firm1Id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatePayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firmName").value("Firm One Renamed"));

        // 5. Cross-firm PATCH print preferences
        mockMvc.perform(patch("/api/firm/" + firm2Id + "/print-preferences")
                .header("X-Auth-Token", authToken)
                .header("X-Firm-Id", firm1Id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"theme\":\"MODERN\",\"color\":\"#000000\",\"format\":\"A4\"}"))
                .andExpect(status().is4xxClientError());

        // 6. Cross-firm DELETE
        mockMvc.perform(delete("/api/firm/" + firm2Id)
                .header("X-Auth-Token", authToken)
                .header("X-Firm-Id", firm1Id))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("AUDIT-034: Deleting the only configured firm is rejected")
    void audit034_deleteOnlyFirmRejected() throws Exception {
        firmRepo.deleteById(firm2Id);
        assertEquals(1, firmRepo.count());

        mockMvc.perform(delete("/api/firm/" + firm1Id)
                .header("X-Auth-Token", authToken)
                .header("X-Firm-Id", firm1Id)
                .header("X-Master-Password", "Saidarshan*1"))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // AUDIT-001: BackupController Hardening
    // =========================================================================

    @Test
    @DisplayName("AUDIT-001: Single-firm export enforces tenant ownership")
    void audit001_singleFirmExportTenantIsolation() throws Exception {
        // Cross-firm export attempt: Firm 1 attempts to export Firm 2
        mockMvc.perform(get("/api/backup/export")
                .header("X-Auth-Token", authToken)
                .header("X-Firm-Id", firm1Id)
                .param("firmId", String.valueOf(firm2Id)))
                .andExpect(status().isForbidden());

        // Same-firm export attempt: Firm 1 exports Firm 1
        mockMvc.perform(get("/api/backup/export")
                .header("X-Auth-Token", authToken)
                .header("X-Firm-Id", firm1Id)
                .param("firmId", String.valueOf(firm1Id)))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Disposition"));
    }

    @Test
    @DisplayName("AUDIT-001: Export-all requires master password authorization")
    void audit001_exportAllRequiresMasterPassword() throws Exception {
        // Attempt without master password -> 403 Forbidden
        mockMvc.perform(get("/api/backup/export/all")
                .header("X-Auth-Token", authToken))
                .andExpect(status().isForbidden());

        // Attempt with master password header -> 200 OK
        mockMvc.perform(get("/api/backup/export/all")
                .header("X-Auth-Token", authToken)
                .header("X-Master-Password", "Saidarshan*1"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Disposition"));
    }

    // =========================================================================
    // AUDIT-021: DataProtection Hardening
    // =========================================================================

    @Test
    @DisplayName("AUDIT-021: DataProtection status masks credentials and restore requires master key")
    void audit021_dataProtectionHardening() throws Exception {
        // Status works for authenticated session and removes credentials
        mockMvc.perform(get("/api/dataprotection/status")
                .header("X-Auth-Token", authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secretKey").doesNotExist())
                .andExpect(jsonPath("$.accessKey").doesNotExist());

        // Cloud restore without master password -> 403 Forbidden
        mockMvc.perform(post("/api/dataprotection/restore-cloud")
                .header("X-Auth-Token", authToken)
                .param("machineId", "test-mac")
                .param("licenseId", "test-lic"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Cloud restore requires master password authorization"));

        // Cloud restore targeting another firm -> 403 Forbidden
        mockMvc.perform(post("/api/dataprotection/restore-cloud")
                .header("X-Auth-Token", authToken)
                .header("X-Firm-Id", firm1Id)
                .header("X-Master-Password", "Saidarshan*1")
                .param("machineId", "test-mac")
                .param("licenseId", "test-lic")
                .param("targetFirmId", String.valueOf(firm2Id)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // AUDIT-027: Optimistic Locking on Core Mutable Entities
    // =========================================================================

    @Test
    @DisplayName("AUDIT-027: Product optimistic locking detects concurrent stale updates (HTTP 409)")
    void audit027_productOptimisticLocking() throws Exception {
        TenantContext.setCurrentFirmId(firm1Id);
        Product p = Product.builder()
                .name("Keyboard")
                .price(new BigDecimal("500.00"))
                .firmId(firm1Id)
                .stockQuantity(BigDecimal.TEN)
                .build();
        p = productRepo.save(p);
        assertEquals(0L, p.getVersion());

        // Successful update increments version to 1
        p.setPrice(new BigDecimal("550.00"));
        p = productService.update(p.getId(), p);
        assertEquals(1L, p.getVersion());

        // Stale update (using version 0) fails closed with HTTP 409
        Product stale = Product.builder()
                .name("Keyboard Stale")
                .price(new BigDecimal("600.00"))
                .version(0L)
                .build();

        mockMvc.perform(put("/api/products/" + p.getId())
                .header("X-Auth-Token", authToken)
                .header("X-Firm-Id", firm1Id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(stale)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Record was modified by another operation. Please reload and try again."));
    }

    @Test
    @DisplayName("AUDIT-027: Customer optimistic locking detects concurrent stale updates (HTTP 409)")
    void audit027_customerOptimisticLocking() throws Exception {
        TenantContext.setCurrentFirmId(firm1Id);
        Customer c = Customer.builder()
                .name("John Doe")
                .phone("9876543210")
                .firmId(firm1Id)
                .build();
        c = customerRepo.save(c);
        assertEquals(0L, c.getVersion());

        // Successful update increments version to 1
        CustomerRequest req1 = new CustomerRequest();
        req1.setName("Johnathan Doe");
        req1.setVersion(0L);
        c = customerService.update(c.getId(), req1);
        assertEquals(1L, c.getVersion());

        // Stale update with version 0 -> 409 Conflict
        CustomerRequest staleReq = new CustomerRequest();
        staleReq.setName("Stale Name");
        staleReq.setVersion(0L);

        mockMvc.perform(put("/api/customers/" + c.getId())
                .header("X-Auth-Token", authToken)
                .header("X-Firm-Id", firm1Id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(staleReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Record was modified by another operation. Please reload and try again."));
    }

    @Test
    @DisplayName("AUDIT-027: Party optimistic locking detects concurrent stale updates (HTTP 409)")
    void audit027_partyOptimisticLocking() throws Exception {
        TenantContext.setCurrentFirmId(firm1Id);
        Party party = Party.builder()
                .name("Supplier Apex")
                .firmId(firm1Id)
                .build();
        party = partyRepo.save(party);
        assertEquals(0L, party.getVersion());

        // Successful update increments version to 1
        Party update1 = Party.builder()
                .name("Supplier Apex Renamed")
                .firmId(firm1Id)
                .version(0L)
                .build();
        party = partyService.updateParty(party.getId(), update1);
        assertEquals(1L, party.getVersion());

        // Stale update with version 0 -> 409 Conflict
        Party staleParty = Party.builder()
                .name("Supplier Stale")
                .firmId(firm1Id)
                .version(0L)
                .build();

        mockMvc.perform(put("/api/parties/" + party.getId())
                .header("X-Auth-Token", authToken)
                .header("X-Firm-Id", firm1Id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(staleParty)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Record was modified by another operation. Please reload and try again."));
    }

    @Test
    @DisplayName("AUDIT-027: PurchaseOrder optimistic locking detects concurrent stale updates (HTTP 409)")
    void audit027_purchaseOrderOptimisticLocking() throws Exception {
        TenantContext.setCurrentFirmId(firm1Id);
        Party party = partyRepo.save(Party.builder().name("Vendor 1").firmId(firm1Id).build());

        PurchaseOrder po = PurchaseOrder.builder()
                .poNumber("PO-2026-001")
                .poDate(LocalDate.now())
                .party(party)
                .firmId(firm1Id)
                .status(PurchaseOrderStatus.DRAFT)
                .build();
        po = poRepo.save(po);
        assertEquals(0L, po.getVersion());

        // Successful update increments version to 1
        po.setVersion(0L);
        po = poService.updatePurchaseOrder(po.getId(), po);
        assertEquals(1L, po.getVersion());

        // Stale update with version 0 -> 409 Conflict
        PurchaseOrder stalePO = PurchaseOrder.builder()
                .poNumber("PO-2026-001")
                .firmId(firm1Id)
                .version(0L)
                .build();

        mockMvc.perform(put("/api/purchase-orders/" + po.getId())
                .header("X-Auth-Token", authToken)
                .header("X-Firm-Id", firm1Id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(stalePO)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Record was modified by another operation. Please reload and try again."));
    }

    @Test
    @DisplayName("AUDIT-027: PricingProfile optimistic locking detects concurrent stale updates (HTTP 409)")
    void audit027_pricingProfileOptimisticLocking() throws Exception {
        TenantContext.setCurrentFirmId(firm1Id);
        PricingProfile pp = PricingProfile.builder()
                .name("Retail Tier")
                .code("RETAIL_1")
                .firmId(firm1Id)
                .build();
        pp = profileRepo.save(pp);
        assertEquals(0L, pp.getVersion());

        // Successful update increments version to 1
        PricingProfileDto dto = PricingProfileDto.builder()
                .name("Retail Tier Updated")
                .code("RETAIL_1")
                .version(0L)
                .build();
        PricingProfileDto updated = pricingService.updateProfile(pp.getId(), dto, firm1Id);
        assertEquals(1L, updated.getVersion());

        // Stale update with version 0 -> 409 Conflict
        PricingProfileDto staleDto = PricingProfileDto.builder()
                .name("Retail Stale")
                .code("RETAIL_1")
                .version(0L)
                .build();

        mockMvc.perform(put("/api/pricing-profiles/" + pp.getId())
                .header("X-Auth-Token", authToken)
                .header("X-Firm-Id", firm1Id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(staleDto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Record was modified by another operation. Please reload and try again."));
    }

    @Test
    @DisplayName("AUDIT-027: StockMovement remains append-only audit ledger without @Version")
    void audit027_stockMovementRemainsAppendOnly() {
        StockMovement sm = StockMovement.builder()
                .firmId(firm1Id)
                .productId(100L)
                .productName("Widget")
                .movementType("MANUAL_ADJUSTMENT")
                .quantityChange(new BigDecimal("5.000"))
                .newStock(new BigDecimal("15.000"))
                .build();
        StockMovement saved = stockMovementRepo.save(sm);
        assertNotNull(saved.getId());
        assertEquals(1, stockMovementRepo.count());
    }

    // =========================================================================
    // Tenant Boundary Sweep: InvoiceMigration Cross-Firm Verification
    // =========================================================================

    @Test
    @DisplayName("Tenant Boundary: Cross-firm invoice migration is rejected")
    void tenantBoundary_crossFirmInvoiceMigrationBlocked() {
        // Invoice belongs to Firm 2
        Customer cust2 = customerRepo.save(Customer.builder().name("Firm 2 Cust").firmId(firm2Id).build());
        Invoice invFirm2 = new Invoice();
        invFirm2.setFirmId(firm2Id);
        invFirm2.setCustomer(cust2);
        invFirm2.setInvoiceNumber("INV-FIRM2-01");
        invFirm2 = invoiceRepo.save(invFirm2);

        // Caller is set to Firm 1
        TenantContext.setCurrentFirmId(firm1Id);

        com.billing.simple.billsoft.dtos.migration.InvoiceMigrationDTOs.InvoiceMigrationRequest req =
                new com.billing.simple.billsoft.dtos.migration.InvoiceMigrationDTOs.InvoiceMigrationRequest();
        req.setTargetFirmId(firm1Id);

        final Long targetInvoiceId = invFirm2.getId();
        assertThrows(TenantSecurityException.class, () -> {
            invoiceMigrationService.migrateInvoice(targetInvoiceId, req);
        });
    }

    // =========================================================================
    // AUDIT-027: Detailed Version Optionality Tests (A: correct, B: stale, C: missing)
    // =========================================================================

    @Test
    @DisplayName("AUDIT-027: Product version optionality (A: correct -> 200, B: stale -> 409, C: missing -> 200)")
    void audit027_productVersionOptionality() throws Exception {
        TenantContext.setCurrentFirmId(firm1Id);
        Product p = productRepo.save(Product.builder().name("Prod Opt").price(new BigDecimal("100.00")).firmId(firm1Id).build());
        assertEquals(0L, p.getVersion());

        // A. Correct version (0) -> 200 OK, increments to 1
        Product updateA = Product.builder().name("Prod A").price(new BigDecimal("110.00")).version(0L).build();
        mockMvc.perform(put("/api/products/" + p.getId())
                .header("X-Auth-Token", authToken)
                .header("X-Firm-Id", firm1Id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        // B. Stale version (0) -> 409 Conflict
        Product staleB = Product.builder().name("Prod B").price(new BigDecimal("120.00")).version(0L).build();
        mockMvc.perform(put("/api/products/" + p.getId())
                .header("X-Auth-Token", authToken)
                .header("X-Firm-Id", firm1Id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(staleB)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Record was modified by another operation. Please reload and try again."));

        // C. Missing version (null) -> 200 OK (backwards compatible), increments to 2
        Product missingC = Product.builder().name("Prod C").price(new BigDecimal("130.00")).build();
        missingC.setVersion(null);
        assertNull(missingC.getVersion());
        mockMvc.perform(put("/api/products/" + p.getId())
                .header("X-Auth-Token", authToken)
                .header("X-Firm-Id", firm1Id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(missingC)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    @DisplayName("AUDIT-027: Customer version optionality (A: correct -> 200, B: stale -> 409, C: missing -> 200)")
    void audit027_customerVersionOptionality() throws Exception {
        TenantContext.setCurrentFirmId(firm1Id);
        Customer c = customerRepo.save(Customer.builder().name("Cust Opt").firmId(firm1Id).build());
        assertEquals(0L, c.getVersion());

        // A. Correct version (0) -> 200 OK, increments to 1
        CustomerRequest reqA = new CustomerRequest();
        reqA.setName("Cust A");
        reqA.setVersion(0L);
        mockMvc.perform(put("/api/customers/" + c.getId())
                .header("X-Auth-Token", authToken)
                .header("X-Firm-Id", firm1Id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        // B. Stale version (0) -> 409 Conflict
        CustomerRequest reqB = new CustomerRequest();
        reqB.setName("Cust B");
        reqB.setVersion(0L);
        mockMvc.perform(put("/api/customers/" + c.getId())
                .header("X-Auth-Token", authToken)
                .header("X-Firm-Id", firm1Id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqB)))
                .andExpect(status().isConflict());

        // C. Missing version (null) -> 200 OK, increments to 2
        CustomerRequest reqC = new CustomerRequest();
        reqC.setName("Cust C");
        mockMvc.perform(put("/api/customers/" + c.getId())
                .header("X-Auth-Token", authToken)
                .header("X-Firm-Id", firm1Id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqC)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));
    }

    // =========================================================================
    // AUDIT-021: Complete 4 Endpoints Authorization Matrix
    // =========================================================================

    @Test
    @DisplayName("AUDIT-021: All 4 DataProtection endpoints validated across unauth, normal user, privileged")
    void audit021_allEndpointsSecurityMatrix() throws Exception {
        // 1. GET /api/dataprotection/status
        // Unauthenticated -> 401
        mockMvc.perform(get("/api/dataprotection/status"))
                .andExpect(status().isUnauthorized());
        // Normal authenticated -> 200 (sanitized)
        mockMvc.perform(get("/api/dataprotection/status")
                .header("X-Auth-Token", authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secretKey").doesNotExist())
                .andExpect(jsonPath("$.accessKey").doesNotExist());

        // 2. POST /api/dataprotection/backup-now
        // Unauthenticated -> 401
        mockMvc.perform(post("/api/dataprotection/backup-now"))
                .andExpect(status().isUnauthorized());
        // Normal authenticated without master password -> 403
        mockMvc.perform(post("/api/dataprotection/backup-now")
                .header("X-Auth-Token", authToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Triggering cloud backup requires privileged authorization"));
        // Privileged (master password) -> 200
        mockMvc.perform(post("/api/dataprotection/backup-now")
                .header("X-Auth-Token", authToken)
                .header("X-Master-Password", "Saidarshan*1"))
                .andExpect(status().isOk());

        // 3. GET/POST /api/dataprotection/inspect-cloud
        // Unauthenticated -> 401
        mockMvc.perform(get("/api/dataprotection/inspect-cloud"))
                .andExpect(status().isUnauthorized());
        // Normal authenticated without master password -> 403
        mockMvc.perform(get("/api/dataprotection/inspect-cloud")
                .header("X-Auth-Token", authToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/dataprotection/inspect-cloud")
                .header("X-Auth-Token", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"machineId\":\"m1\",\"licenseId\":\"lic1\"}"))
                .andExpect(status().isForbidden());

        // 4. POST /api/dataprotection/restore-cloud
        // Unauthenticated -> 401
        mockMvc.perform(post("/api/dataprotection/restore-cloud")
                .param("machineId", "m1")
                .param("licenseId", "lic1"))
                .andExpect(status().isUnauthorized());
        // Normal authenticated without master password -> 403
        mockMvc.perform(post("/api/dataprotection/restore-cloud")
                .header("X-Auth-Token", authToken)
                .param("machineId", "m1")
                .param("licenseId", "lic1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Cloud restore requires master password authorization"));
    }

    // =========================================================================
    // AUDIT-001: Complete Backup Endpoints Validation
    // =========================================================================

    @Test
    @DisplayName("AUDIT-001: All backup endpoints validated (/export/all, /auto/configure, /auto/trigger, /restore/all)")
    void audit001_allBackupEndpointsSecurityMatrix() throws Exception {
        // 1. /export/all
        // Normal user -> 403
        mockMvc.perform(get("/api/backup/export/all")
                .header("X-Auth-Token", authToken))
                .andExpect(status().isForbidden());
        // Privileged -> 200
        mockMvc.perform(get("/api/backup/export/all")
                .header("X-Auth-Token", authToken)
                .header("X-Master-Password", "Saidarshan*1"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Disposition"));

        // 2. /auto/trigger (or /auto/run-now)
        // Normal user -> 403
        mockMvc.perform(post("/api/backup/auto/trigger")
                .header("X-Auth-Token", authToken))
                .andExpect(status().isForbidden());
        // Privileged -> 200
        mockMvc.perform(post("/api/backup/auto/trigger")
                .header("X-Auth-Token", authToken)
                .header("X-Master-Password", "Saidarshan*1"))
                .andExpect(status().isOk());

        // 3. /auto/configure
        // Normal user -> 403
        mockMvc.perform(post("/api/backup/auto/configure")
                .header("X-Auth-Token", authToken))
                .andExpect(status().isForbidden());
        // Privileged -> 200
        mockMvc.perform(post("/api/backup/auto/configure")
                .header("X-Auth-Token", authToken)
                .header("X-Master-Password", "Saidarshan*1"))
                .andExpect(status().isOk());

        // 4. /upload-restore/firm cross-firm check
        org.springframework.mock.web.MockMultipartFile mockFile =
                new org.springframework.mock.web.MockMultipartFile("file", "test.json", "application/json", "{}".getBytes());
        mockMvc.perform(multipart("/api/backup/upload-restore/firm")
                .file(mockFile)
                .param("firmId", String.valueOf(firm2Id))
                .header("X-Auth-Token", authToken)
                .header("X-Firm-Id", firm1Id))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Cross-firm backup import prohibited"));

        // 5. /restore/all requires master password
        mockMvc.perform(multipart("/api/backup/restore/all")
                .file(mockFile)
                .header("X-Auth-Token", authToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("System-wide restore requires privileged authorization"));
    }
}
