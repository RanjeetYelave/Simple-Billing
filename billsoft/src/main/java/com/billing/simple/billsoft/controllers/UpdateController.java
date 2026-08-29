package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.service.UpdateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/system")
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
        // Create a fresh SSE emitter with cleanup callbacks
        return updateService.createProgressEmitter();
    }

    @GetMapping("/check-update-complete")
    public ResponseEntity<Map<String, Object>> checkUpdateComplete() {
        return ResponseEntity.ok(updateService.checkForUpdateComplete());
    }

    @PostMapping("/apply-update")
    public ResponseEntity<Map<String, String>> applyUpdate() {
        boolean started = updateService.startUpdateAsync();
        if (started) {
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Update download started successfully"
            ));
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Failed to start update download"
            ));
        }
    }
}
