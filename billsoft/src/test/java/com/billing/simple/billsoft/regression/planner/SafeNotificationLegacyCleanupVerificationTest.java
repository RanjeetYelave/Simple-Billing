package com.billing.simple.billsoft.regression.planner;

import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.dto.NotificationActionRequest;
import com.billing.simple.billsoft.dto.NotificationPreferencesDto;
import com.billing.simple.billsoft.dto.NotificationRequest;
import com.billing.simple.billsoft.dto.NotificationSummaryResponse;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.licensing.model.CustomerMessage;
import com.billing.simple.billsoft.licensing.LicenseStorage;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.repositories.PartyRepository;
import com.billing.simple.billsoft.repositories.PurchaseOrderRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import com.billing.simple.billsoft.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@DisplayName("Safe Notification Legacy Cleanup — Full System Verification")
public class SafeNotificationLegacyCleanupVerificationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationPreferenceRepository preferenceRepository;

    @Autowired
    private ReminderRepository reminderRepository;

    @Autowired
    private ReminderService reminderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private SalaryRecordRepository salaryRecordRepository;

    @Autowired
    private FirmDetailsRepository firmDetailsRepository;

    @Autowired
    private PlannerNotificationScheduler plannerNotificationScheduler;

    @Autowired
    private BackupService backupService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private Long firmAId;
    private Long firmBId;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        backupService.factoryReset();
        notificationRepository.deleteAll();
        preferenceRepository.deleteAll();

        FirmDetails fA = new FirmDetails();
        fA.setFirmName("Alpha Corp");
        fA = firmDetailsRepository.save(fA);
        firmAId = fA.getId();

        FirmDetails fB = new FirmDetails();
        fB.setFirmName("Beta LLC");
        fB = firmDetailsRepository.save(fB);
        firmBId = fB.getId();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Phase 23: Complete Notification Producer Matrix Invariant Verification")
    void testProducerMatrixAllProducers() {
        LocalDateTime now = LocalDateTime.now();

        // 1. Reminder Producer
        Reminder rem = Reminder.builder()
                .firmId(firmAId)
                .title("Client Meeting")
                .type("reminder")
                .dueDate(now.minusMinutes(5))
                .inboxNotified(false)
                .completed(false)
                .build();
        rem = reminderRepository.save(rem);

        // 2. Task Producer
        Reminder task = Reminder.builder()
                .firmId(firmAId)
                .title("File GST Returns")
                .type("task")
                .dueDate(now.minusMinutes(10))
                .inboxNotified(false)
                .completed(false)
                .build();
        task = reminderRepository.save(task);

        // 3. Product / Low stock Producer
        Product p = Product.builder()
                .firmId(firmAId)
                .name("Industrial Valve")
                .sku("VALVE-01")
                .stockQuantity(new BigDecimal("1.000"))
                .minStockLevel(new BigDecimal("10.000"))
                .price(new BigDecimal("500.00"))
                .build();
        productRepository.save(p);

        // 4. Overdue Invoice Producer
        Invoice inv = new Invoice();
        inv.setFirmId(firmAId);
        inv.setInvoiceNumber("INV-2026-001");
        inv.setInvoiceDate(LocalDateTime.now().minusDays(15));
        inv.setDueDate(LocalDate.now().minusDays(5));
        inv.setStatus(InvoiceStatus.UNPAID);
        inv.setTotalAmount(new BigDecimal("15000.00"));
        inv = invoiceRepository.save(inv);

        // 5. Pending PO Delivery Producer
        Party party = new Party();
        party.setFirmId(firmAId);
        party.setName("Vendor Supplies Ltd");
        party = partyRepository.save(party);

        PurchaseOrder po = new PurchaseOrder();
        po.setFirmId(firmAId);
        po.setPoNumber("PO-2026-101");
        po.setParty(party);
        po.setPoDate(LocalDate.now().minusDays(10));
        po.setStatus(PurchaseOrderStatus.ISSUED);
        po.setExpectedDeliveryDate(LocalDate.now().minusDays(2));
        po.setTotalAmount(new BigDecimal("25000.00"));
        po = purchaseOrderRepository.save(po);

        // 6. Payroll Producer
        Employee emp = new Employee();
        emp.setFirmId(firmAId);
        emp.setName("John Doe");
        emp.setPhone("9876543210");
        emp.setIsActive(true);
        emp.setDateOfJoining(LocalDate.now().minusMonths(6));
        employeeRepository.save(emp);

        // Run Scheduler check ticks
        plannerNotificationScheduler.checkDuePlannerItems();
        plannerNotificationScheduler.checkLowStockAlerts();
        plannerNotificationScheduler.checkOverdueInvoices();
        plannerNotificationScheduler.checkPendingPurchaseOrderDeliveries();
        plannerNotificationScheduler.checkPayrollMonthlyReminders();

        // 7. Licensing Announcement Producer
        notificationService.createOrUpdate(NotificationRequest.builder()
                .firmId(Notification.GLOBAL_FIRM_ID)
                .eventKey("management:broadcast:patch-v1")
                .category(NotificationCategory.LICENSING)
                .priority(NotificationPriority.HIGH)
                .title("Maintenance Release")
                .body("Release notes v1.0.0")
                .build());

        // Verify producers created canonical notifications
        List<Notification> firmANotifs = notificationRepository.findByFirmId(firmAId);
        assertThat(firmANotifs.size()).isGreaterThanOrEqualTo(5);

        Set<String> eventKeys = new HashSet<>();
        for (Notification n : firmANotifs) {
            eventKeys.add(n.getEventKey());
        }

        assertThat(eventKeys).contains(
                "planner:reminder:" + rem.getId(),
                "planner:task:" + task.getId(),
                "inventory:low-stock:aggregate:" + firmAId,
                "billing:invoice:overdue:" + inv.getId(),
                "purchase:po:delivery:" + po.getId()
        );

        Optional<Notification> globalNotif = notificationRepository.findByFirmIdAndEventKey(Notification.GLOBAL_FIRM_ID, "management:broadcast:patch-v1");
        assertThat(globalNotif).isPresent();
    }

    @Test
    @DisplayName("Phase 17: Zero Duplication Stress Verification across repeated ticks and concurrency")
    void testZeroDuplicationUnderRepeatedExecutionAndConcurrency() throws Exception {
        // Setup initial entities
        Product p = Product.builder()
                .firmId(firmAId)
                .name("Safety Goggles")
                .stockQuantity(new BigDecimal("0.000"))
                .minStockLevel(new BigDecimal("5.000"))
                .build();
        productRepository.save(p);

        // Execute scheduler ticks 10 times consecutively
        for (int i = 0; i < 10; i++) {
            plannerNotificationScheduler.checkLowStockAlerts();
        }

        // Concurrently invoke createOrUpdate across 8 threads for identical eventKey
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Notification>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                latch.await();
                return notificationService.createOrUpdate(NotificationRequest.builder()
                        .firmId(firmAId)
                        .eventKey("inventory:low-stock:aggregate:" + firmAId)
                        .category(NotificationCategory.INVENTORY)
                        .title("Low Stock Alert")
                        .body("Concurrent check")
                        .build());
            }));
        }

        latch.countDown();
        for (Future<Notification> f : futures) {
            assertThat(f.get()).isNotNull();
        }
        executor.shutdown();

        // Query database directly: verify ZERO duplicate (firm_id, event_key) pairs
        List<Map<String, Object>> duplicates = jdbcTemplate.queryForList(
                "SELECT firm_id, event_key, COUNT(*) AS cnt FROM notifications GROUP BY firm_id, event_key HAVING COUNT(*) > 1"
        );
        assertThat(duplicates).isEmpty();

        long count = notificationRepository.count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("Phase 4 & 16: Complete Lifecycle Resolution Invariant Verification")
    void testLifecycleResolutionInvariants() {
        // 1. Invoice Overdue -> Paid & Deleted
        Invoice inv = new Invoice();
        inv.setFirmId(firmAId);
        inv.setInvoiceNumber("INV-888");
        inv.setStatus(InvoiceStatus.UNPAID);
        inv.setTotalAmount(new BigDecimal("2000.00"));
        inv.setDueDate(LocalDate.now().minusDays(3));
        inv = invoiceRepository.save(inv);

        plannerNotificationScheduler.checkOverdueInvoices();
        Notification invNotif = notificationRepository.findByFirmIdAndEventKey(firmAId, "billing:invoice:overdue:" + inv.getId()).orElseThrow();
        assertThat(invNotif.getStatus()).isEqualTo(NotificationStatus.UNREAD);

        // Action invoice payment -> notification transitions to ACTIONED
        invoiceService.recordPayment(inv.getId(), new BigDecimal("2000.00"), LocalDate.now(), "CASH", "Paid in full", "TXN123");
        invNotif = notificationRepository.findById(invNotif.getId()).orElseThrow();
        assertThat(invNotif.getStatus()).isEqualTo(NotificationStatus.ACTIONED);

        // Re-running scheduler must NOT resurrect ACTIONED notification
        plannerNotificationScheduler.checkOverdueInvoices();
        invNotif = notificationRepository.findById(invNotif.getId()).orElseThrow();
        assertThat(invNotif.getStatus()).isEqualTo(NotificationStatus.ACTIONED);

        // 2. Inventory Low Stock -> Restocked / Replenished
        Product p = Product.builder()
                .firmId(firmAId)
                .name("Copper Rod")
                .stockQuantity(new BigDecimal("1.000"))
                .minStockLevel(new BigDecimal("10.000"))
                .build();
        p = productRepository.save(p);

        plannerNotificationScheduler.checkLowStockAlerts();
        Notification stockNotif = notificationRepository.findByFirmIdAndEventKey(firmAId, "inventory:low-stock:aggregate:" + firmAId).orElseThrow();
        assertThat(stockNotif.getStatus()).isEqualTo(NotificationStatus.UNREAD);

        // Replenish stock above threshold
        p.setStockQuantity(new BigDecimal("25.000"));
        productRepository.save(p);

        // Scheduler tick detects replenishment -> resolves notification to ACTIONED
        plannerNotificationScheduler.checkLowStockAlerts();
        stockNotif = notificationRepository.findById(stockNotif.getId()).orElseThrow();
        assertThat(stockNotif.getStatus()).isEqualTo(NotificationStatus.ACTIONED);

        // 3. Reminder -> Completed via API Action
        Reminder rem = Reminder.builder()
                .firmId(firmAId)
                .title("Call Supplier")
                .type("task")
                .dueDate(LocalDateTime.now().minusMinutes(1))
                .inboxNotified(false)
                .completed(false)
                .build();
        rem = reminderRepository.save(rem);

        plannerNotificationScheduler.checkDuePlannerItems();
        Notification taskNotif = notificationRepository.findByFirmIdAndEventKey(firmAId, "planner:task:" + rem.getId()).orElseThrow();
        assertThat(taskNotif.getStatus()).isEqualTo(NotificationStatus.UNREAD);

        notificationService.executeAction(taskNotif.getId(), NotificationActionRequest.builder().actionChoice("SECONDARY").build());
        taskNotif = notificationRepository.findById(taskNotif.getId()).orElseThrow();
        assertThat(taskNotif.getStatus()).isEqualTo(NotificationStatus.ACTIONED);

        Reminder completedRem = reminderRepository.findById(rem.getId()).orElseThrow();
        assertThat(completedRem.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("Phase 5 & 21: Multi-Tenant Isolation & Global Announcement Restrictions")
    void testMultiTenantIsolation() {
        // Create notification for Firm A
        Notification notifA = notificationService.createOrUpdate(NotificationRequest.builder()
                .firmId(firmAId)
                .eventKey("custom:firmA:alert")
                .category(NotificationCategory.SYSTEM)
                .title("Firm A Private Alert")
                .body("Sensitive data")
                .build());

        // Create notification for Firm B
        Notification notifB = notificationService.createOrUpdate(NotificationRequest.builder()
                .firmId(firmBId)
                .eventKey("custom:firmB:alert")
                .category(NotificationCategory.SYSTEM)
                .title("Firm B Private Alert")
                .body("Sensitive data")
                .build());

        // Create Global announcement
        Notification globalNotif = notificationService.createOrUpdate(NotificationRequest.builder()
                .firmId(Notification.GLOBAL_FIRM_ID)
                .eventKey("management:broadcast:system-wide")
                .category(NotificationCategory.LICENSING)
                .title("System Maintenance")
                .body("Global maintenance window")
                .build());

        // Firm A context
        TenantContext.setCurrentFirmId(firmAId);

        // Firm A list contains Firm A and Global, but NOT Firm B
        List<Notification> firmAList = notificationService.listNotifications(firmAId, "ALL", null, 100);
        List<Long> firmANotifIds = firmAList.stream().map(Notification::getId).toList();
        assertThat(firmANotifIds).contains(notifA.getId(), globalNotif.getId());
        assertThat(firmANotifIds).doesNotContain(notifB.getId());

        // Firm A summary
        NotificationSummaryResponse summaryA = notificationService.getSummary(firmAId);
        assertThat(summaryA.getUnreadCount()).isEqualTo(2); // notifA + globalNotif

        // Cross-firm mutation attempt: Firm A tries to dismiss Firm B notification -> MUST THROW TenantSecurityException
        assertThatThrownBy(() -> notificationService.dismiss(notifB.getId()))
                .isInstanceOf(TenantSecurityException.class);

        // Firm A updates its own notification
        notificationService.markRead(notifA.getId());
        Notification updatedA = notificationRepository.findById(notifA.getId()).orElseThrow();
        assertThat(updatedA.getStatus()).isEqualTo(NotificationStatus.READ);
    }

    @Test
    @DisplayName("Phase 8: Backward Compatibility with Legacy Backup containing inboxMessages")
    void testBackupRestoreBackwardCompatibility() throws Exception {
        String legacyBackupJson = "{\n" +
                "  \"metadata\": { \"sourceFirmId\": " + firmAId + ", \"version\": \"v0.9.9\" },\n" +
                "  \"firmDetails\": { \"id\": " + firmAId + ", \"firmName\": \"Alpha Corp\" },\n" +
                "  \"inboxMessages\": [\n" +
                "    { \"id\": 999, \"subject\": \"Old Legacy DB Inbox Message\", \"body\": \"This was in the old DB inbox table\", \"read\": true }\n" +
                "  ],\n" +
                "  \"notifications\": [\n" +
                "    { \"firmId\": " + firmAId + ", \"eventKey\": \"legacy:imported:1\", \"title\": \"Canonical Notif\", \"body\": \"Restored cleanly\", \"status\": \"UNREAD\" }\n" +
                "  ],\n" +
                "  \"notificationPreferences\": [\n" +
                "    { \"firmId\": " + firmAId + ", \"enabled\": true, \"bellEnabled\": true, \"inboxEnabled\": true, \"defaultSnooze\": \"3d\" }\n" +
                "  ]\n" +
                "}";

        BackupDTO backupDto = objectMapper.readValue(legacyBackupJson, BackupDTO.class);
        assertThat(backupDto).isNotNull();
        assertThat(backupDto.getNotifications()).hasSize(1);
        assertThat(backupDto.getNotificationPreferences()).hasSize(1);

        // Perform importData without error
        backupService.importData(backupDto, firmAId, true);

        Optional<Notification> importedNotif = notificationRepository.findByFirmIdAndEventKey(firmAId, "legacy:imported:1");
        assertThat(importedNotif).isPresent();
        assertThat(importedNotif.get().getTitle()).isEqualTo("Canonical Notif");

        NotificationPreferencesDto prefDto = notificationService.getPreferences(firmAId);
        assertThat(prefDto.getDefaultSnooze()).isEqualTo("3d");
    }

    @Test
    @DisplayName("Phase 2 & 19: Licensing Announcement Store Integrity")
    void testLicensingAnnouncementStoreIntegrity() {
        LicenseStorage storage = new LicenseStorage();
        List<CustomerMessage> messages = storage.loadInboxMessages();
        assertThat(messages).isNotNull(); // inbox_messages.json is read cleanly
    }

    @Test
    @DisplayName("Phase 6: Reminder.inboxNotified scheduler gating verification")
    void testReminderInboxNotifiedSchedulerGating() {
        Reminder rem = Reminder.builder()
                .firmId(firmAId)
                .title("Gated Task")
                .type("task")
                .dueDate(LocalDateTime.now().minusMinutes(2))
                .inboxNotified(false)
                .completed(false)
                .build();
        rem = reminderRepository.save(rem);

        List<Reminder> dueBefore = reminderRepository.findDueReminders(LocalDateTime.now());
        assertThat(dueBefore).extracting(Reminder::getId).contains(rem.getId());

        // Run scheduler
        plannerNotificationScheduler.checkDuePlannerItems();

        Reminder remAfter = reminderRepository.findById(rem.getId()).orElseThrow();
        assertThat(remAfter.isInboxNotified()).isTrue();

        // Subsequent query must NOT include rem
        List<Reminder> dueAfter = reminderRepository.findDueReminders(LocalDateTime.now());
        assertThat(dueAfter).extracting(Reminder::getId).doesNotContain(rem.getId());
    }

    @Test
    @DisplayName("Phase 1 & 22: Schema Verification - Canonical tables exist, legacy INBOX_MESSAGES does NOT exist")
    void testFreshDatabaseSchemaAndTableExclusions() throws Exception {
        java.sql.Connection conn = Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection();
        java.sql.DatabaseMetaData metaData = conn.getMetaData();

        // 1. Verify canonical tables exist
        try (java.sql.ResultSet rs = metaData.getTables(null, null, "NOTIFICATIONS", null)) {
            assertThat(rs.next()).isTrue();
        }
        try (java.sql.ResultSet rs = metaData.getTables(null, null, "NOTIFICATION_PREFERENCES", null)) {
            assertThat(rs.next()).isTrue();
        }

        // 2. Verify legacy inbox_messages table does NOT exist
        try (java.sql.ResultSet rs = metaData.getTables(null, null, "INBOX_MESSAGES", null)) {
            assertThat(rs.next()).isFalse();
        }

        conn.close();
    }
}
