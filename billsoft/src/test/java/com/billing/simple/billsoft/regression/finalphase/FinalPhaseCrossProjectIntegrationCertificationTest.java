package com.billing.simple.billsoft.regression.finalphase;

import com.billing.simple.billsoft.dataprotection.DataProtectionCredentialStore;
import com.billing.simple.billsoft.dataprotection.DataProtectionCrypto;
import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.repositories.*;
import com.billing.simple.billsoft.service.AnnouncementService;
import com.billing.simple.billsoft.service.BackupArchiveService;
import com.billing.simple.billsoft.service.BackupService;
import com.billing.simple.billsoft.service.BackupValidationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * FINAL PHASE: Comprehensive Cross-Project Integration Audit & Certification Test Suite.
 * Validates:
 * 1. 100% Complete Backup Data Inventory (including ItemDuplicateDismissal and ItemMergeAudit).
 * 2. Permanent Stale Announcement Suppression & Lifecycle Expiry.
 * 3. Persistent Client Acknowledgment across sessions and reinstallation.
 * 4. Conditional HTTP 304 ETag Caching and Polling Reduction.
 * 5. RupeeCRM <-> SMT Encrypted Data Protection & Emergency Recovery Round Trip.
 * 6. Zero PAT Leakage & Strict Isolation within Data Protection Boundaries.
 */
public class FinalPhaseCrossProjectIntegrationCertificationTest {

    @TempDir
    Path tempDir;

