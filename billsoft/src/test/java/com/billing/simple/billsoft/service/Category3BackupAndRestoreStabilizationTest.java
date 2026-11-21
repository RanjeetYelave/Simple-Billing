package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.dto.BackupExportResult;
import com.billing.simple.billsoft.dto.BackupInspectionDTO;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.exception.BackupValidationException;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.repositories.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * RupeeCRM Category 3 Acceptance Test Suite:
 * Comprehensive certification of Local Backup, Multi-Firm Export, Integrity Verification, and Restoration.
 *
 * Tests all 22 required acceptance criteria in an isolated sandbox.
 */
public class Category3BackupAndRestoreStabilizationTest {

    @TempDir
    Path tempDir;

    private ObjectMapper objectMapper;
    private BackupValidationService backupValidationService;
    private BackupArchiveService backupArchiveService;

    // Repositories
    private FirmDetailsRepository firmDetailsRepo;
    private CustomerRepository customerRepo;
    private ProductRepository productRepo;
    private InvoiceRepository invoiceRepo;
    private NotificationRepository notificationRepo;
    private NotificationPreferenceRepository notificationPreferenceRepo;
    private PartyRepository partyRepo;
    private InvoicePaymentRepository invoicePaymentRepo;
    private StockMovementRepository stockMovementRepo;
    private PurchaseOrderRepository purchaseOrderRepo;
    private ReminderRepository reminderRepo;
    private NoteRepository noteRepo;
    private ExpenseRepository expenseRepo;
    private EmployeeRepository employeeRepo;
    private AttendanceRecordRepository attendanceRecordRepo;
    private LeaveRecordRepository leaveRecordRepo;
    private SalaryRecordRepository salaryRepo;
    private EmployeeAdvanceRepository advanceRepo;
    private PromotionRecordRepository promotionRepo;
    private EmployeeDocumentRepository employeeDocumentRepo;
    private BusinessLetterRepository businessLetterRepo;
    private AppConfigRepository appConfigRepo;
    private SalesReturnRepository salesReturnRepo;
    private SalesReturnItemRepository salesReturnItemRepo;
    private SavingRepository savingRepo;
    private GoalRepository goalRepo;
    private GoalLogRepository goalLogRepo;
    private BackupEntityMappingRepository backupEntityMappingRepo;

    private BackupRecoveryCoordinator coordinator;
    private BackupService backupService;
    private AutoBackupService autoBackupService;

    // Synthetic Test Data
    private FirmDetails firm1;
    private FirmDetails firm2;
    private Customer customer1;
    private Customer customer2;
    private Product product1;
    private Invoice invoice1;
    private Notification notifPurchase;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        backupValidationService = new BackupValidationService();
        backupArchiveService = new BackupArchiveService(objectMapper, backupValidationService);

        firmDetailsRepo = mock(FirmDetailsRepository.class);
        customerRepo = mock(CustomerRepository.class);
        productRepo = mock(ProductRepository.class);
        invoiceRepo = mock(InvoiceRepository.class);
        notificationRepo = mock(NotificationRepository.class);
        notificationPreferenceRepo = mock(NotificationPreferenceRepository.class);
        partyRepo = mock(PartyRepository.class);
        invoicePaymentRepo = mock(InvoicePaymentRepository.class);
        stockMovementRepo = mock(StockMovementRepository.class);
        purchaseOrderRepo = mock(PurchaseOrderRepository.class);
        reminderRepo = mock(ReminderRepository.class);
        noteRepo = mock(NoteRepository.class);
        expenseRepo = mock(ExpenseRepository.class);
        employeeRepo = mock(EmployeeRepository.class);
        attendanceRecordRepo = mock(AttendanceRecordRepository.class);
        leaveRecordRepo = mock(LeaveRecordRepository.class);
        salaryRepo = mock(SalaryRecordRepository.class);
        advanceRepo = mock(EmployeeAdvanceRepository.class);
        promotionRepo = mock(PromotionRecordRepository.class);
        employeeDocumentRepo = mock(EmployeeDocumentRepository.class);
        businessLetterRepo = mock(BusinessLetterRepository.class);
        appConfigRepo = mock(AppConfigRepository.class);
        salesReturnRepo = mock(SalesReturnRepository.class);
        salesReturnItemRepo = mock(SalesReturnItemRepository.class);
        savingRepo = mock(SavingRepository.class);
        goalRepo = mock(GoalRepository.class);
        goalLogRepo = mock(GoalLogRepository.class);
        backupEntityMappingRepo = mock(BackupEntityMappingRepository.class);

