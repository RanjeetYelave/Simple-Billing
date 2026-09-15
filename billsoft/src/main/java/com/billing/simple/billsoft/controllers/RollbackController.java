package com.billing.simple.billsoft.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Exposes a REST endpoint that triggers a rollback request for the backend service.
 * This endpoint is called by the frontend UI when the restart timeout expires.
 */
@RestController
@RequestMapping("/api/system")
public class RollbackController {

    private Path getDataDirectory() {
        return com.billing.simple.billsoft.util.DataDirectoryResolver.resolveDataDirectory().toPath();
    }

    /**
     * POST /api/system/rollbackNow
     *
     * Creates a rollback.request marker file in the data directory.
     * The launcher supervisor loop detects this marker and rolls back the previous WAR.
     */
    @PostMapping("/rollbackNow")
    public ResponseEntity<String> rollbackNow() {
        try {
            Path marker = getDataDirectory().resolve("rollback.request");
            if (marker.getParent() != null && !Files.exists(marker.getParent())) {
                Files.createDirectories(marker.getParent());
            }
            if (!Files.exists(marker)) {
                Files.createFile(marker);
            }
            return ResponseEntity.ok("Rollback request submitted");
        } catch (Exception e) {
            System.err.println("Failed to trigger rollback: " + e.getMessage());
            return ResponseEntity.status(500).body("Failed to trigger rollback");
        }
    }
}
