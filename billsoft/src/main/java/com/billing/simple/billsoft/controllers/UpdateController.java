package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.service.UpdateService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class UpdateController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UpdateController.class);

    private final UpdateService updateService;

    public UpdateController(UpdateService updateService) {
        this.updateService = updateService;
    }

    @GetMapping("/update-status")
    public ResponseEntity<Map<String, Object>> checkUpdate(
            @RequestParam(name = "force", required = false, defaultValue = "false") boolean force,
            @RequestParam(name = "forceRefresh", required = false, defaultValue = "false") boolean forceRefresh) {
        boolean doForce = force || forceRefresh;
        return ResponseEntity.ok(updateService.checkUpdate(doForce));
    }

    private static final java.util.Map<String, java.time.Instant> ACTIVE_PROGRESS_TICKETS = new java.util.concurrent.ConcurrentHashMap<>();

    public static boolean isValidProgressTicket(String ticket) {
        if (ticket == null || ticket.isBlank()) return false;
        java.time.Instant expiry = ACTIVE_PROGRESS_TICKETS.remove(ticket); // Single-use consumption
        return expiry != null && expiry.isAfter(java.time.Instant.now());
    }

    @PostMapping("/update-progress-ticket")
    public ResponseEntity<Map<String, Object>> createProgressTicket() {
        // Housekeep expired tickets
        java.time.Instant now = java.time.Instant.now();
        ACTIVE_PROGRESS_TICKETS.entrySet().removeIf(e -> e.getValue().isBefore(now));

        String ticket = "prog_" + java.util.UUID.randomUUID().toString().replace("-", "");
        ACTIVE_PROGRESS_TICKETS.put(ticket, now.plusSeconds(60)); // 60 seconds single-use ticket
        return ResponseEntity.ok(Map.of(
                "ticket", ticket,
                "expiresInSeconds", 60
        ));
    }

    @GetMapping(value = "/update-progress", produces = "text/event-stream")
    public SseEmitter updateProgress(jakarta.servlet.http.HttpServletRequest request,
                                     @RequestParam(name = "ticket", required = false) String ticket) {
        if (ticket != null && !ticket.isBlank()) {
            boolean alreadyValidated = Boolean.TRUE.equals(request.getAttribute("RUPEE_STREAM_TICKET_VALIDATED"));
            if (!alreadyValidated && !isValidProgressTicket(ticket)) {
                throw new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid or expired update progress streaming ticket");
            }
        }
        return updateService.createProgressEmitter();
    }

    @GetMapping("/check-update-complete")
    public ResponseEntity<Map<String, Object>> checkUpdateComplete() {
        return ResponseEntity.ok(updateService.checkForUpdateComplete());
    }

    @PostMapping("/apply-update")
    public ResponseEntity<Map<String, String>> applyUpdate(
            @RequestParam(name = "targetVersion", required = false) String targetVersion) {
        boolean started = updateService.applyUpdate(targetVersion);
        if (started) {
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Update pipeline started successfully"
            ));
        } else {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "Cannot start update: another update transaction is in progress or no verified update was found"
            ));
        }
    }

    @PostMapping("/remind-later")
    public ResponseEntity<Map<String, Object>> remindLater() {
        boolean deferred = updateService.remindLater();
        if (deferred) {
            return ResponseEntity.ok(Map.of(
                    "status", "deferred",
                    "message", "Update deferred. Staged package preserved."
            ));
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Could not record deferred update state"
            ));
        }
    }

    private boolean isLoopbackRequest(HttpServletRequest request) {
        if (request == null) return false;
        String remote = request.getRemoteAddr();
        if (remote == null) return false;
        return remote.equals("127.0.0.1") ||
                remote.equals("0:0:0:0:0:0:0:1") ||
                remote.equals("::1") ||
                remote.equalsIgnoreCase("localhost");
    }

    @PostMapping(value = "/manual-update/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadManualUpdate(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "confirmReplace", required = false, defaultValue = "false") boolean confirmReplace) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No update package file was provided."));
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && !originalFilename.toLowerCase().endsWith(".zip")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Selected file must be a .zip update package."));
        }
        try (InputStream is = file.getInputStream()) {
            Map<String, Object> result = updateService.stageManualUpdatePackage(is, originalFilename, confirmReplace);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (java.security.GeneralSecurityException e) {
            log.warn("Manual update package cryptographic verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "error", "Cryptographic verification failed: " + e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to stage update package: " + e.getMessage()));
        }
    }

    @PostMapping("/manual-update/stage-local")
    public ResponseEntity<Map<String, Object>> stageLocalManualUpdate(
            @RequestParam("packagePath") String packagePath,
            @RequestParam(name = "confirmReplace", required = false, defaultValue = "false") boolean confirmReplace,
            HttpServletRequest request) {
        if (!isLoopbackRequest(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Local file staging is restricted to loopback connections."));
        }
        if (packagePath == null || packagePath.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Package path cannot be empty."));
        }
        Path path = Path.of(packagePath.trim());
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Specified update package does not exist or is not a regular file: " + packagePath));
        }
        if (!path.getFileName().toString().toLowerCase().endsWith(".zip")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Package must be a .zip update archive."));
        }
        try {
            Map<String, Object> result = updateService.stageManualUpdatePackage(path, confirmReplace);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (java.security.GeneralSecurityException e) {
            log.warn("Local manual update package cryptographic verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "error", "Cryptographic verification failed: " + e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to stage update package: " + e.getMessage()));
        }
    }

    @PostMapping("/reset-update-state")
    public ResponseEntity<Map<String, Object>> resetUpdateState() {
        updateService.resetUpdateState();
        return ResponseEntity.ok(Map.of("status", "idle", "message", "Update state reset successfully"));
    }

    @PostMapping("/pause-update")
    public ResponseEntity<Map<String, Object>> pauseUpdate() {
        boolean paused = updateService.pauseUpdate();
        if (paused) {
            return ResponseEntity.ok(Map.of(
                    "status", "paused",
                    "message", "Update download paused successfully"
            ));
        } else {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "Cannot pause: no active download in progress or installation is already committing"
            ));
        }
    }

    @PostMapping("/resume-update")
    public ResponseEntity<Map<String, Object>> resumeUpdate() {
        boolean resumed = updateService.resumeUpdate();
        if (resumed) {
            return ResponseEntity.ok(Map.of(
                    "status", "resumed",
                    "message", "Update download resumed successfully"
            ));
        } else {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "Cannot resume: update is already in progress or cannot be resumed"
            ));
        }
    }

    @PostMapping("/retry-update")
    public ResponseEntity<Map<String, Object>> retryUpdate() {
        boolean retried = updateService.retryUpdate();
        if (retried) {
            return ResponseEntity.ok(Map.of(
                    "status", "retrying",
                    "message", "Update retry initiated successfully"
            ));
        } else {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "Cannot retry: an update transaction is already running"
            ));
        }
    }

    @PostMapping("/install-update")
    public ResponseEntity<Map<String, Object>> installUpdate() {
        boolean installed = updateService.installStagedUpdate();
        if (installed) {
            return ResponseEntity.ok(Map.of(
                    "status", "restarting",
                    "message", "Update activation initiated. Service restarting..."
            ));
        } else {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "Cannot install update: no verified update is staged and ready to install"
            ));
        }
    }

    @PostMapping("/cancel-update")
    public ResponseEntity<Map<String, String>> cancelUpdate() {
        boolean cancelled = updateService.cancelUpdate();
        if (cancelled) {
            return ResponseEntity.ok(Map.of(
                    "status", "cancelled",
                    "message", "Update download cancelled successfully"
            ));
        } else {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "Cannot cancel update: no active download in progress or installation already committing"
            ));
        }
    }

    @GetMapping("/update-diagnostics")
    public ResponseEntity<Map<String, Object>> getUpdateDiagnostics() {
        return ResponseEntity.ok(updateService.getUpdateDiagnostics());
    }

    @GetMapping("/version-info")
    public ResponseEntity<Map<String, Object>> getVersionInfo() {
        return ResponseEntity.ok(Map.of(
                "version", updateService.getCurrentVersion(),
                "engine", "Unified-OTA-v2",
                "updaterReady", true,
                "timestamp", java.time.Instant.now().toString()
        ));
    }

    @GetMapping("/release-notes")
    public ResponseEntity<Map<String, Object>> getReleaseNotes(
            @RequestParam(name = "version", required = false) String version,
            @RequestParam(name = "refresh", required = false, defaultValue = "false") boolean refresh) {
        com.billing.simple.billsoft.service.ReleaseNotesService rns = updateService.getReleaseNotesService();
        if (rns == null) {
            return ResponseEntity.ok(Map.of(
                    "status", "unavailable",
                    "message", "Release notes service is not configured"
            ));
        }
        if (refresh) {
            rns.refreshFromRemote(true);
        }
        if (version != null && !version.isBlank()) {
            boolean available = rns.hasReleaseNotesForVersion(version);
            String notes = rns.getReleaseNotesForVersion(version);
            java.util.List<String> items = rns.getReleaseNoteItemsForVersion(version);
            return ResponseEntity.ok(Map.of(
                    "version", com.billing.simple.billsoft.service.ReleaseNotesService.normalizeVersion(version),
                    "available", available,
                    "releaseNotes", notes,
                    "releaseNoteItems", items
            ));
        }
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "notes", rns.getAllReleaseNotes()
        ));
    }
}