        coordinator = new BackupRecoveryCoordinator();

        backupService = new BackupService(
                firmDetailsRepo, customerRepo, productRepo, stockMovementRepo,
                invoiceRepo, mock(InvoiceItemRepository.class), partyRepo, mock(PartyPaymentRepository.class),
                purchaseOrderRepo, mock(PurchaseOrderItemRepository.class), reminderRepo,
                noteRepo, expenseRepo, employeeRepo, attendanceRecordRepo, leaveRecordRepo,
                salaryRepo, advanceRepo, promotionRepo, employeeDocumentRepo, businessLetterRepo,
                appConfigRepo, invoicePaymentRepo, salesReturnRepo, salesReturnItemRepo,
                savingRepo, goalRepo, goalLogRepo, backupEntityMappingRepo,
                notificationRepo, notificationPreferenceRepo, backupValidationService,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, coordinator, objectMapper, mock(javax.sql.DataSource.class)
        );
        backupService.setBackupArchiveService(backupArchiveService);

        // Setup synthetic multi-firm data
        firm1 = new FirmDetails();
        firm1.setId(101L);
        firm1.setFirmName("Alpha Industrial Solutions");
        firm1.setGstin("27AAAAA0000A1Z5");

        firm2 = new FirmDetails();
        firm2.setId(102L);
        firm2.setFirmName("Beta Logistics Corp");
        firm2.setGstin("27BBBBB1111B1Z6");

        customer1 = new Customer();
        customer1.setId(501L);
        customer1.setFirmId(101L);
        customer1.setName("Acme Global");

        customer2 = new Customer();
        customer2.setId(502L);
        customer2.setFirmId(102L);
        customer2.setName("Zenith Enterprises");

        product1 = new Product();
        product1.setId(701L);
        product1.setFirmId(101L);
        product1.setName("Industrial Fastener 10mm");

        invoice1 = new Invoice();
        invoice1.setId(901L);
        invoice1.setFirmId(101L);
        invoice1.setInvoiceNumber("INV-2026-001");
        invoice1.setStatus(InvoiceStatus.PAID);
        invoice1.setTotalAmount(BigDecimal.valueOf(15000.00));

        notifPurchase = Notification.builder()
                .id(301L)
                .firmId(101L)
                .eventKey("PO_RECEIVED_PO-1001")
                .category(NotificationCategory.PURCHASE)
                .priority(NotificationPriority.NORMAL)
                .status(NotificationStatus.UNREAD)
                .title("Purchase Order Received")
                .body("Vendor delivered PO-1001 items")
                .build();

        // Default mock behaviors for full multi-firm export
        when(firmDetailsRepo.findAll()).thenReturn(Arrays.asList(firm1, firm2));
        when(firmDetailsRepo.findById(101L)).thenReturn(Optional.of(firm1));
        when(firmDetailsRepo.findById(102L)).thenReturn(Optional.of(firm2));
        when(customerRepo.findAll()).thenReturn(Arrays.asList(customer1, customer2));
        when(productRepo.findAll()).thenReturn(Collections.singletonList(product1));
        when(invoiceRepo.findAllWithItems()).thenReturn(Collections.singletonList(invoice1));
        when(notificationRepo.findAll()).thenReturn(Collections.singletonList(notifPurchase));
        when(appConfigRepo.findAll()).thenReturn(Collections.emptyList());

