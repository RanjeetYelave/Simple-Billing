package com.billing.simple.billsoft.regression.billing;

import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceRequestItem;
import com.billing.simple.billsoft.dtos.migration.InvoiceMigrationDTOs.InvoiceMigrationRecordDTO;
import com.billing.simple.billsoft.dtos.migration.InvoiceMigrationDTOs.InvoiceMigrationRequest;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.service.BackupService;
import com.billing.simple.billsoft.service.InvoiceMigrationService;
import com.billing.simple.billsoft.service.InvoiceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Group 10 — Automated Regression Tests for Invoice Migration History Backup/Restore Integrity.
 *
 * Covers:
 * 1. Full-system backup and restore round-trip under Decision A1 (history and revert capability preserved).
 * 2. Tenant-scoped backup strict isolation under Decision B1 (records and cross-firm snapshots excluded).
 * 3. Cascading purge on invoice deletion under Decision C2 (records deleted when invoice deleted).
 * 4. Backward compatibility with older backup payloads lacking migration-history fields.
 * 5. Revert fails closed with 409 Conflict when migration history is absent.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Group 10 — Invoice Migration History Backup/Restore Integrity Tests")
public class InvoiceMigrationBackupRestoreIntegrationTest {

    @Autowired
    private BackupService backupService;

    @Autowired
    private InvoiceMigrationService migrationService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private FirmDetailsRepository firmRepo;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private InvoiceRepository invoiceRepo;

    @Autowired
    private InvoiceMigrationRecordRepository migrationRecordRepo;

    @Autowired
    private InvoicePaymentRepository invoicePaymentRepo;

    @Autowired
    private SalesReturnRepository salesReturnRepo;

    @Autowired
    private SalesReturnItemRepository salesReturnItemRepo;

    @Autowired
    private StockMovementRepository stockMovementRepo;

    @Autowired
    private InvoiceItemRepository invoiceItemRepo;

    @Autowired
    private ObjectMapper objectMapper;

    private FirmDetails firmA;
    private FirmDetails firmB;
    private Customer customerA;
    private Product productA;
    private Product productB;

    private void cleanDatabase() {
        TenantContext.clear();
        migrationRecordRepo.deleteAll();
        invoicePaymentRepo.deleteAll();
        salesReturnItemRepo.deleteAll();
        salesReturnRepo.deleteAll();
        stockMovementRepo.deleteAll();
        invoiceItemRepo.deleteAll();
        invoiceRepo.deleteAll();
        customerRepo.deleteAll();
        productRepo.deleteAll();
        firmRepo.deleteAll();
    }

