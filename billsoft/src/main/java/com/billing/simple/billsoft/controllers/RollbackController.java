package com.billing.simple.billsoft.controllers;

import com.billing.simple.launcher.LauncherMain;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes a REST endpoint that triggers a rollback request for the backend service.
 * This endpoint is called by the frontend UI when the restart timeout expires.
 */
@RestController
@RequestMapping("/api/system")
public class RollbackController {

    /**
     * POST /api/system/rollbackNow
     *
     * Calls {@link LauncherMain#requestRollback()} which creates a {@code rollback.request}
     * marker file. The launcher loop monitors this marker and performs a rollback of the
     * previous version before restarting the service.
     */
    @PostMapping("/rollbackNow")
    public ResponseEntity<String> rollbackNow() {
        try {
            LauncherMain.requestRollback();
            return ResponseEntity.ok("Rollback request submitted");
        } catch (Exception e) {
            // Log the error (could be enhanced with a proper logger) and return a 500.
            System.err.println("Failed to trigger rollback: " + e.getMessage());
            return ResponseEntity.status(500).body("Failed to trigger rollback");
        }
    }
}
