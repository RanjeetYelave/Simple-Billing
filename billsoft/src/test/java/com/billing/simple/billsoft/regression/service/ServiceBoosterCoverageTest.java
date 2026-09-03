package com.billing.simple.billsoft.regression.service;

import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Tag("regression")
@Tag("unit")
@DisplayName("Comprehensive Service Layer Booster & Edge Branch Coverage Tests")
class ServiceBoosterCoverageTest {

    @Autowired
    private BusinessLetterPdfService letterPdfService;

    @Autowired
    private FirmDetailsService firmService;

    @Autowired
    private ReminderService reminderService;

    @Autowired
    private NoteService noteService;

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private InboxMessageService inboxService;

    @Autowired
    private BackupService backupService;

    private FirmDetails testFirm;
    private final Long testFirmId = 1L;

    @BeforeEach
    void setUp() {
        backupService.factoryReset();

        testFirm = new FirmDetails();
        testFirm.setFirmName("Global Tech Logistics");
        testFirm.setGstin("27AABCT9988D1Z2");
        testFirm.setAddressLine1("Phase 2 Hinjawadi");
        testFirm.setPhone("9811223344");
        testFirm.setEmail("contact@globaltech.com");
        testFirm.setBankName("HDFC Bank");
        testFirm.setBankAccount("50200012345678");
        testFirm.setBankIfsc("HDFC0001234");
        testFirm = firmService.create(testFirm);
    }

    @Test
    @DisplayName("Should generate Business Letter PDF with firm sender and custom sender")
    void testBusinessLetterPdfGeneration() throws Exception {
        // 1. Firm sender letter
        BusinessLetter firmLetter = new BusinessLetter();
        firmLetter.setLetterNumber("LTR-2026-001");
        firmLetter.setLetterDate(LocalDate.now());
        firmLetter.setSenderType("FIRM");
        firmLetter.setFirmId(testFirm.getId());
        firmLetter.setRecipientName("Mr. Rajesh Khanna");
        firmLetter.setRecipientCompany("Apex Industries");
        firmLetter.setRecipientAddress("45 Industrial Area, Pune");
        firmLetter.setSubject("Annual Partnership Agreement Confirmation");
        firmLetter.setContent("Dear Mr. Khanna,\n\nWe are pleased to confirm the renewal of our supply chain partnership.\n\nPlease find attached the terms.");
        firmLetter.setSignatoryName("Vikram Singhania");
        firmLetter.setSignatoryDesignation("Managing Director");

        byte[] pdf1 = letterPdfService.generatePdf(firmLetter);
        assertThat(pdf1).isNotNull();
        assertThat(pdf1.length).isGreaterThan(1000);

        // 2. Custom sender letter
        BusinessLetter customLetter = new BusinessLetter();
        customLetter.setLetterNumber("LTR-2026-002");
        customLetter.setLetterDate(LocalDate.now());
        customLetter.setSenderType("CUSTOM");
        customLetter.setSenderName("Ananya Sharma");
        customLetter.setSenderCompany("Nova Solutions Ltd");
        customLetter.setSenderAddress("Tower 5 Cyber City");
        customLetter.setRecipientName("Customer Support Desk");
        customLetter.setSubject("Formal Notice of Equipment Handover");
        customLetter.setContent("This is to formally confirm equipment handover.\n\nWarm regards.");

        byte[] pdf2 = letterPdfService.generatePdf(customLetter);
        assertThat(pdf2).isNotNull();
        assertThat(pdf2.length).isGreaterThan(1000);
    }

    @Test
    @DisplayName("Should test ReminderService lifecycle and state transitions")
    void testReminderServiceBranches() {
        Reminder rem = new Reminder();
        rem.setTitle("Renew ISO Certification");
        rem.setDueDate(LocalDateTime.now().plusDays(10));
        rem.setFirmId(testFirmId);
        rem.setType("task");
        rem.setTags("compliance,annual");
        rem.setProgress(25);
        rem.setCompleted(false);

        rem = reminderService.create(rem);
        assertThat(rem.getId()).isNotNull();

        // Query methods
        assertThat(reminderService.getAll()).isNotEmpty();
        assertThat(reminderService.getByFirm(testFirmId)).isNotEmpty();
        assertThat(reminderService.getActiveByFirm(testFirmId)).isNotEmpty();

        // Update completion
        rem.setCompleted(true);
        rem.setProgress(100);
        Reminder updated = reminderService.update(rem.getId(), rem);
        assertThat(updated.isCompleted()).isTrue();
        assertThat(updated.getCompletedAt()).isNotNull();

        // Toggle back to not completed
        updated.setCompleted(false);
        updated.setDueDate(LocalDateTime.now().plusDays(15));
        Reminder toggled = reminderService.update(updated.getId(), updated);
        assertThat(toggled.isCompleted()).isFalse();
        assertThat(toggled.getCompletedAt()).isNull();

        // Delete
        boolean deleted = reminderService.delete(rem.getId());
        assertThat(deleted).isTrue();
    }