    @BeforeEach
    void setUp() {
        cleanDatabase();

        FirmDetails fA = new FirmDetails();
        fA.setFirmName("G10 Firm A " + System.nanoTime());
        fA.setGstin("27AAAAA0000A1Z5");
        fA.setAddressLine1("Pune");
        fA.setState("Maharashtra");
        firmA = firmRepo.save(fA);

        FirmDetails fB = new FirmDetails();
        fB.setFirmName("G10 Firm B " + System.nanoTime());
        fB.setGstin("27BBBBB0000B1Z6");
        fB.setAddressLine1("Mumbai");
        fB.setState("Maharashtra");
        firmB = firmRepo.save(fB);

        TenantContext.setCurrentFirmId(firmA.getId());

        customerA = customerRepo.save(Customer.builder()
                .firmId(firmA.getId())
                .name("Customer A")
                .phone("9999900001")
                .address("Pune")
                .build());

        productA = productRepo.save(Product.builder()
                .firmId(firmA.getId())
                .name("Product A")
                .price(BigDecimal.valueOf(100.0))
                .stockQuantity(BigDecimal.valueOf(50.0))
                .inInventory(true)
                .build());

        TenantContext.setCurrentFirmId(firmB.getId());

        productB = productRepo.save(Product.builder()
                .firmId(firmB.getId())
                .name("Product A")
                .price(BigDecimal.valueOf(100.0))
                .stockQuantity(BigDecimal.valueOf(50.0))
                .inInventory(true)
                .build());

        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private Invoice createTestInvoice(FirmDetails firm, Customer customer, Product product) {
        TenantContext.setCurrentFirmId(firm.getId());
        InvoiceRequest request = new InvoiceRequest();
        request.setFirmId(firm.getId());
        request.setCustomerId(customer.getId());
        request.setInvoiceDate(LocalDateTime.now().toString());

        InvoiceRequestItem item = new InvoiceRequestItem();
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setQty(2);
        item.setPricePerUnit(BigDecimal.valueOf(100.0));
        item.setGstPercent(BigDecimal.ZERO);
        request.setItems(Collections.singletonList(item));

        Invoice created = invoiceService.createInvoice(request);
        TenantContext.clear();
        return created;
    }

    @Test
    @DisplayName("G10-001 / Decision A1: Full-system backup and restore preserves migration history and revert capability")
    void testFullBackupAndRestore_PreservesMigrationHistoryAndRevertCapability() {
        // 1. Create invoice in Firm A
        Invoice originalInv = createTestInvoice(firmA, customerA, productA);
        Long invoiceId = originalInv.getId();

        // 2. Migrate from Firm A to Firm B
        TenantContext.setCurrentFirmId(firmA.getId());
        InvoiceMigrationRequest migReq = InvoiceMigrationRequest.builder()
                .targetFirmId(firmB.getId())
                .build();
        migrationService.migrateInvoice(invoiceId, migReq);

        // Verify pre-backup state
        Invoice migratedInv = invoiceRepo.findById(invoiceId).orElseThrow();
        assertEquals(firmB.getId(), migratedInv.getFirmId());
        List<InvoiceMigrationRecord> initialRecords = migrationRecordRepo.findByInvoiceIdOrderByCreatedAtDesc(invoiceId);
        assertEquals(1, initialRecords.size());
        assertEquals("ACTIVE", initialRecords.get(0).getStatus());

        // 3. Export Full-System Backup (Decision A1)
        BackupDTO fullBackup = backupService.exportAllData();
        assertNotNull(fullBackup.getInvoiceMigrationRecords(), "Full backup must include migration records");
        assertFalse(fullBackup.getInvoiceMigrationRecords().isEmpty());

        boolean containsOurRecord = fullBackup.getInvoiceMigrationRecords().stream()
                .anyMatch(r -> invoiceId.equals(r.getInvoiceId()));
        assertTrue(containsOurRecord, "Backup payload must contain migration record for migrated invoice");

        // 4. Restore Full-System Backup
        backupService.importSelectiveData(fullBackup, null, "merge", null);

        // 5. Verify restored state
        Invoice restoredInv = invoiceRepo.findById(invoiceId).orElseThrow();
        assertEquals(firmB.getId(), restoredInv.getFirmId(), "Restored invoice must remain in target firm");

        List<InvoiceMigrationRecord> restoredRecords = migrationRecordRepo.findByInvoiceIdOrderByCreatedAtDesc(invoiceId);
        assertFalse(restoredRecords.isEmpty(), "Migration records must be restored");
        assertEquals("ACTIVE", restoredRecords.get(0).getStatus());

        // 6. Verify history endpoint works after restore
        TenantContext.setCurrentFirmId(firmB.getId());
        List<InvoiceMigrationRecordDTO> history = migrationService.getMigrationHistory(invoiceId);
        assertFalse(history.isEmpty(), "History must be populated from restored records");
        assertEquals(firmA.getId(), history.get(0).getSourceFirmId());
        assertEquals(firmB.getId(), history.get(0).getTargetFirmId());

        // 7. Verify revert succeeds after restore!
        migrationService.revertMigration(invoiceId);

        Invoice revertedInv = invoiceRepo.findById(invoiceId).orElseThrow();
        assertEquals(firmA.getId(), revertedInv.getFirmId(), "Invoice must be reverted back to Firm A");
    }

    @Test
    @DisplayName("G10-003 / Decision B1: Tenant-scoped backup strictly excludes migration records to prevent cross-firm leakage")
    void testTenantScopedBackup_StrictIsolation_OmitsMigrationRecords() {
        // 1. Create and migrate invoice
        Invoice inv = createTestInvoice(firmA, customerA, productA);
        TenantContext.setCurrentFirmId(firmA.getId());
        migrationService.migrateInvoice(inv.getId(), InvoiceMigrationRequest.builder()
                .targetFirmId(firmB.getId())
                .build());

        // 2. Export single-firm tenant backup for Firm B
        TenantContext.setCurrentFirmId(firmB.getId());
        BackupDTO tenantBackup = backupService.exportData(firmB.getId());

        // 3. Assert strict isolation: migration records must be null in tenant export
        assertNull(tenantBackup.getInvoiceMigrationRecords(),
                "Decision B1: Single-firm tenant export must NOT include cross-firm migration records");
    }

    @Test
    @DisplayName("G10-004 / Decision C2: Permanently deleting an invoice purges its migration records")
    void testInvoiceDeletion_CascadingPurge_DeletesMigrationRecords() {
        // 1. Create and migrate invoice
        Invoice inv = createTestInvoice(firmA, customerA, productA);
        Long invId = inv.getId();
        TenantContext.setCurrentFirmId(firmA.getId());
        migrationService.migrateInvoice(invId, InvoiceMigrationRequest.builder()
                .targetFirmId(firmB.getId())
                .build());

        // Verify migration record exists
        assertFalse(migrationRecordRepo.findByInvoiceIdOrderByCreatedAtDesc(invId).isEmpty());

        // 2. Delete invoice via InvoiceService
        TenantContext.setCurrentFirmId(firmB.getId());
        invoiceService.delete(invId);

        // 3. Verify cascading purge: migration records for this invoice are purged
        List<InvoiceMigrationRecord> remainingRecords = migrationRecordRepo.findByInvoiceIdOrderByCreatedAtDesc(invId);
        assertTrue(remainingRecords.isEmpty(), "Decision C2: Deleting invoice must purge associated migration records");
    }

    @Test
    @DisplayName("Backward Compatibility: Older backups without invoiceMigrationRecords deserialize and restore safely")
    void testOlderBackupPayload_DeserializesAndRestoresSafely() throws Exception {
        // Create minimal backup JSON string without invoiceMigrationRecords
        String olderBackupJson = "{"
                + "\"metadata\": {\"type\": \"FULL_SYSTEM_BACKUP\", \"version\": \"1.0\"},"
                + "\"allFirms\": []"
                + "}";

        BackupDTO olderBackup = objectMapper.readValue(olderBackupJson, BackupDTO.class);
        assertNull(olderBackup.getInvoiceMigrationRecords(), "Field should deserialize cleanly as null");

        // Verify import completes without error
        assertDoesNotThrow(() -> backupService.importSelectiveData(olderBackup, null, "merge", null));
    }

    @Test
    @DisplayName("Fail-Closed Safety: Attempting to revert an invoice without migration history throws IllegalStateException")
    void testRestoredInvoiceWithoutHistory_RevertFailsClosed() {
        // Create invoice directly in Firm B (never migrated)
        Customer custB = customerRepo.save(Customer.builder().firmId(firmB.getId()).name("Cust B").build());
        Invoice nativeInv = createTestInvoice(firmB, custB, productB);
        TenantContext.setCurrentFirmId(firmB.getId());

        // Attempting to revert must fail closed with IllegalStateException (maps to HTTP 409)
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                migrationService.revertMigration(nativeInv.getId())
        );
        assertTrue(ex.getMessage().contains("No migration record found"));
    }

