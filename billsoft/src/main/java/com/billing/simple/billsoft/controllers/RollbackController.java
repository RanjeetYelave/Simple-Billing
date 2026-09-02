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
        String envPath = System.getenv("RUPEECRM_DATA_DIR");
        if (envPath != null && !envPath.trim().isEmpty()) {
            return Paths.get(envPath.trim());
        }
        envPath = System.getenv("BILLSOFT_DATA_DIR");
        if (envPath != null && !envPath.trim().isEmpty()) {
            return Paths.get(envPath.trim());
        }

        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isEmpty()) {
                return Paths.get(appData, "SimpleBilling");
            }
        } else if (os.contains("mac")) {
            String userHome = System.getProperty("user.home");
            return Paths.get(userHome, "Library", "Application Support", "SimpleBilling");
        }
        return Paths.get(System.getProperty("user.home"), ".simplebilling");
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
