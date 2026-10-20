package com.billing.simple.billsoft.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller for rollback request telemetry.
 * Binary rollback and startup verification are owned and executed by the native LauncherMain supervisor.
 */
@RestController
@RequestMapping("/api/system")
public class RollbackController {

    @PostMapping("/rollbackNow")
    public ResponseEntity<Map<String, String>> rollbackNow() {
        return ResponseEntity.ok(Map.of(
                "status", "acknowledged",
                "message", "Startup verification and binary rollback are supervisor-managed by LauncherMain"
        ));
    }
}