    @Test
    @DisplayName("Group 10B / Req 2: Tenant restore clears stale migration records for restored invoice while strictly preserving unrelated invoice history")
    void testTenantRestore_ClearsStaleMigrationHistory_PreservesUnrelatedInvoiceHistory() {
        // 1. Create and migrate Invoice 1 from Firm A to Firm B
        Invoice inv1 = createTestInvoice(firmA, customerA, productA);
        Long inv1Id = inv1.getId();
        TenantContext.setCurrentFirmId(firmA.getId());
        migrationService.migrateInvoice(inv1Id, InvoiceMigrationRequest.builder()
                .targetFirmId(firmB.getId())
                .build());

        // 2. Create and migrate Invoice 2 from Firm A to Firm B (unrelated invoice)
        Invoice inv2 = createTestInvoice(firmA, customerA, productA);
        Long inv2Id = inv2.getId();
        TenantContext.setCurrentFirmId(firmA.getId());
        migrationService.migrateInvoice(inv2Id, InvoiceMigrationRequest.builder()
                .targetFirmId(firmB.getId())
                .build());

        // Verify pre-restore state: both have migration records
        assertFalse(migrationRecordRepo.findByInvoiceIdOrderByCreatedAtDesc(inv1Id).isEmpty());
        assertFalse(migrationRecordRepo.findByInvoiceIdOrderByCreatedAtDesc(inv2Id).isEmpty());

        // 3. Export tenant-scoped backup for Firm B
        TenantContext.setCurrentFirmId(firmB.getId());
        BackupDTO tenantBackup = backupService.exportData(firmB.getId());
        // Verify tenant export contains no migration records (Decision B1)
        assertNull(tenantBackup.getInvoiceMigrationRecords());

        // Filter tenant backup so it ONLY contains Invoice 1 (to simulate restoring an export containing only Invoice 1)
        Invoice inv1InBackup = tenantBackup.getInvoices().stream()
                .filter(i -> i.getId().equals(inv1Id))
                .findFirst().orElseThrow();
        tenantBackup.setInvoices(Collections.singletonList(inv1InBackup));

        // 4. Restore tenant backup into Firm B in merge mode
        backupService.importSelectiveData(tenantBackup, null, "merge", firmB.getId());

        // 5. Verify: Invoice 1's stale migration history was purged
        List<InvoiceMigrationRecord> inv1RecordsAfter = migrationRecordRepo.findByInvoiceIdOrderByCreatedAtDesc(inv1Id);
        assertTrue(inv1RecordsAfter.isEmpty(), "Tenant restore must purge stale migration history for restored/merged invoice");

        // 6. Verify: Unrelated Invoice 2's migration history is preserved intact!
        List<InvoiceMigrationRecord> inv2RecordsAfter = migrationRecordRepo.findByInvoiceIdOrderByCreatedAtDesc(inv2Id);
        assertFalse(inv2RecordsAfter.isEmpty(), "Unrelated invoice's migration history must be strictly preserved");
    }

