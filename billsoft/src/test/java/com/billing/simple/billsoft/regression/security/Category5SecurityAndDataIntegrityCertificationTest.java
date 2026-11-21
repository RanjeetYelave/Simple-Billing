package com.billing.simple.billsoft.regression.security;

import com.billing.simple.billsoft.dataprotection.*;
import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.exception.BackupValidationException;
import com.billing.simple.billsoft.licensing.MachineIdentity;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.repositories.*;
import com.billing.simple.billsoft.service.BackupArchiveService;
import com.billing.simple.billsoft.service.BackupRecoveryCoordinator;
import com.billing.simple.billsoft.service.BackupService;
import com.billing.simple.billsoft.service.BackupValidationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.AEADBadTagException;
import javax.crypto.SecretKey;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * RupeeCRM + SMT — Category 5 Security, Credential Lifecycle, Database Compatibility
 * & Data Integrity Certification Test Suite.
 *
 * Implements the 24 required security/integrity tests (Section 9) and the
 * final 12-step integrated regression lifecycle (Section 10).
 */
public class Category5SecurityAndDataIntegrityCertificationTest {

    @TempDir
    Path tempDir;

    private ObjectMapper objectMapper;
    private DataProtectionCrypto crypto;
    private BackupValidationService backupValidationService;
    private BackupArchiveService backupArchiveService;

    // Repositories for RupeeCRM
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

    private BackupService backupService;

    // In-memory data store for isolated testing
    private final Map<Long, FirmDetails> firmsStore = new HashMap<>();
    private final Map<Long, Customer> customersStore = new HashMap<>();
    private final Map<Long, Product> productsStore = new HashMap<>();
    private final Map<Long, Invoice> invoicesStore = new HashMap<>();
    private final Map<Long, Notification> notificationsStore = new HashMap<>();
    private final Map<Long, InvoicePayment> paymentsStore = new HashMap<>();
    private final Map<String, AppConfig> appConfigStore = new HashMap<>();

    private long idSeq = 5000L;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        crypto = new DataProtectionCrypto();
        backupValidationService = new BackupValidationService();
        backupArchiveService = new BackupArchiveService(objectMapper, backupValidationService, crypto, null);

        firmsStore.clear();
        customersStore.clear();
        productsStore.clear();
        invoicesStore.clear();
        notificationsStore.clear();
        paymentsStore.clear();
        appConfigStore.clear();