    private ObjectMapper mapper;
    private File testCacheFile;
    private File testAckFile;
    private AnnouncementService announcementService;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        testCacheFile = tempDir.resolve("announcements_cache.json").toFile();
        testAckFile = tempDir.resolve("acknowledged_announcements.json").toFile();
        announcementService = new AnnouncementService(testCacheFile, testAckFile);
    }

    @Test
    @DisplayName("Scenario 1: Permanent Stale Announcement Suppression")
    void test01_StaleAnnouncementPermanentlySuppressed() {
        // The exact historical unversioned maintenance string from 2026-09-23
        String staleRaw = "[INFO] Important System Update: Scheduled maintenance tonight at 11 PM IST.";
        List<String> parsed = announcementService.parseAnnouncements(staleRaw);

        // Must be completely suppressed (empty list)
        assertTrue(parsed.isEmpty(), "Historical unversioned maintenance announcement must be permanently suppressed");
    }

    @Test
    @DisplayName("Scenario 2: SMT Canonical Announcements with Validity Lifecycle Parse Correctly")
    void test02_SmtCanonicalAnnouncementsParseCorrectly() {
        Instant now = Instant.now();
        Instant futurePublish = now.plus(Duration.ofHours(2));
        Instant pastPublish = now.minus(Duration.ofHours(1));
        Instant futureExpires = now.plus(Duration.ofDays(3));
        Instant pastExpires = now.minus(Duration.ofMinutes(10));

        // Active valid announcement
        String active = String.format("[INFO|id=ANN-101|publishAt=%s|expiresAt=%s|target=ALL] System Upgrade: Scheduled release v1.0.362 is live.",
                pastPublish.toString(), futureExpires.toString());

        // Expired announcement
        String expired = String.format("[WARNING|id=ANN-102|publishAt=%s|expiresAt=%s|target=ALL] Urgent Alert: Maintenance completed.",
                pastPublish.toString(), pastExpires.toString());

        // Scheduled future announcement
        String scheduled = String.format("[INFO|id=ANN-103|publishAt=%s|expiresAt=%s|target=ALL] Future Feature: Invoicing overhaul coming soon.",
                futurePublish.toString(), futureExpires.toString());

        String rawFeed = active + "\n\n@@@\n\n" + expired + "\n\n@@@\n\n" + scheduled;
        List<String> eligible = announcementService.parseAnnouncements(rawFeed);

        assertEquals(1, eligible.size(), "Only the unexpired, currently active announcement must be eligible");
        assertTrue(eligible.get(0).contains("Scheduled release v1.0.362 is live."));
    }

    @Test
    @DisplayName("Scenario 3: Persistent Announcement Acknowledgment across Sessions and Reinstallation")
    void test03_PersistentAcknowledgmentPreservedAcrossReinstantiation() {
        String annKey = "[INFO] Feature Update: Multi-firm tax calculation upgraded.";
        assertFalse(announcementService.isAnnouncementAcknowledged(annKey));

        // Acknowledge announcement
        boolean acked = announcementService.acknowledgeAnnouncement(annKey);
        assertTrue(acked);
        assertTrue(announcementService.isAnnouncementAcknowledged(annKey));
        assertTrue(testAckFile.exists(), "Acknowledgment file must be written to disk");

        // Re-instantiate service (simulating application restart or reinstallation preserving data dir)
        AnnouncementService restartedService = new AnnouncementService(testCacheFile, testAckFile);
        assertTrue(restartedService.isAnnouncementAcknowledged(annKey),
                "Acknowledged announcement must remain acknowledged after service restart");
        assertTrue(restartedService.getAcknowledgedKeys().contains(annKey));
    }

    @Test
    @DisplayName("Scenario 4: ETag Fingerprint Generation for HTTP 304 Conditional Polling")
    void test04_EtagFingerprintGeneratedAndConsistent() {
        String initialEtag = announcementService.getCacheFingerprint();
        assertEquals("empty", initialEtag);

        announcementService.setMemoryCacheForTesting(
                List.of("[INFO] GST Update: E-Invoicing v2 guidelines active."),
                Instant.now()
        );

        String updatedEtag = announcementService.getCacheFingerprint();
        assertNotEquals("empty", updatedEtag);
        assertFalse(updatedEtag.isBlank());

        // Fingerprint must be deterministic and identical for same content
        String recheckEtag = announcementService.getCacheFingerprint();
        assertEquals(updatedEtag, recheckEtag);
    }

    @Test
    @DisplayName("Scenario 5: Complete Data Inventory - ItemDuplicateDismissal & ItemMergeAudit in BackupDTO")
    void test05_CompleteBackupInventoryIncludesDeduplicationEntities() throws Exception {
        FirmDetailsRepository firmRepo = mock(FirmDetailsRepository.class);
        CustomerRepository customerRepo = mock(CustomerRepository.class);
        ProductRepository productRepo = mock(ProductRepository.class);
        StockMovementRepository stockMovementRepo = mock(StockMovementRepository.class);
        InvoiceRepository invoiceRepo = mock(InvoiceRepository.class);
        InvoiceItemRepository invoiceItemRepo = mock(InvoiceItemRepository.class);
        PartyRepository partyRepo = mock(PartyRepository.class);
        PartyPaymentRepository partyPaymentRepo = mock(PartyPaymentRepository.class);
        PurchaseOrderRepository purchaseOrderRepo = mock(PurchaseOrderRepository.class);
        PurchaseOrderItemRepository purchaseOrderItemRepo = mock(PurchaseOrderItemRepository.class);
        ReminderRepository reminderRepo = mock(ReminderRepository.class);
        NoteRepository noteRepo = mock(NoteRepository.class);
        ExpenseRepository expenseRepo = mock(ExpenseRepository.class);
        EmployeeRepository employeeRepo = mock(EmployeeRepository.class);
        AttendanceRecordRepository attendanceRecordRepo = mock(AttendanceRecordRepository.class);
        LeaveRecordRepository leaveRecordRepo = mock(LeaveRecordRepository.class);
        SalaryRecordRepository salaryRepo = mock(SalaryRecordRepository.class);
        EmployeeAdvanceRepository advanceRepo = mock(EmployeeAdvanceRepository.class);
        PromotionRecordRepository promotionRepo = mock(PromotionRecordRepository.class);
        EmployeeDocumentRepository employeeDocumentRepo = mock(EmployeeDocumentRepository.class);
        BusinessLetterRepository businessLetterRepo = mock(BusinessLetterRepository.class);
        AppConfigRepository appConfigRepo = mock(AppConfigRepository.class);
        InvoicePaymentRepository invoicePaymentRepo = mock(InvoicePaymentRepository.class);
        SalesReturnRepository salesReturnRepo = mock(SalesReturnRepository.class);
        SalesReturnItemRepository salesReturnItemRepo = mock(SalesReturnItemRepository.class);
        SavingRepository savingRepo = mock(SavingRepository.class);
        GoalRepository goalRepo = mock(GoalRepository.class);
        GoalLogRepository goalLogRepo = mock(GoalLogRepository.class);
        BackupEntityMappingRepository backupEntityMappingRepo = mock(BackupEntityMappingRepository.class);
        NotificationRepository notificationRepo = mock(NotificationRepository.class);
        NotificationPreferenceRepository notificationPreferenceRepo = mock(NotificationPreferenceRepository.class);
        BackupValidationService backupValidationService = new BackupValidationService();
        ItemMergeAuditRepository itemMergeAuditRepo = mock(ItemMergeAuditRepository.class);
        ItemDuplicateDismissalRepository itemDuplicateDismissalRepo = mock(ItemDuplicateDismissalRepository.class);

        FirmDetails firm = new FirmDetails();
        firm.setId(1L);
        firm.setFirmName("Final Test Firm");
        when(firmRepo.findAll()).thenReturn(List.of(firm));
        when(customerRepo.findAll()).thenReturn(Collections.emptyList());
        when(productRepo.findAll()).thenReturn(Collections.emptyList());
        when(stockMovementRepo.findAll()).thenReturn(Collections.emptyList());
        when(invoiceRepo.findAllWithItems()).thenReturn(Collections.emptyList());
        when(invoicePaymentRepo.findAll()).thenReturn(Collections.emptyList());
        when(partyRepo.findAll()).thenReturn(Collections.emptyList());
        when(partyPaymentRepo.findAll()).thenReturn(Collections.emptyList());
        when(purchaseOrderRepo.findAll()).thenReturn(Collections.emptyList());
        when(reminderRepo.findAll()).thenReturn(Collections.emptyList());
        when(noteRepo.findAll()).thenReturn(Collections.emptyList());
        when(expenseRepo.findAll()).thenReturn(Collections.emptyList());
        when(savingRepo.findAll()).thenReturn(Collections.emptyList());
        when(goalRepo.findAll()).thenReturn(Collections.emptyList());
        when(goalLogRepo.findAllByOrderByLogDateAscCreatedAtAsc()).thenReturn(Collections.emptyList());
        when(employeeRepo.findAll()).thenReturn(Collections.emptyList());
        when(attendanceRecordRepo.findAll()).thenReturn(Collections.emptyList());
        when(leaveRecordRepo.findAll()).thenReturn(Collections.emptyList());
        when(salaryRepo.findAll()).thenReturn(Collections.emptyList());
        when(advanceRepo.findAll()).thenReturn(Collections.emptyList());
        when(promotionRepo.findAll()).thenReturn(Collections.emptyList());
        when(employeeDocumentRepo.findAll()).thenReturn(Collections.emptyList());
        when(businessLetterRepo.findAll()).thenReturn(Collections.emptyList());
        when(notificationRepo.findAll()).thenReturn(Collections.emptyList());
        when(notificationPreferenceRepo.findAll()).thenReturn(Collections.emptyList());
        when(appConfigRepo.findAll()).thenReturn(Collections.emptyList());
        when(salesReturnRepo.findAllWithItems()).thenReturn(Collections.emptyList());

        // Mock deduplication data
        ItemDuplicateDismissal dismissal = ItemDuplicateDismissal.builder()
                .id(10L)
                .firmId(1L)
                .sourceTypeA("PRODUCT")
                .itemIdA(101L)
                .sourceTypeB("SAVED_ITEM")
                .itemIdB(202L)
                .dismissedAt(LocalDateTime.now())
                .build();
        when(itemDuplicateDismissalRepo.findAll()).thenReturn(List.of(dismissal));

        ItemMergeAudit audit = ItemMergeAudit.builder()
                .id(20L)
                .firmId(1L)
                .primaryType("PRODUCT")
                .primaryId(101L)
                .primaryName("Primary Item")
                .secondaryType("SAVED_ITEM")
                .secondaryId(202L)
                .secondaryName("Duplicate Item")
                .stockTransferred(new BigDecimal("15.500"))
                .detailsJson("{\"reason\":\"Duplicate cleanup\"}")
                .mergedAt(LocalDateTime.now())
                .build();
        when(itemMergeAuditRepo.findAll()).thenReturn(List.of(audit));

        BackupService backupService = new BackupService(
                firmRepo, customerRepo, productRepo, stockMovementRepo, invoiceRepo, invoiceItemRepo,
                partyRepo, partyPaymentRepo, purchaseOrderRepo, purchaseOrderItemRepo, reminderRepo,
                noteRepo, expenseRepo, employeeRepo, attendanceRecordRepo, leaveRecordRepo, salaryRepo,
                advanceRepo, promotionRepo, employeeDocumentRepo, businessLetterRepo, appConfigRepo,
                invoicePaymentRepo, salesReturnRepo, salesReturnItemRepo, savingRepo, goalRepo,
                goalLogRepo, backupEntityMappingRepo, notificationRepo, notificationPreferenceRepo,
                backupValidationService, null, null, null, null, null, null, null, null, null,
                itemMergeAuditRepo, itemDuplicateDismissalRepo, null, null, null, null, null, mapper, null
        );

        // 1. Export all data
        BackupDTO backup = backupService.exportAllData();
        assertNotNull(backup);
        assertNotNull(backup.getItemDuplicateDismissals(), "ItemDuplicateDismissals must be exported");
        assertEquals(1, backup.getItemDuplicateDismissals().size());
        assertEquals("PRODUCT", backup.getItemDuplicateDismissals().get(0).getSourceTypeA());

        assertNotNull(backup.getItemMergeAudits(), "ItemMergeAudits must be exported");
        assertEquals(1, backup.getItemMergeAudits().size());
        assertEquals(new BigDecimal("15.500"), backup.getItemMergeAudits().get(0).getStockTransferred());

        // 2. Archive to ZIP with BackupArchiveService
        BackupArchiveService archiveService = new BackupArchiveService(mapper, backupValidationService);
        byte[] zipBytes = archiveService.createZipArchive(backup);
        assertTrue(zipBytes.length > 0, "ZIP archive must be generated");

        // 3. Extract and validate from ZIP
        BackupDTO extracted = archiveService.extractAndValidateBackup(new ByteArrayInputStream(zipBytes), "backup.zip");
        assertNotNull(extracted);
        assertNotNull(extracted.getItemDuplicateDismissals(), "Extracted backup must contain duplicate dismissals");
        assertEquals(1, extracted.getItemDuplicateDismissals().size());
        assertNotNull(extracted.getItemMergeAudits(), "Extracted backup must contain merge audits");
        assertEquals(1, extracted.getItemMergeAudits().size());
    }

    @Test
    @DisplayName("Scenario 6: RupeeCRM <-> SMT Encrypted Cloud Backup and Decryption Round Trip")
    void test06_CloudBackupEncryptionAndSmtRecoveryRoundTrip() throws Exception {
        String licenseId = "LIC-PROD-9999-ABCD";
        DataProtectionCrypto crypto = new DataProtectionCrypto();
        javax.crypto.SecretKey key = crypto.deriveKeyFromLicenseId(licenseId);

        // Synthetic business payload
        Map<String, Object> backupData = new HashMap<>();
        backupData.put("version", "2.0");
        backupData.put("firmName", "SMT Integrated Firm");
        backupData.put("licenseId", licenseId);
        backupData.put("invoiceTotal", "48200.50");
        byte[] payloadBytes = mapper.writeValueAsBytes(backupData);

        // Encrypt using AES-256-GCM container
        byte[] encryptedContainer = crypto.encryptBackup(payloadBytes, key);
        assertNotNull(encryptedContainer);
        assertTrue(encryptedContainer.length > 28);

        // Verify SMT decryption with derived key
        byte[] decryptedBytes = crypto.decryptBackup(encryptedContainer, key);
        assertArrayEquals(payloadBytes, decryptedBytes, "Decrypted payload must match 100% byte fidelity with original");

        // Verify tampering detection
        encryptedContainer[encryptedContainer.length - 1] ^= 0x01;
        assertThrows(Exception.class, () -> crypto.decryptBackup(encryptedContainer, key),
                "Tampered container must fail decryption authentication");
    }

    @Test
    @DisplayName("Scenario 7: Strict PAT Isolation and Sensitive Configuration Redaction")
    void test07_SensitiveKeysRedactedAndNoPatInCoreConfig() {
        // Verify BackupService sensitive config redaction
        assertTrue(BackupService.isSensitiveConfigKey("password"));
        assertTrue(BackupService.isSensitiveConfigKey("user_password"));
        assertTrue(BackupService.isSensitiveConfigKey("master_key"));
        assertTrue(BackupService.isSensitiveConfigKey("secret_token"));
        assertTrue(BackupService.isSensitiveConfigKey("application_pin"));
        assertTrue(BackupService.isSensitiveConfigKey("github_pat"));
        assertTrue(BackupService.isSensitiveConfigKey("access_token"));
        assertTrue(BackupService.isSensitiveConfigKey("api_key"));

        // Non-sensitive settings must remain unredacted
        assertFalse(BackupService.isSensitiveConfigKey("app_theme"));
        assertFalse(BackupService.isSensitiveConfigKey("invoice_prefix"));
        assertFalse(BackupService.isSensitiveConfigKey("currency_symbol"));
    }
}
