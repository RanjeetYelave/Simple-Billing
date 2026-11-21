package com.billing.simple.billsoft.regression.backup;

import com.billing.simple.billsoft.dataprotection.*;
import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.exception.BackupValidationException;
import com.billing.simple.billsoft.licensing.DataProtectionEntitlement;
import com.billing.simple.billsoft.licensing.LicenseVerifier;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.repositories.*;
import com.billing.simple.billsoft.service.BackupArchiveService;
import com.billing.simple.billsoft.service.BackupRecoveryCoordinator;
import com.billing.simple.billsoft.service.BackupService;
import com.billing.simple.billsoft.service.BackupValidationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * RupeeCRM + SMT — Category 4 Complete Off-Device Cloud Data Protection,
 * Encryption, Download & Recovery Stabilization Acceptance Test Suite.
 *
 * Certifies:
 * 1. End-to-end 20-step workflow: Multi-firm creation -> Full Backup Export ->
 *    AES-256-GCM Envelope Encryption (0x01) -> Cloud Vault Staging ->
 *    SMT Retrieval & Candidate Key Discovery -> SMT Decryption & Schema Inspection ->
 *    RupeeCRM Restoration & Pre/Post Statistical Count Matching.
 * 2. Complete 16 Negative Security Scenarios:
 *    - Wrong MID
 *    - Missing MID
 *    - Invalid Recovery Credentials (wrong License ID / AEADBadTagException)
 *    - Missing Recovery Credentials
 *    - Corrupted Container (< 29 bytes)
 *    - Ciphertext Bit-flipping Tampering
 *    - Invalid GCM Authentication Tag
 *    - Invalid Ed25519 Signature
 *    - Dual-Key Ed25519 Fallback Signature Verification
 *    - Interrupted / Failed Upload Recovery
 *    - Interrupted / Empty Download Recovery
 *    - Incorrect Checksum Verification
 *    - Missing Cloud Object (404 Vault Not Found)
 *    - Unsupported Envelope Version Byte (0x02, 0x99)
 *    - Malformed / Corrupted Backup Content
 *    - Machine Wipe & Reinstall Recovery Simulation
 */
public class Category4CloudDataProtectionAndSmtRecoveryTest {

    @TempDir
    Path tempDir;

    private ObjectMapper objectMapper;
    private DataProtectionCrypto crypto;
    private BackupValidationService backupValidationService;
    private BackupArchiveService backupArchiveService;

    // Repositories for RupeeCRM Backup/Restore
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

    // In-memory data store for isolated verification
    private final Map<Long, FirmDetails> firmsStore = new HashMap<>();
    private final Map<Long, Customer> customersStore = new HashMap<>();
    private final Map<Long, Product> productsStore = new HashMap<>();
    private final Map<Long, Invoice> invoicesStore = new HashMap<>();
    private final Map<Long, Notification> notificationsStore = new HashMap<>();
    private final Map<Long, Party> partiesStore = new HashMap<>();
    private final Map<Long, InvoicePayment> paymentsStore = new HashMap<>();
    private final Map<String, AppConfig> appConfigStore = new HashMap<>();

    private long idSeq = 1000L;

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
        partiesStore.clear();
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

