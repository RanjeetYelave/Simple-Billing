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
    private InboxMessageRepository inboxMessageRepo;
    @Mock
    private AppConfigRepository appConfigRepo;
    @Mock
    private InvoicePaymentRepository invoicePaymentRepo;

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
    void testImportSelectiveDataCleanWipe() {
        BackupDTO backup = new BackupDTO();
        Map<String, Object> meta = new HashMap<>();
        meta.put("version", "2.0");
        backup.setMetadata(meta);

        FirmDetails firm = new FirmDetails();
        firm.setId(1L);
        firm.setFirmName("Fresh Restore");
        backup.setFirmDetails(firm);

        when(firmDetailsRepo.save(any(FirmDetails.class))).thenAnswer(i -> i.getArguments()[0]);

        service.importSelectiveData(backup, null, "clean_wipe", null);

        // Verify factory reset occurred before import
        verify(firmDetailsRepo, times(1)).deleteAllInBatch();
        verify(customerRepo, times(1)).deleteAllInBatch();
        verify(productRepo, times(1)).deleteAllInBatch();
        verify(invoiceRepo, times(1)).deleteAllInBatch();
        verify(firmDetailsRepo, times(1)).save(any(FirmDetails.class));
    }
}