    @Test
    @DisplayName("Group 10B / Req 3: Deterministic parent-before-child restoration resolves parent IDs correctly even if payload is out of order")
    void testDeterministicParentBeforeChildRestoration_EvenIfUnordered() {
        // 1. Create invoice in Firm A
        Invoice originalInv = createTestInvoice(firmA, customerA, productA);
        Long invoiceId = originalInv.getId();

        // 2. Migrate Firm A -> Firm B
        TenantContext.setCurrentFirmId(firmA.getId());
        migrationService.migrateInvoice(invoiceId, InvoiceMigrationRequest.builder()
                .targetFirmId(firmB.getId())
                .build());

        // 3. Revert Firm B -> Firm A (creates child record linked to active parent)
        TenantContext.setCurrentFirmId(firmB.getId());
        migrationService.revertMigration(invoiceId);

        // Verify pre-backup records
        List<InvoiceMigrationRecord> preRecords = migrationRecordRepo.findByInvoiceIdOrderByCreatedAtDesc(invoiceId);
        assertEquals(2, preRecords.size());
        InvoiceMigrationRecord childRec = preRecords.get(0); // REVERT record (child)
        InvoiceMigrationRecord parentRec = preRecords.get(1); // MIGRATION record (parent)
        assertNotNull(childRec.getParentMigrationId());
        assertEquals(parentRec.getId(), childRec.getParentMigrationId());

        // 4. Export full backup
        BackupDTO fullBackup = backupService.exportAllData();
        assertNotNull(fullBackup.getInvoiceMigrationRecords());

        // Deliberately scramble record order so Child record is BEFORE Parent record in the payload!
        List<InvoiceMigrationRecord> scrambled = new ArrayList<>(fullBackup.getInvoiceMigrationRecords());
        Collections.reverse(scrambled);
        fullBackup.setInvoiceMigrationRecords(scrambled);

        // 5. Restore full backup into DB in merge mode
        backupService.importSelectiveData(fullBackup, null, "merge", null);

        // 6. Verify restored records: child's parentMigrationId points to the newly saved parent record ID!
        List<InvoiceMigrationRecord> restoredRecords = migrationRecordRepo.findByInvoiceIdOrderByCreatedAtDesc(invoiceId);
        assertEquals(2, restoredRecords.size());
        InvoiceMigrationRecord restoredChild = restoredRecords.stream().filter(r -> Boolean.TRUE.equals(r.getIsRevert())).findFirst().orElseThrow();
        InvoiceMigrationRecord restoredParent = restoredRecords.stream().filter(r -> !Boolean.TRUE.equals(r.getIsRevert())).findFirst().orElseThrow();

        assertEquals(restoredParent.getId(), restoredChild.getParentMigrationId(),
                "Topological ordering must guarantee parent is persisted first and child points to new parent ID");
    }