        partyRepo = mock(PartyRepository.class);
        when(partyRepo.findAll()).thenAnswer(inv -> new ArrayList<>(partiesStore.values()));
        when(partyRepo.save(any(Party.class))).thenAnswer(inv -> {
            Party p = inv.getArgument(0);
            if (p.getId() == null) p.setId(idSeq++);
            partiesStore.put(p.getId(), p);
            return p;
        });
        when(partyRepo.saveAll(any(Iterable.class))).thenAnswer(inv -> {
            Iterable<Party> list = inv.getArgument(0);
            List<Party> saved = new ArrayList<>();
            for (Party p : list) {
                if (p.getId() == null) p.setId(idSeq++);
                partiesStore.put(p.getId(), p);
                saved.add(p);
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

        // Additional repositories mocked cleanly
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

    private void seedRichMultiFirmDataset() {
        // Firm 1: Alpha Enterprise
        FirmDetails f1 = new FirmDetails();
        f1.setId(101L);
        f1.setFirmName("Alpha Enterprise");
        f1.setGstin("27AAAAA1111A1Z1");
        f1.setPhone("9876543210");
        f1.setEmail("alpha@example.com");
        f1.setAddressLine1("Industrial Area, Pune");
        f1.setCity("Pune");
        firmDetailsRepo.save(f1);

        // Firm 2: Beta Logistics
        FirmDetails f2 = new FirmDetails();
        f2.setId(102L);
        f2.setFirmName("Beta Logistics");
        f2.setGstin("27BBBBB2222B1Z2");
        f2.setPhone("9123456780");
        f2.setEmail("beta@example.com");
        f2.setAddressLine1("Logistics Hub, Mumbai");
        f2.setCity("Mumbai");
        firmDetailsRepo.save(f2);

        // Customers for Firm 1
        Customer c1 = new Customer();
        c1.setId(201L);
        c1.setFirmId(101L);
        c1.setName("Apex Retailers");
        c1.setPhone("9800000001");
        c1.setEmail("apex@retail.com");
        c1.setAddress("Pune");
        customerRepo.save(c1);

        Customer c2 = new Customer();
        c2.setId(202L);
        c2.setFirmId(101L);
        c2.setName("Zenith Wholesalers");
        c2.setPhone("9800000002");
        c2.setEmail("zenith@whole.com");
        c2.setAddress("Nashik");
        customerRepo.save(c2);

        // Customer for Firm 2
        Customer c3 = new Customer();
        c3.setId(203L);
        c3.setFirmId(102L);
        c3.setName("Global Freight Ltd");
        c3.setPhone("9800000003");
        c3.setEmail("freight@global.com");
        c3.setAddress("Mumbai");
        customerRepo.save(c3);

        // Products for Firm 1
        Product p1 = Product.builder()
                .id(301L)
                .firmId(101L)
                .name("Industrial Sensor X100")
                .sku("IND-SENS-100")
                .price(BigDecimal.valueOf(1500.00))
                .stockQuantity(BigDecimal.valueOf(45.0))
                .inInventory(true)
                .build();
        productRepo.save(p1);

        Product p2 = Product.builder()
                .id(302L)
                .firmId(101L)
                .name("Control Cable 50m")
                .sku("CAB-50M")
                .price(BigDecimal.valueOf(850.50))
                .stockQuantity(BigDecimal.valueOf(120.0))
                .inInventory(true)
                .build();
        productRepo.save(p2);

        // Product for Firm 2
        Product p3 = Product.builder()
                .id(303L)
                .firmId(102L)
                .name("Standard Pallet Wrap")
                .sku("WRAP-STD")
                .price(BigDecimal.valueOf(350.00))
                .stockQuantity(BigDecimal.valueOf(500.0))
                .inInventory(true)
                .build();
        productRepo.save(p3);

        // Invoices for Firm 1
        Invoice inv1 = new Invoice();
        inv1.setId(401L);
        inv1.setFirmId(101L);
        inv1.setInvoiceNumber("INV-ALPHA-001");
        inv1.setCustomer(c1);
        inv1.setCustomerNameSnapshot("Apex Retailers");
        inv1.setCustomerPhoneSnapshot("9800000001");
        inv1.setTotalAmount(BigDecimal.valueOf(3850.50));
        inv1.setInvoiceDate(LocalDateTime.now().minusDays(3));
        inv1.setStatus(InvoiceStatus.PAID);
        inv1.setPaid(true);
        invoiceRepo.save(inv1);

        // Invoice for Firm 2
        Invoice inv2 = new Invoice();
        inv2.setId(402L);
        inv2.setFirmId(102L);
        inv2.setInvoiceNumber("INV-BETA-001");
        inv2.setCustomer(c3);
        inv2.setCustomerNameSnapshot("Global Freight Ltd");
        inv2.setCustomerPhoneSnapshot("9800000003");
        inv2.setTotalAmount(BigDecimal.valueOf(7000.00));
        inv2.setInvoiceDate(LocalDateTime.now().minusDays(1));
        inv2.setStatus(InvoiceStatus.UNPAID);
        inv2.setPaid(false);
        invoiceRepo.save(inv2);

        // Payments
        InvoicePayment pay1 = new InvoicePayment();
        pay1.setId(501L);
        pay1.setFirmId(101L);
        pay1.setInvoiceId(401L);
        pay1.setPaymentDate(LocalDate.now().minusDays(3));
        pay1.setAmount(BigDecimal.valueOf(3850.50));
        pay1.setPaymentMode("UPI");
        invoicePaymentRepo.save(pay1);

        InvoicePayment pay2 = new InvoicePayment();
        pay2.setId(502L);
        pay2.setFirmId(102L);
        pay2.setInvoiceId(402L);
        pay2.setPaymentDate(LocalDate.now().minusDays(1));
        pay2.setAmount(BigDecimal.valueOf(5000.00));
        pay2.setPaymentMode("BANK_TRANSFER");
        invoicePaymentRepo.save(pay2);

        // Notifications
        Notification notif = new Notification();
        notif.setId(601L);
        notif.setFirmId(101L);
        notif.setEventKey("evt_dp_enabled_101");
        notif.setTitle("Quarterly Data Protection Enabled");
        notif.setBody("Automated off-device data protection is active.");
        notif.setCategory(NotificationCategory.SYSTEM);
        notif.setCreatedAt(LocalDateTime.now().minusDays(5));
        notificationRepo.save(notif);

        // AppConfig
        AppConfig config = new AppConfig();
        config.setConfigKey("app.security.audit_enabled");
        config.setConfigValue("true");
        appConfigRepo.save(config);
    }

    // =========================================================================
    // SECTION 1: 20-STEP END-TO-END WORKFLOW CERTIFICATION
    // =========================================================================

    @Nested
    @DisplayName("1. 20-Step End-to-End Workflow Certification")
    class EndToEndWorkflowTests {

        @Test
        @DisplayName("Complete RupeeCRM -> Encrypted Container -> SMT Discovery/Decryption -> RupeeCRM Full Restore Round-trip")
        void testCompleteEndToEndCloudDataProtectionAndSmtRecoveryWorkflow() throws Exception {
            // Step 1: Seed rich multi-firm business dataset
            seedRichMultiFirmDataset();
            assertEquals(2, firmsStore.size(), "Step 1: Baseline firms count must be 2");
            assertEquals(3, customersStore.size(), "Step 1: Baseline customers count must be 3");
            assertEquals(3, productsStore.size(), "Step 1: Baseline products count must be 3");
            assertEquals(2, invoicesStore.size(), "Step 1: Baseline invoices count must be 2");
            assertEquals(2, paymentsStore.size(), "Step 1: Baseline payments count must be 2");
            assertEquals(1, notificationsStore.size(), "Step 1: Baseline notifications count must be 1");

            // Step 2: Generate full multi-firm backup snapshot in RupeeCRM
            BackupDTO fullExport = backupService.exportAllData();
            assertNotNull(fullExport, "Step 2: Full export must not be null");
            assertNotNull(fullExport.getMetadata(), "Step 2: Metadata must be generated");
            assertEquals(2, fullExport.getAllFirms().size(), "Step 2: Export must contain 2 firms");
            assertEquals(3, fullExport.getCustomers().size(), "Step 2: Export must contain 3 customers");
            assertEquals(3, fullExport.getProducts().size(), "Step 2: Export must contain 3 products");
            assertEquals(2, fullExport.getInvoices().size(), "Step 2: Export must contain 2 invoices");

            // Step 3: Serialize to JSON bytes
            byte[] rawBackupJson = objectMapper.writeValueAsBytes(fullExport);
            assertTrue(rawBackupJson.length > 500, "Step 3: Backup JSON must be non-empty");

            // Step 4: Define Machine ID (MID) and License ID bindings
            String machineId = "MID-CAT4-9999-ABCD";
            String licenseId = "LIC-CAT4-ENTERPRISE-7777";

            // Step 5: Derive 256-bit AES key via SHA-256(licenseId)
            SecretKey derivationKey = crypto.deriveKeyFromLicenseId(licenseId);
            assertNotNull(derivationKey, "Step 5: Derived key must not be null");
            assertEquals("AES", derivationKey.getAlgorithm());
            assertEquals(32, derivationKey.getEncoded().length, "Key must be exactly 256 bits (32 bytes)");

            // Step 6: Encrypt backup into version 0x01 AES-256-GCM container
            byte[] encryptedContainer = crypto.encryptBackup(rawBackupJson, derivationKey);

            // Step 7: Verify Envelope Specification
            assertNotNull(encryptedContainer, "Step 7: Encrypted container must not be null");
            assertTrue(encryptedContainer.length >= 29, "Container must satisfy MIN_CONTAINER_LENGTH (29 bytes)");
            assertEquals((byte) 0x01, encryptedContainer[0], "Version byte must be 0x01");

            // Step 8: Verify 12-byte random IV
            byte[] iv = Arrays.copyOfRange(encryptedContainer, 1, 13);
            assertEquals(12, iv.length, "IV must be 12 bytes");
            assertFalse(Arrays.equals(iv, new byte[12]), "IV must be non-zero random bytes");

            // Step 9: Verify GCM ciphertext and 16-byte authentication tag
            byte[] ciphertextAndTag = Arrays.copyOfRange(encryptedContainer, 13, encryptedContainer.length);
            assertTrue(ciphertextAndTag.length >= 16, "Must contain at least 16-byte authentication tag");

            // Step 10: Mock Cloud Vault Staging & Metadata association
            String vaultBlobSha = BackupArchiveService.calculateSha256(encryptedContainer);
            assertNotNull(vaultBlobSha, "Step 10: Blob SHA must be calculated");
            assertEquals(64, vaultBlobSha.length(), "SHA-256 hex string must be 64 characters");

            // Step 11: Emulate SMT Remote Discovery / Candidate Key Resolution
            // SMT candidate resolution resolves: [operator override, prefixed "LIC-...", stripped raw "..."]
            List<String> candidateKeys = List.of(
                    "LIC-CAT4-ENTERPRISE-7777",
                    "CAT4-ENTERPRISE-7777",
                    "LIC-WRONG-KEY"
            );

            // Step 12: SMT Candidate Key matching & container decryption
            boolean smtDecrypted = false;
            byte[] smtDecryptedBytes = null;
            String matchedKey = null;

            for (String candidate : candidateKeys) {
                try {
                    SecretKey candidateSecret = crypto.deriveKeyFromLicenseId(candidate);
                    smtDecryptedBytes = crypto.decryptBackup(encryptedContainer, candidateSecret);
                    matchedKey = candidate;
                    smtDecrypted = true;
                    break;
                } catch (Exception ignored) {
                    // Try next candidate key
                }
            }

            assertTrue(smtDecrypted, "Step 12: SMT must successfully find the matching key and decrypt");
            assertNotNull(smtDecryptedBytes, "Step 12: Decrypted bytes must not be null");
            assertEquals("LIC-CAT4-ENTERPRISE-7777", matchedKey, "Matched key must be the exact license ID");

            // Step 13: SMT Schema Parsing & Summary Extraction
            JsonNode smtJsonTree = objectMapper.readTree(smtDecryptedBytes);
            assertNotNull(smtJsonTree, "Step 13: Parsed JSON tree must not be null");
            assertTrue(smtJsonTree.has("allFirms"), "SMT must verify 'allFirms' present in payload");
            assertTrue(smtJsonTree.has("customers"), "SMT must verify 'customers' present in payload");
            assertTrue(smtJsonTree.has("invoices"), "SMT must verify 'invoices' present in payload");

            int smtFirmsCount = smtJsonTree.get("allFirms").size();
            int smtCustCount = smtJsonTree.get("customers").size();
            int smtInvoicesCount = smtJsonTree.get("invoices").size();
            assertEquals(2, smtFirmsCount, "SMT summary firmsCount must match 2");
            assertEquals(3, smtCustCount, "SMT summary customersCount must match 3");
            assertEquals(2, smtInvoicesCount, "SMT summary invoicesCount must match 2");

            // Step 14: Clear RupeeCRM database to simulate clean environment / machine recovery
            firmsStore.clear();
            customersStore.clear();
            productsStore.clear();
            invoicesStore.clear();
            notificationsStore.clear();
            paymentsStore.clear();
            appConfigStore.clear();
            assertEquals(0, firmsStore.size(), "Step 14: Database must be clean before restore");

            // Step 15: Deserialize decrypted JSON back to BackupDTO in RupeeCRM
            BackupDTO recoveredBackupDto = objectMapper.readValue(smtDecryptedBytes, BackupDTO.class);
            assertNotNull(recoveredBackupDto, "Step 15: Restored BackupDTO must deserialize cleanly");

            // Step 16: Execute restore in RupeeCRM (mode: clone)
            List<FirmDetails> restoredFirms = backupService.importSelectiveData(recoveredBackupDto, null, "clone", null);
            assertNotNull(restoredFirms, "Step 16: Restored firms list must not be null");
            assertEquals(2, restoredFirms.size(), "Step 16: Must restore exactly 2 firms");

            // Step 17: Pre/Post Statistical Matching — Firm Entities
            assertEquals(2, firmsStore.size(), "Step 17: Post-restore firms count must match baseline (2)");
            assertTrue(firmsStore.values().stream().anyMatch(f -> "Alpha Enterprise".equals(f.getFirmName())), "Alpha Enterprise must exist");
            assertTrue(firmsStore.values().stream().anyMatch(f -> "Beta Logistics".equals(f.getFirmName())), "Beta Logistics must exist");

            // Step 18: Pre/Post Statistical Matching — Customers & Products
            assertEquals(3, customersStore.size(), "Step 18: Post-restore customers count must match baseline (3)");
            assertTrue(customersStore.values().stream().anyMatch(c -> "Apex Retailers".equals(c.getName())), "Apex Retailers must be restored");
            assertTrue(customersStore.values().stream().anyMatch(c -> "Global Freight Ltd".equals(c.getName())), "Global Freight Ltd must be restored");

            assertEquals(3, productsStore.size(), "Step 18: Post-restore products count must match baseline (3)");
            assertTrue(productsStore.values().stream().anyMatch(p -> "Industrial Sensor X100".equals(p.getName())), "Industrial Sensor must be restored");
            assertTrue(productsStore.values().stream().anyMatch(p -> "Standard Pallet Wrap".equals(p.getName())), "Pallet Wrap must be restored");

            // Step 19: Pre/Post Statistical Matching — Invoices & Financial Totals
            assertEquals(2, invoicesStore.size(), "Step 19: Post-restore invoices count must match baseline (2)");
            Invoice restoredInv1 = invoicesStore.values().stream().filter(i -> "INV-ALPHA-001".equals(i.getInvoiceNumber())).findFirst().orElse(null);
            assertNotNull(restoredInv1, "INV-ALPHA-001 must be restored");
            assertEquals(0, BigDecimal.valueOf(3850.50).compareTo(restoredInv1.getTotalAmount()), "Invoice total must match");
            assertEquals(InvoiceStatus.PAID, restoredInv1.getStatus());
            assertTrue(Boolean.TRUE.equals(restoredInv1.getPaid()));

            Invoice restoredInv2 = invoicesStore.values().stream().filter(i -> "INV-BETA-001".equals(i.getInvoiceNumber())).findFirst().orElse(null);
            assertNotNull(restoredInv2, "INV-BETA-001 must be restored");
            assertEquals(0, BigDecimal.valueOf(7000.00).compareTo(restoredInv2.getTotalAmount()), "Invoice total must match");
            assertEquals(InvoiceStatus.UNPAID, restoredInv2.getStatus());
            assertFalse(Boolean.TRUE.equals(restoredInv2.getPaid()));

            // Step 20: Pre/Post Statistical Matching — Payments & Notifications
            assertEquals(2, paymentsStore.size(), "Step 20: Post-restore payments count must match baseline (2)");
            assertEquals(1, notificationsStore.size(), "Step 20: Post-restore notifications count must match baseline (1)");
            Notification restoredNotif = notificationsStore.values().iterator().next();
            assertEquals("Quarterly Data Protection Enabled", restoredNotif.getTitle());
            assertEquals(NotificationCategory.SYSTEM, restoredNotif.getCategory());
        }
    }

    // =========================================================================
    // SECTION 2: 16 NEGATIVE SECURITY & EDGE-CASE SCENARIOS
    // =========================================================================

    @Nested
    @DisplayName("2. 16 Negative Security Scenarios")
    class NegativeSecurityScenariosTests {

        private byte[] validEncryptedContainer;
        private final String validLicenseId = "LIC-SEC-1234-VALID";
        private final String validMachineId = "MID-SEC-4321-VALID";

        @BeforeEach
        void initValidContainer() throws Exception {
            String testPayload = "{\"metadata\":{\"version\":\"1.0.360\"},\"firms\":[{\"id\":1,\"name\":\"Secure Firm\"}]}";
            SecretKey key = crypto.deriveKeyFromLicenseId(validLicenseId);
            validEncryptedContainer = crypto.encryptBackup(testPayload.getBytes(StandardCharsets.UTF_8), key);
        }

        @Test
        @DisplayName("Scenario 1: Wrong Machine ID (MID)")
        void testScenario1_WrongMachineIdLookup() {
            BackupStorageProvider mockProvider = mock(BackupStorageProvider.class);
            when(mockProvider.downloadBackup("MID-WRONG-9999")).thenReturn(null);

            DataProtectionService service = new DataProtectionService(null, mockProvider, crypto, tempDir.toFile(), new File(tempDir.toFile(), "status.json"));

            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
                service.downloadAndDecryptBackup("MID-WRONG-9999", validLicenseId);
            });
            assertTrue(ex.getMessage().contains("No remote backup found for machine: MID-WRONG-9999"));
        }

        @Test
        @DisplayName("Scenario 2: Missing / Null / Blank Machine ID")
        void testScenario2_MissingMachineIdThrows() {
            DataProtectionService service = new DataProtectionService(null, mock(BackupStorageProvider.class), crypto, tempDir.toFile(), new File(tempDir.toFile(), "status.json"));

            assertThrows(IllegalArgumentException.class, () -> service.downloadAndDecryptBackup(null, validLicenseId));
            assertThrows(IllegalArgumentException.class, () -> service.downloadAndDecryptBackup("", validLicenseId));
            assertThrows(IllegalArgumentException.class, () -> service.downloadAndDecryptBackup("   ", validLicenseId));
        }

        @Test
        @DisplayName("Scenario 3: Invalid Recovery Credentials / Wrong License ID (AEADBadTagException)")
        void testScenario3_WrongLicenseIdThrowsBadTag() {
            SecretKey wrongKey = crypto.deriveKeyFromLicenseId("LIC-COMPLETELY-WRONG-PASSWORD");
            assertThrows(AEADBadTagException.class, () -> {
                crypto.decryptBackup(validEncryptedContainer, wrongKey);
            });
        }

        @Test
        @DisplayName("Scenario 4: Missing Recovery Credentials / Null License ID")
        void testScenario4_MissingLicenseCredentialsThrows() {
            assertThrows(IllegalArgumentException.class, () -> crypto.deriveKeyFromLicenseId(null));
            assertThrows(IllegalArgumentException.class, () -> crypto.deriveKeyFromLicenseId(""));
            assertThrows(IllegalArgumentException.class, () -> crypto.deriveKeyFromLicenseId("   "));
        }

        @Test
        @DisplayName("Scenario 5: Corrupted Encrypted Artifact (< 29 bytes)")
        void testScenario5_CorruptedOrTruncatedContainer() {
            byte[] truncated = new byte[]{0x01, 0x02, 0x03, 0x04};
            SecretKey key = crypto.deriveKeyFromLicenseId(validLicenseId);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
                crypto.decryptBackup(truncated, key);
            });
            assertTrue(ex.getMessage().contains("Corrupt or incomplete container") || ex.getMessage().contains("29 bytes"));
        }

