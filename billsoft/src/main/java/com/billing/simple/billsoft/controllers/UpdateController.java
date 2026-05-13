package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.service.UpdateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/system")
@CrossOrigin
public class UpdateController {

    private final UpdateService updateService;

    public UpdateController(UpdateService updateService) {
        this.updateService = updateService;
    }

    @GetMapping("/update-status")
    public ResponseEntity<Map<String, Object>> checkUpdate() {
        return ResponseEntity.ok(updateService.checkUpdate());
    }

    @GetMapping(value = "/update-progress", produces = "text/event-stream")
    public SseEmitter updateProgress() {
        // Create a long‑lived emitter and give it to the service.
        SseEmitter emitter = new SseEmitter(0L);
        updateService.setProgressEmitter(emitter);
        return emitter;
    }

    @GetMapping("/check-update-complete")
    public ResponseEntity<Map<String, Object>> checkUpdateComplete() {
        return ResponseEntity.ok(updateService.checkForUpdateComplete());
    }

    @PostMapping("/apply-update")
    public ResponseEntity<Map<String, String>> applyUpdate(@RequestBody Map<String, String> payload) {
        String url = payload.get("downloadUrl");
        if (url == null || url.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "downloadUrl is required"));
        }
        boolean success = updateService.applyUpdate(url);
        if (success) {
            return ResponseEntity.ok(Map.of("message", "Update downloaded successfully. Restarting..."));
        } else {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to download update"));
        }
    }
}