    @Test
    @DisplayName("Group 10B / Req 4: Selective full-backup restore only restores migration records when both source and target firms are selected")
    void testSafeSelectiveFullBackupRestore_SafetyPolicy() {
        // Create 3rd firm: Firm C
        FirmDetails fC = new FirmDetails();
        fC.setFirmName("G10 Firm C " + System.nanoTime());
        fC.setGstin("27CCCCC0000C1Z7");
        fC.setAddressLine1("Nagpur");
        fC.setState("Maharashtra");
        FirmDetails firmC = firmRepo.save(fC);

        Customer custA = customerRepo.save(Customer.builder().firmId(firmA.getId()).name("Cust A3").build());
        Product prodA = productRepo.save(Product.builder().firmId(firmA.getId()).name("Prod A3").price(BigDecimal.valueOf(50)).stockQuantity(BigDecimal.valueOf(100.0)).inInventory(true).build());
        Product prodB = productRepo.save(Product.builder().firmId(firmB.getId()).name("Prod A3").price(BigDecimal.valueOf(50)).stockQuantity(BigDecimal.valueOf(100.0)).inInventory(true).build());
        Product prodC = productRepo.save(Product.builder().firmId(firmC.getId()).name("Prod A3").price(BigDecimal.valueOf(50)).stockQuantity(BigDecimal.valueOf(100.0)).inInventory(true).build());

        FirmDetails fD = new FirmDetails();
        fD.setFirmName("G10 Firm D " + System.nanoTime());
        fD.setGstin("27DDDDD0000D1Z7");
        fD.setAddressLine1("Nashik");
        fD.setState("Maharashtra");
        FirmDetails firmD = firmRepo.save(fD);
        Product prodD = productRepo.save(Product.builder().firmId(firmD.getId()).name("Prod D3").price(BigDecimal.valueOf(50)).stockQuantity(BigDecimal.valueOf(100.0)).inInventory(true).build());

        // Invoice 1: Migrated Firm A -> Firm B (both will be selected)
        Invoice inv1 = createTestInvoice(firmA, custA, prodA);
        TenantContext.setCurrentFirmId(firmA.getId());
        migrationService.migrateInvoice(inv1.getId(), InvoiceMigrationRequest.builder().targetFirmId(firmB.getId()).build());

        // Invoice 2: Migrated Firm B -> Firm C (only Firm B will be selected, Firm C will not)
        Invoice inv2 = createTestInvoice(firmB, customerRepo.save(Customer.builder().firmId(firmB.getId()).name("Cust B3").build()), prodB);
        TenantContext.setCurrentFirmId(firmB.getId());
        migrationService.migrateInvoice(inv2.getId(), InvoiceMigrationRequest.builder().targetFirmId(firmC.getId()).build());

        // Invoice 3: Unrelated migration chain (Firm C -> Firm D, neither selected)
        Invoice inv3 = createTestInvoice(firmC, customerRepo.save(Customer.builder().firmId(firmC.getId()).name("Cust C3").build()), prodC);
        TenantContext.setCurrentFirmId(firmC.getId());
        migrationService.migrateInvoice(inv3.getId(), InvoiceMigrationRequest.builder().targetFirmId(firmD.getId()).build());
        Long inv3RecordId = migrationRecordRepo.findByInvoiceIdOrderByCreatedAtDesc(inv3.getId()).get(0).getId();

        // Export full backup
        BackupDTO fullBackup = backupService.exportAllData();

        // Selective restore of ONLY Firm A and Firm B
        Set<Long> selectedFirms = new HashSet<>();
        selectedFirms.add(firmA.getId());
        selectedFirms.add(firmB.getId());

        // Clear records for inv1 and inv2 so we can verify what selective restore actually imports
        migrationRecordRepo.deleteAll(migrationRecordRepo.findByInvoiceIdOrderByCreatedAtDesc(inv1.getId()));
        migrationRecordRepo.deleteAll(migrationRecordRepo.findByInvoiceIdOrderByCreatedAtDesc(inv2.getId()));

        backupService.importSelectiveData(fullBackup, selectedFirms, "merge", null);

        // Assert Invoice 1 migration record is restored (both Firm A and Firm B were selected)
        List<InvoiceMigrationRecord> inv1Records = migrationRecordRepo.findByInvoiceIdOrderByCreatedAtDesc(inv1.getId());
        assertFalse(inv1Records.isEmpty(), "Migration record where both source and target firms are selected must be restored");

        // Assert Invoice 2 migration record is SKIPPED (Firm C was not selected, so relationship cannot be faithfully represented)
        List<InvoiceMigrationRecord> inv2Records = migrationRecordRepo.findByInvoiceIdOrderByCreatedAtDesc(inv2.getId());
        assertTrue(inv2Records.isEmpty(), "Migration record where target firm was not selected must be skipped to prevent inconsistent state");

        // Assert Invoice 3's existing record in the database is retained
        assertTrue(migrationRecordRepo.existsById(inv3RecordId), "Existing unrelated migration records must remain untouched");
    }

