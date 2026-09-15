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
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BackupSavingsGoalsTest {

    @Mock private FirmDetailsRepository firmDetailsRepo;
    @Mock private CustomerRepository customerRepo;
    @Mock private ProductRepository productRepo;
    @Mock private StockMovementRepository stockMovementRepo;
    @Mock private InvoiceRepository invoiceRepo;
    @Mock private InvoiceItemRepository invoiceItemRepo;
    @Mock private PartyRepository partyRepo;
    @Mock private PartyPaymentRepository partyPaymentRepo;
    @Mock private PurchaseOrderRepository purchaseOrderRepo;
    @Mock private PurchaseOrderItemRepository purchaseOrderItemRepo;
    @Mock private ReminderRepository reminderRepo;
    @Mock private NoteRepository noteRepo;
    @Mock private ExpenseRepository expenseRepo;
    @Mock private EmployeeRepository employeeRepo;
    @Mock private AttendanceRecordRepository attendanceRecordRepo;
    @Mock private LeaveRecordRepository leaveRecordRepo;
    @Mock private SalaryRecordRepository salaryRepo;
    @Mock private EmployeeAdvanceRepository advanceRepo;
    @Mock private PromotionRecordRepository promotionRepo;
    @Mock private EmployeeDocumentRepository employeeDocumentRepo;
    @Mock private BusinessLetterRepository businessLetterRepo;
    @Mock private InboxMessageRepository inboxMessageRepo;
    @Mock private AppConfigRepository appConfigRepo;
    @Mock private InvoicePaymentRepository invoicePaymentRepo;
    @Mock private SalesReturnRepository salesReturnRepo;
    @Mock private SalesReturnItemRepository salesReturnItemRepo;
    @Mock private SavingRepository savingRepo;
    @Mock private GoalRepository goalRepo;

    @InjectMocks
    private BackupService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testExportIncludesSavingsAndGoals() {
        Long firmId = 1L;
        FirmDetails firm = new FirmDetails();
        firm.setId(firmId);
        firm.setFirmName("Alpha Enterprise");

        when(firmDetailsRepo.findById(firmId)).thenReturn(Optional.of(firm));
        when(savingRepo.findByFirmIdOrderBySavingDateDescIdDesc(firmId)).thenReturn(List.of(
                SavingRecord.builder().id(101L).firmId(firmId).title("SIP Mutual Fund").amount(new BigDecimal("10000.00")).build()
        ));
        when(goalRepo.findByFirmIdOrderByCreatedAtDesc(firmId)).thenReturn(List.of(
                Goal.builder().id(501L).firmId(firmId).title("Emergency Fund").goalType(GoalType.SAVINGS_TARGET).build()
        ));

        BackupDTO backup = service.exportData(firmId);

        assertNotNull(backup);
        assertEquals(1, backup.getSavings().size());
        assertEquals("SIP Mutual Fund", backup.getSavings().get(0).getTitle());
        assertEquals(1, backup.getGoals().size());
        assertEquals("Emergency Fund", backup.getGoals().get(0).getTitle());
    }

    @Test
    void testInspectionCountsSavingsAndGoals() {
        BackupDTO backup = new BackupDTO();
        Map<String, Object> meta = new HashMap<>();
        meta.put("version", "2.0");
        meta.put("type", "SINGLE_FIRM");
        meta.put("firmId", 1L);
        backup.setMetadata(meta);

        FirmDetails firm = new FirmDetails();
        firm.setId(1L);
        firm.setFirmName("Alpha Enterprise");
        backup.setFirmDetails(firm);

        backup.setSavings(List.of(
                SavingRecord.builder().id(1L).firmId(1L).amount(new BigDecimal("5000")).build(),
                SavingRecord.builder().id(2L).firmId(1L).amount(new BigDecimal("15000")).build()
        ));
        backup.setGoals(List.of(
                Goal.builder().id(10L).firmId(1L).title("Goal 1").build()
        ));

        BackupInspectionDTO inspection = service.inspectBackup(backup);

        assertNotNull(inspection);
        assertEquals(2, inspection.getFirms().get(0).getSavingCount());
        assertEquals(1, inspection.getFirms().get(0).getGoalCount());
        assertEquals(2, inspection.getTotalStats().get("totalSavings"));
        assertEquals(1, inspection.getTotalStats().get("totalGoals"));
    }

    @Test
    void testImportRemapsGoalIdInSavings() {
        BackupDTO backup = new BackupDTO();
        Map<String, Object> meta = new HashMap<>();
        meta.put("version", "2.0");
        meta.put("type", "SINGLE_FIRM");
        meta.put("firmId", 1L);
        backup.setMetadata(meta);

        FirmDetails firm = new FirmDetails();
        firm.setId(1L);
        firm.setFirmName("Restored Enterprise");
        backup.setFirmDetails(firm);

        Goal oldGoal = Goal.builder()
                .id(999L)
                .firmId(1L)
                .title("Save 2L")
                .goalType(GoalType.SAVINGS_TARGET)
                .build();
        backup.setGoals(List.of(oldGoal));

        SavingRecord oldSaving = SavingRecord.builder()
                .id(888L)
                .firmId(1L)
                .title("Gold Deposit")
                .amount(new BigDecimal("20000.00"))
                .goalId(999L)
                .build();
        backup.setSavings(List.of(oldSaving));

        when(firmDetailsRepo.save(any(FirmDetails.class))).thenAnswer(i -> {
            FirmDetails f = i.getArgument(0);
            f.setId(10L); // New firm ID
            return f;
        });

        when(goalRepo.save(any(Goal.class))).thenAnswer(i -> {
            Goal g = i.getArgument(0);
            g.setId(200L); // New Goal ID
            return g;
        });

        when(savingRepo.save(any(SavingRecord.class))).thenAnswer(i -> {
            SavingRecord s = i.getArgument(0);
            s.setId(300L);
            return s;
        });

        List<FirmDetails> restored = service.importSelectiveData(backup, Set.of(1L), "clone", null);

        assertNotNull(restored);
        assertEquals(1, restored.size());

        // Verify goal was saved with new firm ID
        verify(goalRepo, times(1)).save(argThat(g ->
                g.getFirmId().equals(10L) && "Save 2L".equals(g.getTitle())
        ));

        // Verify saving was saved with mapped goal ID 200L and new firm ID 10L
        verify(savingRepo, times(1)).save(argThat(s ->
                s.getFirmId().equals(10L) && Long.valueOf(200L).equals(s.getGoalId()) && "Gold Deposit".equals(s.getTitle())
        ));
    }
}