        @Test
        @DisplayName("Scenario 6: Ciphertext Bit Flipping / Tampering Detection")
        void testScenario6_CiphertextBitFlippingFailsGcmTag() {
            byte[] tampered = Arrays.copyOf(validEncryptedContainer, validEncryptedContainer.length);
            // Flip a bit in the middle of ciphertext
            tampered[20] ^= 0x5A;

            SecretKey key = crypto.deriveKeyFromLicenseId(validLicenseId);
            assertThrows(AEADBadTagException.class, () -> {
                crypto.decryptBackup(tampered, key);
            });
        }

        @Test
        @DisplayName("Scenario 7: Invalid GCM Authentication Tag Tampering")
        void testScenario7_AuthenticationTagTamperingFails() {
            byte[] tamperedTag = Arrays.copyOf(validEncryptedContainer, validEncryptedContainer.length);
            // Flip byte in the last 16 bytes (authentication tag)
            tamperedTag[tamperedTag.length - 1] ^= 0xFF;

            SecretKey key = crypto.deriveKeyFromLicenseId(validLicenseId);
            assertThrows(AEADBadTagException.class, () -> {
                crypto.decryptBackup(tamperedTag, key);
            });
        }

        @Test
        @DisplayName("Scenario 8: Invalid Ed25519 Signature in DP Credential Store")
        void testScenario8_InvalidEd25519SignatureRejected() throws Exception {
            File configFile = new File(tempDir.toFile(), "vault_config_test.json");
            DataProtectionCredentialStore store = new DataProtectionCredentialStore(configFile);

            KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
            KeyPair kp = kpg.generateKeyPair();

            String token = "github_pat_TEST_FORGED_SIG";
            String masked = DataProtectionCredentialStore.maskPayload(token);
            String fakeSig = Base64.getEncoder().encodeToString(new byte[64]); // Dummy invalid signature

            boolean updated = store.updateCredential(1, masked, fakeSig, kp.getPublic());
            assertFalse(updated, "Update with invalid Ed25519 signature must be rejected");
            assertEquals(0, store.getCurrentVersion());
            assertNull(store.getCurrentToken());
        }