    @Test
    @DisplayName("Should test NoteService lifecycle and updates")
    void testNoteServiceBranches() {
        Note note = new Note();
        note.setTitle("Warehouse Reorganization");
        note.setContent("Relocate fast-moving SKUs closer to dispatch bay.");
        note.setTags("warehouse,logistics");
        note.setFirmId(testFirmId);

        note = noteService.create(note);
        assertThat(note.getId()).isNotNull();

        assertThat(noteService.getAll()).isNotEmpty();
        assertThat(noteService.getByFirm(testFirmId)).isNotEmpty();

        note.setContent("Updated SKU distribution mapping completed.");
        Note updated = noteService.update(note.getId(), note);
        assertThat(updated.getContent()).contains("Updated SKU");

        boolean deleted = noteService.delete(note.getId());
        assertThat(deleted).isTrue();
    }

    @Test
    @DisplayName("Should test ExpenseService creation without date and comprehensive updates")
    void testExpenseServiceBranches() {
        // Create without date (should default to today)
        Expense exp = new Expense();
        exp.setTitle("Office Stationery");
        exp.setAmount(BigDecimal.valueOf(1250.75));
        exp.setCategory("Admin");
        exp.setPaymentMode("UPI");
        exp.setNotes("Printer papers and pens");
        exp.setFirmId(testFirmId);

        exp = expenseService.createExpense(exp);
        assertThat(exp.getId()).isNotNull();
        assertThat(exp.getExpenseDate()).isEqualTo(LocalDate.now());

        // Update
        exp.setTitle("Office Stationery & Ink Cartridges");
        exp.setAmount(BigDecimal.valueOf(3200.00));
        Expense updated = expenseService.updateExpense(exp.getId(), exp);
        assertThat(updated.getTitle()).contains("Cartridges");

        assertThat(expenseService.getExpensesByFirm(testFirmId)).isNotEmpty();

        boolean deleted = expenseService.deleteExpense(exp.getId());
        assertThat(deleted).isTrue();
    }

    @Test
    @DisplayName("Should test InboxMessageService aggregated low stock and duplicate prevention")
    void testInboxMessageServiceBranches() {
        Product outOfStock = Product.builder()
                .name("Thermal Paper Rolls")
                .price(BigDecimal.valueOf(40.0))
                .stockQuantity(BigDecimal.ZERO)
                .minStockLevel(BigDecimal.valueOf(20.0))
                .firmId(testFirmId)
                .build();

        Product lowStock = Product.builder()
                .name("Barcode Ribbons")
                .price(BigDecimal.valueOf(150.0))
                .stockQuantity(BigDecimal.valueOf(3.0))
                .minStockLevel(BigDecimal.valueOf(10.0))
                .firmId(testFirmId)
                .build();

        // 1. Aggregated low stock notifications
        inboxService.notifyAggregatedLowStock(testFirmId, List.of(outOfStock, lowStock));
        List<InboxMessage> msgs = inboxService.getMessagesByFirm(testFirmId);
        assertThat(msgs).isNotEmpty();

        // 2. Notification if absent (new vs duplicate)
        boolean sentFirst = inboxService.sendNotificationIfAbsent(
                testFirmId,
                "PAYROLL:",
                "PAYROLL: September Disbursal Due",
                "Monthly payroll is due for review.",
                "HR System"
        );
        assertThat(sentFirst).isTrue();

        // Second time with same prefix should be skipped (returns false)
        boolean sentDuplicate = inboxService.sendNotificationIfAbsent(
                testFirmId,
                "PAYROLL:",
                "PAYROLL: September Disbursal Due",
                "Monthly payroll is due for review.",
                "HR System"
        );
        assertThat(sentDuplicate).isFalse();

        // Mark as read and delete
        InboxMessage first = msgs.get(0);
        InboxMessage readMsg = inboxService.markAsRead(first.getId());
        assertThat(readMsg.isRead()).isTrue();

        boolean deleted = inboxService.deleteMessage(first.getId());
        assertThat(deleted).isTrue();
    }
}