    @Test
    @DisplayName("Group 10B / Req 5: Clone restore maps firm and invoice IDs, updates snapshot JSON, and enables post-clone revert")
    void testCloneRestore_FullFidelityWithRemappedFirmAndInvoiceIds() throws Exception {
        // 1. Create and migrate invoice in Firm A -> Firm B
        Invoice originalInv = createTestInvoice(firmA, customerA, productA);
        Long origInvoiceId = originalInv.getId();
        TenantContext.setCurrentFirmId(firmA.getId());
        migrationService.migrateInvoice(origInvoiceId, InvoiceMigrationRequest.builder()
                .targetFirmId(firmB.getId())
                .build());

        // 2. Export full backup
        BackupDTO fullBackup = backupService.exportAllData();
        System.err.println("EXPORTED SNAPSHOT: " + fullBackup.getInvoiceMigrationRecords().get(0).getPreMigrationSnapshot());

        // 3. Restore in CLONE mode
        List<FirmDetails> clonedFirms = backupService.importSelectiveData(fullBackup, null, "clone", null);
        assertNotNull(clonedFirms);
        assertEquals(2, clonedFirms.size());

        FirmDetails clonedFirmA = clonedFirms.stream().filter(f -> f.getFirmName().contains("Firm A")).findFirst().orElseThrow();
        FirmDetails clonedFirmB = clonedFirms.stream().filter(f -> f.getFirmName().contains("Firm B")).findFirst().orElseThrow();

        // Cloned firm IDs must be different from original firm IDs
        assertNotEquals(firmA.getId(), clonedFirmA.getId());
        assertNotEquals(firmB.getId(), clonedFirmB.getId());

        // Find cloned invoice in Cloned Firm B
        TenantContext.setCurrentFirmId(clonedFirmB.getId());
        List<Invoice> clonedInvoices = invoiceRepo.findAllByFirmIdWithItems(clonedFirmB.getId());
        assertFalse(clonedInvoices.isEmpty());
        Invoice clonedInv = clonedInvoices.get(0);
        assertNotEquals(origInvoiceId, clonedInv.getId());

        // 4. Verify cloned migration record has remapped IDs
        List<InvoiceMigrationRecord> clonedRecords = migrationRecordRepo.findByInvoiceIdOrderByCreatedAtDesc(clonedInv.getId());
        assertFalse(clonedRecords.isEmpty());
        InvoiceMigrationRecord clonedRec = clonedRecords.get(0);

        assertEquals(clonedInv.getId(), clonedRec.getInvoiceId(), "Migration record invoiceId must match cloned invoice ID");
        assertEquals(clonedFirmA.getId(), clonedRec.getSourceFirmId(), "Migration record sourceFirmId must match cloned Firm A ID");
        assertEquals(clonedFirmB.getId(), clonedRec.getTargetFirmId(), "Migration record targetFirmId must match cloned Firm B ID");

        // 5. Verify snapshot JSON was updated with remapped firm and invoice IDs
        assertNotNull(clonedRec.getPreMigrationSnapshot());
        com.fasterxml.jackson.databind.JsonNode snapshotNode = objectMapper.readTree(clonedRec.getPreMigrationSnapshot());
        assertEquals(clonedFirmA.getId(), snapshotNode.get("sourceFirmId").asLong(), "Snapshot sourceFirmId must be remapped to cloned Firm A ID");
        assertEquals(clonedInv.getId(), snapshotNode.get("invoiceId").asLong(), "Snapshot invoiceId must be remapped to cloned invoice ID");

        // 6. Verify history endpoint works in cloned context
        List<InvoiceMigrationRecordDTO> history = migrationService.getMigrationHistory(clonedInv.getId());
        assertFalse(history.isEmpty());
        assertEquals(clonedFirmA.getId(), history.get(0).getSourceFirmId());
        assertEquals(clonedFirmB.getId(), history.get(0).getTargetFirmId());

        // 7. Verify post-clone revert succeeds!
        migrationService.revertMigration(clonedInv.getId());

        Invoice revertedClonedInv = invoiceRepo.findById(clonedInv.getId()).orElseThrow();
        assertEquals(clonedFirmA.getId(), revertedClonedInv.getFirmId(), "Cloned invoice must revert to cloned Firm A");
    }

