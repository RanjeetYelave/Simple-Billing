package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.dto.BackupInspectionDTO;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BackupSelectiveImportTest {

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
    @Mock
    private BackupValidationService backupValidationService;

    @InjectMocks
    private BackupService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testInspectBackupSingleFirm() {
        BackupDTO backup = new BackupDTO();
        Map<String, Object> meta = new HashMap<>();
        meta.put("version", "2.0");
        meta.put("exportDate", "2026-09-04T12:00:00");
        meta.put("firmId", 1L);
        backup.setMetadata(meta);

        FirmDetails firm = new FirmDetails();
        firm.setId(1L);
        firm.setFirmName("Single Test Firm");
        firm.setGstin("27AAAAA0000A1Z5");
        backup.setFirmDetails(firm);

        Customer c = new Customer();
        c.setId(10L);
        c.setFirmId(1L);
        c.setName("Customer 1");
        backup.setCustomers(Collections.singletonList(c));

        Product p = Product.builder().firmId(1L).name("Prod 1").price(BigDecimal.TEN).build();
        p.setId(20L);
        backup.setProducts(Collections.singletonList(p));

        BackupInspectionDTO inspection = service.inspectBackup(backup);
        assertNotNull(inspection);
        assertEquals("SINGLE_FIRM", inspection.getBackupType());
        assertEquals(1, inspection.getFirms().size());
        assertEquals("Single Test Firm", inspection.getFirms().get(0).getFirmName());
        assertEquals(1, inspection.getFirms().get(0).getCustomerCount());
        assertEquals(1, inspection.getFirms().get(0).getProductCount());
        assertEquals(1, inspection.getTotalStats().get("totalFirms"));
    }

    @Test
    void testInspectBackupMultiFirm() {
        BackupDTO backup = new BackupDTO();
        Map<String, Object> meta = new HashMap<>();
        meta.put("version", "2.0");
        meta.put("type", "FULL_SYSTEM_BACKUP");
        backup.setMetadata(meta);

        FirmDetails f1 = new FirmDetails();
        f1.setId(1L);
        f1.setFirmName("Firm Alpha");

        FirmDetails f2 = new FirmDetails();
        f2.setId(2L);
        f2.setFirmName("Firm Beta");

        backup.setAllFirms(Arrays.asList(f1, f2));

        Customer c1 = new Customer();
        c1.setFirmId(1L);
        Customer c2 = new Customer();
        c2.setFirmId(2L);
        Customer c3 = new Customer();
        c3.setFirmId(1L);
        backup.setCustomers(Arrays.asList(c1, c2, c3));

        BackupInspectionDTO inspection = service.inspectBackup(backup);
        assertNotNull(inspection);
        assertEquals("FULL_SYSTEM_BACKUP", inspection.getBackupType());
        assertEquals(2, inspection.getFirms().size());
        assertEquals("Firm Alpha", inspection.getFirms().get(0).getFirmName());
        assertEquals(2, inspection.getFirms().get(0).getCustomerCount());
        assertEquals("Firm Beta", inspection.getFirms().get(1).getFirmName());
        assertEquals(1, inspection.getFirms().get(1).getCustomerCount());
        assertEquals(3, inspection.getTotalStats().get("totalCustomers"));
    }

    @Test
    void testImportSelectiveDataOnlySelectedFirm() {
        BackupDTO backup = new BackupDTO();
        Map<String, Object> meta = new HashMap<>();
        meta.put("type", "FULL_SYSTEM_BACKUP");
        backup.setMetadata(meta);

        FirmDetails f1 = new FirmDetails();
        f1.setId(1L);
        f1.setFirmName("Firm 1");

        FirmDetails f2 = new FirmDetails();
        f2.setId(2L);
        f2.setFirmName("Firm 2");

        backup.setAllFirms(Arrays.asList(f1, f2));

        Customer c1 = new Customer();
        c1.setId(101L);
        c1.setFirmId(1L);
        c1.setName("Cust F1");

        Customer c2 = new Customer();
        c2.setId(102L);
        c2.setFirmId(2L);
        c2.setName("Cust F2");

        backup.setCustomers(Arrays.asList(c1, c2));

        when(firmDetailsRepo.save(any(FirmDetails.class))).thenAnswer(i -> {
            FirmDetails f = (FirmDetails) i.getArguments()[0];
            f.setId(100L);
            return f;
        });
        when(customerRepo.save(any(Customer.class))).thenAnswer(i -> i.getArguments()[0]);

        // Select only Firm 1 (ID: 1L)
        Set<Long> selected = Collections.singleton(1L);
        List<FirmDetails> restored = service.importSelectiveData(backup, selected, "clone", null);

        assertEquals(1, restored.size());
        verify(firmDetailsRepo, times(1)).save(any(FirmDetails.class));
        // Only 1 customer belonging to Firm 1 should be saved
        verify(customerRepo, times(1)).save(any(Customer.class));
    }

    @Test
    void testImportSelectiveDataCleanWipeIsProhibitedWhenFirmsExist() {
        when(firmDetailsRepo.count()).thenReturn(1L);
        BackupDTO backup = new BackupDTO();
        Map<String, Object> meta = new HashMap<>();
        meta.put("version", "2.0");
        backup.setMetadata(meta);

        FirmDetails firm = new FirmDetails();
        firm.setId(1L);
        firm.setFirmName("Fresh Restore");
        backup.setFirmDetails(firm);

        assertThrows(IllegalArgumentException.class, () ->
                service.importSelectiveData(backup, null, "clean_wipe", null)
        );

        // Verify factory reset NEVER occurred
        verify(firmDetailsRepo, never()).deleteAllInBatch();
        verify(customerRepo, never()).deleteAllInBatch();
        verify(productRepo, never()).deleteAllInBatch();
        verify(invoiceRepo, never()).deleteAllInBatch();
    }

    @Test
    void testImportSelectiveDataCleanWipeOnFirstRunAdaptsToClone() {
        when(firmDetailsRepo.count()).thenReturn(0L);
        when(firmDetailsRepo.save(any(FirmDetails.class))).thenAnswer(inv -> inv.getArgument(0));

        BackupDTO backup = new BackupDTO();
        Map<String, Object> meta = new HashMap<>();
        meta.put("version", "2.0");
        backup.setMetadata(meta);

        FirmDetails firm = new FirmDetails();
        firm.setId(1L);
        firm.setFirmName("Fresh Restore");
        backup.setFirmDetails(firm);

        assertDoesNotThrow(() ->
                service.importSelectiveData(backup, null, "clean_wipe", null)
        );
    }

    @Test
    void testImportSelectiveData_MultiFirmWithoutSelectionTargetingFirm_ThrowsIllegalArgumentException() {
        when(firmDetailsRepo.count()).thenReturn(1L);
        FirmDetails targetFirm = new FirmDetails();
        targetFirm.setId(10L);
        targetFirm.setFirmName("Target Live Firm");
        when(firmDetailsRepo.findById(10L)).thenReturn(Optional.of(targetFirm));

        BackupDTO backup = new BackupDTO();
        backup.setMetadata(Map.of("version", "2.0", "type", "FULL_SYSTEM_BACKUP"));
        FirmDetails f1 = new FirmDetails(); f1.setId(1L); f1.setFirmName("Firm 1");
        FirmDetails f2 = new FirmDetails(); f2.setId(2L); f2.setFirmName("Firm 2");
        backup.setAllFirms(Arrays.asList(f1, f2));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.importSelectiveData(backup, null, "merge", 10L)
        );
        assertTrue(ex.getMessage().contains("Explicit source firm selection is required"));
    }

    @Test
    void testImportSelectiveData_MultiFirmWithUnknownSelection_ThrowsIllegalArgumentException() {
        when(firmDetailsRepo.count()).thenReturn(1L);
        BackupDTO backup = new BackupDTO();
        backup.setMetadata(Map.of("version", "2.0", "type", "FULL_SYSTEM_BACKUP"));
        FirmDetails f1 = new FirmDetails(); f1.setId(1L); f1.setFirmName("Firm 1");
        FirmDetails f2 = new FirmDetails(); f2.setId(2L); f2.setFirmName("Firm 2");
        backup.setAllFirms(Arrays.asList(f1, f2));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.importSelectiveData(backup, Collections.singleton(999L), "merge", null)
        );
        assertTrue(ex.getMessage().contains("Selected source firm(s) [999] not found"));
    }

    @Test
    void testImportSelectiveData_MultiFirmWithPartialUnknownSelection_ThrowsIllegalArgumentException() {
        when(firmDetailsRepo.count()).thenReturn(1L);
        BackupDTO backup = new BackupDTO();
        backup.setMetadata(Map.of("version", "2.0", "type", "FULL_SYSTEM_BACKUP"));
        FirmDetails f1 = new FirmDetails(); f1.setId(1L); f1.setFirmName("Firm 1");
        FirmDetails f2 = new FirmDetails(); f2.setId(2L); f2.setFirmName("Firm 2");
        backup.setAllFirms(Arrays.asList(f1, f2));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.importSelectiveData(backup, new HashSet<>(Arrays.asList(1L, 999L)), "merge", null)
        );
        assertTrue(ex.getMessage().contains("Selected source firm(s) [999] not found"));
    }

    @Test
    void testImportSelectiveData_UnselectedFirmRecordsNeverImportedOrSaved() {
        when(firmDetailsRepo.count()).thenReturn(1L);
        FirmDetails targetFirm = new FirmDetails();
        targetFirm.setId(10L);
        targetFirm.setFirmName("Target Firm 10");
        when(firmDetailsRepo.findById(10L)).thenReturn(Optional.of(targetFirm));

        BackupDTO backup = new BackupDTO();
        backup.setMetadata(Map.of("version", "2.0", "type", "FULL_SYSTEM_BACKUP"));
        FirmDetails f1 = new FirmDetails(); f1.setId(1L); f1.setFirmName("Firm 1");
        FirmDetails f2 = new FirmDetails(); f2.setId(2L); f2.setFirmName("Firm 2");
        backup.setAllFirms(Arrays.asList(f1, f2));

        Customer c1 = new Customer(); c1.setId(101L); c1.setFirmId(1L); c1.setName("Cust 1");
        Customer c2 = new Customer(); c2.setId(201L); c2.setFirmId(2L); c2.setName("Cust 2");
        backup.setCustomers(Arrays.asList(c1, c2));

        Product p1 = Product.builder().firmId(1L).name("Prod 1").price(BigDecimal.TEN).build(); p1.setId(102L);
        Product p2 = Product.builder().firmId(2L).name("Prod 2").price(BigDecimal.TEN).build(); p2.setId(202L);
        backup.setProducts(Arrays.asList(p1, p2));

        Invoice inv1 = new Invoice(); inv1.setId(103L); inv1.setFirmId(1L); inv1.setInvoiceNumber("INV-F1");
        Invoice inv2 = new Invoice(); inv2.setId(203L); inv2.setFirmId(2L); inv2.setInvoiceNumber("INV-F2");
        backup.setInvoices(Arrays.asList(inv1, inv2));

        BusinessLetter bl1 = BusinessLetter.builder().firmId(1L).letterNumber("LTR-1").recipientName("R1").build(); bl1.setId(104L);
        BusinessLetter bl2 = BusinessLetter.builder().firmId(2L).letterNumber("LTR-2").recipientName("R2").build(); bl2.setId(204L);
        backup.setBusinessLetters(Arrays.asList(bl1, bl2));

        List<Customer> savedCustomers = new ArrayList<>();
        List<Product> savedProducts = new ArrayList<>();
        List<Invoice> savedInvoices = new ArrayList<>();
        List<BusinessLetter> savedLetters = new ArrayList<>();

        when(customerRepo.save(any(Customer.class))).thenAnswer(i -> {
            Customer c = i.getArgument(0);
            savedCustomers.add(c);
            return c;
        });
        when(productRepo.save(any(Product.class))).thenAnswer(i -> {
            Product p = i.getArgument(0);
            savedProducts.add(p);
            return p;
        });
        when(invoiceRepo.save(any(Invoice.class))).thenAnswer(i -> {
            Invoice inv = i.getArgument(0);
            inv.setId(555L);
            savedInvoices.add(inv);
            return inv;
        });
        when(businessLetterRepo.save(any(BusinessLetter.class))).thenAnswer(i -> {
            BusinessLetter bl = i.getArgument(0);
            savedLetters.add(bl);
            return bl;
        });

        // Import only Firm 1 into Target Firm 10
        service.importSelectiveData(backup, Collections.singleton(1L), "merge", 10L);

        // Verify: only records belonging to selected firm 1 were saved, mapped to target firm 10
        assertEquals(1, savedCustomers.size());
        assertEquals("Cust 1", savedCustomers.get(0).getName());
        assertEquals(10L, savedCustomers.get(0).getFirmId());

        assertEquals(1, savedProducts.size());
        assertEquals("Prod 1", savedProducts.get(0).getName());
        assertEquals(10L, savedProducts.get(0).getFirmId());

        assertEquals(1, savedInvoices.size());
        assertEquals("INV-F1", savedInvoices.get(0).getInvoiceNumber());
        assertEquals(10L, savedInvoices.get(0).getFirmId());

        assertEquals(1, savedLetters.size());
        assertEquals("LTR-1", savedLetters.get(0).getLetterNumber());
        assertEquals(10L, savedLetters.get(0).getFirmId());
    }

    @Test
    void testImportSelectiveData_DuplicateInvoiceNumberWithinSelectedFirm_ThrowsRestoreConflictException() {
        when(firmDetailsRepo.count()).thenReturn(1L);
        FirmDetails targetFirm = new FirmDetails();
        targetFirm.setId(10L);
        targetFirm.setFirmName("Target Firm 10");
        when(firmDetailsRepo.findById(10L)).thenReturn(Optional.of(targetFirm));

        BackupDTO backup = new BackupDTO();
        backup.setMetadata(Map.of("version", "2.0", "type", "FULL_SYSTEM_BACKUP"));
        FirmDetails f1 = new FirmDetails(); f1.setId(1L); f1.setFirmName("Firm 1");
        backup.setAllFirms(Collections.singletonList(f1));

        Invoice inv1 = new Invoice(); inv1.setId(101L); inv1.setFirmId(1L); inv1.setInvoiceNumber("INV-DUP-01");
        Invoice inv2 = new Invoice(); inv2.setId(102L); inv2.setFirmId(1L); inv2.setInvoiceNumber("INV-DUP-01");
        backup.setInvoices(Arrays.asList(inv1, inv2));

        Invoice liveInvoice = new Invoice();
        liveInvoice.setId(999L);
        liveInvoice.setFirmId(10L);
        liveInvoice.setInvoiceNumber("INV-DUP-01");

        when(invoiceRepo.findByInvoiceNumberAndFirmId("INV-DUP-01", 10L)).thenReturn(Optional.of(liveInvoice));
        when(invoiceRepo.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));

        com.billing.simple.billsoft.exception.RestoreConflictException ex = assertThrows(
                com.billing.simple.billsoft.exception.RestoreConflictException.class,
                () -> service.importSelectiveData(backup, Collections.singleton(1L), "merge", 10L)
        );
        assertEquals("INVOICE", ex.getEntityType());
        assertEquals("INV-DUP-01", ex.getConflictKey());
    }

    @Test
    void testImportSelectiveData_DuplicateInvoiceNumberInUnselectedFirm_IgnoredAndDoesNotConflict() {
        when(firmDetailsRepo.count()).thenReturn(1L);
        FirmDetails targetFirm = new FirmDetails();
        targetFirm.setId(10L);
        targetFirm.setFirmName("Target Firm 10");
        when(firmDetailsRepo.findById(10L)).thenReturn(Optional.of(targetFirm));

        BackupDTO backup = new BackupDTO();
        backup.setMetadata(Map.of("version", "2.0", "type", "FULL_SYSTEM_BACKUP"));
        FirmDetails f1 = new FirmDetails(); f1.setId(1L); f1.setFirmName("Firm 1");
        FirmDetails f2 = new FirmDetails(); f2.setId(2L); f2.setFirmName("Firm 2");
        backup.setAllFirms(Arrays.asList(f1, f2));

        // Selected Firm 1 has INV-UNIQUE-01
        Invoice inv1 = new Invoice(); inv1.setId(101L); inv1.setFirmId(1L); inv1.setInvoiceNumber("INV-UNIQUE-01");
        // Unselected Firm 2 has INV-DUP-01 which collides with live firm's invoice
        Invoice inv2 = new Invoice(); inv2.setId(201L); inv2.setFirmId(2L); inv2.setInvoiceNumber("INV-DUP-01");
        backup.setInvoices(Arrays.asList(inv1, inv2));

        Invoice liveInvoice = new Invoice();
        liveInvoice.setId(999L);
        liveInvoice.setFirmId(10L);
        liveInvoice.setInvoiceNumber("INV-DUP-01");

        when(invoiceRepo.findByInvoiceNumberAndFirmId("INV-DUP-01", 10L)).thenReturn(Optional.of(liveInvoice));
        when(invoiceRepo.save(any(Invoice.class))).thenAnswer(i -> {
            Invoice inv = i.getArgument(0);
            inv.setId(777L);
            return inv;
        });

        // Only Firm 1 is imported into target firm 10
        assertDoesNotThrow(() ->
                service.importSelectiveData(backup, Collections.singleton(1L), "merge", 10L)
        );
    }
}