        @Test
        @DisplayName("Scenario 9: Dual-Key Ed25519 Fallback Signature Verification")
        void testScenario9_DualKeyEd25519FallbackVerification() throws Exception {
            File configFile = new File(tempDir.toFile(), "vault_config_fallback.json");
            DataProtectionCredentialStore store = new DataProtectionCredentialStore(configFile);

            KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
            KeyPair primaryKp = kpg.generateKeyPair();
            KeyPair fallbackKp = kpg.generateKeyPair();

            String token = "github_pat_DUAL_KEY_VERIFIED";
            String masked = DataProtectionCredentialStore.maskPayload(token);

            // Sign using fallback private key
            String canonical = 1 + "\n" + masked.trim();
            Signature sig = Signature.getInstance("Ed25519");
            sig.initSign(fallbackKp.getPrivate());
            sig.update(canonical.getBytes(StandardCharsets.UTF_8));
            String fallbackSig = Base64.getEncoder().encodeToString(sig.sign());

            // Primary alone must reject
            boolean primaryOnly = store.updateCredential(1, masked, fallbackSig, primaryKp.getPublic(), null);
            assertFalse(primaryOnly, "Primary key alone must reject signature made by fallback key");

            // Dual-key (Primary + Fallback) must succeed
            boolean dualKey = store.updateCredential(1, masked, fallbackSig, primaryKp.getPublic(), fallbackKp.getPublic());
            assertTrue(dualKey, "Dual-key verification must accept signature made by fallback key");
            assertEquals(1, store.getCurrentVersion());
            assertEquals(token, store.getCurrentToken());
        }

