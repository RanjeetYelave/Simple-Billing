package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.entities.Goal;
import com.billing.simple.billsoft.entities.GoalType;
import com.billing.simple.billsoft.entities.SavingRecord;
import com.billing.simple.billsoft.repo.GoalRepository;
import com.billing.simple.billsoft.repo.SavingRepository;
import com.billing.simple.billsoft.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GoalServiceTest {

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private SavingRepository savingRepository;

    @InjectMocks
    private GoalService goalService;

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
    void testCreateGoal() {
        Goal goal = Goal.builder()
                .title("Save 1 Lakh")
                .goalType(GoalType.SAVINGS_TARGET)
                .targetValue(new BigDecimal("100000.00"))
                .build();

        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> {
            Goal g = invocation.getArgument(0);
            g.setId(50L);
            return g;
        });

        Goal created = goalService.createGoal(goal);

        assertNotNull(created);
        assertEquals(50L, created.getId());
        assertEquals(1L, created.getFirmId());
        assertEquals("ACTIVE", created.getStatus());
        assertEquals(BigDecimal.ZERO, created.getCurrentValue());
    }

    @Test
    void testCheckInHabitConsecutiveStreak() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        Goal habit = Goal.builder()
                .id(10L)
                .firmId(1L)
                .title("Daily Exercise")
                .goalType(GoalType.HABIT_STREAK)
                .currentStreak(4)
                .longestStreak(5)
                .lastCheckInDate(yesterday)
                .build();

        when(goalRepository.findByIdAndFirmId(10L, 1L)).thenReturn(Optional.of(habit));
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Goal checkedIn = goalService.checkInHabit(10L);

        assertEquals(5, checkedIn.getCurrentStreak());
        assertEquals(5, checkedIn.getLongestStreak());
        assertEquals(LocalDate.now(), checkedIn.getLastCheckInDate());
    }

    @Test
    void testCheckInHabitSameDayDoesNotDoubleCount() {
        LocalDate today = LocalDate.now();
        Goal habit = Goal.builder()
                .id(10L)
                .firmId(1L)
                .title("Daily Exercise")
                .goalType(GoalType.HABIT_STREAK)
                .currentStreak(4)
                .longestStreak(5)
                .lastCheckInDate(today)
                .build();

        when(goalRepository.findByIdAndFirmId(10L, 1L)).thenReturn(Optional.of(habit));

        Goal checkedIn = goalService.checkInHabit(10L);

        assertEquals(4, checkedIn.getCurrentStreak());
        verify(goalRepository, never()).save(any(Goal.class));
    }

    @Test
    void testIncrementProgressMilestone() {
        Goal milestone = Goal.builder()
                .id(20L)
                .firmId(1L)
                .title("Onboard 50 Clients")
                .goalType(GoalType.LIFE_MILESTONE)
                .currentValue(new BigDecimal("10.00"))
                .targetValue(new BigDecimal("50.00"))
                .status("ACTIVE")
                .build();

        when(goalRepository.findByIdAndFirmId(20L, 1L)).thenReturn(Optional.of(milestone));
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Goal updated = goalService.incrementProgress(20L, new BigDecimal("1.00"));

        assertEquals(new BigDecimal("11.00"), updated.getCurrentValue());
        assertEquals("ACTIVE", updated.getStatus());

        // Increment to target completes goal
        Goal achieved = goalService.incrementProgress(20L, new BigDecimal("39.00"));
        assertEquals(new BigDecimal("50.00"), achieved.getCurrentValue());
        assertEquals("ACHIEVED", achieved.getStatus());
    }

    @Test
    void testResetQuitHabit() {
        Goal quitGoal = Goal.builder()
                .id(30L)
                .firmId(1L)
                .title("No Smoking")
                .goalType(GoalType.QUIT_HABIT)
                .currentStreak(25)
                .longestStreak(30)
                .startDate(LocalDate.now().minusDays(25))
                .build();

        when(goalRepository.findByIdAndFirmId(30L, 1L)).thenReturn(Optional.of(quitGoal));
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Goal reset = goalService.resetQuitHabit(30L);

        assertEquals(0, reset.getCurrentStreak());
        assertEquals(30, reset.getLongestStreak()); // Longest streak preserved
        assertEquals(LocalDate.now(), reset.getStartDate());
    }

    @Test
    void testRecalculateSavingsGoal() {
        Goal savingsGoal = Goal.builder()
                .id(40L)
                .firmId(1L)
                .title("Emergency Fund")
                .goalType(GoalType.SAVINGS_TARGET)
                .targetValue(new BigDecimal("50000.00"))
                .currentValue(BigDecimal.ZERO)
                .status("ACTIVE")
                .build();

        SavingRecord s1 = SavingRecord.builder().amount(new BigDecimal("30000.00")).build();
        SavingRecord s2 = SavingRecord.builder().amount(new BigDecimal("20000.00")).build();

        when(goalRepository.findByIdAndFirmId(40L, 1L)).thenReturn(Optional.of(savingsGoal));
        when(savingRepository.findByFirmIdAndGoalId(1L, 40L)).thenReturn(List.of(s1, s2));
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        goalService.recalculateSavingsGoal(1L, 40L);

        verify(goalRepository, times(1)).save(argThat(g ->
                new BigDecimal("50000.00").compareTo(g.getCurrentValue()) == 0 && "ACHIEVED".equals(g.getStatus())
        ));
    }

    @Test
    void testDeleteGoalDeLinksSavings() {
        when(goalRepository.existsByIdAndFirmId(40L, 1L)).thenReturn(true);

        boolean deleted = goalService.deleteGoal(40L);

        assertTrue(deleted);
        verify(savingRepository, times(1)).clearGoalIdByFirmIdAndGoalId(1L, 40L);
        verify(goalRepository, times(1)).deleteByIdAndFirmId(40L, 1L);
    }

    @Test
    void testAddSavingsToGoal() {
        Goal savingsGoal = Goal.builder()
                .id(40L)
                .firmId(1L)
                .title("Car Fund")
                .goalType(GoalType.SAVINGS_TARGET)
                .targetValue(new BigDecimal("500000.00"))
                .tags("Car,Auto")
                .build();

        when(goalRepository.findByIdAndFirmId(40L, 1L)).thenReturn(Optional.of(savingsGoal));
        when(savingRepository.save(any(SavingRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(savingRepository.findByFirmIdAndGoalId(1L, 40L)).thenReturn(List.of(
                SavingRecord.builder().amount(new BigDecimal("5000.00")).build()
        ));

        Goal updatedGoal = goalService.addSavingsToGoal(40L, new BigDecimal("5000.00"), "UPI", "Quick save");

        assertNotNull(updatedGoal);
        assertEquals(40L, updatedGoal.getId());
        verify(savingRepository, atLeastOnce()).save(any(SavingRecord.class));
        verify(goalRepository, atLeastOnce()).save(any(Goal.class));
    }

    @Test
    void testAddStreakDays() {
        Goal streakGoal = Goal.builder()
                .id(60L)
                .firmId(1L)
                .title("Morning Yoga")
                .goalType(GoalType.HABIT_STREAK)
                .currentStreak(5)
                .longestStreak(10)
                .targetValue(new BigDecimal("30"))
                .build();

        when(goalRepository.findByIdAndFirmId(60L, 1L)).thenReturn(Optional.of(streakGoal));
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Goal updated = goalService.addStreakDays(60L, 30);

        assertEquals(35, updated.getCurrentStreak());
        assertEquals(35, updated.getLongestStreak());
        assertEquals("ACHIEVED", updated.getStatus());
    }
}
