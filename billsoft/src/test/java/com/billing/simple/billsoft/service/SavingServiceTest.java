package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dtos.PageResponse;
import com.billing.simple.billsoft.entities.SavingRecord;
import com.billing.simple.billsoft.repo.SavingRepository;
import com.billing.simple.billsoft.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SavingServiceTest {

    @Mock
    private SavingRepository repository;

    @Mock
    private GoalService goalService;

    @InjectMocks
    private SavingService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TenantContext.setCurrentFirmId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testCreateSaving() {
        SavingRecord record = SavingRecord.builder()
                .title("Nifty SIP")
                .amount(new BigDecimal("5000.00"))
                .category("Mutual Funds / SIP")
                .goalId(10L)
                .build();

        when(repository.save(any(SavingRecord.class))).thenAnswer(invocation -> {
            SavingRecord r = invocation.getArgument(0);
            r.setId(100L);
            return r;
        });

        SavingRecord created = service.createSaving(record);

        assertNotNull(created);
        assertEquals(100L, created.getId());
        assertEquals(1L, created.getFirmId());
        assertEquals(new BigDecimal("5000.00"), created.getAmount());
        assertNotNull(created.getSavingDate());
        verify(goalService, times(1)).recalculateSavingsGoal(1L, 10L);
    }

    @Test
    void testGetSavingsByFirm() {
        SavingRecord r1 = SavingRecord.builder().id(1L).firmId(1L).title("Gold").build();
        SavingRecord r2 = SavingRecord.builder().id(2L).firmId(1L).title("SIP").build();
        when(repository.findByFirmIdOrderBySavingDateDescIdDesc(1L)).thenReturn(Arrays.asList(r1, r2));

        List<SavingRecord> result = service.getSavingsByFirm(1L);

        assertEquals(2, result.size());
        verify(repository, times(1)).findByFirmIdOrderBySavingDateDescIdDesc(1L);
    }

    @Test
    void testGetPaginatedSavings() {
        Pageable pageable = PageRequest.of(0, 10);
        SavingRecord r1 = SavingRecord.builder().id(1L).firmId(1L).title("Gold").build();
        when(repository.findByFirmId(1L, pageable)).thenReturn(new PageImpl<>(List.of(r1), pageable, 1));

        PageResponse<SavingRecord> resp = service.getPaginatedSavings(1L, null, null, pageable);

        assertNotNull(resp);
        assertEquals(1, resp.getContent().size());
        assertEquals(1, resp.getTotalElements());
    }

    @Test
    void testUpdateSavingAndGoalSync() {
        SavingRecord existing = SavingRecord.builder()
                .id(100L)
                .firmId(1L)
                .title("Old Title")
                .amount(new BigDecimal("2000.00"))
                .goalId(5L)
                .build();

        SavingRecord updated = SavingRecord.builder()
                .title("New Title")
                .amount(new BigDecimal("3500.00"))
                .category("Emergency Fund")
                .savingDate(LocalDate.now())
                .paymentMode("UPI")
                .goalId(8L)
                .build();

        when(repository.findByIdAndFirmId(100L, 1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(SavingRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SavingRecord result = service.updateSaving(100L, updated);

        assertEquals("New Title", result.getTitle());
        assertEquals(new BigDecimal("3500.00"), result.getAmount());
        assertEquals(8L, result.getGoalId());

        // Should recalculate both old goal (5L) and new goal (8L)
        verify(goalService, times(1)).recalculateSavingsGoal(1L, 5L);
        verify(goalService, times(1)).recalculateSavingsGoal(1L, 8L);
    }

    @Test
    void testDeleteSaving() {
        SavingRecord existing = SavingRecord.builder()
                .id(100L)
                .firmId(1L)
                .title("Deposit")
                .goalId(12L)
                .build();

        when(repository.findByIdAndFirmId(100L, 1L)).thenReturn(Optional.of(existing));

        boolean deleted = service.deleteSaving(100L);

        assertTrue(deleted);
        verify(repository, times(1)).deleteByIdAndFirmId(100L, 1L);
        verify(goalService, times(1)).recalculateSavingsGoal(1L, 12L);
    }

    @Test
    void testCustomCategoriesExtraction() {
        SavingRecord r1 = SavingRecord.builder().id(1L).firmId(1L).category("Crypto Reserve").build();
        SavingRecord r2 = SavingRecord.builder().id(2L).firmId(1L).category("Mutual Funds / SIP").build();
        when(repository.findByFirmIdOrderBySavingDateDescIdDesc(1L)).thenReturn(Arrays.asList(r1, r2));

        List<String> cats = service.getCategoriesByFirm(1L);

        assertTrue(cats.contains("Crypto Reserve"));
        assertTrue(cats.contains("Mutual Funds / SIP"));
        assertTrue(cats.contains("Emergency Fund"));
    }

    @Test
    void testSummaryMonthlyAndTotals() {
        LocalDate today = LocalDate.now();
        SavingRecord r1 = SavingRecord.builder()
                .id(1L)
                .firmId(1L)
                .amount(new BigDecimal("10000.00"))
                .savingDate(today)
                .category("Emergency Fund")
                .build();
        SavingRecord r2 = SavingRecord.builder()
                .id(2L)
                .firmId(1L)
                .amount(new BigDecimal("5000.00"))
                .savingDate(today.minusMonths(2))
                .category("Gold & Silver")
                .build();

        when(repository.findByFirmIdOrderBySavingDateDescIdDesc(1L)).thenReturn(Arrays.asList(r1, r2));

        Map<String, Object> summary = service.getSummaryByFirm(1L);

        assertNotNull(summary);
        assertEquals(new BigDecimal("15000.00"), summary.get("totalAllTime"));
        assertEquals(new BigDecimal("10000.00"), summary.get("totalCurrentMonth"));
        assertEquals(2, summary.get("count"));
    }
}
