package com.billing.simple.billsoft.recovery;

import com.billing.simple.billsoft.exception.BackupValidationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/recovery")
public class DiagnosticRecoveryController {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticRecoveryController.class);
    public static final String SESSION_AUTH_ATTR = "DIAGNOSTIC_AUTHENTICATED";
    public static final String RUPEE_DIAG_COOKIE = "RUPEE_DIAG_SESSION";

    // In-memory registry of active diagnostic session tokens: token -> expiration timestamp (ms)
    // Provides complete session isolation from normal RupeeCRM application logins
    private static final Map<String, Long> ACTIVE_DIAG_TOKENS = new ConcurrentHashMap<>();

    private final DiagnosticAuthService authService;
    private final DiagnosticSystemHealthService healthService;
    private final DiagnosticBackupScanner backupScanner;
    private final DiagnosticBackupOrchestrator orchestrator;
    private final DiagnosticLogService logService;
    private final DiagnosticSessionService sessionService;

    public DiagnosticRecoveryController(DiagnosticAuthService authService,
                                        DiagnosticSystemHealthService healthService,
                                        DiagnosticBackupScanner backupScanner,
                                        DiagnosticBackupOrchestrator orchestrator,
                                        DiagnosticLogService logService,
                                        DiagnosticSessionService sessionService) {
        this.authService = authService;
        this.healthService = healthService;
        this.backupScanner = backupScanner;
        this.orchestrator = orchestrator;
        this.logService = logService;
        this.sessionService = sessionService;
    }

    private boolean isAuthorized(HttpServletRequest request) {
        // 1. Check HttpSession attribute (isolated attribute on server session)
        HttpSession session = request.getSession(false);
        if (session != null && Boolean.TRUE.equals(session.getAttribute(SESSION_AUTH_ATTR))) {
            return true;
        }

        // 2. Check dedicated diagnostic cookie (RUPEE_DIAG_SESSION)
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (RUPEE_DIAG_COOKIE.equals(c.getName())) {
                    String token = c.getValue();
                    Long expiry = ACTIVE_DIAG_TOKENS.get(token);
                    if (expiry != null) {
                        if (expiry > System.currentTimeMillis()) {
                            return true;
                        } else {
                            ACTIVE_DIAG_TOKENS.remove(token);
                        }
                    }
                }
            }
        }

        // 3. Fallback to X-Diagnostic-Key header (for automated testing / CLI tooling)
        String key = request.getHeader("X-Diagnostic-Key");
        if (key != null && authService.verifyKey(key)) {
            return true;
        }
        return false;
    }

    private <T> ResponseEntity<Map<String, Object>> unauthorizedResponse() {
        Map<String, Object> err = new HashMap<>();
        err.put("success", false);
        err.put("code", "UNAUTHORIZED");
        err.put("message", "Diagnostic clearance key is invalid or missing.");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(err);
    }

    // 1. Diagnostic Authentication Gate (Establishes Authenticated Session)
    @PostMapping("/auth/verify")
    public ResponseEntity<Map<String, Object>> verifyKey(HttpServletRequest request,
                                                         HttpServletResponse response,
                                                         @RequestBody(required = false) Map<String, String> body) {
        String key = null;
        if (body != null && body.containsKey("key")) {
            key = body.get("key");
        }
        if (key == null || key.isBlank()) {
            key = request.getHeader("X-Diagnostic-Key");
        }

        Map<String, Object> resp = new HashMap<>();
        if (authService.verifyKey(key)) {
            // Establish session attribute
            HttpSession session = request.getSession(true);
            session.setAttribute(SESSION_AUTH_ATTR, Boolean.TRUE);
            session.setMaxInactiveInterval(30 * 60); // 30 minutes idle timeout

            // Generate isolated diagnostic cookie
            String diagToken = UUID.randomUUID().toString();
            ACTIVE_DIAG_TOKENS.put(diagToken, System.currentTimeMillis() + (30 * 60 * 1000L));
            Cookie cookie = new Cookie(RUPEE_DIAG_COOKIE, diagToken);
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            cookie.setMaxAge(30 * 60);
            response.addCookie(cookie);

            resp.put("success", true);
            resp.put("authenticated", true);
            resp.put("message", "Diagnostic authentication verified and session established.");
            return ResponseEntity.ok(resp);
        } else {
            resp.put("success", false);
            resp.put("authenticated", false);
            resp.put("code", "INVALID_KEY");
            resp.put("message", "Diagnostic master clearance key is invalid or unauthorized.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(resp);
        }
    }

    // 1b. Check Active Diagnostic Session Status
    @GetMapping("/auth/session")
    public ResponseEntity<Map<String, Object>> checkSession(HttpServletRequest request) {
        boolean auth = isAuthorized(request);
        Map<String, Object> resp = new HashMap<>();
        resp.put("authenticated", auth);
        return ResponseEntity.ok(resp);
    }

    // 1c. Invalidate Diagnostic Session / Lock Console
    // Preserves normal RupeeCRM application session by clearing ONLY diagnostic credentials
    @PostMapping("/auth/lock")
    public ResponseEntity<Map<String, Object>> lockSession(HttpServletRequest request, HttpServletResponse response) {
        // 1. Remove diagnostic attribute from HttpSession WITHOUT calling session.invalidate()
        // This guarantees normal application login session remains 100% active and untouched
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(SESSION_AUTH_ATTR);
        }

        // 2. Remove diagnostic cookie token from active tokens
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (RUPEE_DIAG_COOKIE.equals(c.getName())) {
                    ACTIVE_DIAG_TOKENS.remove(c.getValue());
                }
            }
        }

        // 3. Clear cookie in response
        Cookie clearCookie = new Cookie(RUPEE_DIAG_COOKIE, "");
        clearCookie.setPath("/");
        clearCookie.setMaxAge(0);
        clearCookie.setHttpOnly(true);
        response.addCookie(clearCookie);

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("authenticated", false);
        resp.put("message", "Diagnostic console locked and session invalidated.");
        return ResponseEntity.ok(resp);
    }

    // 2. System Health Dashboard
    @GetMapping("/health")
    public ResponseEntity<?> getHealth(HttpServletRequest request) {
        if (!isAuthorized(request)) {
            return unauthorizedResponse();
        }
        return ResponseEntity.ok(healthService.getHealth());
    }

    // 3. Multi-tier Backup Discovery Scan
    @GetMapping("/backups/scan")
    public ResponseEntity<?> scanBackups(HttpServletRequest request) {
        if (!isAuthorized(request)) {
            return unauthorizedResponse();
        }
        return ResponseEntity.ok(backupScanner.scanAll());
    }

    // 4. Backup Inspection
    @GetMapping("/backups/inspect")
    public ResponseEntity<?> inspectBackup(HttpServletRequest request,
                                          @RequestParam("recoveryId") String recoveryId) {
        if (!isAuthorized(request)) {
            return unauthorizedResponse();
        }
        try {
            Object inspection = backupScanner.inspect(recoveryId);
            return ResponseEntity.ok(inspection);
        } catch (IllegalArgumentException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("code", "INVALID_BACKUP_ID");
            err.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        } catch (BackupValidationException | JsonProcessingException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("code", "INVALID_BACKUP_SCHEMA");
            err.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        } catch (Exception e) {
            log.error("Backup inspection failed for {}: {}", recoveryId, e.getMessage(), e);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("code", "INSPECTION_FAILED");
            err.put("message", "Failed to inspect backup: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    // 5. Download Backup (Streamed)
    @GetMapping("/backups/download")
    public ResponseEntity<?> downloadBackup(HttpServletRequest request,
                                            @RequestParam("recoveryId") String recoveryId) {
        if (!isAuthorized(request)) {
            return unauthorizedResponse();
        }
        try {
            DiagnosticBackupOrchestrator.DownloadPayload payload = orchestrator.getDownloadPayload(recoveryId);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + payload.getFilename() + "\"")
                    .contentType(MediaType.parseMediaType(payload.getContentType()))
                    .contentLength(payload.getContentLength())
                    .body(payload.getResource());
        } catch (IllegalArgumentException | SecurityException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("code", "INVALID_REQUEST");
            err.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        } catch (Exception e) {
            log.error("Backup download failed for {}: {}", recoveryId, e.getMessage(), e);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("code", "DOWNLOAD_FAILED");
            err.put("message", "Unable to download requested backup artifact: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    // 6. Create H2 Snapshot
    @PostMapping("/backups/snapshot/h2")
    public ResponseEntity<?> createH2Snapshot(HttpServletRequest request) {
        if (!isAuthorized(request)) {
            return unauthorizedResponse();
        }
        try {
            Map<String, Object> res = orchestrator.createH2Snapshot();
            return ResponseEntity.ok(res);
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("in progress")) {
                Map<String, Object> err = new HashMap<>();
                err.put("success", false);
                err.put("code", "OPERATION_IN_PROGRESS");
                err.put("message", e.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT).body(err);
            }
            log.error("H2 snapshot creation failed: {}", e.getMessage(), e);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("code", "H2_SNAPSHOT_FAILED");
            err.put("message", "Failed to create H2 database snapshot: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        } catch (Exception e) {
            log.error("H2 snapshot creation failed: {}", e.getMessage(), e);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("code", "H2_SNAPSHOT_FAILED");
            err.put("message", "Failed to create H2 database snapshot: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    // 7. Create JSON Snapshot
    @PostMapping("/backups/snapshot/json")
    public ResponseEntity<?> createJsonSnapshot(HttpServletRequest request) {
        if (!isAuthorized(request)) {
            return unauthorizedResponse();
        }
        try {
            Map<String, Object> res = orchestrator.createJsonSnapshot();
            return ResponseEntity.ok(res);
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("in progress")) {
                Map<String, Object> err = new HashMap<>();
                err.put("success", false);
                err.put("code", "OPERATION_IN_PROGRESS");
                err.put("message", e.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT).body(err);
            }
            log.error("JSON snapshot creation failed: {}", e.getMessage(), e);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("code", "JSON_SNAPSHOT_FAILED");
            err.put("message", "Failed to create JSON snapshot: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        } catch (Exception e) {
            log.error("JSON snapshot creation failed: {}", e.getMessage(), e);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("code", "JSON_SNAPSHOT_FAILED");
            err.put("message", "Failed to create JSON snapshot: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    // 8. Human-Authorized Transactional Restore (Reusing Application Flow)
    @PostMapping("/backups/restore")
    public ResponseEntity<?> restoreBackup(HttpServletRequest request,
                                           @RequestBody Map<String, Object> body) {
        if (!isAuthorized(request)) {
            return unauthorizedResponse();
        }
        String recoveryId = body != null && body.get("recoveryId") != null ? String.valueOf(body.get("recoveryId")) : null;
        String mode = body != null && body.get("mode") != null ? String.valueOf(body.get("mode")) : null;
        String confirmation = body != null && body.get("confirmation") != null ? String.valueOf(body.get("confirmation")) : null;

        List<Long> firmIds = null;
        if (body != null && body.get("firmIds") instanceof List) {
            firmIds = new java.util.ArrayList<>();
            for (Object obj : (List<?>) body.get("firmIds")) {
                if (obj instanceof Number) {
                    firmIds.add(((Number) obj).longValue());
                } else if (obj != null) {
                    try {
                        firmIds.add(Long.parseLong(String.valueOf(obj)));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        Long targetFirmId = null;
        if (body != null && body.get("targetFirmId") != null) {
            try {
                targetFirmId = Long.parseLong(String.valueOf(body.get("targetFirmId")));
            } catch (NumberFormatException ignored) {}
        }

        try {
            Map<String, Object> result = orchestrator.restoreBackup(recoveryId, firmIds, mode, targetFirmId, confirmation);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("code", "INVALID_RESTORE_PARAMS");
            err.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        } catch (BackupValidationException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("code", "INVALID_BACKUP_SCHEMA");
            err.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        } catch (JsonProcessingException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("code", "CORRUPTED_BACKUP_JSON");
            err.put("message", "Invalid or corrupted backup JSON payload: " + e.getMessage());
            return ResponseEntity.badRequest().body(err);
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("in progress")) {
                Map<String, Object> err = new HashMap<>();
                err.put("success", false);
                err.put("code", "OPERATION_IN_PROGRESS");
                err.put("message", e.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT).body(err);
            }
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("code", "PRE_RESTORE_SNAPSHOT_FAILED");
            err.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        } catch (Exception e) {
            log.error("Transactional restore failed for {}: {}", recoveryId, e.getMessage(), e);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("code", "RESTORE_FAILED");
            err.put("message", "Restore failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    // 9. Password Reset (Sets auth_enabled = false)
    @PostMapping("/auth/reset-password")
    public ResponseEntity<?> resetPassword(HttpServletRequest request,
                                           @RequestBody Map<String, String> body) {
        if (!isAuthorized(request)) {
            return unauthorizedResponse();
        }
        String confirmation = body != null ? body.get("confirmation") : null;
        if (!"CONFIRM".equalsIgnoreCase(confirmation != null ? confirmation.trim() : "")) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("code", "CONFIRMATION_REQUIRED");
            err.put("message", "Password reset requires typing CONFIRM explicitly.");
            return ResponseEntity.badRequest().body(err);
        }

        boolean success = authService.resetPassword();
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", success);
        if (success) {
            resp.put("message", "Application authentication has been disabled (auth_enabled = false). You may now access the system without a password.");
            return ResponseEntity.ok(resp);
        } else {
            resp.put("code", "PASSWORD_RESET_FAILED");
            resp.put("message", "Failed to update configuration key in database.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
        }
    }

    // 10. Recent Logs
    @GetMapping("/logs/recent")
    public ResponseEntity<?> getRecentLogs(HttpServletRequest request,
                                           @RequestParam(value = "lines", defaultValue = "100") int lines) {
        if (!isAuthorized(request)) {
            return unauthorizedResponse();
        }
        int boundedLines = Math.min(Math.max(lines, 1), 500);
        List<String> logLines = logService.getRecentLogs(boundedLines);
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("lineCount", logLines.size());
        resp.put("lines", logLines);
        return ResponseEntity.ok(resp);
    }

    // 11. Download Logs
    @GetMapping("/logs/download")
    public ResponseEntity<?> downloadLogs(HttpServletRequest request) {
        if (!isAuthorized(request)) {
            return unauthorizedResponse();
        }
        try {
            File logFile = logService.getLogFile();
            if (!logFile.exists() || !logFile.isFile()) {
                Map<String, Object> err = new HashMap<>();
                err.put("success", false);
                err.put("code", "LOG_NOT_FOUND");
                err.put("message", "Debug log file has not been created yet.");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(err);
            }

            org.springframework.core.io.FileSystemResource resource = new org.springframework.core.io.FileSystemResource(logFile);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + logFile.getName() + "\"")
                    .contentType(MediaType.TEXT_PLAIN)
                    .contentLength(logFile.length())
                    .body(resource);
        } catch (Exception e) {
            log.error("Failed to download log file: {}", e.getMessage(), e);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("code", "LOG_DOWNLOAD_FAILED");
            err.put("message", "Error reading log file.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    // 12. Toggle Verbose Debug Logging
    @PostMapping("/logs/verbose")
    public ResponseEntity<?> setVerboseLogging(HttpServletRequest request,
                                               @RequestBody Map<String, Boolean> body) {
        if (!isAuthorized(request)) {
            return unauthorizedResponse();
        }
        boolean enabled = body != null && Boolean.TRUE.equals(body.get("enabled"));
        Map<String, Object> status = logService.setVerboseLogging(enabled);
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("enabled", enabled);
        resp.put("status", status);
        return ResponseEntity.ok(resp);
    }

    // 13. Controlled Graceful Restart via System.exit(10)
    @PostMapping("/restart")
    public ResponseEntity<?> restartApplication(HttpServletRequest request) {
        if (!isAuthorized(request)) {
            return unauthorizedResponse();
        }

        log.info("Diagnostic recovery requested backend restart with exit code 10...");

        // Trigger graceful supervisor restart asynchronously to allow HTTP 200 response to complete
        Thread restartThread = new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {}
            log.info("Diagnostic exit with code 10 executing now.");
            System.exit(10);
        }, "Diagnostic-Restart-Thread");
        restartThread.setDaemon(false);
        restartThread.start();

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Application restart initiated. Process exiting with code 10...");
        return ResponseEntity.ok(resp);
    }

    // 14. Public Diagnostic Mode Status Check (No secrets, safe for main app)
    @GetMapping("/diagnostic-mode/status")
    public ResponseEntity<?> getDiagnosticModeStatus() {
        return ResponseEntity.ok(sessionService.getPublicStatus());
    }

    // 15. Manage Persistent Live Console Session (Requires Diagnostic Clearance)
    @PostMapping("/settings/persistent-console")
    public ResponseEntity<?> updatePersistentConsoleSettings(HttpServletRequest request,
                                                             @RequestBody Map<String, Object> body) {
        if (!isAuthorized(request)) {
            return unauthorizedResponse();
        }
        if (body != null) {
            if (body.containsKey("enabled")) {
                sessionService.setPersistentConsoleEnabled(Boolean.TRUE.equals(body.get("enabled")));
            } else if (body.containsKey("logLevel") && body.get("logLevel") != null) {
                sessionService.setLogLevel(String.valueOf(body.get("logLevel")));
            }
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("status", sessionService.getPublicStatus());
        return ResponseEntity.ok(resp);
    }

    // 16. Live Log Streaming (Cursor-based, incremental, sanitized for unauthenticated viewers)
    @GetMapping("/logs/live")
    public ResponseEntity<?> getLiveLogs(HttpServletRequest request,
                                         @RequestParam(value = "sinceSeq", defaultValue = "0") long sinceSeq,
                                         @RequestParam(value = "lines", defaultValue = "100") int lines) {
        boolean authorized = isAuthorized(request);
        boolean persistentConsoleActive = sessionService.isPersistentConsoleEnabled();

        if (!authorized && !persistentConsoleActive) {
            return unauthorizedResponse();
        }

        boolean sanitize = !authorized;
        int boundedLines = Math.min(Math.max(lines, 1), 500);
        Map<String, Object> logData = logService.getLogsSince(sinceSeq, boundedLines, sanitize);

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("lines", logData.get("lines"));
        resp.put("nextSeq", logData.get("nextSeq"));
        resp.put("hasNew", logData.get("hasNew"));
        resp.put("totalBuffered", logData.get("totalBuffered"));
        resp.put("resyncRequired", logData.get("resyncRequired"));
        resp.put("earliestSeq", logData.get("earliestSeq"));
        resp.put("latestSeq", logData.get("latestSeq"));
        resp.put("lineCount", ((List<?>) logData.get("lines")).size());
        resp.put("connectionState", "STREAMING");
        return ResponseEntity.ok(resp);
    }

    // 17. Live Diagnostic Log Snapshot Download (On-demand text file, does not reset cursor)
    @GetMapping("/logs/live/download")
    public ResponseEntity<?> downloadLiveLogs(HttpServletRequest request) {
        boolean authorized = isAuthorized(request);
        boolean persistentConsoleActive = sessionService.isPersistentConsoleEnabled();

        if (!authorized && !persistentConsoleActive) {
            return unauthorizedResponse();
        }

        boolean sanitize = !authorized;
        String logContent = logService.exportAllBufferedLogs(sanitize);

        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"));
        String filename = "diagnostic-logs-" + timestamp + ".txt";

        byte[] bytes = logContent.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.TEXT_PLAIN)
                .contentLength(bytes.length)
                .body(bytes);
    }
}