    @Test
    @DisplayName("Group 10B / Req 5 & 2: Restore failure rolls back invoice and migration changes, leaving original state untouched")
    void testRestoreTransactionRollback_PreservesOriginalMigrationState() {
        // 1. Create and migrate invoice
        Invoice inv = createTestInvoice(firmA, customerA, productA);
        TenantContext.setCurrentFirmId(firmA.getId());
        migrationService.migrateInvoice(inv.getId(), InvoiceMigrationRequest.builder()
                .targetFirmId(firmB.getId())
                .build());

        List<InvoiceMigrationRecord> preRecords = migrationRecordRepo.findByInvoiceIdOrderByCreatedAtDesc(inv.getId());
        assertFalse(preRecords.isEmpty());
        Long origRecordId = preRecords.get(0).getId();

        // 2. Export full backup
        BackupDTO backup = backupService.exportAllData();

        // Inject duplicate conflicting invoice into backup to trigger RestoreConflictException midway through restore
        Invoice dup = new Invoice();
        dup.setId(999999L);
        dup.setFirmId(firmB.getId());
        dup.setInvoiceNumber(inv.getInvoiceNumber());
        List<Invoice> invList = new ArrayList<>(backup.getInvoices());
        invList.add(dup);
        backup.setInvoices(invList);

        assertThrows(Exception.class, () -> backupService.importSelectiveData(backup, null, "merge", null));

        // 3. Verify original record was NOT deleted or altered
        assertTrue(migrationRecordRepo.existsById(origRecordId), "Original migration record must be preserved after rollback");
    }
}