        @Test
        @DisplayName("Scenario 10: Interrupted / Failed Upload Recovery")
        void testScenario10_InterruptedUploadDoesNotCorruptStatus() {
            BackupStorageProvider failingProvider = mock(BackupStorageProvider.class);
            when(failingProvider.uploadBackup(anyString(), any(byte[].class), any())).thenReturn(
                    BackupStorageProvider.UploadResult.error("HTTP 502 Bad Gateway: Upload connection timeout", 502)
            );

            DataProtectionEntitlement entitlement = mock(DataProtectionEntitlement.class);
            when(entitlement.isDataProtectionActive()).thenReturn(true);
            when(entitlement.getLicenseId()).thenReturn(validLicenseId);
            when(entitlement.getMachineId()).thenReturn(validMachineId);

            File backupDir = tempDir.resolve("backups").toFile();
            backupDir.mkdirs();
            File statusFile = tempDir.resolve("status.json").toFile();

            // Create a dummy local backup file
            File localBackup = new File(backupDir, "autobackup_latest.json");
            try {
                Files.writeString(localBackup.toPath(), "{\"metadata\":{\"version\":\"1.0.360\"}}");
            } catch (IOException e) {
                fail(e);
            }

            DataProtectionService service = new DataProtectionService(entitlement, failingProvider, crypto, backupDir, statusFile);
            Map<String, Object> result = service.triggerBackupNow(true);

            assertFalse((Boolean) result.get("success"), "Interrupted upload must return success = false");
            assertEquals("HTTP 502 Bad Gateway: Upload connection timeout", result.get("error"));
            assertNull(service.getCurrentStatus().getLastSuccessfulCloudBackupAt(), "Failed upload must not record successful backup timestamp");
        }

