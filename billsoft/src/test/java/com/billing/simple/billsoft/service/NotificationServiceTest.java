package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dto.*;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.NotificationPreferenceRepository;
import com.billing.simple.billsoft.repo.NotificationRepository;
import com.billing.simple.billsoft.repo.ReminderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@Transactional
@DisplayName("Notification Service Unit & Invariant Tests")
class NotificationServiceTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationPreferenceRepository preferenceRepository;

    @Autowired
    private ReminderRepository reminderRepository;

    @Autowired
    private BackupService backupService;

    private final Long firm1 = 101L;
    private final Long firm2 = 202L;

    @BeforeEach
    void setUp() {
        backupService.factoryReset();
        notificationRepository.deleteAll();
        preferenceRepository.deleteAll();
    }

    @Test
    @DisplayName("Should create notification and deduplicate by firmId + eventKey")
    void testCreateOrUpdateDeduplication() {
        NotificationRequest req1 = NotificationRequest.builder()
                .firmId(firm1)
                .eventKey("inventory:low-stock:aggregate:101")
                .category(NotificationCategory.INVENTORY)
                .priority(NotificationPriority.HIGH)
                .title("Low Stock: 2 items")
                .body("Items: Widget A, Widget B are below reorder level.")
                .build();

        Notification created1 = notificationService.createOrUpdate(req1);
        assertThat(created1.getId()).isNotNull();
        assertThat(created1.getTitle()).isEqualTo("Low Stock: 2 items");

        // Second run with updated body
        NotificationRequest req2 = NotificationRequest.builder()
                .firmId(firm1)
                .eventKey("inventory:low-stock:aggregate:101")
                .category(NotificationCategory.INVENTORY)
                .priority(NotificationPriority.HIGH)
                .title("Low Stock: 3 items")
                .body("Items: Widget A, Widget B, Widget C are below reorder level.")
                .build();

        Notification updated = notificationService.createOrUpdate(req2);
        assertThat(updated.getId()).isEqualTo(created1.getId());
        assertThat(updated.getTitle()).isEqualTo("Low Stock: 3 items");

        // Confirm only 1 row exists
        long count = notificationRepository.count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("Scheduler run should NEVER resurrect DISMISSED or ACTIONED notifications")
    void testNoResurrectionOnDismissedOrActioned() {
        String eventKey = "billing:invoice:overdue:555";
        NotificationRequest req = NotificationRequest.builder()
                .firmId(firm1)
                .eventKey(eventKey)
                .category(NotificationCategory.BILLING)
                .priority(NotificationPriority.NORMAL)
                .title("Invoice #555 Overdue")
                .body("Payment of Rs 1,200 is overdue.")
                .build();

        Notification n = notificationService.createOrUpdate(req);
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.UNREAD);

        // User dismisses
        notificationService.dismiss(n.getId());
        Notification dismissed = notificationRepository.findById(n.getId()).orElseThrow();
        assertThat(dismissed.getStatus()).isEqualTo(NotificationStatus.DISMISSED);

        // Next scheduler run arrives with same eventKey
        Notification afterScheduler = notificationService.createOrUpdate(req);
        assertThat(afterScheduler.getStatus()).isEqualTo(NotificationStatus.DISMISSED);

        // Test ACTIONED status preservation
        String actionEventKey = "planner:task:999";
        NotificationRequest taskReq = NotificationRequest.builder()
                .firmId(firm1)
                .eventKey(actionEventKey)
                .category(NotificationCategory.PLANNER)
                .title("Submit Tax")
                .body("Due today")
                .primaryActionLabel("Mark Done")
                .primaryActionType(NotificationActionType.API_ACTION)
                .primaryActionTarget("MARK_TASK_DONE")
                .build();

        Notification taskNotif = notificationService.createOrUpdate(taskReq);
        notificationService.executeAction(taskNotif.getId(), NotificationActionRequest.builder().actionChoice("PRIMARY").build());

        Notification actioned = notificationRepository.findById(taskNotif.getId()).orElseThrow();
        assertThat(actioned.getStatus()).isEqualTo(NotificationStatus.ACTIONED);

        // Scheduler runs again
        Notification taskAfterScheduler = notificationService.createOrUpdate(taskReq);
        assertThat(taskAfterScheduler.getStatus()).isEqualTo(NotificationStatus.ACTIONED);
    }

    @Test
    @DisplayName("Should handle global notifications with firmId = 0L without collision")
    void testGlobalNotificationScope() {
        NotificationRequest globalReq = NotificationRequest.builder()
                .firmId(null) // Should default to GLOBAL_FIRM_ID (0L)
                .eventKey("management:broadcast:ALL:patch-100")
                .category(NotificationCategory.LICENSING)
                .priority(NotificationPriority.HIGH)
                .title("Critical Security Update")
                .body("Please update your client.")
                .build();

        Notification globalNotif = notificationService.createOrUpdate(globalReq);
        assertThat(globalNotif.getFirmId()).isEqualTo(Notification.GLOBAL_FIRM_ID);

        // Visible in summary for any firm
        NotificationSummaryResponse sum1 = notificationService.getSummary(firm1);
        assertThat(sum1.getItems()).anyMatch(i -> i.getEventKey().equals("management:broadcast:ALL:patch-100"));

        NotificationSummaryResponse sum2 = notificationService.getSummary(firm2);
        assertThat(sum2.getItems()).anyMatch(i -> i.getEventKey().equals("management:broadcast:ALL:patch-100"));
    }

    @Test
    @DisplayName("Should auto-reconcile expired snoozes into UNREAD before reads")
    void testSnoozeAndReconciliation() {
        NotificationRequest req = NotificationRequest.builder()
                .firmId(firm1)
                .eventKey("purchase:po:delivery:12")
                .category(NotificationCategory.PURCHASE)
                .title("PO #12 Delivery Due")
                .body("Vendor shipment expected")
                .build();

        Notification n = notificationService.createOrUpdate(req);
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.UNREAD);

        // Snooze for 1 hour
        notificationService.snooze(n.getId(), "1h");
        Notification snoozed = notificationRepository.findById(n.getId()).orElseThrow();
        assertThat(snoozed.getStatus()).isEqualTo(NotificationStatus.SNOOZED);
        assertThat(snoozed.getSnoozedUntil()).isAfter(LocalDateTime.now());

        // Simulate time passing: set snoozedUntil in the past
        snoozed.setSnoozedUntil(LocalDateTime.now().minusMinutes(5));
        notificationRepository.save(snoozed);

        // Read summary -> triggers automatic reconciliation
        NotificationSummaryResponse summary = notificationService.getSummary(firm1);
        assertThat(summary.getUnreadCount()).isEqualTo(1);

        Notification reconciled = notificationRepository.findById(n.getId()).orElseThrow();
        assertThat(reconciled.getStatus()).isEqualTo(NotificationStatus.UNREAD);
    }

    @Test
    @DisplayName("Should isolate notifications strictly between firms")
    void testTenantIsolation() {
        NotificationRequest reqFirm1 = NotificationRequest.builder()
                .firmId(firm1)
                .eventKey("firm1:secret")
                .category(NotificationCategory.BILLING)
                .title("Firm 1 Notice")
                .body("Private data")
                .build();

        NotificationRequest reqFirm2 = NotificationRequest.builder()
                .firmId(firm2)
                .eventKey("firm2:secret")
                .category(NotificationCategory.BILLING)
                .title("Firm 2 Notice")
                .body("Private data")
                .build();

        notificationService.createOrUpdate(reqFirm1);
        notificationService.createOrUpdate(reqFirm2);

        List<Notification> listFirm1 = notificationService.listNotifications(firm1, null, null, 100);
        assertThat(listFirm1).hasSize(1);
        assertThat(listFirm1.get(0).getTitle()).isEqualTo("Firm 1 Notice");

        List<Notification> listFirm2 = notificationService.listNotifications(firm2, null, null, 100);
        assertThat(listFirm2).hasSize(1);
        assertThat(listFirm2.get(0).getTitle()).isEqualTo("Firm 2 Notice");
    }

    @Test
    @DisplayName("Should execute API action and mark linked reminder completed")
    void testExecuteActionWithReminderCompletion() {
        Reminder reminder = new Reminder();
        reminder.setTitle("Send Audit Report");
        reminder.setFirmId(firm1);
        reminder.setCompleted(false);
        reminder = reminderRepository.save(reminder);

        NotificationRequest req = NotificationRequest.builder()
                .firmId(firm1)
                .eventKey("planner:reminder:" + reminder.getId())
                .category(NotificationCategory.PLANNER)
                .title("Reminder: Send Audit Report")
                .body("Reminder due now")
                .primaryActionLabel("Mark Done")
                .primaryActionType(NotificationActionType.API_ACTION)
                .primaryActionTarget("MARK_TASK_DONE")
                .build();

        Notification notif = notificationService.createOrUpdate(req);

        Notification afterAction = notificationService.executeAction(
                notif.getId(),
                NotificationActionRequest.builder().actionChoice("PRIMARY").build()
        );
        assertThat(afterAction.getStatus()).isEqualTo(NotificationStatus.ACTIONED);

        Reminder updatedReminder = reminderRepository.findById(reminder.getId()).orElseThrow();
        assertThat(updatedReminder.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("Should maintain exactly one global notification for same broadcast messageId across repeated ingestions")
    void testGlobalBroadcastDeduplicationAndIdempotency() {
        String msgId = "MSG-1789702831414";
        String eventKey = "management:broadcast:" + msgId;

        for (int i = 0; i < 10; i++) {
            NotificationRequest req = NotificationRequest.builder()
                    .firmId(Notification.GLOBAL_FIRM_ID)
                    .eventKey(eventKey)
                    .category(NotificationCategory.LICENSING)
                    .priority(NotificationPriority.HIGH)
                    .title("License Downgraded")
                    .body("Due to violation")
                    .sender("RupeeCRM Management")
                    .build();
            Notification n = notificationService.createOrUpdate(req);
            assertThat(n.getFirmId()).isEqualTo(Notification.GLOBAL_FIRM_ID);
            assertThat(n.getEventKey()).isEqualTo(eventKey);
        }

        List<Notification> globalList = notificationRepository.findByFirmIdAndEventKeyStartingWith(
                Notification.GLOBAL_FIRM_ID, "management:broadcast:");
        assertThat(globalList).hasSize(1);
        assertThat(globalList.get(0).getEventKey()).isEqualTo(eventKey);
    }

    @Test
    @DisplayName("Different broadcast messageIds must remain distinct global notifications")
    void testDifferentBroadcastMessageIdsRemainDistinct() {
        NotificationRequest req1 = NotificationRequest.builder()
                .firmId(Notification.GLOBAL_FIRM_ID)
                .eventKey("management:broadcast:MSG-AAA")
                .category(NotificationCategory.LICENSING)
                .priority(NotificationPriority.HIGH)
                .title("Notice A")
                .body("Body A")
                .build();

        NotificationRequest req2 = NotificationRequest.builder()
                .firmId(Notification.GLOBAL_FIRM_ID)
                .eventKey("management:broadcast:MSG-BBB")
                .category(NotificationCategory.LICENSING)
                .priority(NotificationPriority.HIGH)
                .title("Notice B")
                .body("Body B")
                .build();

        notificationService.createOrUpdate(req1);
        notificationService.createOrUpdate(req2);

        List<Notification> globalList = notificationRepository.findByFirmIdAndEventKeyStartingWith(
                Notification.GLOBAL_FIRM_ID, "management:broadcast:");
        assertThat(globalList).hasSize(2);
        assertThat(globalList).extracting(Notification::getEventKey)
                .containsExactlyInAnyOrder("management:broadcast:MSG-AAA", "management:broadcast:MSG-BBB");
    }

    @Test
    @DisplayName("Legacy duplicate rows with different machine IDs should consolidate safely and idempotently")
    void testLegacyDuplicateConsolidation() {
        // Insert 3 legacy duplicate rows with different machine IDs
        Notification legacy1 = Notification.builder()
                .firmId(Notification.GLOBAL_FIRM_ID)
                .eventKey("management:broadcast:SFCK-RDJR-12AD-JXY2:MSG-999")
                .category(NotificationCategory.LICENSING)
                .priority(NotificationPriority.HIGH)
                .title("License Downgraded")
                .body("Due to violation")
                .status(NotificationStatus.UNREAD)
                .createdAt(LocalDateTime.now().minusDays(2))
                .updatedAt(LocalDateTime.now().minusDays(2))
                .build();

        Notification legacy2 = Notification.builder()
                .firmId(Notification.GLOBAL_FIRM_ID)
                .eventKey("management:broadcast:T578-SFHA-E6RX-DGEW:MSG-999")
                .category(NotificationCategory.LICENSING)
                .priority(NotificationPriority.HIGH)
                .title("License Downgraded")
                .body("Due to violation")
                .status(NotificationStatus.READ) // User read this one
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();

        Notification legacy3 = Notification.builder()
                .firmId(Notification.GLOBAL_FIRM_ID)
                .eventKey("management:broadcast:Z3CS-4BVX-G2V0-MTKT:MSG-999")
                .category(NotificationCategory.LICENSING)
                .priority(NotificationPriority.HIGH)
                .title("License Downgraded")
                .body("Due to violation")
                .status(NotificationStatus.UNREAD)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        notificationRepository.saveAll(List.of(legacy1, legacy2, legacy3));
        assertThat(notificationRepository.findByFirmIdAndEventKeyStartingWith(Notification.GLOBAL_FIRM_ID, "management:broadcast:")).hasSize(3);

        // Run migration / consolidation
        notificationService.migrateLegacyData();

        List<Notification> afterCleanup = notificationRepository.findByFirmIdAndEventKeyStartingWith(
                Notification.GLOBAL_FIRM_ID, "management:broadcast:MSG-999");
        assertThat(afterCleanup).hasSize(1);
        assertThat(afterCleanup.get(0).getEventKey()).isEqualTo("management:broadcast:MSG-999");
        assertThat(afterCleanup.get(0).getStatus()).isEqualTo(NotificationStatus.READ); // Preserved best status

        // Re-run migration to verify idempotency
        notificationService.migrateLegacyData();

        List<Notification> afterSecondRun = notificationRepository.findByFirmIdAndEventKeyStartingWith(
                Notification.GLOBAL_FIRM_ID, "management:broadcast:MSG-999");
        assertThat(afterSecondRun).hasSize(1);
        assertThat(afterSecondRun.get(0).getEventKey()).isEqualTo("management:broadcast:MSG-999");
        assertThat(afterSecondRun.get(0).getStatus()).isEqualTo(NotificationStatus.READ);
    }
}
