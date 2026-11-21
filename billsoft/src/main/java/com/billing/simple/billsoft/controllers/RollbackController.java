package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.service.UpdateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller for controlled emergency recovery and rollback operations.
 * Requires explicit user confirmation, valid justification reason, and verified pre-update backup.
 * Binary rollback and startup verification are executed in coordination with LauncherMain supervisor.
 */
@RestController
@RequestMapping("/api/system")
public class RollbackController {

    private final UpdateService updateService;

    public RollbackController(UpdateService updateService) {
        this.updateService = updateService;
    }

    @PostMapping("/rollbackNow")
    public ResponseEntity<Map<String, String>> rollbackNow() {
        return ResponseEntity.ok(Map.of(
                "status", "acknowledged",
                "message", "Startup verification and binary rollback are supervisor-managed by LauncherMain"
        ));
    }

    @PostMapping("/recovery/rollback")
    public ResponseEntity<Map<String, Object>> recoveryRollback(
            @RequestParam(name = "confirmRollback", required = false, defaultValue = "false") boolean confirmRollback,
            @RequestParam(name = "reason", required = false, defaultValue = "Authorized emergency recovery rollback") String reason) {
        try {
            Map<String, Object> result = updateService.executeAuthorizedRollback(confirmRollback, reason);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Rollback failed: " + e.getMessage()));
        }
    }
}