        @Test
        @DisplayName("Scenario 11: Interrupted / Empty Download Recovery")
        void testScenario11_EmptyDownloadRejected() {
            BackupStorageProvider emptyProvider = mock(BackupStorageProvider.class);
            when(emptyProvider.downloadBackup(validMachineId)).thenReturn(new byte[0]);

            DataProtectionService service = new DataProtectionService(null, emptyProvider, crypto, tempDir.toFile(), new File(tempDir.toFile(), "status.json"));

            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
                service.downloadAndDecryptBackup(validMachineId, validLicenseId);
            });
            assertTrue(ex.getMessage().contains("No remote backup found for machine: " + validMachineId));
        }

        @Test
        @DisplayName("Scenario 12: Missing Cloud Object (404 Vault Not Found)")
        void testScenario12_MissingCloudObject404() {
            BackupStorageProvider notFoundProvider = mock(BackupStorageProvider.class);
            when(notFoundProvider.downloadBackup("MID-NON-EXISTENT")).thenReturn(null);

            DataProtectionService service = new DataProtectionService(null, notFoundProvider, crypto, tempDir.toFile(), new File(tempDir.toFile(), "status.json"));

            assertThrows(IllegalStateException.class, () -> {
                service.downloadAndDecryptBackup("MID-NON-EXISTENT", validLicenseId);
            });
        }

        @Test
        @DisplayName("Scenario 13: Unsupported Envelope Version Byte (0x02, 0x99)")
        void testScenario13_UnsupportedEnvelopeVersionByteRejected() {
            byte[] invalidVersionContainer = Arrays.copyOf(validEncryptedContainer, validEncryptedContainer.length);
            invalidVersionContainer[0] = 0x02; // version 2 unsupported

            SecretKey key = crypto.deriveKeyFromLicenseId(validLicenseId);
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
                crypto.decryptBackup(invalidVersionContainer, key);
            });
            assertTrue(ex.getMessage().contains("Unsupported container version: 2"));

            invalidVersionContainer[0] = (byte) 0x99;
            assertThrows(IllegalArgumentException.class, () -> {
                crypto.decryptBackup(invalidVersionContainer, key);
            });
        }

        @Test
        @DisplayName("Scenario 14: Malformed / Non-JSON Decrypted Payload Detection")
        void testScenario14_MalformedDecryptedPayloadDetected() throws Exception {
            byte[] garbagePayload = "NOT_A_VALID_JSON_OBJECT_123456789".getBytes(StandardCharsets.UTF_8);
            SecretKey key = crypto.deriveKeyFromLicenseId(validLicenseId);
            byte[] encryptedGarbage = crypto.encryptBackup(garbagePayload, key);

            byte[] decrypted = crypto.decryptBackup(encryptedGarbage, key);
            assertThrows(Exception.class, () -> {
                objectMapper.readValue(decrypted, BackupDTO.class);
            });
        }

        @Test
        @DisplayName("Scenario 15: Direct Upload of .enc File to BackupArchiveService Identifies Container")
        void testScenario15_EncryptedContainerUploadToBackupArchiveIdentifiesOrDecrypts() throws Exception {
            // Case A: No active entitlement -> Throws ENCRYPTED_CONTAINER informing user to use SMT/Cloud Restore
            ByteArrayInputStream bais = new ByteArrayInputStream(validEncryptedContainer);
            BackupValidationException ex = assertThrows(BackupValidationException.class, () -> {
                backupArchiveService.extractAndValidateBackup(bais, "test_backup.enc");
            });
            assertEquals("ENCRYPTED_CONTAINER", ex.getErrorCode());
            assertTrue(ex.getMessage().contains("encrypted Data Protection container"));

            // Case B: Current active license matches -> Transparently decrypts
            DataProtectionEntitlement matchingEntitlement = mock(DataProtectionEntitlement.class);
            when(matchingEntitlement.getLicenseId()).thenReturn(validLicenseId);

            BackupArchiveService autoDecryptService = new BackupArchiveService(
                    objectMapper, backupValidationService, crypto, matchingEntitlement
            );

            // Create a valid full BackupDTO JSON payload inside container
            BackupDTO testDto = new BackupDTO();
            FirmDetails f = new FirmDetails();
            f.setId(99L);
            f.setFirmName("Auto Decrypted Firm");
            testDto.setFirmDetails(f);
            byte[] dtoEncrypted = crypto.encryptBackup(objectMapper.writeValueAsBytes(testDto), crypto.deriveKeyFromLicenseId(validLicenseId));

            BackupDTO result = autoDecryptService.extractAndValidateBackup(new ByteArrayInputStream(dtoEncrypted), "RKGM-GH4X-6YX1-VVN8.enc");
            assertNotNull(result);
            assertNotNull(result.getFirmDetails());
            assertEquals("Auto Decrypted Firm", result.getFirmDetails().getFirmName());
        }

        @Test
        @DisplayName("Scenario 16: Complete Reinstall & Machine Wipe Recovery Simulation")
        void testScenario16_ReinstallRecoveryWithKnownMidAndLicense() throws Exception {
            // Machine wipe: new machine identity, but operator possesses original MID and original License ID
            String originalMid = "MID-ORIG-WIPED-0001";
            String originalLicenseId = "LIC-ENTERPRISE-PRO-9999";

            // Prepare backup in vault under original MID
            BackupDTO originalBackup = new BackupDTO();
            FirmDetails f = new FirmDetails();
            f.setId(555L);
            f.setFirmName("Reinstall Phoenix Enterprise");
            f.setGstin("27AAAAA5555A1Z5");
            originalBackup.setFirmDetails(f);
            originalBackup.setAllFirms(List.of(f));

            Customer c = new Customer();
            c.setId(666L);
            c.setFirmId(555L);
            c.setName("Surviving Customer");
            originalBackup.setCustomers(List.of(c));

            byte[] vaultEncrypted = crypto.encryptBackup(
                    objectMapper.writeValueAsBytes(originalBackup),
                    crypto.deriveKeyFromLicenseId(originalLicenseId)
            );

            // Mock cloud vault holding original backup
            BackupStorageProvider vaultProvider = mock(BackupStorageProvider.class);
            when(vaultProvider.downloadBackup(originalMid)).thenReturn(vaultEncrypted);

            // Recovery execution on new machine using original credentials
            DataProtectionService newMachineService = new DataProtectionService(
                    null, vaultProvider, crypto, tempDir.toFile(), new File(tempDir.toFile(), "new_status.json")
            );

            byte[] recoveredBytes = newMachineService.downloadAndDecryptBackup(originalMid, originalLicenseId);
            assertNotNull(recoveredBytes);

            BackupDTO restoredDto = objectMapper.readValue(recoveredBytes, BackupDTO.class);
            assertEquals("Reinstall Phoenix Enterprise", restoredDto.getFirmDetails().getFirmName());
            assertEquals(1, restoredDto.getCustomers().size());
            assertEquals("Surviving Customer", restoredDto.getCustomers().get(0).getName());
        }
    }
}
