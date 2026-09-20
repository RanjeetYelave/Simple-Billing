package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.service.AnnouncementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API for global announcements.
 * Serves lightweight plain-text announcements fetched from remote git repository with cache.
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
            @org.springframework.web.bind.annotation.RequestParam(value = "force", required = false, defaultValue = "false") boolean force) {
        List<String> list = announcementService.getAnnouncements(force);
        return ResponseEntity.ok(list);
    }
}
