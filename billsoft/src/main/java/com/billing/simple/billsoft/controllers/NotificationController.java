package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.dto.NotificationActionRequest;
import com.billing.simple.billsoft.dto.NotificationPreferencesDto;
import com.billing.simple.billsoft.dto.NotificationSummaryResponse;
import com.billing.simple.billsoft.entities.Notification;
import com.billing.simple.billsoft.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<Notification> list(@RequestParam(required = false) Long firmId,
                                   @RequestParam(required = false, defaultValue = "ACTIVE") String status,
                                   @RequestParam(required = false) String category,
                                   @RequestParam(required = false, defaultValue = "100") int limit) {
        return notificationService.listNotifications(firmId, status, category, limit);
    }

    @GetMapping("/summary")
    public NotificationSummaryResponse summary(@RequestParam(required = false) Long firmId) {
        return notificationService.getSummary(firmId);
    }

    @RequestMapping(value = "/{id}/read", method = {RequestMethod.POST, RequestMethod.PUT})
    public Notification markRead(@PathVariable Long id) {
        return notificationService.markRead(id);
    }

    @RequestMapping(value = {"/read-all", "/mark-all-read"}, method = RequestMethod.POST)
    public ResponseEntity<Map<String, Object>> markAllRead(@RequestParam(required = false) Long firmId) {
        int updated = notificationService.markAllRead(firmId);
        return ResponseEntity.ok(Map.of("success", true, "status", "ok", "updatedCount", updated));
    }

    @PostMapping("/{id}/snooze")
    public Notification snooze(@PathVariable Long id,
                               @RequestParam(required = false) String duration,
                               @RequestBody(required = false) Map<String, String> body) {
        String effectiveDuration = (duration != null && !duration.isBlank())
                ? duration
                : (body != null && body.containsKey("duration") ? body.get("duration") : "1d");
        return notificationService.snooze(id, effectiveDuration);
    }

    @RequestMapping(value = {"/{id}/dismiss", "/{id}"}, method = {RequestMethod.POST, RequestMethod.DELETE})
    public ResponseEntity<Map<String, Object>> dismiss(@PathVariable Long id) {
        Notification n = notificationService.dismiss(id);
        return ResponseEntity.ok(Map.of("success", true, "status", "ok", "id", n.getId()));
    }

    @PostMapping("/{id}/action")
    public Notification executeAction(@PathVariable Long id, @RequestBody(required = false) NotificationActionRequest request) {
        return notificationService.executeAction(id, request);
    }

    @GetMapping("/preferences")
    public NotificationPreferencesDto getPreferences(@RequestParam(required = false) Long firmId) {
        return notificationService.getPreferences(firmId);
    }

    @PutMapping("/preferences")
    public NotificationPreferencesDto updatePreferences(@RequestParam(required = false) Long firmId,
                                                        @RequestBody NotificationPreferencesDto dto) {
        return notificationService.updatePreferences(firmId, dto);
    }
}
