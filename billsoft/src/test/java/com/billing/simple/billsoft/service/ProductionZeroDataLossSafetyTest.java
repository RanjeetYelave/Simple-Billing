package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.exception.BackupValidationException;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductionZeroDataLossSafetyTest {

    @Mock
    private FirmDetailsRepository firmDetailsRepo;
    @Mock
    private CustomerRepository customerRepo;
    @Mock
    private ProductRepository productRepo;
    @Mock
    private StockMovementRepository stockMovementRepo;
    @Mock
    private InvoiceRepository invoiceRepo;
    @Mock
    private InvoiceItemRepository invoiceItemRepo;
    @Mock
    private PartyRepository partyRepo;
    @Mock
    private PartyPaymentRepository partyPaymentRepo;
    @Mock
    private PurchaseOrderRepository purchaseOrderRepo;
    @Mock
    private PurchaseOrderItemRepository purchaseOrderItemRepo;
    @Mock
    private ReminderRepository reminderRepo;
    @Mock
    private NoteRepository noteRepo;
    @Mock
    private ExpenseRepository expenseRepo;
    @Mock
    private EmployeeRepository employeeRepo;
    @Mock
    private AttendanceRecordRepository attendanceRecordRepo;
    @Mock
    private LeaveRecordRepository leaveRecordRepo;
    @Mock
    private SalaryRecordRepository salaryRepo;
    @Mock
    private EmployeeAdvanceRepository advanceRepo;
    @Mock
    private PromotionRecordRepository promotionRepo;
    @Mock
    private EmployeeDocumentRepository employeeDocumentRepo;
    @Mock
    private BusinessLetterRepository businessLetterRepo;
    @Mock
    private NotificationRepository notificationRepo;
    @Mock
    private NotificationPreferenceRepository notificationPreferenceRepo;
    @Mock
    private AppConfigRepository appConfigRepo;
    @Mock
    private InvoicePaymentRepository invoicePaymentRepo;
    @Mock
    private SalesReturnRepository salesReturnRepo;
    @Mock
    private SalesReturnItemRepository salesReturnItemRepo;
    @Mock
    private SavingRepository savingRepo;
    @Mock
    private GoalRepository goalRepo;
    @Mock
    private GoalLogRepository goalLogRepo;
    @Mock
    private BackupEntityMappingRepository backupEntityMappingRepo;

    @org.mockito.Spy
    private BackupValidationService backupValidationService = new BackupValidationService();

    @InjectMocks
    private BackupService backupService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // =========================================================================
    // Test 1 — Invalid auto-restore: No DB mutation, factoryReset NOT called
    // =========================================================================
    @Test
    void test1_InvalidAutoRestore_RejectsWithoutMutatingDatabase() {
        BackupDTO invalidBackup = new BackupDTO();
        invalidBackup.setMetadata(null); // Missing metadata

        assertThrows(BackupValidationException.class, () ->
                backupService.importSelectiveData(invalidBackup, null, "clone", null)
        );

        // Verify factoryReset() / deleteAllInBatch() was NEVER called
        verify(invoiceRepo, never()).deleteAllInBatch();
        verify(customerRepo, never()).deleteAllInBatch();
        verify(productRepo, never()).deleteAllInBatch();
        verify(firmDetailsRepo, never()).deleteAllInBatch();

        // Verify no save was performed
        verify(firmDetailsRepo, never()).save(any());
        verify(invoiceRepo, never()).save(any());
    }

    // =========================================================================
    // Test 2 — Duplicate invoice backup: Preflight validation fails, no DB mutation
    // =========================================================================
    @Test
    void test2_DuplicateInvoiceInBackup_PreflightFailsAndAborts() {
        BackupDTO backup = new BackupDTO();
        Map<String, Object> meta = new HashMap<>();
        meta.put("version", "1.0");
        meta.put("type", "FULL_SYSTEM_BACKUP");
        backup.setMetadata(meta);

        FirmDetails firm = new FirmDetails();
        firm.setId(1L);
        firm.setFirmName("Alpha Corp");
        backup.setAllFirms(Collections.singletonList(firm));

        Invoice inv1 = new Invoice();
        inv1.setId(101L);
        inv1.setFirmId(1L);
        inv1.setInvoiceNumber("INV-100");

        Invoice inv2 = new Invoice();
        inv2.setId(102L);
        inv2.setFirmId(1L);
        inv2.setInvoiceNumber("INV-100"); // Duplicate!

        backup.setInvoices(Arrays.asList(inv1, inv2));

        BackupValidationException ex = assertThrows(BackupValidationException.class, () ->
                backupService.importSelectiveData(backup, null, "clone", null)
        );

        assertTrue(ex.getMessage().contains("Duplicate invoice number 'INV-100'"));

        // Verify zero deletes and zero writes to DB
        verify(invoiceRepo, never()).deleteAllInBatch();
        verify(firmDetailsRepo, never()).deleteAllInBatch();
        verify(invoiceRepo, never()).save(any());
    }

    // =========================================================================
    // Test 3 — Failed restore halfway through: Exception stops flow safely
    // =========================================================================
    @Test
    void test3_FailedRestore_ThrowsAndDoesNotInvokeFactoryReset() {
        BackupDTO backup = new BackupDTO();
        Map<String, Object> meta = new HashMap<>();
        meta.put("version", "1.0");
        meta.put("type", "FULL_SYSTEM_BACKUP");
        backup.setMetadata(meta);

        FirmDetails firm = new FirmDetails();
        firm.setId(1L);
        firm.setFirmName("Gamma Corp");
        backup.setAllFirms(Collections.singletonList(firm));

        Customer c1 = new Customer();
        c1.setId(10L);
        c1.setName("Alice");
        c1.setFirmId(1L);
        backup.setCustomers(Collections.singletonList(c1));

        // Mock firm repo to throw an unexpected database exception during firm save
        when(firmDetailsRepo.save(any())).thenThrow(new RuntimeException("Simulated DB Connection Disruption"));

        assertThrows(RuntimeException.class, () ->
                backupService.importSelectiveData(backup, null, "clone", null)
        );

        // Verify factoryReset() was NEVER called
        verify(invoiceRepo, never()).deleteAllInBatch();
        verify(customerRepo, never()).deleteAllInBatch();
        verify(productRepo, never()).deleteAllInBatch();
    }

    // =========================================================================
    // Test 4 — Empty DB + valid backup: Restores successfully without wipe
    // =========================================================================
    @Test
    void test4_EmptyDbValidBackup_RestoresSuccessfully() {
        BackupDTO backup = new BackupDTO();
        Map<String, Object> meta = new HashMap<>();
        meta.put("version", "1.0");
        meta.put("type", "FULL_SYSTEM_BACKUP");
        backup.setMetadata(meta);

        FirmDetails firm = new FirmDetails();
        firm.setId(1L);
        firm.setFirmName("Clean Start Firm");
        backup.setAllFirms(Collections.singletonList(firm));

        Customer c = new Customer();
        c.setId(50L);
        c.setName("Valid Customer");
        c.setFirmId(1L);
        backup.setCustomers(Collections.singletonList(c));

        when(firmDetailsRepo.save(any())).thenAnswer(inv -> {
            FirmDetails f = inv.getArgument(0);
            f.setId(100L);
            return f;
        });
        when(customerRepo.save(any())).thenAnswer(inv -> {
            Customer cust = inv.getArgument(0);
            cust.setId(500L);
            return cust;
        });

        List<FirmDetails> restored = backupService.importSelectiveData(backup, null, "clone", null);

        assertNotNull(restored);
        assertEquals(1, restored.size());
        verify(firmDetailsRepo, times(1)).save(any());
        verify(customerRepo, times(1)).save(any());

        // Zero deleteAllInBatch calls
        verify(firmDetailsRepo, never()).deleteAllInBatch();
        verify(customerRepo, never()).deleteAllInBatch();
    }

    // =========================================================================
    // Test 5 — Existing DB + auto-restore: clean_wipe mode rejected
    // =========================================================================
    @Test
    void test5_AutoRestore_RejectsCleanWipeMode() {
        BackupDTO backup = new BackupDTO();
        Map<String, Object> meta = new HashMap<>();
        meta.put("version", "1.0");
        meta.put("type", "FULL_SYSTEM_BACKUP");
        backup.setMetadata(meta);

        FirmDetails firm = new FirmDetails();
        firm.setId(1L);
        firm.setFirmName("Production Firm");
        backup.setAllFirms(Collections.singletonList(firm));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                backupService.importSelectiveData(backup, null, "clean_wipe", null)
        );

        assertTrue(ex.getMessage().contains("Destructive clean_wipe mode is not permitted"));

        // Verify factoryReset() was NEVER invoked
        verify(invoiceRepo, never()).deleteAllInBatch();
        verify(customerRepo, never()).deleteAllInBatch();
        verify(firmDetailsRepo, never()).deleteAllInBatch();
    }

    // =========================================================================
    // Test 6 — Factory Reset: Dedicated administrative feature works as intended
    // =========================================================================
    @Test
    void test6_FactoryReset_ExplicitAdminOperationPreserved() {
        // Explicitly calling factoryReset() directly (e.g. from POST /api/backup/factory-reset)
        backupService.factoryReset();

        // Verify all 22 entity tables are cleared in batch as designed for intentional factory reset
        verify(salesReturnItemRepo, times(1)).deleteAllInBatch();
        verify(salesReturnRepo, times(1)).deleteAllInBatch();
        verify(invoicePaymentRepo, times(1)).deleteAllInBatch();
        verify(invoiceItemRepo, times(1)).deleteAllInBatch();
        verify(invoiceRepo, times(1)).deleteAllInBatch();
        verify(stockMovementRepo, times(1)).deleteAllInBatch();
        verify(partyPaymentRepo, times(1)).deleteAllInBatch();
        verify(purchaseOrderItemRepo, times(1)).deleteAllInBatch();
        verify(purchaseOrderRepo, times(1)).deleteAllInBatch();
        verify(partyRepo, times(1)).deleteAllInBatch();
        verify(businessLetterRepo, times(1)).deleteAllInBatch();
        verify(attendanceRecordRepo, times(1)).deleteAllInBatch();
        verify(leaveRecordRepo, times(1)).deleteAllInBatch();
        verify(employeeDocumentRepo, times(1)).deleteAllInBatch();
        verify(salaryRepo, times(1)).deleteAllInBatch();
        verify(promotionRepo, times(1)).deleteAllInBatch();
        verify(advanceRepo, times(1)).deleteAllInBatch();
        verify(employeeRepo, times(1)).deleteAllInBatch();
        verify(goalLogRepo, times(1)).deleteAllInBatch();
        verify(savingRepo, times(1)).deleteAllInBatch();
        verify(goalRepo, times(1)).deleteAllInBatch();
        verify(expenseRepo, times(1)).deleteAllInBatch();
        verify(reminderRepo, times(1)).deleteAllInBatch();
        verify(notificationRepo, times(1)).deleteAllInBatch();
        verify(notificationPreferenceRepo, times(1)).deleteAllInBatch();
        verify(noteRepo, times(1)).deleteAllInBatch();
        verify(productRepo, times(1)).deleteAllInBatch();
        verify(customerRepo, times(1)).deleteAllInBatch();
        verify(firmDetailsRepo, times(1)).deleteAllInBatch();
        verify(appConfigRepo, times(1)).deleteAllInBatch();
    }

    // =========================================================================
    // Test 7 — Auto-backup failure: Preserves previous latest backup & database
    // =========================================================================
    @Test
    void test7_AutoBackupFailure_PreservesPreviousBackupAndCleansTemp(@TempDir Path tempDir) throws IOException {
        File backupDir = tempDir.toFile();
        File previousLatest = new File(backupDir, "autobackup_latest.json");
        Files.writeString(previousLatest.toPath(), "{\"metadata\":{\"version\":\"1.0\",\"status\":\"PREVIOUS_GOOD\"}}");

        AutoBackupService autoBackupService = new AutoBackupService(backupService, backupValidationService) {
            @Override
            public File getBackupDirectory() {
                return backupDir;
            }
        };

        // Make exportAllData throw an unexpected error
        when(invoiceRepo.findAllWithItems()).thenThrow(new RuntimeException("Simulated Backup Read Error"));

        Map<String, Object> result = autoBackupService.runAutoBackup();

        assertEquals("ERROR", result.get("status"));

        // Assert previous latest backup is UNTOUCHED
        assertTrue(previousLatest.exists(), "Previous good backup must remain intact");
        assertEquals("{\"metadata\":{\"version\":\"1.0\",\"status\":\"PREVIOUS_GOOD\"}}",
                Files.readString(previousLatest.toPath()));

        // Assert no temp files remain
        File[] tempFiles = backupDir.listFiles((dir, name) -> name.startsWith("autobackup_temp_"));
        assertEquals(0, tempFiles != null ? tempFiles.length : 0, "All temp files must be cleanly deleted");
    }

    // =========================================================================
    // Test 8 — Backup cleanup: Only prunes autobackup_temp_*, never valid backups
    // =========================================================================
    @Test
    void test8_BackupCleanup_OnlyDeletesTempFiles(@TempDir Path tempDir) throws IOException {
        File backupDir = tempDir.toFile();

        File latest = new File(backupDir, "autobackup_latest.json");
        Files.writeString(latest.toPath(), "{\"status\":\"LATEST\"}");

        File historical = new File(backupDir, "autobackup_2026-10-01.json");
        Files.writeString(historical.toPath(), "{\"status\":\"HISTORICAL_DAILY\"}");

        File manual = new File(backupDir, "manual_backup.json");
        Files.writeString(manual.toPath(), "{\"status\":\"MANUAL_EXPORT\"}");

        File tempStray = new File(backupDir, "autobackup_temp_12345678.json");
        Files.writeString(tempStray.toPath(), "{\"status\":\"UNFINISHED_TEMP\"}");

        AutoBackupService autoBackupService = new AutoBackupService(backupService, backupValidationService) {
            @Override
            public File getBackupDirectory() {
                return backupDir;
            }
        };

        // Mock successful export
        BackupDTO dummy = new BackupDTO();
        dummy.setMetadata(new HashMap<>(Map.of("version", "1.0", "type", "FULL_SYSTEM_BACKUP")));
        dummy.setAllFirms(Collections.emptyList());

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
        when(salesReturnRepo.findAll()).thenReturn(Collections.emptyList());
        when(firmDetailsRepo.findAll()).thenReturn(Collections.emptyList());

        Map<String, Object> result = autoBackupService.runAutoBackup();
        assertEquals("HEALTHY", result.get("status"));

        // Valid backups must ALL exist
        assertTrue(latest.exists(), "autobackup_latest.json must exist");
        assertTrue(historical.exists(), "autobackup_2026-10-01.json must NEVER be deleted");
        assertTrue(manual.exists(), "manual_backup.json must NEVER be deleted");

        // The stray temp file MUST be pruned
        assertFalse(tempStray.exists(), "autobackup_temp_12345678.json must be cleaned up");
    }
}
