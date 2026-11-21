package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.service.AnnouncementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST API for global announcements.
 * Serves lightweight plain-text announcements fetched from remote git repository with cache,
 * conditional HTTP 304 evaluation via ETags, and persistent client acknowledgments.
 */
@RestController
@RequestMapping("/api/announcements")
@CrossOrigin
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @Autowired
    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping
    public ResponseEntity<List<String>> getAnnouncements(
            @RequestParam(value = "force", required = false, defaultValue = "false") boolean force,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        String etag = "\"" + announcementService.getCacheFingerprint() + "\"";
        if (!force && ifNoneMatch != null && ifNoneMatch.trim().equals(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        List<String> list = announcementService.getAnnouncements(force);
        return ResponseEntity.ok().eTag(etag).body(list);
    }

    @PostMapping("/acknowledge")
    public ResponseEntity<Map<String, Object>> acknowledgeAnnouncement(@RequestBody(required = false) Map<String, String> body) {
        String key = body != null ? body.get("key") : null;
        boolean success = announcementService.acknowledgeAnnouncement(key);
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", success);
        resp.put("acknowledged", true);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/acknowledged")
    public ResponseEntity<Set<String>> getAcknowledged() {
        return ResponseEntity.ok(announcementService.getAcknowledgedKeys());
    }
}
