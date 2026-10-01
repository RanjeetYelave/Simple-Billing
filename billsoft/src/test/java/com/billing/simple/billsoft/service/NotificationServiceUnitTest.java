package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dto.NotificationRequest;
import com.billing.simple.billsoft.dto.NotificationSummaryResponse;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Notification Service Fast Invariant & Remediation Unit Tests")
class NotificationServiceUnitTest {

    @Mock
    private NotificationRepository notificationRepo;

    @Mock
    private NotificationPreferenceRepository preferenceRepo;



    @Mock
    private ReminderService reminderService;

    @InjectMocks
    private NotificationService notificationService;

    private final Long firm1 = 101L;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        NotificationPreference defaultPref = NotificationPreference.builder()
                .firmId(firm1)
                .enabled(true)
                .billingEnabled(true)
                .inventoryEnabled(true)
                .purchaseEnabled(true)
                .plannerEnabled(true)
                .hrEnabled(true)
                .systemEnabled(true)
                .build();
        when(preferenceRepo.findByFirmId(firm1)).thenReturn(Optional.of(defaultPref));
    }

    @Test
    @DisplayName("RC-01 / RC-10: Deterministic deduplication and concurrent race recovery")
    void testCreateOrUpdateConcurrencyAndDeduplication() {
        String eventKey = "billing:invoice:overdue:999";
        NotificationRequest req = NotificationRequest.builder()
                .firmId(firm1)
                .eventKey(eventKey)
                .category(NotificationCategory.BILLING)
                .title("Invoice #999 Overdue")
                .body("Payment of Rs 1,500 is overdue.")
                .build();

        Notification existingNotif = Notification.builder()
                .id(1L)
                .firmId(firm1)
                .eventKey(eventKey)
                .category(NotificationCategory.BILLING)
                .title("Invoice #999 Overdue")
                .status(NotificationStatus.UNREAD)
                .build();

        // 1. New notification creation
        when(notificationRepo.findByFirmIdAndEventKey(firm1, eventKey))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingNotif));
        when(notificationRepo.save(any(Notification.class))).thenReturn(existingNotif);

        Notification created = notificationService.createOrUpdate(req);
        assertThat(created).isNotNull();
        assertThat(created.getEventKey()).isEqualTo(eventKey);

        // 2. Multi-node race collision simulation (DataIntegrityViolationException on save)
        when(notificationRepo.findByFirmIdAndEventKey(firm1, eventKey))
                .thenReturn(Optional.empty()) // Initial find misses
                .thenReturn(Optional.of(existingNotif)); // Second find succeeds
        when(notificationRepo.save(any(Notification.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry uk_notifications_firm_event_key"));

        Notification recovered = notificationService.createOrUpdate(req);
        assertThat(recovered).isNotNull();
        assertThat(recovered.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("RC-03 / RC-12: resolveByEventKey lifecycle resolution for Paid / Cancelled / Deleted")
    void testResolveByEventKey() {
        String eventKey1 = "billing:invoice:overdue:505";
        Notification activeNotif1 = Notification.builder()
                .id(50L)
                .firmId(firm1)
                .eventKey(eventKey1)
                .status(NotificationStatus.UNREAD)
                .build();

        when(notificationRepo.findByFirmIdAndEventKey(firm1, eventKey1)).thenReturn(Optional.of(activeNotif1));
        when(notificationRepo.save(any(Notification.class))).thenAnswer(i -> i.getArguments()[0]);

        // Resolve as ACTIONED on full payment
        boolean resolved = notificationService.resolveByEventKey(firm1, eventKey1, NotificationStatus.ACTIONED);
        assertThat(resolved).isTrue();
        assertThat(activeNotif1.getStatus()).isEqualTo(NotificationStatus.ACTIONED);

        // Resolve another active notification as DISMISSED on invoice cancellation
        String eventKey2 = "billing:invoice:overdue:606";
        Notification activeNotif2 = Notification.builder()
                .id(60L)
                .firmId(firm1)
                .eventKey(eventKey2)
                .status(NotificationStatus.UNREAD)
                .build();
        when(notificationRepo.findByFirmIdAndEventKey(firm1, eventKey2)).thenReturn(Optional.of(activeNotif2));

        boolean cancelled = notificationService.resolveByEventKey(firm1, eventKey2, NotificationStatus.DISMISSED);
        assertThat(cancelled).isTrue();
        assertThat(activeNotif2.getStatus()).isEqualTo(NotificationStatus.DISMISSED);
    }



    @Test
    @DisplayName("RC-08: Notification summary calculates accurate unread and active counts")
    void testNotificationSummary() {
        Notification unread = Notification.builder().id(1L).firmId(firm1).status(NotificationStatus.UNREAD).build();
        Notification read = Notification.builder().id(2L).firmId(firm1).status(NotificationStatus.READ).build();

        when(notificationRepo.countByFirmIdAndStatus(eq(firm1), eq(NotificationStatus.UNREAD)))
                .thenReturn(1L);
        when(notificationRepo.countByFirmIdAndStatusIn(eq(firm1), anyCollection()))
                .thenReturn(2L);
        when(notificationRepo.findByFirmIdInAndStatusInOrderByCreatedAtDesc(anyCollection(), anyCollection(), any()))
                .thenReturn(List.of(unread, read));

        NotificationSummaryResponse summary = notificationService.getSummary(firm1);
        assertThat(summary).isNotNull();
        assertThat(summary.getUnreadCount()).isEqualTo(1);
        assertThat(summary.getTotalActiveCount()).isEqualTo(2);
    }
}
