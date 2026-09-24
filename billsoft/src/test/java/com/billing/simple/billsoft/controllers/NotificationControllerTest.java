package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.dto.NotificationPreferencesDto;
import com.billing.simple.billsoft.dto.NotificationRequest;
import com.billing.simple.billsoft.entities.NotificationCategory;
import com.billing.simple.billsoft.entities.NotificationPriority;
import com.billing.simple.billsoft.entities.NotificationStatus;
import com.billing.simple.billsoft.repo.NotificationRepository;
import com.billing.simple.billsoft.service.BackupService;
import com.billing.simple.billsoft.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@AutoConfigureMockMvc
@DisplayName("Notification Controller REST API Tests")
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private com.billing.simple.billsoft.repo.NotificationPreferenceRepository preferenceRepository;

    @Autowired
    private BackupService backupService;

    @Autowired
    private ObjectMapper objectMapper;

    private final Long testFirmId = 1L;

    @BeforeEach
    void setUp() {
        backupService.factoryReset();
        notificationRepository.deleteAll();
        preferenceRepository.deleteAll();
    }

    @Test
    @DisplayName("GET /api/notifications should return list of notifications for firm")
    void testListNotifications() throws Exception {
        notificationService.createOrUpdate(NotificationRequest.builder()
                .firmId(testFirmId)
                .eventKey("test:alert:1")
                .category(NotificationCategory.SYSTEM)
                .priority(NotificationPriority.NORMAL)
                .title("System Health Normal")
                .body("All background tasks operational.")
                .build());

        mockMvc.perform(get("/api/notifications")
                        .param("firmId", String.valueOf(testFirmId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title", is("System Health Normal")));
    }

    @Test
    @DisplayName("GET /api/notifications/summary should return unread counts and items")
    void testGetSummary() throws Exception {
        notificationService.createOrUpdate(NotificationRequest.builder()
                .firmId(testFirmId)
                .eventKey("test:alert:summary")
                .category(NotificationCategory.LICENSING)
                .priority(NotificationPriority.HIGH)
                .title("Cloud Vault Ready")
                .body("Your vault snapshot is ready.")
                .build());

        mockMvc.perform(get("/api/notifications/summary")
                        .param("firmId", String.valueOf(testFirmId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount", is(1)))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.bellEnabled", is(true)));
    }

    @Test
    @DisplayName("PUT /api/notifications/{id}/read should mark notification as READ")
    void testMarkAsRead() throws Exception {
        var notif = notificationService.createOrUpdate(NotificationRequest.builder()
                .firmId(testFirmId)
                .eventKey("test:mark:read")
                .category(NotificationCategory.PLANNER)
                .title("Task Reminder")
                .body("Complete paperwork")
                .build());

        mockMvc.perform(put("/api/notifications/" + notif.getId() + "/read")
                        .param("firmId", String.valueOf(testFirmId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("READ")));
    }

    @Test
    @DisplayName("POST /api/notifications/mark-all-read should mark all unread notifications as READ")
    void testMarkAllRead() throws Exception {
        notificationService.createOrUpdate(NotificationRequest.builder()
                .firmId(testFirmId)
                .eventKey("test:mark:all:1")
                .category(NotificationCategory.INVENTORY)
                .title("Item A Low")
                .body("Low stock")
                .build());

        notificationService.createOrUpdate(NotificationRequest.builder()
                .firmId(testFirmId)
                .eventKey("test:mark:all:2")
                .category(NotificationCategory.INVENTORY)
                .title("Item B Low")
                .body("Low stock")
                .build());

        mockMvc.perform(post("/api/notifications/mark-all-read")
                        .param("firmId", String.valueOf(testFirmId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ok")));

        mockMvc.perform(get("/api/notifications/summary")
                        .param("firmId", String.valueOf(testFirmId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount", is(0)));
    }

    @Test
    @DisplayName("POST /api/notifications/{id}/snooze should snooze notification")
    void testSnooze() throws Exception {
        var notif = notificationService.createOrUpdate(NotificationRequest.builder()
                .firmId(testFirmId)
                .eventKey("test:snooze:1")
                .category(NotificationCategory.PURCHASE)
                .title("Pending Delivery")
                .body("Check shipment")
                .build());

        mockMvc.perform(post("/api/notifications/" + notif.getId() + "/snooze")
                        .param("duration", "4h")
                        .param("firmId", String.valueOf(testFirmId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SNOOZED")))
                .andExpect(jsonPath("$.snoozedUntil", notNullValue()));
    }

    @Test
    @DisplayName("DELETE /api/notifications/{id} should dismiss notification")
    void testDismiss() throws Exception {
        var notif = notificationService.createOrUpdate(NotificationRequest.builder()
                .firmId(testFirmId)
                .eventKey("test:dismiss:1")
                .category(NotificationCategory.HR)
                .title("Payroll notice")
                .body("Check hours")
                .build());

        mockMvc.perform(delete("/api/notifications/" + notif.getId())
                        .param("firmId", String.valueOf(testFirmId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ok")));

        var dismissed = notificationRepository.findById(notif.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(dismissed.getStatus()).isEqualTo(NotificationStatus.DISMISSED);
    }

    @Test
    @DisplayName("GET & PUT /api/notifications/preferences should fetch and update notification preferences")
    void testPreferences() throws Exception {
        mockMvc.perform(get("/api/notifications/preferences")
                        .param("firmId", String.valueOf(testFirmId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled", is(true)))
                .andExpect(jsonPath("$.bellEnabled", is(true)));

        NotificationPreferencesDto updateDto = NotificationPreferencesDto.builder()
                .enabled(true)
                .bellEnabled(false)
                .inboxEnabled(true)
                .defaultSnooze("3d")
                .billingEnabled(true)
                .inventoryEnabled(true)
                .purchaseEnabled(false)
                .build();

        mockMvc.perform(put("/api/notifications/preferences")
                        .param("firmId", String.valueOf(testFirmId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bellEnabled", is(false)))
                .andExpect(jsonPath("$.defaultSnooze", is("3d")))
                .andExpect(jsonPath("$.purchaseEnabled", is(false)));
    }
}
