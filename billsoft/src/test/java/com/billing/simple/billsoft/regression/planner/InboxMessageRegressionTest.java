package com.billing.simple.billsoft.regression.planner;

import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.InboxMessage;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;
import com.billing.simple.billsoft.security.TenantContext;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
}