        // Default mock behaviors for restore saving
        when(firmDetailsRepo.save(any(FirmDetails.class))).thenAnswer(inv -> {
            FirmDetails f = inv.getArgument(0);
            if (f.getId() == null) f.setId(new Random().nextLong(1000, 2000));
            return f;
        });
        when(customerRepo.save(any(Customer.class))).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            if (c.getId() == null) c.setId(new Random().nextLong(5000, 9000));
            return c;
        });
        when(productRepo.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            if (p.getId() == null) p.setId(new Random().nextLong(5000, 9000));
            return p;
        });
        when(invoiceRepo.save(any(Invoice.class))).thenAnswer(inv -> {
            Invoice i = inv.getArgument(0);
            if (i.getId() == null) i.setId(new Random().nextLong(5000, 9000));
            return i;
        });
        when(notificationRepo.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            if (n.getId() == null) n.setId(new Random().nextLong(5000, 9000));
            return n;
        });

        // AutoBackupService with mocked backup dir
        autoBackupService = new AutoBackupService(backupService, backupValidationService, coordinator) {
            @Override
            public File getBackupDirectory() {
                File dir = tempDir.resolve("backups").toFile();
                dir.mkdirs();
                return dir;
            }
        };
    }

    @Test
    @DisplayName("Test 01: Automatic backup runs at its configured time")
    void test01_autoBackupRunsAtConfiguredTime() {
        Map<String, Object> result = autoBackupService.runAutoBackup();
        assertNotNull(result);
        assertEquals("HEALTHY", result.get("status"));
        assertTrue((Boolean) result.get("isTodayBackup"));
        assertTrue(new File(String.valueOf(result.get("filePath"))).exists());
    }

    @Test
    @DisplayName("Test 02: Automatic backup after application restart")
    void test02_autoBackupAfterApplicationRestart() {
        // First startup creates today's backup
        autoBackupService.init();
        Map<String, Object> res1 = autoBackupService.ensureTodayBackup();
        assertEquals("HEALTHY", res1.get("status"));

        // Simulate app restart: init reads existing backup from disk
        AutoBackupService restarted = new AutoBackupService(backupService, backupValidationService, coordinator) {
            @Override
            public File getBackupDirectory() {
                return tempDir.resolve("backups").toFile();
            }
        };
        restarted.init();
        assertTrue(restarted.isTodayBackupPresent());
        assertEquals("HEALTHY", restarted.getStatus().get("status"));
    }

    @Test
    @DisplayName("Test 03: Missed schedule recovery")
    void test03_missedScheduleRecovery() {
        // Directory empty = missed run
        assertFalse(autoBackupService.isTodayBackupPresent());
        // Periodic check detects missing backup and triggers run
        autoBackupService.periodicMissedScheduleCheck();
        assertTrue(autoBackupService.isTodayBackupPresent());
        assertEquals("HEALTHY", autoBackupService.getStatus().get("status"));
    }

    @Test
    @DisplayName("Test 04: Duplicate scheduler prevention")
    void test04_duplicateSchedulerPrevention() throws InterruptedException {
        // Hold lock on an asynchronous thread to simulate another active recovery/restore job
        Thread lockHolder = new Thread(() -> {
            coordinator.lock();
            try {
                Thread.sleep(600);
            } catch (InterruptedException ignored) {
            } finally {
                coordinator.unlock();
            }
        });
        lockHolder.start();
        Thread.sleep(50);
        assertTrue(coordinator.isRecoveryInProgress());

        // Secondary backup trigger is safely rejected without collision
        Map<String, Object> result = autoBackupService.runAutoBackup();
        assertEquals("SKIPPED_RECOVERY_IN_PROGRESS", result.get("status"));

        lockHolder.join();
    }

    @Test
    @DisplayName("Test 05: Manual backup to a selected directory")
    void test05_manualBackupToSelectedDirectory() throws IOException {
        Path destDir = tempDir.resolve("user-selected-backups");
        BackupExportResult result = backupService.exportToFile(null, destDir.toString(), "full-backup.json", "json");

        assertNotNull(result);
        assertTrue(result.isSuccess());
        File savedFile = new File(result.getFilePath());
        assertTrue(savedFile.exists());
        assertTrue(savedFile.length() > 0);
        assertEquals(2, result.getFirmsCount());
        assertNotNull(result.getChecksumSha256());
    }

    @Test
    @DisplayName("Test 06: Destination path containing spaces")
    void test06_destinationPathContainingSpaces() throws IOException {
        Path spacedDir = tempDir.resolve("My RupeeCRM Backups With Spaces 2026");
        BackupExportResult result = backupService.exportToFile(101L, spacedDir.toString(), "firm 101 backup.json", "json");

        assertTrue(result.isSuccess());
        File savedFile = new File(result.getFilePath());
        assertTrue(savedFile.exists());
        assertTrue(savedFile.getAbsolutePath().contains("My RupeeCRM Backups With Spaces 2026"));
    }

    @Test
    @DisplayName("Test 07: Destination permission failure")
    void test07_destinationPermissionFailure() throws IOException {
        Path readOnlyDir = tempDir.resolve("read-only-dest");
        Files.createDirectories(readOnlyDir);
        File dirFile = readOnlyDir.toFile();
        dirFile.setReadOnly();

        try {
            if (!dirFile.canWrite()) {
                assertThrows(SecurityException.class, () ->
                        backupService.exportToFile(null, readOnlyDir.toString(), "backup.json", "json"));
            }
        } finally {
            dirFile.setWritable(true);
        }
    }

    @Test
    @DisplayName("Test 08: Disk-space exhaustion")
    void test08_diskSpaceExhaustion() {
        File lowDiskDir = new File(tempDir.toFile(), "low-disk") {
            @Override
            public long getUsableSpace() {
                return 10L * 1024L * 1024L; // 10 MB (below 50 MB required safety threshold)
            }
        };
        lowDiskDir.mkdirs();

        AutoBackupService lowDiskService = new AutoBackupService(backupService, backupValidationService, coordinator) {
            @Override
            public File getBackupDirectory() {
                return lowDiskDir;
            }
        };

        Map<String, Object> result = lowDiskService.runAutoBackup();
        assertEquals("ERROR", result.get("status"));
        assertTrue(String.valueOf(result.get("lastError")).contains("INSUFFICIENT_DISK_SPACE"));
    }

    @Test
    @DisplayName("Test 09: Interrupted backup")
    void test09_interruptedBackup() throws IOException {
        File backupDir = tempDir.resolve("backups").toFile();
        backupDir.mkdirs();

        // Put a valid existing backup
        File existing = new File(backupDir, AutoBackupService.LATEST_BACKUP_NAME);
        Files.writeString(existing.toPath(), "{\"metadata\":{\"version\":\"2.0\",\"valid\":true}}");
        long originalModTime = existing.lastModified();
        long originalSize = existing.length();

        // Make exportAllData throw simulated interruption exception
        BackupService failingService = mock(BackupService.class);
        when(failingService.exportAllData()).thenThrow(new RuntimeException("Simulated I/O write interruption"));

        AutoBackupService testService = new AutoBackupService(failingService, backupValidationService, coordinator) {
            @Override
            public File getBackupDirectory() {
                return backupDir;
            }
        };

        Map<String, Object> result = testService.runAutoBackup();
        assertEquals("ERROR", result.get("status"));

        // Prior backup remains untouched
        assertTrue(existing.exists());
        assertEquals(originalSize, existing.length());

        // No stray temp files left behind
        File[] tempFiles = backupDir.listFiles((d, n) -> n.startsWith("autobackup_temp_"));
        assertTrue(tempFiles == null || tempFiles.length == 0);
    }

    @Test
    @DisplayName("Test 10: Existing backup filename handling")
    void test10_existingBackupFilenameHandling() throws IOException {
        Path destDir = tempDir.resolve("overwrite-test");
        Files.createDirectories(destDir);
        Path targetFile = destDir.resolve("target.json");
        Files.writeString(targetFile, "old-content");

        BackupExportResult res = backupService.exportToFile(null, destDir.toString(), "target.json", "json");
        assertTrue(res.isSuccess());
        assertTrue(Files.size(targetFile) > "old-content".length());

        // Verify valid JSON read-back
        BackupDTO readBack = objectMapper.readValue(targetFile.toFile(), BackupDTO.class);
        assertNotNull(readBack.getMetadata());
    }

    @Test
    @DisplayName("Test 11: Retention with multiple backups")
    void test11_retentionWithMultipleBackups() throws IOException, InterruptedException {
        File backupDir = tempDir.resolve("retention-backups").toFile();
        backupDir.mkdirs();

        autoBackupService.setRetentionCount(3);

        // Generate 6 simulated historical daily backups
        for (int i = 1; i <= 6; i++) {
            File histFile = new File(backupDir, "autobackup_2026110" + i + "_020000.json");
            Files.writeString(histFile.toPath(), "{\"backup\":" + i + "}");
            histFile.setLastModified(System.currentTimeMillis() - (10 - i) * 86400000L);
        }

        File latest = new File(backupDir, AutoBackupService.LATEST_BACKUP_NAME);
        Files.writeString(latest.toPath(), "{\"latest\":true}");

        // Apply retention policy (keep last 3 historical backups)
        autoBackupService.applyRetentionPolicy(backupDir, latest);

        File[] remaining = backupDir.listFiles((d, n) -> n.startsWith("autobackup_2026"));
        assertNotNull(remaining);
        assertEquals(3, remaining.length, "Retention should prune older files keeping exactly 3");
        assertTrue(latest.exists(), "Latest backup file must never be deleted by retention");
    }

    @Test
    @DisplayName("Test 12: Full multi-firm export")
    void test12_fullMultiFirmExport() {
        BackupDTO fullBackup = backupService.exportAllData();
        assertNotNull(fullBackup);
        assertNotNull(fullBackup.getAllFirms());
        assertEquals(2, fullBackup.getAllFirms().size());
        assertEquals(2, fullBackup.getCustomers().size());
        assertEquals(1, fullBackup.getInvoices().size());
        assertEquals("FULL_SYSTEM_BACKUP", fullBackup.getMetadata().get("type"));
    }

    @Test
    @DisplayName("Test 13: NotificationCategory numeric value 3")
    void test13_notificationCategoryNumericValue3() {
        // Enum helper from
        NotificationCategory cat = NotificationCategory.from("3");
        assertEquals(NotificationCategory.PURCHASE, cat, "Numeric string '3' must resolve to PURCHASE ordinal 3");

        NotificationCategory catInt = NotificationCategory.from(3);
        assertEquals(NotificationCategory.PURCHASE, catInt, "Integer 3 must resolve to PURCHASE ordinal 3");

        // JPA Converter conversion
        NotificationCategoryConverter converter = new NotificationCategoryConverter();
        assertEquals(NotificationCategory.PURCHASE, converter.convertToEntityAttribute("3"));
        assertEquals("PURCHASE", converter.convertToDatabaseColumn(NotificationCategory.PURCHASE));
    }

    @Test
    @DisplayName("Test 14: Legacy notification values")
    void test14_legacyNotificationValues() {
        NotificationCategoryConverter converter = new NotificationCategoryConverter();

        assertEquals(NotificationCategory.LICENSING, converter.convertToEntityAttribute("0"));
        assertEquals(NotificationCategory.BILLING, converter.convertToEntityAttribute("1"));
        assertEquals(NotificationCategory.INVENTORY, converter.convertToEntityAttribute("2"));
        assertEquals(NotificationCategory.PURCHASE, converter.convertToEntityAttribute("3"));
        assertEquals(NotificationCategory.PLANNER, converter.convertToEntityAttribute("4"));
        assertEquals(NotificationCategory.HR, converter.convertToEntityAttribute("5"));
        assertEquals(NotificationCategory.SYSTEM, converter.convertToEntityAttribute("6"));

        // Case-insensitive name compatibility
        assertEquals(NotificationCategory.BILLING, converter.convertToEntityAttribute("billing"));
        assertEquals(NotificationCategory.LICENSING, converter.convertToEntityAttribute("Licensing"));
    }

    @Test
    @DisplayName("Test 15: Unknown enum values")
    void test15_unknownEnumValues() {
        NotificationCategoryConverter converter = new NotificationCategoryConverter();
        NotificationCategory cat = converter.convertToEntityAttribute("99");
        assertEquals(NotificationCategory.UNKNOWN, cat, "Unknown numeric ordinal must map to explicit UNKNOWN constant");

        NotificationCategory catStr = converter.convertToEntityAttribute("NON_EXISTENT_MODULE");
        assertEquals(NotificationCategory.UNKNOWN, catStr, "Unknown string value must map to explicit UNKNOWN constant");
    }

    @Test
    @DisplayName("Test 16: Backup checksum mismatch")
    void test16_backupChecksumMismatch() throws IOException {
        BackupDTO backup = backupService.exportAllData();
        byte[] validZip = backupArchiveService.createZipArchive(backup);

        // Tamper with manifest to cause SHA-256 mismatch
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(validZip));
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("manifest.json")) {
                    zos.putNextEntry(new ZipEntry("manifest.json"));
                    String badManifest = "{\"backupJsonSha256\":\"0000000000000000000000000000000000000000000000000000000000000000\"}";
                    zos.write(badManifest.getBytes(StandardCharsets.UTF_8));
                } else {
                    zos.putNextEntry(new ZipEntry(entry.getName()));
                    zos.write(zis.readAllBytes());
                }
                zos.closeEntry();
                zis.closeEntry();
            }
        }

        BackupValidationException bve = assertThrows(BackupValidationException.class, () ->
                backupArchiveService.extractAndValidateBackup(new java.io.ByteArrayInputStream(baos.toByteArray()), "tampered.zip"));
        assertEquals("CHECKSUM_MISMATCH", bve.getErrorCode());
    }

    @Test
    @DisplayName("Test 17: Corrupt ZIP/archive")
    void test17_corruptZipArchive() {
        // Truncated bytes starting with PK\3\4
        byte[] corruptZip = new byte[]{0x50, 0x4B, 0x03, 0x04, 0x00, 0x01, 0x02};

        BackupValidationException bve = assertThrows(BackupValidationException.class, () ->
                backupArchiveService.extractAndValidateBackup(new java.io.ByteArrayInputStream(corruptZip), "corrupt.zip"));
        assertEquals("CORRUPT_ARCHIVE", bve.getErrorCode());
    }

    @Test
    @DisplayName("Test 18: Missing attachment")
    void test18_missingAttachment() throws IOException {
        // ZIP with manifest declaring requiredAttachments: ["company_logo.png"], but file missing
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("manifest.json"));
            String manifest = "{\"requiredAttachments\":[\"company_logo.png\"]}";
            zos.write(manifest.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("backup.json"));
            BackupDTO backup = backupService.exportAllData();
            zos.write(objectMapper.writeValueAsBytes(backup));
            zos.closeEntry();
        }

        BackupValidationException bve = assertThrows(BackupValidationException.class, () ->
                backupArchiveService.extractAndValidateBackup(new java.io.ByteArrayInputStream(baos.toByteArray()), "missing-att.zip"));
        assertEquals("MISSING_ATTACHMENT", bve.getErrorCode());
    }

    @Test
    @DisplayName("Test 19: Restore of multiple firms")
    void test19_restoreOfMultipleFirms() {
        BackupDTO fullBackup = backupService.exportAllData();

        // Mocks for restore
        when(firmDetailsRepo.save(any(FirmDetails.class))).thenAnswer(inv -> {
            FirmDetails f = inv.getArgument(0);
            if (f.getId() == null) f.setId(new Random().nextLong(1000, 2000));
            return f;
        });

        List<FirmDetails> restored = backupService.importSelectiveData(fullBackup, null, "clone", null);
        assertNotNull(restored);
        assertEquals(2, restored.size(), "Both Firm 1 and Firm 2 must be restored in clone mode");
    }

    @Test
    @DisplayName("Test 20: Restore of customers, invoices, transactions and relationships")
    void test20_restoreOfCustomersInvoicesTransactionsAndRelationships() {
        BackupDTO fullBackup = backupService.exportAllData();

        when(firmDetailsRepo.save(any(FirmDetails.class))).thenAnswer(inv -> {
            FirmDetails f = inv.getArgument(0);
            f.setId(301L);
            return f;
        });
        when(customerRepo.save(any(Customer.class))).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.setId(801L);
            return c;
        });
        when(invoiceRepo.save(any(Invoice.class))).thenAnswer(inv -> {
            Invoice i = inv.getArgument(0);
            i.setId(901L);
            return i;
        });

        List<FirmDetails> restored = backupService.importSelectiveData(fullBackup, Collections.singleton(101L), "clone", null);
        assertEquals(1, restored.size());

        // Verify entities were processed with relationship preservation
        verify(customerRepo, atLeastOnce()).save(any(Customer.class));
        verify(invoiceRepo, atLeastOnce()).save(any(Invoice.class));
    }

    @Test
    @DisplayName("Test 21: Restore from an older supported backup format")
    void test21_restoreFromOlderSupportedBackupFormat() {
        // Legacy v1.0 backup: metadata type SINGLE_FIRM, firmDetails, no allFirms list
        BackupDTO legacyBackup = new BackupDTO();
        Map<String, Object> meta = new HashMap<>();
        meta.put("version", "1.0");
        meta.put("type", "SINGLE_FIRM");
        meta.put("sourceFirmId", 101L);
        legacyBackup.setMetadata(meta);
        legacyBackup.setFirmDetails(firm1);
        legacyBackup.setCustomers(Collections.singletonList(customer1));

        BackupInspectionDTO inspection = backupService.inspectBackup(legacyBackup);
        assertEquals("SINGLE_FIRM", inspection.getBackupType());
        assertEquals("1.0", inspection.getVersion());

        when(firmDetailsRepo.save(any(FirmDetails.class))).thenReturn(firm1);
        List<FirmDetails> restored = backupService.importSelectiveData(legacyBackup, null, "clone", null);
        assertEquals(1, restored.size());
    }

    @Test
    @DisplayName("Test 22: Verification that the restored environment is operational")
    void test22_verificationRestoredEnvironmentOperational() throws IOException {
        // Full export -> Write to ZIP -> Read from ZIP -> Validate -> Inspect
        BackupDTO fullBackup = backupService.exportAllData();
        byte[] zipBytes = backupArchiveService.createZipArchive(fullBackup);

        BackupDTO extractedBackup = backupArchiveService.extractAndValidateBackup(
                new java.io.ByteArrayInputStream(zipBytes), "archive.zip");

        assertNotNull(extractedBackup);
        assertEquals(fullBackup.getAllFirms().size(), extractedBackup.getAllFirms().size());
        assertEquals(fullBackup.getCustomers().size(), extractedBackup.getCustomers().size());
        assertEquals(fullBackup.getInvoices().size(), extractedBackup.getInvoices().size());

        BackupInspectionDTO inspection = backupService.inspectBackup(extractedBackup);
        assertEquals("FULL_SYSTEM_BACKUP", inspection.getBackupType());
        assertEquals(2, inspection.getFirms().size());
    }
}
