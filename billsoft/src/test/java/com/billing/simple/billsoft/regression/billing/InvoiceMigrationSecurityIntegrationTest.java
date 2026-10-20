package com.billing.simple.billsoft.regression.billing;

import com.billing.simple.billsoft.controllers.AuthController;
import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceRequestItem;
import com.billing.simple.billsoft.dtos.migration.InvoiceMigrationDTOs.InvoiceMigrationRequest;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.forecast.repository.ForecastSalesTransactionRepository;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.service.InvoiceService;
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
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Group 9 — Automated Regression Tests for Multi-Firm Invoice Migration Security Hardening.
 *
 * Covers all 8 mandatory integration scenarios:
 * 1. Migration request without X-Firm-Id (HTTP 400, no state change).
 * 2. Revert request without X-Firm-Id (HTTP 400, no state change).
 * 3. Migration with a firm different from the source invoice's firm (HTTP 403, no state change).
 * 4. Preview of another firm's invoice (HTTP 404, unscoped leak prevention).
 * 5. History lookup for another firm's invoice (HTTP 404, metadata leak prevention).
 * 6. Revert attempt against another firm's invoice or migration (HTTP 403, no state change).
 * 7. A valid same-firm migration and revert still work (HTTP 200 OK round-trip).
 * 8. An authenticated user cannot gain cross-firm access merely by changing X-Firm-Id (fails closed).
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DisplayName("Group 9 — Invoice Migration Security Hardening Integration Tests")
public class InvoiceMigrationSecurityIntegrationTest {

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
    private StockMovementRepository stockMovementRepo;

    @Autowired
    private InvoicePaymentRepository invoicePaymentRepo;

    @Autowired
    private SalesReturnRepository salesReturnRepo;

    @Autowired
    private InvoiceMigrationRecordRepository migrationRecordRepo;

    @Autowired
    private ForecastSalesTransactionRepository forecastTransactionRepo;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private AuthController authController;

    @Autowired
    private AppConfigRepository appConfigRepo;

    @Autowired
    private ReminderRepository reminderRepo;

    private Long firmAId;
    private Long firmBId;
    private Long firmCId;

    private Customer customerA;
    private Product productA;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        migrationRecordRepo.deleteAll();
        invoicePaymentRepo.deleteAll();
        salesReturnRepo.deleteAll();
        stockMovementRepo.deleteAll();
        invoiceRepo.deleteAll();
        customerRepo.deleteAll();
        productRepo.deleteAll();
        firmRepo.deleteAll();

        FirmDetails firmA = new FirmDetails();
        firmA.setFirmName("Firm Alpha Security");
        firmA.setGstin("27AAAAA1111A1Z1");
        firmA = firmRepo.save(firmA);

        FirmDetails firmB = new FirmDetails();
        firmB.setFirmName("Firm Beta Security");
        firmB.setGstin("27BBBBB2222B1Z2");
        firmB = firmRepo.save(firmB);

        FirmDetails firmC = new FirmDetails();
        firmC.setFirmName("Firm Gamma Security");
        firmC.setGstin("27CCCCC3333C1Z3");
        firmC = firmRepo.save(firmC);

        firmAId = firmA.getId();
        firmBId = firmB.getId();
        firmCId = firmC.getId();

        TenantContext.setCurrentFirmId(firmAId);

        customerA = customerRepo.save(Customer.builder()
                .name("Alpha Security Client")
                .phone("9876543210")
                .gstin("27SEC1234F1Z9")
                .address("Mumbai, MH")
                .firmId(firmAId)
                .build());

        productA = productRepo.save(Product.builder()
                .name("Secure Widget")
                .sku("SEC-WID-01")
                .price(new BigDecimal("500.00"))
                .stockQuantity(new BigDecimal("100.000"))
                .firmId(firmAId)
                .itemType("GOODS")
                .build());

        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Invoice createTestInvoiceInFirmA() {
        TenantContext.setCurrentFirmId(firmAId);
        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(customerA.getId());
        req.setInvoiceDate(LocalDateTime.now().toString());
        req.setStatus(InvoiceStatus.UNPAID);
        req.setPaid(false);

        InvoiceRequestItem item = new InvoiceRequestItem();
        item.setProductId(productA.getId());
        item.setProductName(productA.getName());
        item.setQty(10);
        item.setPricePerUnit(new BigDecimal("500.00"));
        item.setGstPercent(new BigDecimal("18.00"));
        req.setItems(Collections.singletonList(item));

        Invoice created = invoiceService.createInvoice(req);
        TenantContext.clear();
        return created;
    }