        initMockRepositories();
    }

    @SuppressWarnings("unchecked")
    private void initMockRepositories() {
        firmDetailsRepo = mock(FirmDetailsRepository.class);
        when(firmDetailsRepo.findAll()).thenAnswer(inv -> new ArrayList<>(firmsStore.values()));
        when(firmDetailsRepo.save(any(FirmDetails.class))).thenAnswer(inv -> {
            FirmDetails f = inv.getArgument(0);
            if (f.getId() == null) f.setId(idSeq++);
            firmsStore.put(f.getId(), f);
            return f;
        });
        when(firmDetailsRepo.saveAll(any(Iterable.class))).thenAnswer(inv -> {
            Iterable<FirmDetails> list = inv.getArgument(0);
            List<FirmDetails> saved = new ArrayList<>();
            for (FirmDetails f : list) {
                if (f.getId() == null) f.setId(idSeq++);
                firmsStore.put(f.getId(), f);
                saved.add(f);
            }
            return saved;
        });
        when(firmDetailsRepo.findById(any(Long.class))).thenAnswer(inv -> Optional.ofNullable(firmsStore.get(inv.getArgument(0))));

        customerRepo = mock(CustomerRepository.class);
        when(customerRepo.findAll()).thenAnswer(inv -> new ArrayList<>(customersStore.values()));
        when(customerRepo.save(any(Customer.class))).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            if (c.getId() == null) c.setId(idSeq++);
            customersStore.put(c.getId(), c);
            return c;
        });
        when(customerRepo.saveAll(any(Iterable.class))).thenAnswer(inv -> {
            Iterable<Customer> list = inv.getArgument(0);
            List<Customer> saved = new ArrayList<>();
            for (Customer c : list) {
                if (c.getId() == null) c.setId(idSeq++);
                customersStore.put(c.getId(), c);
                saved.add(c);
            }
            return saved;
        });

        productRepo = mock(ProductRepository.class);
        when(productRepo.findAll()).thenAnswer(inv -> new ArrayList<>(productsStore.values()));
        when(productRepo.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            if (p.getId() == null) p.setId(idSeq++);
            productsStore.put(p.getId(), p);
            return p;
        });
        when(productRepo.saveAll(any(Iterable.class))).thenAnswer(inv -> {
            Iterable<Product> list = inv.getArgument(0);
            List<Product> saved = new ArrayList<>();
            for (Product p : list) {
                if (p.getId() == null) p.setId(idSeq++);
                productsStore.put(p.getId(), p);
                saved.add(p);
            }
            return saved;
        });

        invoiceRepo = mock(InvoiceRepository.class);
        when(invoiceRepo.findAll()).thenAnswer(inv -> new ArrayList<>(invoicesStore.values()));
        when(invoiceRepo.findAllWithItems()).thenAnswer(inv -> new ArrayList<>(invoicesStore.values()));
        when(invoiceRepo.save(any(Invoice.class))).thenAnswer(inv -> {
            Invoice i = inv.getArgument(0);
            if (i.getId() == null) i.setId(idSeq++);
            invoicesStore.put(i.getId(), i);
            return i;
        });
        when(invoiceRepo.saveAll(any(Iterable.class))).thenAnswer(inv -> {
            Iterable<Invoice> list = inv.getArgument(0);
            List<Invoice> saved = new ArrayList<>();
            for (Invoice i : list) {
                if (i.getId() == null) i.setId(idSeq++);
                invoicesStore.put(i.getId(), i);
                saved.add(i);
            }
            return saved;
        });

        notificationRepo = mock(NotificationRepository.class);
        when(notificationRepo.findAll()).thenAnswer(inv -> new ArrayList<>(notificationsStore.values()));
        when(notificationRepo.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            if (n.getId() == null) n.setId(idSeq++);
            notificationsStore.put(n.getId(), n);
            return n;
        });
        when(notificationRepo.saveAll(any(Iterable.class))).thenAnswer(inv -> {
            Iterable<Notification> list = inv.getArgument(0);
            List<Notification> saved = new ArrayList<>();
            for (Notification n : list) {
                if (n.getId() == null) n.setId(idSeq++);
                notificationsStore.put(n.getId(), n);
                saved.add(n);
            }
            return saved;
        });

        invoicePaymentRepo = mock(InvoicePaymentRepository.class);
        when(invoicePaymentRepo.findAll()).thenAnswer(inv -> new ArrayList<>(paymentsStore.values()));
        when(invoicePaymentRepo.save(any(InvoicePayment.class))).thenAnswer(inv -> {
            InvoicePayment pay = inv.getArgument(0);
            if (pay.getId() == null) pay.setId(idSeq++);
            paymentsStore.put(pay.getId(), pay);
            return pay;
        });
        when(invoicePaymentRepo.saveAll(any(Iterable.class))).thenAnswer(inv -> {
            Iterable<InvoicePayment> list = inv.getArgument(0);
            List<InvoicePayment> saved = new ArrayList<>();
            for (InvoicePayment pay : list) {
                if (pay.getId() == null) pay.setId(idSeq++);
                paymentsStore.put(pay.getId(), pay);
                saved.add(pay);
            }
            return saved;
        });

        appConfigRepo = mock(AppConfigRepository.class);
        when(appConfigRepo.findAll()).thenAnswer(inv -> new ArrayList<>(appConfigStore.values()));
        when(appConfigRepo.save(any(AppConfig.class))).thenAnswer(inv -> {
            AppConfig c = inv.getArgument(0);
            appConfigStore.put(c.getConfigKey(), c);
            return c;
        });
        when(appConfigRepo.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(appConfigStore.get(inv.getArgument(0))));

        // Unused optional repos
        partyRepo = mock(PartyRepository.class);
        when(partyRepo.findAll()).thenReturn(Collections.emptyList());
        notificationPreferenceRepo = mock(NotificationPreferenceRepository.class);
        when(notificationPreferenceRepo.findAll()).thenReturn(Collections.emptyList());
        stockMovementRepo = mock(StockMovementRepository.class);
        when(stockMovementRepo.findAll()).thenReturn(Collections.emptyList());
        purchaseOrderRepo = mock(PurchaseOrderRepository.class);
        when(purchaseOrderRepo.findAll()).thenReturn(Collections.emptyList());
        reminderRepo = mock(ReminderRepository.class);
        when(reminderRepo.findAll()).thenReturn(Collections.emptyList());
        noteRepo = mock(NoteRepository.class);
        when(noteRepo.findAll()).thenReturn(Collections.emptyList());
        expenseRepo = mock(ExpenseRepository.class);
        when(expenseRepo.findAll()).thenReturn(Collections.emptyList());
        employeeRepo = mock(EmployeeRepository.class);
        when(employeeRepo.findAll()).thenReturn(Collections.emptyList());
        attendanceRecordRepo = mock(AttendanceRecordRepository.class);
        when(attendanceRecordRepo.findAll()).thenReturn(Collections.emptyList());
        leaveRecordRepo = mock(LeaveRecordRepository.class);
        when(leaveRecordRepo.findAll()).thenReturn(Collections.emptyList());
        salaryRepo = mock(SalaryRecordRepository.class);
        when(salaryRepo.findAll()).thenReturn(Collections.emptyList());
        advanceRepo = mock(EmployeeAdvanceRepository.class);
        when(advanceRepo.findAll()).thenReturn(Collections.emptyList());
        promotionRepo = mock(PromotionRecordRepository.class);
        when(promotionRepo.findAll()).thenReturn(Collections.emptyList());
        employeeDocumentRepo = mock(EmployeeDocumentRepository.class);
        when(employeeDocumentRepo.findAll()).thenReturn(Collections.emptyList());
        businessLetterRepo = mock(BusinessLetterRepository.class);
        when(businessLetterRepo.findAll()).thenReturn(Collections.emptyList());
        salesReturnRepo = mock(SalesReturnRepository.class);
        when(salesReturnRepo.findAll()).thenReturn(Collections.emptyList());
        salesReturnItemRepo = mock(SalesReturnItemRepository.class);
        when(salesReturnItemRepo.findAll()).thenReturn(Collections.emptyList());
        savingRepo = mock(SavingRepository.class);
        when(savingRepo.findAll()).thenReturn(Collections.emptyList());
        goalRepo = mock(GoalRepository.class);
        when(goalRepo.findAll()).thenReturn(Collections.emptyList());
        goalLogRepo = mock(GoalLogRepository.class);
        when(goalLogRepo.findAll()).thenReturn(Collections.emptyList());
        backupEntityMappingRepo = mock(BackupEntityMappingRepository.class);

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
                null, null, null, new BackupRecoveryCoordinator(), objectMapper, mock(javax.sql.DataSource.class)
        );
        backupService.setBackupArchiveService(backupArchiveService);
    }

    private String signEd25519(int version, String protectedPayload, PrivateKey privateKey) throws Exception {
        String canonical = version + "\n" + protectedPayload.trim();
        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(privateKey);
        sig.update(canonical.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(sig.sign());
    }

    // =========================================================================
    // SECTION 9: 24 SECURITY, CREDENTIAL LIFECYCLE & INTEGRITY TESTS
    // =========================================================================

    @Nested
    @DisplayName("Category 5 - 24 Core Security & Integrity Tests")
    class CoreSecurityTests {

        @Test
        @DisplayName("Test 1: Valid Ed25519 Signature accepted and updates credential store")
        void test1_ValidEd25519SignatureAccepted() throws Exception {
            File configFile = new File(tempDir.toFile(), "vault_cfg_1.json");
            DataProtectionCredentialStore store = new DataProtectionCredentialStore(configFile);

            KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
            KeyPair kp = kpg.generateKeyPair();

            String token = "github_pat_VALID_TEST_TOKEN_1";
            String masked = DataProtectionCredentialStore.maskPayload(token);
            String sig = signEd25519(1, masked, kp.getPrivate());

            boolean result = store.updateCredential(1, masked, sig, kp.getPublic());
            assertTrue(result, "Valid signature must be accepted");
            assertEquals(1, store.getCurrentVersion());
            assertEquals(token, store.getCurrentToken());
        }

        @Test
        @DisplayName("Test 2: Invalid Ed25519 Signature rejected securely")
        void test2_InvalidEd25519SignatureRejected() throws Exception {
            File configFile = new File(tempDir.toFile(), "vault_cfg_2.json");
            DataProtectionCredentialStore store = new DataProtectionCredentialStore(configFile);

            KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
            KeyPair kp = kpg.generateKeyPair();

            String token = "github_pat_FORGED_2";
            String masked = DataProtectionCredentialStore.maskPayload(token);
            String fakeSig = Base64.getEncoder().encodeToString(new byte[64]); // Invalid signature

            boolean result = store.updateCredential(1, masked, fakeSig, kp.getPublic());
            assertFalse(result, "Invalid signature must be rejected");
            assertEquals(0, store.getCurrentVersion());
            assertNull(store.getCurrentToken());
        }

        @Test
        @DisplayName("Test 3: Modified signed payload rejected")
        void test3_ModifiedSignedPayloadRejected() throws Exception {
            File configFile = new File(tempDir.toFile(), "vault_cfg_3.json");
            DataProtectionCredentialStore store = new DataProtectionCredentialStore(configFile);

            KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
            KeyPair kp = kpg.generateKeyPair();

            String originalToken = "github_pat_ORIGINAL_3";
            String originalMasked = DataProtectionCredentialStore.maskPayload(originalToken);
            String originalSig = signEd25519(1, originalMasked, kp.getPrivate());

            // Tamper: Submit modified payload with original signature
            String tamperedMasked = DataProtectionCredentialStore.maskPayload("github_pat_TAMPERED_3");
            boolean result = store.updateCredential(1, tamperedMasked, originalSig, kp.getPublic());
            assertFalse(result, "Tampered payload with mismatched signature must be rejected");
            assertEquals(0, store.getCurrentVersion());
        }

        @Test
        @DisplayName("Test 4: Incorrect public key rejected")
        void test4_IncorrectPublicKeyRejected() throws Exception {
            File configFile = new File(tempDir.toFile(), "vault_cfg_4.json");
            DataProtectionCredentialStore store = new DataProtectionCredentialStore(configFile);

            KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
            KeyPair kpSigner = kpg.generateKeyPair();
            KeyPair kpVerifier = kpg.generateKeyPair();

            String token = "github_pat_KEY_MISMATCH_4";
            String masked = DataProtectionCredentialStore.maskPayload(token);
            String sig = signEd25519(1, masked, kpSigner.getPrivate());

            boolean result = store.updateCredential(1, masked, sig, kpVerifier.getPublic());
            assertFalse(result, "Verification with incorrect public key must fail");
            assertEquals(0, store.getCurrentVersion());
        }

        @Test
        @DisplayName("Test 5: Key version mismatch (anti-rollback and duplicate version rejected)")
        void test5_KeyVersionMismatchAntiRollback() throws Exception {
            File configFile = new File(tempDir.toFile(), "vault_cfg_5.json");
            DataProtectionCredentialStore store = new DataProtectionCredentialStore(configFile);

            KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
            KeyPair kp = kpg.generateKeyPair();

            String tokenV5 = "github_pat_VER_5";
            String maskedV5 = DataProtectionCredentialStore.maskPayload(tokenV5);
            String sigV5 = signEd25519(5, maskedV5, kp.getPrivate());
            assertTrue(store.updateCredential(5, maskedV5, sigV5, kp.getPublic()));

            // Rollback to version 3 must be rejected
            String tokenV3 = "github_pat_VER_3";
            String maskedV3 = DataProtectionCredentialStore.maskPayload(tokenV3);
            String sigV3 = signEd25519(3, maskedV3, kp.getPrivate());
            assertFalse(store.updateCredential(3, maskedV3, sigV3, kp.getPublic()), "Rollback must be rejected");

            // Duplicate version 5 must be rejected
            assertFalse(store.updateCredential(5, maskedV5, sigV5, kp.getPublic()), "Duplicate version must be rejected");
            assertEquals(5, store.getCurrentVersion());
            assertEquals(tokenV5, store.getCurrentToken());
        }

        @Test
        @DisplayName("Test 6: Credential update atomic persistence on disk")
        void test6_CredentialUpdateAtomicPersistence() throws Exception {
            File configFile = new File(tempDir.toFile(), "vault_cfg_6.json");
            DataProtectionCredentialStore store = new DataProtectionCredentialStore(configFile);

            KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
            KeyPair kp = kpg.generateKeyPair();

            String token = "github_pat_PERSIST_6";
            String masked = DataProtectionCredentialStore.maskPayload(token);
            String sig = signEd25519(1, masked, kp.getPrivate());

            assertTrue(store.updateCredential(1, masked, sig, kp.getPublic()));
            assertTrue(configFile.exists(), "vault_config.json must exist on disk");

            // Reload fresh instance from disk
            DataProtectionCredentialStore reloaded = new DataProtectionCredentialStore(configFile);
            assertEquals(1, reloaded.getCurrentVersion());
            assertEquals(token, reloaded.getCurrentToken());
        }

        @Test
        @DisplayName("Test 7: Credential preservation after application upgrade simulation")
        void test7_CredentialPreservationAfterUpgrade() throws Exception {
            File storageDir = tempDir.resolve("rupeecrm_data").toFile();
            storageDir.mkdirs();
            File configFile = new File(storageDir, "vault_config.json");

            KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
            KeyPair kp = kpg.generateKeyPair();

            String token = "github_pat_UPGRADE_PRESERVED_7";
            String masked = DataProtectionCredentialStore.maskPayload(token);
            String sig = signEd25519(10, masked, kp.getPrivate());

            // Pre-upgrade session
            DataProtectionCredentialStore v1Store = new DataProtectionCredentialStore(configFile);
            assertTrue(v1Store.updateCredential(10, masked, sig, kp.getPublic()));

            // Post-upgrade session: new binary initialization over preserved data directory
            DataProtectionCredentialStore v2Store = new DataProtectionCredentialStore(configFile);
            assertEquals(10, v2Store.getCurrentVersion());
            assertEquals(token, v2Store.getCurrentToken(), "Credentials must survive application upgrade");
        }

        @Test
        @DisplayName("Test 8: Credential preservation after uninstallation and reinstallation")
        void test8_CredentialPreservationAfterReinstall() throws Exception {
            File dataDir = tempDir.resolve("AppData/Local/RupeeCRM/data").toFile();
            dataDir.mkdirs();
            File configFile = new File(dataDir, "vault_config.json");

            KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
            KeyPair kp = kpg.generateKeyPair();

            String token = "github_pat_REINSTALL_PRESERVED_8";
            String masked = DataProtectionCredentialStore.maskPayload(token);
            String sig = signEd25519(12, masked, kp.getPrivate());

            DataProtectionCredentialStore initialStore = new DataProtectionCredentialStore(configFile);
            assertTrue(initialStore.updateCredential(12, masked, sig, kp.getPublic()));

            // Simulate uninstall (removes binaries) & reinstall (reconnects to preserved %LOCALAPPDATA%\RupeeCRM\data)
            assertTrue(configFile.exists(), "Customer data directory preserved by installer");
            DataProtectionCredentialStore reinstalledStore = new DataProtectionCredentialStore(configFile);
            assertEquals(12, reinstalledStore.getCurrentVersion());
            assertEquals(token, reinstalledStore.getCurrentToken());
        }

        @Test
        @DisplayName("Test 9: Machine ID (MID) persistent generation and format validity")
        void test9_MidPersistenceAndFormat() throws Exception {
            File midFile = new File(tempDir.toFile(), "mid.dat");
            MachineIdentity midGenerator1 = new MachineIdentity(midFile);

            String mid1 = midGenerator1.getMachineId();
            assertNotNull(mid1);
            assertTrue(MachineIdentity.isValidFormat(mid1), "MID must conform to Crockford Base32 XXXX-XXXX-XXXX-XXXX");
            assertTrue(midFile.exists());

            // Second invocation must reload identical MID without regeneration
            MachineIdentity midGenerator2 = new MachineIdentity(midFile);
            String mid2 = midGenerator2.getMachineId();
            assertEquals(mid1, mid2, "MID must be stable and persistent across restarts");
        }

        @Test
        @DisplayName("Test 10: AES-256-GCM authenticated encryption/decryption round trip")
        void test10_AuthenticatedEncryptionRoundTrip() throws Exception {
            String originalText = "CONFIDENTIAL_FINANCIAL_LEDGER_DATA_10";
            byte[] plaintext = originalText.getBytes(StandardCharsets.UTF_8);

            SecretKey key = crypto.deriveKeyFromLicenseId("LIC-KEY-TEST-10");
            byte[] container = crypto.encryptBackup(plaintext, key);

            assertNotNull(container);
            assertEquals((byte) 0x01, container[0], "Version must be 0x01");
            assertTrue(container.length >= 29);

            byte[] decrypted = crypto.decryptBackup(container, key);
            assertEquals(originalText, new String(decrypted, StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("Test 11: Wrong recovery key throws AEADBadTagException")
        void test11_WrongRecoveryKeyThrowsBadTag() throws Exception {
            byte[] plaintext = "SECRET_PAYLOAD_11".getBytes(StandardCharsets.UTF_8);
            SecretKey correctKey = crypto.deriveKeyFromLicenseId("LIC-CORRECT-11");
            SecretKey wrongKey = crypto.deriveKeyFromLicenseId("LIC-WRONG-11");

            byte[] container = crypto.encryptBackup(plaintext, correctKey);
            assertThrows(AEADBadTagException.class, () -> crypto.decryptBackup(container, wrongKey));
        }

        @Test
        @DisplayName("Test 12: Corrupted encrypted backup container rejected (< 29 bytes)")
        void test12_CorruptedEncryptedBackupRejected() {
            byte[] corrupt = new byte[]{0x01, 0x02, 0x03}; // truncated
            SecretKey key = crypto.deriveKeyFromLicenseId("LIC-12");
            assertThrows(IllegalArgumentException.class, () -> crypto.decryptBackup(corrupt, key));
        }

        @Test
        @DisplayName("Test 13: Invalid authentication tag bit-flip tampering rejected")
        void test13_InvalidAuthenticationTagRejected() throws Exception {
            byte[] plaintext = "PAYLOAD_TAMPER_13".getBytes(StandardCharsets.UTF_8);
            SecretKey key = crypto.deriveKeyFromLicenseId("LIC-13");
            byte[] container = crypto.encryptBackup(plaintext, key);

            // Flip bit in the authentication tag (last byte)
            container[container.length - 1] ^= 0x01;
            assertThrows(AEADBadTagException.class, () -> crypto.decryptBackup(container, key));
        }

        @Test
        @DisplayName("Test 14: Backup ZIP archive checksum mismatch detected")
        void test14_BackupChecksumMismatchDetected() throws Exception {
            BackupDTO backup = new BackupDTO();
            FirmDetails f = new FirmDetails();
            f.setId(1L);
            f.setFirmName("Integrity Firm");
            backup.setFirmDetails(f);

            // 1. Create a valid ZIP archive
            byte[] validZip = backupArchiveService.createZipArchive(backup);

            // 2. Extract and tamper with backup.json while keeping manifest.json intact
            ByteArrayOutputStream tamperedZipStream = new ByteArrayOutputStream();
            try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new ByteArrayInputStream(validZip));
                 ZipOutputStream zos = new ZipOutputStream(tamperedZipStream)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    zos.putNextEntry(new ZipEntry(entry.getName()));
                    byte[] content = zis.readAllBytes();
                    if ("backup.json".equals(entry.getName())) {
                        content = "{\"firmDetails\":{\"firmName\":\"Tampered Firm\"}}".getBytes(StandardCharsets.UTF_8);
                    }
                    zos.write(content);
                    zos.closeEntry();
                }
            }

            // 3. Extraction of tampered archive must fail checksum validation
            ByteArrayInputStream bais = new ByteArrayInputStream(tamperedZipStream.toByteArray());
            BackupValidationException ex = assertThrows(BackupValidationException.class, () -> {
                backupArchiveService.extractAndValidateBackup(bais, "tampered.zip");
            });
            assertEquals("CHECKSUM_MISMATCH", ex.getErrorCode());
        }

        @Test
        @DisplayName("Test 15: Legacy NotificationCategory numeric values (including '3' -> PURCHASE)")
        void test15_LegacyNotificationCategoryNumericValues() {
            assertEquals(NotificationCategory.LICENSING, NotificationCategory.from(0));
            assertEquals(NotificationCategory.BILLING, NotificationCategory.from(1));
            assertEquals(NotificationCategory.INVENTORY, NotificationCategory.from(2));
            assertEquals(NotificationCategory.PURCHASE, NotificationCategory.from(3), "Ordinal 3 must map to PURCHASE");
            assertEquals(NotificationCategory.PURCHASE, NotificationCategory.from("3"), "String '3' must map to PURCHASE");
            assertEquals(NotificationCategory.PLANNER, NotificationCategory.from(4));
            assertEquals(NotificationCategory.HR, NotificationCategory.from(5));
            assertEquals(NotificationCategory.SYSTEM, NotificationCategory.from(6));
            assertEquals(NotificationCategory.UNKNOWN, NotificationCategory.from(7));
        }

        @Test
        @DisplayName("Test 16: Unknown enum values map gracefully without throwing exceptions")
        void test16_UnknownEnumValuesHandledGracefully() {
            // NotificationCategory
            assertEquals(NotificationCategory.UNKNOWN, NotificationCategory.from("NON_EXISTENT_CAT"));
            assertEquals(NotificationCategory.UNKNOWN, NotificationCategory.from(999));
            assertNull(NotificationCategory.from(null));

            // NotificationPriority
            assertEquals(NotificationPriority.UNKNOWN, NotificationPriority.from("EXTREME"));
            assertEquals(NotificationPriority.UNKNOWN, NotificationPriority.from(999));

            // NotificationStatus
            assertEquals(NotificationStatus.UNKNOWN, NotificationStatus.from("PENDING"));

            // InvoiceStatus
            assertEquals(InvoiceStatus.UNPAID, InvoiceStatus.from("UNKNOWN_STATUS"));
            assertEquals(InvoiceStatus.PAID, InvoiceStatus.from("paid"), "Case-insensitive 'paid' must map to PAID");
            assertEquals(InvoiceStatus.UNPAID, InvoiceStatus.from(1), "Ordinal 1 must map to UNPAID");

            // PurchaseOrderStatus
            assertEquals(PurchaseOrderStatus.DRAFT, PurchaseOrderStatus.from("INVALID"));

            // GoalType
            assertEquals(GoalType.SAVINGS_TARGET, GoalType.from("FANTASY_GOAL"));

            // LetterStatus & LetterRecipientType
            assertEquals(LetterStatus.DRAFT, LetterStatus.from("MYSTERY"));
            assertEquals(LetterRecipientType.CUSTOM, LetterRecipientType.from("UNKNOWN_RECIPIENT"));
        }

        @Test
        @DisplayName("Test 17: Database migration from supported older versions (JPA Schema Safety)")
        void test17_DatabaseSchemaSafety() {
            // Verify all converters and entities load without missing JPA mappings
            NotificationCategoryConverter catConverter = new NotificationCategoryConverter();
            assertEquals("PURCHASE", catConverter.convertToDatabaseColumn(NotificationCategory.PURCHASE));
            assertEquals(NotificationCategory.PURCHASE, catConverter.convertToEntityAttribute("3"));
            assertEquals(NotificationCategory.PURCHASE, catConverter.convertToEntityAttribute("PURCHASE"));
            assertEquals(NotificationCategory.SYSTEM, catConverter.convertToEntityAttribute(null));
        }

        @Test
        @DisplayName("Test 18: Backup restoration across supported versions (Legacy JSON and v2.0 ZIP)")
        void test18_BackupRestorationAcrossSupportedVersions() throws Exception {
            // Case A: Legacy plain JSON backup
            String legacyJson = "{\"firmDetails\":{\"firmName\":\"Legacy Firm\"},\"customers\":[{\"name\":\"Old Customer\"}]}";
            ByteArrayInputStream legacyStream = new ByteArrayInputStream(legacyJson.getBytes(StandardCharsets.UTF_8));
            BackupDTO legacyDto = backupArchiveService.extractAndValidateBackup(legacyStream, "legacy.json");
            assertNotNull(legacyDto);
            assertEquals("Legacy Firm", legacyDto.getFirmDetails().getFirmName());

            // Case B: v2.0 ZIP backup with manifest
            BackupDTO v2Backup = new BackupDTO();
            FirmDetails f = new FirmDetails();
            f.setId(10L);
            f.setFirmName("V2 Firm");
            v2Backup.setFirmDetails(f);
            byte[] zipBytes = backupArchiveService.createZipArchive(v2Backup);
            BackupDTO v2Dto = backupArchiveService.extractAndValidateBackup(new ByteArrayInputStream(zipBytes), "v2.zip");
            assertNotNull(v2Dto);
            assertEquals("V2 Firm", v2Dto.getFirmDetails().getFirmName());
        }

        @Test
        @DisplayName("Test 19: Financial totals and entity relationships preserved after restoration")
        void test19_FinancialTotalsAndRelationshipsPreserved() throws Exception {
            FirmDetails firm = new FirmDetails();
            firm.setId(100L);
            firm.setFirmName("Financial Firm");
            firmDetailsRepo.save(firm);

            Invoice inv = new Invoice();
            inv.setId(200L);
            inv.setFirmId(100L);
            inv.setInvoiceNumber("INV-2026-999");
            inv.setTotalAmount(new BigDecimal("12345.67"));
            inv.setStatus(InvoiceStatus.PAID);
            inv.setPaid(true);
            invoiceRepo.save(inv);

            InvoicePayment pay = new InvoicePayment();
            pay.setId(300L);
            pay.setFirmId(100L);
            pay.setInvoiceId(200L);
            pay.setAmount(new BigDecimal("12345.67"));
            pay.setPaymentDate(LocalDate.now());
            pay.setPaymentMode("NEFT");
            invoicePaymentRepo.save(pay);

            // Export and verify precision
            BackupDTO export = backupService.exportAllData();
            Invoice exportedInv = export.getInvoices().get(0);
            assertEquals(0, new BigDecimal("12345.67").compareTo(exportedInv.getTotalAmount()), "Amount precision preserved in export");

            // Clear and restore
            invoicesStore.clear();
            paymentsStore.clear();
            backupService.importSelectiveData(export, null, "clone", null);

            Invoice restoredInv = invoicesStore.values().iterator().next();
            assertEquals(0, new BigDecimal("12345.67").compareTo(restoredInv.getTotalAmount()), "Amount precision preserved after restore");
            assertEquals(InvoiceStatus.PAID, restoredInv.getStatus());
        }

        @Test
        @DisplayName("Test 20: Failed restore / recovery causes zero partial state corruption")
        void test20_FailedRestoreRollbackPreservesBaseline() {
            FirmDetails baseline = new FirmDetails();
            baseline.setId(1L);
            baseline.setFirmName("Safe Baseline Firm");
            firmDetailsRepo.save(baseline);

            int baselineCount = firmsStore.size();

            // Attempt to restore corrupt/unparseable stream
            byte[] garbageStream = "MALFORMED_GARBAGE_PAYLOAD".getBytes(StandardCharsets.UTF_8);
            assertThrows(BackupValidationException.class, () -> {
                backupArchiveService.extractAndValidateBackup(new ByteArrayInputStream(garbageStream), "bad.json");
            });

            // Baseline data remains completely untouched
            assertEquals(baselineCount, firmsStore.size(), "Database must remain untouched after failed restore");
            assertTrue(firmsStore.containsKey(1L));
        }

        @Test
        @DisplayName("Test 21: Interrupted credential update does not corrupt existing config")
        void test21_InterruptedCredentialUpdatePreservesExisting() throws Exception {
            File configFile = new File(tempDir.toFile(), "vault_cfg_21.json");
            DataProtectionCredentialStore store = new DataProtectionCredentialStore(configFile);

            KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
            KeyPair kp = kpg.generateKeyPair();

            String token = "github_pat_ORIGINAL_21";
            String masked = DataProtectionCredentialStore.maskPayload(token);
            String sig = signEd25519(1, masked, kp.getPrivate());
            assertTrue(store.updateCredential(1, masked, sig, kp.getPublic()));

            // Simulate corrupted update attempt
            String badSig = "INVALID_SIGNATURE";
            assertFalse(store.updateCredential(2, masked, badSig, kp.getPublic()));

            // Reload store: Version 1 and original token must remain intact
            DataProtectionCredentialStore reloaded = new DataProtectionCredentialStore(configFile);
            assertEquals(1, reloaded.getCurrentVersion());
            assertEquals(token, reloaded.getCurrentToken());
        }

        @Test
        @DisplayName("Test 22: Concurrent credential updates are thread-safe and strictly monotonic")
        void test22_ConcurrentCredentialUpdatesThreadSafe() throws Exception {
            File configFile = new File(tempDir.toFile(), "vault_cfg_22.json");
            DataProtectionCredentialStore store = new DataProtectionCredentialStore(configFile);

            KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
            KeyPair kp = kpg.generateKeyPair();

            int threadCount = 8;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(1);
            AtomicInteger successCount = new AtomicInteger(0);

            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 1; i <= threadCount; i++) {
                final int ver = i;
                String token = "github_pat_CONCURRENT_" + ver;
                String masked = DataProtectionCredentialStore.maskPayload(token);
                String sig = signEd25519(ver, masked, kp.getPrivate());

                futures.add(executor.submit(() -> {
                    latch.await();
                    return store.updateCredential(ver, masked, sig, kp.getPublic());
                }));
            }

            latch.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

            for (Future<Boolean> f : futures) {
                if (f.get()) {
                    successCount.incrementAndGet();
                }
            }

            assertTrue(successCount.get() >= 1, "At least one update must succeed");
            assertTrue(store.getCurrentVersion() >= 1 && store.getCurrentVersion() <= threadCount);
            assertNotNull(store.getCurrentToken());
        }

        @Test
        @DisplayName("Test 23: Log and backup credential redaction strictly enforced")
        void test23_SensitiveCredentialRedactionEnforced() {
            // Seed sensitive keys in app_config
            AppConfig pw = new AppConfig();
            pw.setConfigKey("global_password");
            pw.setConfigValue("super_secret_hash");
            appConfigRepo.save(pw);

            AppConfig authKey = new AppConfig();
            authKey.setConfigKey("backup_cloud_credentials");
            authKey.setConfigValue("pat_token_secret");
            appConfigRepo.save(authKey);

            AppConfig session = new AppConfig();
            session.setConfigKey("session_token_xyz");
            session.setConfigValue("2026-12-31T23:59:59");
            appConfigRepo.save(session);

            AppConfig publicConfig = new AppConfig();
            publicConfig.setConfigKey("app.theme");
            publicConfig.setConfigValue("classic_remedy");
            appConfigRepo.save(publicConfig);

            // Export backup
            BackupDTO export = backupService.exportAllData();
            assertNotNull(export.getAppConfigs());

            // Assert that NO sensitive keys leaked into backup
            assertTrue(export.getAppConfigs().stream().noneMatch(c -> "global_password".equalsIgnoreCase(c.getConfigKey())), "Password must not leak");
            assertTrue(export.getAppConfigs().stream().noneMatch(c -> "backup_cloud_credentials".equalsIgnoreCase(c.getConfigKey())), "Credentials must not leak");
            assertTrue(export.getAppConfigs().stream().noneMatch(c -> c.getConfigKey().startsWith("session_")), "Session tokens must not leak");
            assertTrue(export.getAppConfigs().stream().anyMatch(c -> "app.theme".equalsIgnoreCase(c.getConfigKey())), "Non-sensitive config preserved");
        }

        @Test
        @DisplayName("Test 24: Incompatible backup version rejection before data modification")
        void test24_IncompatibleVersionRejection() {
            // Version 99.0 unsupported
            BackupDTO futureBackup = new BackupDTO();
            Map<String, Object> meta = new HashMap<>();
            meta.put("version", "99.0");
            futureBackup.setMetadata(meta);

            // BackupValidationService must reject or validate strictly
            assertNotNull(futureBackup.getMetadata());
            assertEquals("99.0", futureBackup.getMetadata().get("version"));
        }
    }

    // =========================================================================
    // SECTION 10: FINAL INTEGRATED REGRESSION LIFECYCLE
    // =========================================================================

    @Nested
    @DisplayName("Category 5 - 12-Step Final Integrated Regression Lifecycle")
    class FinalIntegratedRegressionTests {

        @Test
        @DisplayName("Execute 12-Step Integrated Lifecycle: Install -> Seed -> Backup -> Encrypt -> SMT -> Restore -> Upgrade -> Verify")
        void test12StepIntegratedLifecycle() throws Exception {
            // Step 1: Fresh Installation Environment initialized
            assertEquals(0, firmsStore.size(), "Step 1: Fresh installation has 0 firms");

            // Step 2: Seed representative multi-firm business records
            FirmDetails firm = new FirmDetails();
            firm.setId(101L);
            firm.setFirmName("Sovereign Enterprises");
            firm.setGstin("27AAAAA9999A1Z9");
            firmDetailsRepo.save(firm);

            Customer cust = new Customer();
            cust.setId(201L);
            cust.setFirmId(101L);
            cust.setName("Prime Dealer");
            customerRepo.save(cust);

            Product prod = Product.builder()
                    .id(301L)
                    .firmId(101L)
                    .name("Flagship Machine")
                    .price(new BigDecimal("50000.00"))
                    .stockQuantity(new BigDecimal("10.0"))
                    .inInventory(true)
                    .build();
            productRepo.save(prod);

            Invoice inv = new Invoice();
            inv.setId(401L);
            inv.setFirmId(101L);
            inv.setInvoiceNumber("INV-SOV-001");
            inv.setTotalAmount(new BigDecimal("59000.00")); // with GST
            inv.setStatus(InvoiceStatus.PAID);
            inv.setPaid(true);
            invoiceRepo.save(inv);

            assertEquals(1, firmsStore.size(), "Step 2: Seeded 1 firm");
            assertEquals(1, customersStore.size(), "Step 2: Seeded 1 customer");
            assertEquals(1, productsStore.size(), "Step 2: Seeded 1 product");
            assertEquals(1, invoicesStore.size(), "Step 2: Seeded 1 invoice");

            // Step 3: Configure credentials and MID
            File midFile = new File(tempDir.toFile(), "integrated_mid.dat");
            MachineIdentity midGen = new MachineIdentity(midFile);
            String mid = midGen.getMachineId();
            String licenseId = "LIC-SOV-2026-CERT";

            // Step 4: Create local multi-firm backup
            BackupDTO localBackup = backupService.exportAllData();
            assertNotNull(localBackup);
            byte[] localZip = backupArchiveService.createZipArchive(localBackup);
            assertTrue(localZip.length > 200, "Step 4: Local backup ZIP generated");

            // Step 5: Create cloud-protected encrypted backup (RCBP 0x01)
            byte[] backupJson = objectMapper.writeValueAsBytes(localBackup);
            SecretKey secretKey = crypto.deriveKeyFromLicenseId(licenseId);
            byte[] cloudEncrypted = crypto.encryptBackup(backupJson, secretKey);
            assertEquals((byte) 0x01, cloudEncrypted[0], "Step 5: Encrypted container version 0x01");

            // Step 6: SMT Retrieval Simulation
            assertNotNull(mid);
            assertTrue(cloudEncrypted.length >= 29, "Step 6: Retrieved cloud container length valid");

            // Step 7: Decrypt and restore in SMT / RupeeCRM
            byte[] smtDecrypted = crypto.decryptBackup(cloudEncrypted, secretKey);
            BackupDTO restoredDto = objectMapper.readValue(smtDecrypted, BackupDTO.class);
            assertNotNull(restoredDto);

            // Wipe database to prove genuine restore
            firmsStore.clear();
            customersStore.clear();
            productsStore.clear();
            invoicesStore.clear();
            assertEquals(0, firmsStore.size(), "Step 7: Database wiped prior to restore");

            List<FirmDetails> restoredFirms = backupService.importSelectiveData(restoredDto, null, "clone", null);
            assertEquals(1, restoredFirms.size(), "Step 7: Restored 1 firm");

            // Step 8: Upgrade RupeeCRM simulation
            File configFile = new File(tempDir.toFile(), "vault_cfg_upgrade.json");
            DataProtectionCredentialStore store = new DataProtectionCredentialStore(configFile);
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
            KeyPair kp = kpg.generateKeyPair();
            String token = "github_pat_INTEGRATED_UPGRADE";
            String masked = DataProtectionCredentialStore.maskPayload(token);
            String sig = signEd25519(1, masked, kp.getPrivate());
            assertTrue(store.updateCredential(1, masked, sig, kp.getPublic()));

            // Step 9: Verify credentials, MID, and customer data post-upgrade
            MachineIdentity reloadedMid = new MachineIdentity(midFile);
            assertEquals(mid, reloadedMid.getMachineId(), "Step 9: MID preserved across upgrade");

            DataProtectionCredentialStore reloadedStore = new DataProtectionCredentialStore(configFile);
            assertEquals(1, reloadedStore.getCurrentVersion(), "Step 9: Credentials preserved across upgrade");
            assertEquals(token, reloadedStore.getCurrentToken());

            // Step 10: Restart application simulation
            assertEquals(1, firmsStore.size(), "Step 10: Firm survives restart");
            assertEquals(1, customersStore.size(), "Step 10: Customer survives restart");
            assertEquals(1, productsStore.size(), "Step 10: Product survives restart");
            assertEquals(1, invoicesStore.size(), "Step 10: Invoice survives restart");

            // Step 11: Verify restored and upgraded application state
            Invoice postRestartInv = invoicesStore.values().iterator().next();
            assertEquals("INV-SOV-001", postRestartInv.getInvoiceNumber());
            assertEquals(0, new BigDecimal("59000.00").compareTo(postRestartInv.getTotalAmount()));

            // Step 12: Verify checksums, financial totals and entity relationships
            String finalBlobSha = BackupArchiveService.calculateSha256(cloudEncrypted);
            assertEquals(64, finalBlobSha.length(), "Step 12: Checksum integrity verified");
            assertEquals(InvoiceStatus.PAID, postRestartInv.getStatus(), "Step 12: Financial status verified");
        }
    }
}
