package com.billing.simple.billsoft.regression.planner;

import com.billing.simple.billsoft.entities.Expense;
import com.billing.simple.billsoft.entities.Note;
import com.billing.simple.billsoft.entities.Reminder;
import com.billing.simple.billsoft.service.ExpenseService;
import com.billing.simple.billsoft.service.NoteService;
import com.billing.simple.billsoft.service.ReminderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Tag("regression")
@Tag("integration")
@DisplayName("Planner, Reminders, Notes & Expenses Regression Tests")
class PlannerAndNotesRegressionTest {

    @Autowired
    private ReminderService reminderService;

    @Autowired
    private NoteService noteService;

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private com.billing.simple.billsoft.repo.ExpenseRepository expenseRepo;

    @Autowired
    private com.billing.simple.billsoft.repo.NoteRepository noteRepo;

    @Autowired
    private com.billing.simple.billsoft.repo.ReminderRepository reminderRepo;

    private final Long testFirmId = 1L;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        expenseRepo.deleteAll();
        noteRepo.deleteAll();
        reminderRepo.deleteAll();
    }

    @Test
    @DisplayName("Should create reminder, query upcoming reminders, and toggle completion")
    void shouldManageReminders() {
        Reminder reminder = reminderService.create(Reminder.builder()
                .title("GST Filing Q3")
                .note("Submit quarterly GST return with accountant")
                .dueDate(LocalDateTime.now().plusDays(2))
                .completed(false)
                .firmId(testFirmId)
                .build());

        assertThat(reminder.getId()).isNotNull();

        List<Reminder> active = reminderService.getActiveByFirm(testFirmId);
        assertThat(active).extracting(Reminder::getTitle).contains("GST Filing Q3");

        // Mark done
        Reminder updated = reminderService.markDone(reminder.getId());
        assertThat(updated.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("Should create and isolate firm-specific notes")
    void shouldManageNotes() {
        Note note = noteService.create(Note.builder()
                .title("Factory Machine Maintenance Checklist")
                .content("Inspect conveyor belts and hydraulic oil levels.")
                .firmId(testFirmId)
                .build());

        assertThat(note.getId()).isNotNull();

        List<Note> notes = noteService.getByFirm(testFirmId);
        assertThat(notes).extracting(Note::getTitle).contains("Factory Machine Maintenance Checklist");

        boolean deleted = noteService.delete(note.getId());
        assertThat(deleted).isTrue();
    }

    @Test
    @DisplayName("Should log business expenses and calculate total by category and firm")
    void shouldManageExpenses() {
        Expense exp1 = expenseService.createExpense(Expense.builder()
                .title("Office Internet Bill")
                .amount(BigDecimal.valueOf(1999.00))
                .category("Utilities")
                .expenseDate(LocalDate.now())
                .firmId(testFirmId)
                .build());

        Expense exp2 = expenseService.createExpense(Expense.builder()
                .title("Printer Cartridges")
                .amount(BigDecimal.valueOf(2500.00))
                .category("Stationery")
                .expenseDate(LocalDate.now())
                .firmId(testFirmId)
                .build());

        List<Expense> expenses = expenseService.getExpensesByFirm(testFirmId);
        assertThat(expenses).hasSize(2);

        BigDecimal total = expenses.stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("4499.00");
    }
}