    // =========================================================================
    // 1. Migration request without X-Firm-Id
    // =========================================================================
    @Test
    @DisplayName("Scenario 1: Migration request without X-Firm-Id fails with HTTP 400 Bad Request")
    void testMigrationRequest_WithoutXFirmId_Returns400_StateUnchanged() throws Exception {
        Invoice invoice = createTestInvoiceInFirmA();
        Long invId = invoice.getId();
        long initialMigrationCount = migrationRecordRepo.count();
        long initialStockMovements = stockMovementRepo.count();

        InvoiceMigrationRequest req = InvoiceMigrationRequest.builder()
                .targetFirmId(firmBId)
                .createCustomerIfMissing(true)
                .build();

        mockMvc.perform(post("/api/invoices/" + invId + "/migrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Active firm context (X-Firm-Id) is required for invoice migration operations."));

        // Verify state is completely unchanged
        Invoice after = invoiceRepo.findById(invId).orElseThrow();
        assertEquals(firmAId, after.getFirmId());
        assertEquals(InvoiceStatus.UNPAID, after.getStatus());
        assertEquals(initialMigrationCount, migrationRecordRepo.count());
        assertEquals(initialStockMovements, stockMovementRepo.count());
    }

    // =========================================================================
    // 2. Revert request without X-Firm-Id
    // =========================================================================
    @Test
    @DisplayName("Scenario 2: Revert request without X-Firm-Id fails with HTTP 400 Bad Request")
    void testRevertRequest_WithoutXFirmId_Returns400_StateUnchanged() throws Exception {
        Invoice invoice = createTestInvoiceInFirmA();
        Long invId = invoice.getId();

        // Perform valid forward migration first
        InvoiceMigrationRequest req = InvoiceMigrationRequest.builder()
                .targetFirmId(firmBId)
                .createCustomerIfMissing(true)
                .build();

        mockMvc.perform(post("/api/invoices/" + invId + "/migrate")
                        .header("X-Firm-Id", firmAId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        Invoice migrated = invoiceRepo.findById(invId).orElseThrow();
        assertEquals(firmBId, migrated.getFirmId());
        long migrationCountBefore = migrationRecordRepo.count();

        // Attempt revert without X-Firm-Id
        mockMvc.perform(post("/api/invoices/" + invId + "/revert-migration"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Active firm context (X-Firm-Id) is required for invoice migration operations."));

        // Verify state remains in Firm B without any revert mutations
        Invoice afterRevertAttempt = invoiceRepo.findById(invId).orElseThrow();
        assertEquals(firmBId, afterRevertAttempt.getFirmId());
        assertEquals(migrationCountBefore, migrationRecordRepo.count());
    }

    // =========================================================================
    // 3. Migration with a firm different from the source invoice's firm
    // =========================================================================
    @Test
    @DisplayName("Scenario 3: Migration with context different from source firm returns 403 Forbidden")
    void testMigration_DifferentFromSourceFirm_Returns403_StateUnchanged() throws Exception {
        Invoice invoice = createTestInvoiceInFirmA();
        Long invId = invoice.getId();
        long initialMigrationCount = migrationRecordRepo.count();
        long initialStockMovements = stockMovementRepo.count();

        InvoiceMigrationRequest req = InvoiceMigrationRequest.builder()
                .targetFirmId(firmCId)
                .createCustomerIfMissing(true)
                .build();

        // Attempt migration under Firm B context while invoice is in Firm A
        mockMvc.perform(post("/api/invoices/" + invId + "/migrate")
                        .header("X-Firm-Id", firmBId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Cross-firm migration blocked: invoice belongs to firm " + firmAId + " but active context is " + firmBId));

        // Verify state is completely unchanged
        Invoice after = invoiceRepo.findById(invId).orElseThrow();
        assertEquals(firmAId, after.getFirmId());
        assertEquals(invoice.getInvoiceNumber(), after.getInvoiceNumber());
        assertEquals(initialMigrationCount, migrationRecordRepo.count());
        assertEquals(initialStockMovements, stockMovementRepo.count());
    }

    // =========================================================================
    // 4. Preview of another firm's invoice
    // =========================================================================
    @Test
    @DisplayName("Scenario 4: Preview of another firm's invoice returns 404 Not Found without leaking data")
    void testPreview_AnotherFirmInvoice_Returns404_NoDataLeak() throws Exception {
        Invoice invoice = createTestInvoiceInFirmA();
        Long invId = invoice.getId();

        // Firm B attempts to preview Firm A's invoice
        mockMvc.perform(get("/api/invoices/" + invId + "/migration-preview")
                        .header("X-Firm-Id", firmBId)
                        .param("targetFirmId", firmCId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.customerResolution").doesNotExist())
                .andExpect(jsonPath("$.productResolutions").doesNotExist())
                .andExpect(jsonPath("$.currentInvoiceNumber").doesNotExist());
    }

    // =========================================================================
    // 5. History lookup for another firm's invoice
    // =========================================================================
    @Test
    @DisplayName("Scenario 5: History lookup for another firm's invoice returns 404 Not Found without leaking metadata")
    void testHistory_AnotherFirmInvoice_Returns404_NoMetadataLeak() throws Exception {
        Invoice invoice = createTestInvoiceInFirmA();
        Long invId = invoice.getId();

        // Migrate invoice from Firm A to Firm B
        InvoiceMigrationRequest req = InvoiceMigrationRequest.builder()
                .targetFirmId(firmBId)
                .createCustomerIfMissing(true)
                .build();

        mockMvc.perform(post("/api/invoices/" + invId + "/migrate")
                        .header("X-Firm-Id", firmAId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // Firm C (unrelated third party) attempts to query migration history
        mockMvc.perform(get("/api/invoices/" + invId + "/migration-history")
                        .header("X-Firm-Id", firmCId))
                .andExpect(status().isNotFound());

        // Firm A (source) and Firm B (target) CAN view history
        mockMvc.perform(get("/api/invoices/" + invId + "/migration-history")
                        .header("X-Firm-Id", firmAId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceFirmId").value(firmAId))
                .andExpect(jsonPath("$[0].targetFirmId").value(firmBId));

        mockMvc.perform(get("/api/invoices/" + invId + "/migration-history")
                        .header("X-Firm-Id", firmBId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceFirmId").value(firmAId))
                .andExpect(jsonPath("$[0].targetFirmId").value(firmBId));
    }

    // =========================================================================
    // 6. Revert attempt against another firm's invoice or migration
    // =========================================================================
    @Test
    @DisplayName("Scenario 6: Revert attempt by unauthorized third-party firm returns 403 Forbidden")
    void testRevert_UnauthorizedThirdParty_Returns403_StateUnchanged() throws Exception {
        Invoice invoice = createTestInvoiceInFirmA();
        Long invId = invoice.getId();

        // Migrate invoice from Firm A to Firm B
        InvoiceMigrationRequest req = InvoiceMigrationRequest.builder()
                .targetFirmId(firmBId)
                .createCustomerIfMissing(true)
                .build();

        mockMvc.perform(post("/api/invoices/" + invId + "/migrate")
                        .header("X-Firm-Id", firmAId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        Invoice migrated = invoiceRepo.findById(invId).orElseThrow();
        assertEquals(firmBId, migrated.getFirmId());
        String firmBInvoiceNumber = migrated.getInvoiceNumber();
        long migrationCountBefore = migrationRecordRepo.count();

        // Firm C (unrelated third party) attempts to revert migration
        mockMvc.perform(post("/api/invoices/" + invId + "/revert-migration")
                        .header("X-Firm-Id", firmCId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Cross-firm revert blocked: caller firm " + firmCId + " is not authorized for invoice in firm " + firmBId));

        // Verify invoice remains in Firm B with zero mutations
        Invoice afterThirdPartyRevert = invoiceRepo.findById(invId).orElseThrow();
        assertEquals(firmBId, afterThirdPartyRevert.getFirmId());
        assertEquals(firmBInvoiceNumber, afterThirdPartyRevert.getInvoiceNumber());
        assertEquals(migrationCountBefore, migrationRecordRepo.count());
    }

    // =========================================================================
    // 7. A valid same-firm migration and revert still work
    // =========================================================================
    @Test
    @DisplayName("Scenario 7: Valid forward migration and exact-snapshot revert round-trip successfully (200 OK)")
    void testValidMigrationAndRevert_RoundTrip_Succeeds() throws Exception {
        Invoice invoice = createTestInvoiceInFirmA();
        Long invId = invoice.getId();
        String originalInvNumber = invoice.getInvoiceNumber();

        // Forward Migration: Firm A -> Firm B
        InvoiceMigrationRequest req = InvoiceMigrationRequest.builder()
                .targetFirmId(firmBId)
                .createCustomerIfMissing(true)
                .build();

        mockMvc.perform(post("/api/invoices/" + invId + "/migrate")
                        .header("X-Firm-Id", firmAId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetFirmId").value(firmBId))
                .andExpect(jsonPath("$.isRevert").value(false));

        Invoice migrated = invoiceRepo.findById(invId).orElseThrow();
        assertEquals(firmBId, migrated.getFirmId());
        assertNotNull(migrated.getInvoiceNumber());

        // Revert Migration: Firm B -> Firm A
        mockMvc.perform(post("/api/invoices/" + invId + "/revert-migration")
                        .header("X-Firm-Id", firmBId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetFirmId").value(firmAId))
                .andExpect(jsonPath("$.isRevert").value(true));

        Invoice reverted = invoiceRepo.findById(invId).orElseThrow();
        assertEquals(firmAId, reverted.getFirmId());
        assertNotEquals(originalInvNumber, reverted.getInvoiceNumber()); // monotonic sequence
    }

    // =========================================================================
    // 8. An authenticated user cannot gain cross-firm access merely by changing X-Firm-Id
    // =========================================================================
    @Test
    @DisplayName("Scenario 8: Authenticated user cannot gain cross-firm access merely by changing X-Firm-Id")
    void testAuthenticatedUser_ManipulatingXFirmId_FailsClosed() throws Exception {
        Invoice invoice = createTestInvoiceInFirmA();
        Long invId = invoice.getId();
        long initialMigrationCount = migrationRecordRepo.count();

        // 1. Trying to preview Firm A's invoice while claiming Firm B context -> 404
        mockMvc.perform(get("/api/invoices/" + invId + "/migration-preview")
                        .header("X-Firm-Id", firmBId)
                        .param("targetFirmId", firmCId.toString()))
                .andExpect(status().isNotFound());

        // 2. Trying to migrate Firm A's invoice while claiming Firm B context -> 403
        InvoiceMigrationRequest migReq = InvoiceMigrationRequest.builder()
                .targetFirmId(firmCId)
                .createCustomerIfMissing(true)
                .build();

        mockMvc.perform(post("/api/invoices/" + invId + "/migrate")
                        .header("X-Firm-Id", firmBId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(migReq)))
                .andExpect(status().isForbidden());

        // 3. Trying to history probe Firm A's invoice while claiming Firm C context -> 404
        mockMvc.perform(get("/api/invoices/" + invId + "/migration-history")
                        .header("X-Firm-Id", firmCId))
                .andExpect(status().isNotFound());

        // 4. Trying to pass non-existent firm ID -> 400 Bad Request
        mockMvc.perform(post("/api/invoices/" + invId + "/migrate")
                        .header("X-Firm-Id", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(migReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Active firm context (999999) does not exist."));

        // Verify Firm A invoice is untouched
        Invoice afterAttacks = invoiceRepo.findById(invId).orElseThrow();
        assertEquals(firmAId, afterAttacks.getFirmId());
        assertEquals(InvoiceStatus.UNPAID, afterAttacks.getStatus());
        assertEquals(initialMigrationCount, migrationRecordRepo.count());
    }

    // =========================================================================
    // 9. Blank, malformed, non-positive X-Firm-Id fails closed with HTTP 400
    // =========================================================================
    @Test
    @DisplayName("Scenario 9: Blank, malformed, non-positive X-Firm-Id fails closed with HTTP 400 Bad Request")
    void testFirmHeader_BlankMalformedAndNonPositive_FailsClosedWith400() throws Exception {
        Invoice invoice = createTestInvoiceInFirmA();
        Long invId = invoice.getId();
        long initialMigrationCount = migrationRecordRepo.count();
        long initialStockMovements = stockMovementRepo.count();

        InvoiceMigrationRequest req = InvoiceMigrationRequest.builder()
                .targetFirmId(firmBId)
                .createCustomerIfMissing(true)
                .build();

        // 1. Blank string header -> 400
        mockMvc.perform(post("/api/invoices/" + invId + "/migrate")
                        .header("X-Firm-Id", "   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Active firm context (X-Firm-Id) is required for invoice migration operations."));

        // 2. Empty string header -> 400
        mockMvc.perform(post("/api/invoices/" + invId + "/migrate")
                        .header("X-Firm-Id", "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Active firm context (X-Firm-Id) is required for invoice migration operations."));

        // 3. Malformed / non-numeric header -> 400
        mockMvc.perform(post("/api/invoices/" + invId + "/migrate")
                        .header("X-Firm-Id", "not-a-number")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid firm ID specified."));

        // 4. Zero firm ID -> 400
        mockMvc.perform(post("/api/invoices/" + invId + "/migrate")
                        .header("X-Firm-Id", "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid firm ID specified."));

        // 5. Negative firm ID -> 400
        mockMvc.perform(post("/api/invoices/" + invId + "/migrate")
                        .header("X-Firm-Id", "-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid firm ID specified."));

        // Verify state is completely unchanged
        Invoice after = invoiceRepo.findById(invId).orElseThrow();
        assertEquals(firmAId, after.getFirmId());
        assertEquals(InvoiceStatus.UNPAID, after.getStatus());
        assertEquals(initialMigrationCount, migrationRecordRepo.count());
        assertEquals(initialStockMovements, stockMovementRepo.count());
    }

    // =========================================================================
    // 10. Real authentication enforcement and cross-firm isolation
    // =========================================================================
    @Test
    @DisplayName("Scenario 10: Real authentication flow verifies token, authorized firm access, and rejects cross-firm manipulation")
    void testRealAuthenticationFlow_TokenEnforcementAndCrossFirmProtection() throws Exception {
        Invoice invoice = createTestInvoiceInFirmA();
        Long invId = invoice.getId();
        long initialMigrationCount = migrationRecordRepo.count();
        long initialStockMovements = stockMovementRepo.count();
        long initialForecastTransactions = forecastTransactionRepo.count();
        long initialReminders = reminderRepo.count();

        // 1. Enable authentication
        AppConfig authEnabled = new AppConfig();
        authEnabled.setConfigKey("auth_enabled");
        authEnabled.setConfigValue("true");
        appConfigRepo.save(authEnabled);

        AppConfig pwConfig = new AppConfig();
        pwConfig.setConfigKey("global_password");
        pwConfig.setConfigValue("secretPass123");
        appConfigRepo.save(pwConfig);

        String token = "session_token_group9_test";
        AppConfig sessionConfig = new AppConfig();
        sessionConfig.setConfigKey("session_" + token);
        sessionConfig.setConfigValue(LocalDateTime.now().plusDays(1).toString());
        appConfigRepo.save(sessionConfig);

        try {
            InvoiceMigrationRequest migReq = InvoiceMigrationRequest.builder()
                    .targetFirmId(firmBId)
                    .createCustomerIfMissing(true)
                    .build();

            // 2. Unauthenticated request (no token) -> 401 Unauthorized
            mockMvc.perform(get("/api/invoices/" + invId + "/migration-preview")
                            .header("X-Firm-Id", firmAId)
                            .param("targetFirmId", firmBId.toString()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Unauthorized: Valid session token required"));

            // 3. Authenticated request with invalid token -> 401 Unauthorized
            mockMvc.perform(get("/api/invoices/" + invId + "/migration-preview")
                            .header("X-Auth-Token", "invalid_bogus_token")
                            .header("X-Firm-Id", firmAId)
                            .param("targetFirmId", firmBId.toString()))
                    .andExpect(status().isUnauthorized());

            // 4. Authenticated user accessing OWN firm's invoice -> 200 OK
            mockMvc.perform(get("/api/invoices/" + invId + "/migration-preview")
                            .header("X-Auth-Token", token)
                            .header("X-Firm-Id", firmAId)
                            .param("targetFirmId", firmBId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.eligible").value(true))
                    .andExpect(jsonPath("$.sourceFirmId").value(firmAId));

            // 5. Authenticated user manipulating X-Firm-Id to foreign firm B -> 404 Preview
            mockMvc.perform(get("/api/invoices/" + invId + "/migration-preview")
                            .header("X-Auth-Token", token)
                            .header("X-Firm-Id", firmBId)
                            .param("targetFirmId", firmCId.toString()))
                    .andExpect(status().isNotFound());

            // 6. Authenticated user attempting cross-firm migration under claimed firm B context -> 403 Forbidden
            mockMvc.perform(post("/api/invoices/" + invId + "/migrate")
                            .header("X-Auth-Token", token)
                            .header("X-Firm-Id", firmBId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(migReq)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("Cross-firm migration blocked: invoice belongs to firm " + firmAId + " but active context is " + firmBId));

            // 7. Verify complete state immutability across all subsystems
            Invoice after = invoiceRepo.findById(invId).orElseThrow();
            assertEquals(firmAId, after.getFirmId());
            assertEquals(InvoiceStatus.UNPAID, after.getStatus());
            assertEquals(invoice.getInvoiceNumber(), after.getInvoiceNumber());
            assertEquals(initialMigrationCount, migrationRecordRepo.count());
            assertEquals(initialStockMovements, stockMovementRepo.count());
            assertEquals(initialForecastTransactions, forecastTransactionRepo.count());
            assertEquals(initialReminders, reminderRepo.count());

        } finally {
            authController.invalidateToken(token);
            appConfigRepo.deleteById("auth_enabled");
            appConfigRepo.deleteById("global_password");
            appConfigRepo.deleteById("session_" + token);
        }
    }
}
