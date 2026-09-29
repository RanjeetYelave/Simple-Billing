package com.billing.simple.billsoft.regression.planner;

import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.InboxMessage;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;
import com.billing.simple.billsoft.repo.InboxMessageRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.service.BackupService;
import com.billing.simple.billsoft.service.InboxMessageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Tag("regression")
@Tag("integration")
@DisplayName("Inbox Messages & Notification Regression Tests")
class InboxMessageRegressionTest {

    @Autowired
    private InboxMessageService messageService;

    @Autowired
    private InboxMessageRepository inboxMessageRepo;

    @Autowired
    private BackupService backupService;

    @Autowired
    private FirmDetailsRepository firmRepo;

    private Long testFirmId;

    @BeforeEach
    void setUp() {
        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Inbox Test Firm " + System.nanoTime());
        firm = firmRepo.save(firm);
        testFirmId = firm.getId();
        TenantContext.setCurrentFirmId(testFirmId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Should create inbox notifications and filter unread messages")
    void shouldManageInboxMessages() {
        InboxMessage msg1 = messageService.createMessage(InboxMessage.builder()
                .subject("Low Stock Alert: Copper Wire")
                .body("Stock has fallen below threshold of 5 units.")
                .sender("System Alert")
                .firmId(testFirmId)
                .isRead(false)
                .build());

        InboxMessage msg2 = messageService.createMessage(InboxMessage.builder()
                .subject("Invoice Overdue: INV-0001")
                .body("Payment is pending for customer Alpha Corp.")
                .sender("Billing Engine")
                .firmId(testFirmId)
                .isRead(false)
                .build());

        List<InboxMessage> allMsgs = messageService.getMessagesByFirm(testFirmId);
        assertThat(allMsgs).hasSize(2);

        // Mark msg1 as read
        InboxMessage readMsg = messageService.markAsRead(msg1.getId());
        assertThat(readMsg.isRead()).isTrue();

        List<InboxMessage> unreadAfter = messageService.getMessagesByFirm(testFirmId).stream()
                .filter(m -> !m.isRead()).toList();
        assertThat(unreadAfter).hasSize(1);
        assertThat(unreadAfter.get(0).getId()).isEqualTo(msg2.getId());
    }

    @Test
    @DisplayName("Multi-tenant repeated backup merge restore preserves isolated inbox messages without duplication")
    void shouldMaintainMultiTenantInboxIdempotencyAcrossCycles() {
        // Create second firm
        FirmDetails firm2 = new FirmDetails();
        firm2.setFirmName("Inbox Second Firm " + System.nanoTime());
        firm2 = firmRepo.save(firm2);
        Long firm2Id = firm2.getId();

        LocalDateTime fixedTime = LocalDateTime.of(2026, 9, 15, 10, 30);

        // Firm 1: message 1
        InboxMessage msgFirm1_A = inboxMessageRepo.save(InboxMessage.builder()
                .firmId(testFirmId)
                .subject("Cross Firm Shared Subject")
                .body("Firm 1 Body Content")
                .sender("System")
                .createdAt(fixedTime)
                .isRead(false)
                .build());

        // Firm 1: message 2
        InboxMessage msgFirm1_B = inboxMessageRepo.save(InboxMessage.builder()
                .firmId(testFirmId)
                .subject("Firm 1 Unique Subject")
                .body("Distinct Body")
                .sender("Alert Service")
                .createdAt(fixedTime)
                .isRead(true)
                .build());

        // Firm 2: message with same subject as Firm 1's msgFirm1_A
        InboxMessage msgFirm2_A = inboxMessageRepo.save(InboxMessage.builder()
                .firmId(firm2Id)
                .subject("Cross Firm Shared Subject")
                .body("Firm 2 Different Body Content")
                .sender("System")
                .createdAt(fixedTime)
                .isRead(false)
                .build());

        assertEquals(2, inboxMessageRepo.findByFirmIdOrderByCreatedAtDesc(testFirmId).size());
        assertEquals(1, inboxMessageRepo.findByFirmIdOrderByCreatedAtDesc(firm2Id).size());

        // Perform 5 consecutive export and merge-restore cycles
        for (int cycle = 1; cycle <= 5; cycle++) {
            BackupDTO backup = backupService.exportAllData();
            assertNotNull(backup);

            backupService.importSelectiveData(backup, null, "merge", null);

            List<InboxMessage> firm1Messages = inboxMessageRepo.findByFirmIdOrderByCreatedAtDesc(testFirmId);
            List<InboxMessage> firm2Messages = inboxMessageRepo.findByFirmIdOrderByCreatedAtDesc(firm2Id);

            assertEquals(2, firm1Messages.size(), "Cycle " + cycle + ": Firm 1 inbox count changed");
            assertEquals(1, firm2Messages.size(), "Cycle " + cycle + ": Firm 2 inbox count changed");

            InboxMessage f1A = firm1Messages.stream().filter(m -> m.getSubject().equals("Cross Firm Shared Subject")).findFirst().orElse(null);
            assertNotNull(f1A, "Cycle " + cycle + ": Firm 1 shared subject message missing");
            assertEquals("Firm 1 Body Content", f1A.getBody());
            assertEquals(fixedTime, f1A.getCreatedAt());

            InboxMessage f2A = firm2Messages.stream().filter(m -> m.getSubject().equals("Cross Firm Shared Subject")).findFirst().orElse(null);
            assertNotNull(f2A, "Cycle " + cycle + ": Firm 2 shared subject message missing");
            assertEquals("Firm 2 Different Body Content", f2A.getBody());
            assertEquals(fixedTime, f2A.getCreatedAt());
        }
    }
}

