package com.billing.simple.billsoft.regression.backup;

import com.billing.simple.billsoft.BillsoftApplication;
import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.exception.BackupValidationException;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.repositories.*;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.service.BackupService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = BillsoftApplication.class)
@ActiveProfiles("test")
public class BackupCrossFirmRestoreIsolationRegressionTest {

    @Autowired
    private BackupService backupService;

    @Autowired
    private FirmDetailsRepository firmDetailsRepo;
    @Autowired
    private CustomerRepository customerRepo;
    @Autowired
    private ProductRepository productRepo;
    @Autowired
    private InvoiceRepository invoiceRepo;
    @Autowired
    private PartyRepository partyRepo;
    @Autowired
    private PurchaseOrderRepository purchaseOrderRepo;
    @Autowired
    private PricingProfileRepository pricingProfileRepo;
    @Autowired
    private ReminderRepository reminderRepo;
    @Autowired
    private NoteRepository noteRepo;
    @Autowired
    private NotificationRepository notificationRepo;

    @BeforeEach
    void setupCleanDatabase() {
        backupService.factoryReset();
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("1. Cloning Firm B while Firm A is active creates a separate firm and preserves Firm A data")
    public void testCloneFirmBWhileFirmAIsActiveCreatesSeparateFirmAndPreservesFirmA() {
        // Step 1: Create active Firm A with existing business data
        FirmDetails firmA = new FirmDetails();
        firmA.setFirmName("Apexx Global Enterprise");
        firmA.setGstin("27APEXX1234F1Z5");
        firmA = firmDetailsRepo.save(firmA);
        Long firmAId = firmA.getId();

        Customer cA1 = new Customer();
        cA1.setFirmId(firmAId);
        cA1.setName("Firm A Client 1");
        cA1.setPhone("9111111111");
        customerRepo.save(cA1);

        Customer cA2 = new Customer();
        cA2.setFirmId(firmAId);
        cA2.setName("Firm A Client 2");
        cA2.setPhone("9111111112");
        customerRepo.save(cA2);

        Product pA1 = Product.builder().firmId(firmAId).name("Firm A Product").sku("APEXX-01").price(new BigDecimal("500.00")).build();
        productRepo.save(pA1);

        Invoice invA1 = Invoice.builder().firmId(firmAId).invoiceNumber("INV-A-001").totalAmount(new BigDecimal("500.00")).invoiceDate(LocalDateTime.now()).build();
        invoiceRepo.save(invA1);

        // Baseline assertions for Firm A
        assertEquals(2, customerRepo.countByFirmId(firmAId));
        assertEquals(1, productRepo.countByFirmId(firmAId));
        assertEquals(1, invoiceRepo.countByFirmId(firmAId));

        // Step 2: Prepare a single-firm backup of Firm B (source ID 97)
        BackupDTO backupB = createSampleBackup(97L, "SANGAM HARDWARE", "27SANGAM9876F1Z1", 4, 3, 2);

        // Step 3: Simulate active session in Firm A and execute clone restore
        // Even if firmAId is passed as targetFirmId, clone mode MUST disregard it!
        TenantContext.setCurrentFirmId(firmAId);
        List<FirmDetails> restored = backupService.importSelectiveData(backupB, Collections.singleton(97L), "clone", firmAId);

        assertNotNull(restored);
        assertEquals(1, restored.size());
        FirmDetails newFirmB = restored.get(0);
        Long newFirmBId = newFirmB.getId();

        // Step 4: Validate strict tenant isolation
        assertNotEquals(firmAId, newFirmBId, "Cloned firm must receive a brand new database ID");
        assertNotEquals(97L, newFirmBId, "Cloned firm ID must be newly generated in current database");

        // Firm A records MUST remain strictly unchanged
        assertEquals(2, customerRepo.countByFirmId(firmAId), "Firm A customer count must remain 2");
        assertEquals(1, productRepo.countByFirmId(firmAId), "Firm A product count must remain 1");
        assertEquals(1, invoiceRepo.countByFirmId(firmAId), "Firm A invoice count must remain 1");

        // Cloned Firm B records MUST all belong to newFirmBId
        assertEquals(4, customerRepo.countByFirmId(newFirmBId), "Cloned firm must contain exactly 4 customers");
        assertEquals(3, productRepo.countByFirmId(newFirmBId), "Cloned firm must contain exactly 3 products");
        assertEquals(2, invoiceRepo.countByFirmId(newFirmBId), "Cloned firm must contain exactly 2 invoices");

        // Verify that none of Firm B's customers, products, or invoices were written to Firm A
        List<Customer> firmACustomers = customerRepo.findByFirmId(firmAId);
        assertTrue(firmACustomers.stream().noneMatch(c -> c.getName().startsWith("SANGAM")), "Firm A must have zero SANGAM customers");
    }

    @Test
    @DisplayName("2. Source and destination firm IDs may collide with external IDs without cross-firm assignment")
    public void testSourceAndDestinationIdCollisionWithoutCrossFirmAssignment() {
        // Firm in local DB has ID 1 (e.g. generated as first firm)
        FirmDetails localFirm1 = new FirmDetails();
        localFirm1.setFirmName("Local First Firm");
        localFirm1 = firmDetailsRepo.save(localFirm1);
        Long localFirm1Id = localFirm1.getId();

        Customer cLocal = new Customer();
        cLocal.setFirmId(localFirm1Id);
        cLocal.setName("Local Client");
        customerRepo.save(cLocal);

        // Backup from external system where source firm also had ID 1
        BackupDTO externalBackup = createSampleBackup(localFirm1Id, "External Transferred Firm", "27EXT1111F1Z1", 3, 2, 1);

        // Execute clone restore
        List<FirmDetails> restored = backupService.importSelectiveData(externalBackup, Collections.singleton(localFirm1Id), "clone", null);
        assertEquals(1, restored.size());
        Long newDestinationId = restored.get(0).getId();

        assertNotEquals(localFirm1Id, newDestinationId, "Destination ID must be newly allocated and not collide with existing ID 1");

        // Local Firm 1 must still have only 1 customer
        assertEquals(1, customerRepo.countByFirmId(localFirm1Id));
        // New destination firm must receive all 3 imported customers
        assertEquals(3, customerRepo.countByFirmId(newDestinationId));
    }

    @Test
    @DisplayName("3. A caller-supplied target ID cannot override the destination in clone mode")
    public void testSuppliedTargetIdCannotOverrideDestinationInCloneMode() {
        FirmDetails existingFirm = new FirmDetails();
        existingFirm.setFirmName("Protected Firm");
        existingFirm = firmDetailsRepo.save(existingFirm);
        Long existingFirmId = existingFirm.getId();

        BackupDTO backup = createSampleBackup(88L, "Incoming Clone Firm", "27INCL8888F1Z8", 2, 2, 1);

        // Pass existingFirmId explicitly as targetFirmId with mode 'clone'
        List<FirmDetails> restored = backupService.importSelectiveData(backup, Collections.singleton(88L), "clone", existingFirmId);
        assertEquals(1, restored.size());
        Long createdFirmId = restored.get(0).getId();

        assertNotEquals(existingFirmId, createdFirmId, "Clone mode must create a new firm and ignore supplied targetFirmId");
        assertEquals(0, customerRepo.countByFirmId(existingFirmId), "Supplied target firm must not receive any cloned records");
        assertEquals(2, customerRepo.countByFirmId(createdFirmId), "New firm must receive all cloned records");
    }

    @Test
    @DisplayName("4. Restore-into-existing-firm ('merge') works with valid destination and rejects invalid destination")
    public void testRestoreIntoExistingFirmModeValidation() {
        FirmDetails targetFirm = new FirmDetails();
        targetFirm.setFirmName("Target Firm For Merge");
        targetFirm = firmDetailsRepo.save(targetFirm);
        Long targetFirmId = targetFirm.getId();

        BackupDTO backup = createSampleBackup(55L, "Source Firm", "27SRC5555F1Z5", 2, 2, 1);

        // Valid merge:
        List<FirmDetails> restored = backupService.importSelectiveData(backup, Collections.singleton(55L), "merge", targetFirmId);
        assertEquals(1, restored.size());
        assertEquals(targetFirmId, restored.get(0).getId());
        assertEquals(2, customerRepo.countByFirmId(targetFirmId));

        // Invalid merge - missing targetFirmId:
        assertThrows(IllegalArgumentException.class, () -> {
            backupService.importSelectiveData(backup, Collections.singleton(55L), "merge", null);
        }, "Merge restore without targetFirmId must be rejected");

        // Invalid merge - non-existent targetFirmId:
        assertThrows(IllegalArgumentException.class, () -> {
            backupService.importSelectiveData(backup, Collections.singleton(55L), "merge", 999999L);
        }, "Merge restore into non-existent targetFirmId must be rejected");
    }

    @Test
    @DisplayName("5. Multi-firm backup maps every source firm independently in clone mode")
    public void testMultiFirmBackupMapsEverySourceFirmIndependently() {
        BackupDTO multiBackup = new BackupDTO();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("version", "2.0");
        metadata.put("type", "FULL_SYSTEM_BACKUP");
        multiBackup.setMetadata(metadata);

        FirmDetails sourceFirm1 = new FirmDetails();
        sourceFirm1.setId(10L);
        sourceFirm1.setFirmName("Source Alpha");
        sourceFirm1.setGstin("27ALPHA1010F1Z1");

        FirmDetails sourceFirm2 = new FirmDetails();
        sourceFirm2.setId(20L);
        sourceFirm2.setFirmName("Source Beta");
        sourceFirm2.setGstin("27BETA2020F1Z2");

        multiBackup.setAllFirms(Arrays.asList(sourceFirm1, sourceFirm2));

        Customer c1 = new Customer();
        c1.setId(101L);
        c1.setFirmId(10L);
        c1.setName("Alpha Customer 1");

        Customer c2 = new Customer();
        c2.setId(201L);
        c2.setFirmId(20L);
        c2.setName("Beta Customer 1");

        multiBackup.setCustomers(Arrays.asList(c1, c2));

        // Restore both firms in clone mode
        List<FirmDetails> restored = backupService.importSelectiveData(multiBackup, null, "clone", null);
        assertEquals(2, restored.size());

        Long newAlphaId = restored.stream().filter(f -> f.getFirmName().contains("Alpha")).findFirst().get().getId();
        Long newBetaId = restored.stream().filter(f -> f.getFirmName().contains("Beta")).findFirst().get().getId();

        assertNotEquals(newAlphaId, newBetaId, "Alpha and Beta firms must receive separate destination IDs");
        assertEquals(1, customerRepo.countByFirmId(newAlphaId));
        assertEquals(1, customerRepo.countByFirmId(newBetaId));

        assertEquals("Alpha Customer 1", customerRepo.findByFirmId(newAlphaId).get(0).getName());
        assertEquals("Beta Customer 1", customerRepo.findByFirmId(newBetaId).get(0).getName());
    }

    @Test
    @DisplayName("6. Missing firm metadata and invalid restore modes fail safely")
    public void testMissingMetadataAndInvalidModesFailSafely() {
        BackupDTO emptyBackup = new BackupDTO();
        // Missing metadata
        assertThrows(BackupValidationException.class, () -> {
            backupService.importSelectiveData(emptyBackup, null, "clone", null);
        });

        BackupDTO validBackup = createSampleBackup(77L, "Test Firm", "27TEST7777F1Z7", 1, 1, 0);

        // Invalid unsupported mode
        assertThrows(IllegalArgumentException.class, () -> {
            backupService.importSelectiveData(validBackup, Collections.singleton(77L), "invalid_mode_xyz", null);
        });
    }

    @Test
    @DisplayName("7. Firm-scoped settings, pricing profiles and notes retain correct ownership")
    public void testFirmScopedSettingsAndRelatedEntitiesRetainOwnership() {
        BackupDTO backup = createSampleBackup(44L, "Specialized Services", "27SPEC4444F1Z4", 1, 1, 1);

        PricingProfile pp = PricingProfile.builder().firmId(44L).name("Wholesale Tier").code("WHOLESALE").isActive(true).build();
        pp.setId(1001L);
        backup.setPricingProfiles(Collections.singletonList(pp));

        Reminder r = Reminder.builder().firmId(44L).title("Tax Filing").dueDate(LocalDateTime.now()).completed(false).build();
        r.setId(2001L);
        backup.setReminders(Collections.singletonList(r));

        Note n = Note.builder().firmId(44L).title("Meeting Notes").content("Discussion").build();
        n.setId(3001L);
        backup.setNotes(Collections.singletonList(n));

        List<FirmDetails> restored = backupService.importSelectiveData(backup, Collections.singleton(44L), "clone", null);
        assertEquals(1, restored.size());
        Long destFirmId = restored.get(0).getId();

        List<PricingProfile> profiles = pricingProfileRepo.findByFirmIdOrderByIdAsc(destFirmId);
        assertEquals(1, profiles.size());
        assertEquals("WHOLESALE", profiles.get(0).getCode());
        assertEquals(destFirmId, profiles.get(0).getFirmId());

        List<Reminder> reminders = reminderRepo.findByFirmId(destFirmId);
        assertEquals(1, reminders.size());
        assertEquals(destFirmId, reminders.get(0).getFirmId());

        List<Note> notes = noteRepo.findByFirmId(destFirmId);
        assertEquals(1, notes.size());
        assertEquals(destFirmId, notes.get(0).getFirmId());
    }

    @Test
    @DisplayName("8. Active firm/session is not silently switched or corrupted as a side effect of restoration")
    public void testActiveFirmSessionContextPreservedDuringCloneRestore() {
        // Arrange active firm A
        FirmDetails firmA = new FirmDetails();
        firmA.setFirmName("Original Active Firm A");
        firmA.setGstin("27ACTV1111F1Z1");
        firmA = firmDetailsRepo.save(firmA);
        Long activeFirmId = firmA.getId();

        TenantContext.setCurrentFirmId(activeFirmId);

        // Backup for Firm B
        BackupDTO backupB = createSampleBackup(77L, "External Firm B", "27EXT7777F1Z2", 2, 2, 2);

        // Act: restore Firm B in clone mode
        List<FirmDetails> restored = backupService.importSelectiveData(backupB, Collections.singleton(77L), "clone", null);
        assertEquals(1, restored.size());
        Long clonedFirmBId = restored.get(0).getId();
        assertNotEquals(activeFirmId, clonedFirmBId);

        // Verify Firm A remains pristine and unmodified in repository
        FirmDetails freshFirmA = firmDetailsRepo.findById(activeFirmId).orElse(null);
        assertNotNull(freshFirmA);
        assertEquals("Original Active Firm A", freshFirmA.getFirmName());
        assertEquals(0, customerRepo.findByFirmId(activeFirmId).size());
        assertEquals(0, invoiceRepo.findByFirmId(activeFirmId).size());

        // Verify Cloned Firm B has its own independent data
        assertEquals(2, customerRepo.findByFirmId(clonedFirmBId).size());
        assertEquals(2, invoiceRepo.findByFirmId(clonedFirmBId).size());
    }

    // --- Helper to construct valid mock BackupDTO instances ---
    private BackupDTO createSampleBackup(Long sourceFirmId, String firmName, String gstin, int custCount, int prodCount, int invCount) {
        BackupDTO dto = new BackupDTO();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("version", "2.0");
        metadata.put("type", "SINGLE_FIRM_BACKUP");
        metadata.put("sourceFirmId", sourceFirmId);
        dto.setMetadata(metadata);
        dto.setSourceFirmId(sourceFirmId);

        FirmDetails fd = new FirmDetails();
        fd.setId(sourceFirmId);
        fd.setFirmName(firmName);
        fd.setGstin(gstin);
        dto.setFirmDetails(fd);

        List<Customer> customers = new ArrayList<>();
        for (int i = 1; i <= custCount; i++) {
            Customer c = new Customer();
            c.setId((long) (i * 100));
            c.setFirmId(sourceFirmId);
            c.setName(firmName + " Customer " + i);
            c.setPhone("980000000" + i);
            customers.add(c);
        }
        dto.setCustomers(customers);

        List<Product> products = new ArrayList<>();
        for (int i = 1; i <= prodCount; i++) {
            Product p = Product.builder()
                    .id((long) (i * 200))
                    .firmId(sourceFirmId)
                    .name(firmName + " Product " + i)
                    .sku("SKU-" + sourceFirmId + "-" + i)
                    .price(new BigDecimal("100.00").multiply(new BigDecimal(i)))
                    .build();
            products.add(p);
        }
        dto.setProducts(products);

        List<Invoice> invoices = new ArrayList<>();
        for (int i = 1; i <= invCount; i++) {
            Invoice inv = Invoice.builder()
                    .id((long) (i * 300))
                    .firmId(sourceFirmId)
                    .invoiceNumber("INV-" + sourceFirmId + "-00" + i)
                    .totalAmount(new BigDecimal("250.00").multiply(new BigDecimal(i)))
                    .invoiceDate(LocalDateTime.now())
                    .build();
            invoices.add(inv);
        }
        dto.setInvoices(invoices);

        return dto;
    }
}
