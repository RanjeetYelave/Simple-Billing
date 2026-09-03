package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dto.BackupDTO;
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

class BackupServiceTest {

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
    void testExportData() {
        Long firmId = 1L;
        FirmDetails firm = new FirmDetails();
        firm.setId(firmId);
        firm.setFirmName("Test Firm");

        when(firmDetailsRepo.findById(firmId)).thenReturn(Optional.of(firm));
        when(customerRepo.findByFirmIdOrderByNameAsc(firmId)).thenReturn(new ArrayList<>());
        when(productRepo.findByFirmId(firmId)).thenReturn(new ArrayList<>());
        when(stockMovementRepo.findByFirmIdOrderByCreatedAtDesc(firmId)).thenReturn(new ArrayList<>());
        when(invoiceRepo.findAllByFirmId(firmId)).thenReturn(new ArrayList<>());
        when(partyRepo.findByFirmIdOrderByNameAsc(firmId)).thenReturn(new ArrayList<>());
        when(partyPaymentRepo.findByFirmIdOrderByPaymentDateDescIdDesc(firmId)).thenReturn(new ArrayList<>());
        when(purchaseOrderRepo.findByFirmIdOrderByPoDateDescIdDesc(firmId)).thenReturn(new ArrayList<>());
        when(employeeRepo.findByFirmId(firmId)).thenReturn(new ArrayList<>());
        when(businessLetterRepo.findByFirmIdOrderByLetterDateDescIdDesc(firmId)).thenReturn(new ArrayList<>());
        when(inboxMessageRepo.findByFirmIdOrderByCreatedAtDesc(firmId)).thenReturn(new ArrayList<>());

        BackupDTO result = service.exportData(firmId);

        assertNotNull(result);
        assertEquals("Test Firm", result.getFirmDetails().getFirmName());
        assertNotNull(result.getMetadata());
        assertEquals("2.0", result.getMetadata().get("version"));
    }

    @Test
    void testImportDataMerge() {
        BackupDTO backup = new BackupDTO();
        backup.setMetadata(new HashMap<>());
        backup.setCustomers(Collections.singletonList(new Customer()));
        backup.getCustomers().get(0).setId(10L);

        Product p = Product.builder().name("Item 1").price(new BigDecimal("100")).costPrice(new BigDecimal("70")).stockQuantity(BigDecimal.TEN).build();
        p.setId(20L);
        backup.setProducts(Collections.singletonList(p));

        Party party = Party.builder().name("Vendor 1").build();
        party.setId(30L);
        backup.setParties(Collections.singletonList(party));
        
        when(customerRepo.save(any(Customer.class))).thenAnswer(i -> i.getArguments()[0]);
        when(productRepo.save(any(Product.class))).thenAnswer(i -> i.getArguments()[0]);
        when(partyRepo.save(any(Party.class))).thenAnswer(i -> i.getArguments()[0]);

        service.importData(backup, 1L, true);

        verify(customerRepo, times(1)).save(any(Customer.class));
        verify(productRepo, times(1)).save(any(Product.class));
        verify(partyRepo, times(1)).save(any(Party.class));
    }

    @Test
    void testImportDataNewFirm() {
        BackupDTO backup = new BackupDTO();
        backup.setMetadata(new HashMap<>());
        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Source Firm");
        backup.setFirmDetails(firm);

        when(firmDetailsRepo.save(any(FirmDetails.class))).thenAnswer(i -> {
            FirmDetails f = (FirmDetails) i.getArguments()[0];
            f.setId(99L);
            return f;
        });
        when(customerRepo.save(any(Customer.class))).thenAnswer(i -> i.getArguments()[0]);

        service.importData(backup, null, false);

        verify(firmDetailsRepo, times(1)).save(any(FirmDetails.class));
    }

    @Test
    void testImportDataInvalid() {
        assertThrows(RuntimeException.class, () -> service.importData(null, 1L, true));
    }

    @Test
    void testFactoryReset() {
        service.factoryReset();
        verify(firmDetailsRepo, times(1)).deleteAllInBatch();
        verify(customerRepo, times(1)).deleteAllInBatch();
        verify(productRepo, times(1)).deleteAllInBatch();
        verify(invoiceRepo, times(1)).deleteAllInBatch();
        verify(partyRepo, times(1)).deleteAllInBatch();
        verify(purchaseOrderRepo, times(1)).deleteAllInBatch();
        verify(stockMovementRepo, times(1)).deleteAllInBatch();
        verify(businessLetterRepo, times(1)).deleteAllInBatch();
    }
}
