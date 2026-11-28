package com.billing.simple.billsoft.telegram.controllers;

import com.billing.simple.billsoft.telegram.client.TelegramApiClient;
import com.billing.simple.billsoft.telegram.entities.TelegramAuthorizedUser;
import com.billing.simple.billsoft.telegram.entities.TelegramConfig;
import com.billing.simple.billsoft.telegram.repo.TelegramConfigRepository;
import com.billing.simple.billsoft.telegram.service.TelegramPollerService;
import com.billing.simple.billsoft.telegram.service.TelegramSecurityManager;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/internal/telegram")
public class TelegramInternalBridgeController {

    private static final Logger log = LoggerFactory.getLogger(TelegramInternalBridgeController.class);
    private static final String BRIDGE_SECRET_ENV = "RUPEECRM_INTERNAL_BRIDGE_SECRET";

    private final TelegramPollerService pollerService;
    private final TelegramSecurityManager securityManager;
    private final TelegramApiClient apiClient;
    private final TelegramConfigRepository configRepo;

    public TelegramInternalBridgeController(TelegramPollerService pollerService,
            TelegramSecurityManager securityManager,
            TelegramApiClient apiClient,
            TelegramConfigRepository configRepo) {
        this.pollerService = pollerService;
        this.securityManager = securityManager;
        this.apiClient = apiClient;
        this.configRepo = configRepo;
    }

    private boolean validateBridgeAccess(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!"127.0.0.1".equals(remoteAddr) && !"0:0:0:0:0:0:0:1".equals(remoteAddr) && !"::1".equals(remoteAddr)) {
            log.warn("Blocked non-loopback access attempt from: {}", remoteAddr);
            return false;
        }

        String expectedSecret = System.getenv(BRIDGE_SECRET_ENV);
        if (expectedSecret == null || expectedSecret.isBlank()) {
            expectedSecret = System.getProperty(BRIDGE_SECRET_ENV, "rupeecrm-internal-loopback-secret-2026");
        }

        String providedHeader = request.getHeader("X-Internal-Bridge-Secret");
        if (providedHeader == null) {
            return false;
        }

        return MessageDigest.isEqual(
                providedHeader.getBytes(StandardCharsets.UTF_8),
                expectedSecret.getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/configure")
    public ResponseEntity<?> configureBot(@RequestBody Map<String, String> payload, HttpServletRequest request) {
        if (!validateBridgeAccess(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
        }

        String botToken = payload.get("botToken");
        if (botToken == null || !botToken.trim().matches("^[0-9]+:[a-zA-Z0-9_-]+$")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid Telegram Bot Token format."));
        }

        try {
            JsonNode me = apiClient.getMe(botToken.trim());
            if (me == null || !me.path("ok").asBoolean()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Failed to validate token with Telegram API."));
            }

            // Clean up any lingering webhooks to ensure getUpdates long-polling operates
            // unimpeded
            try {
                apiClient.deleteWebhook(botToken.trim());
                log.info("Cleared existing webhook before starting poller.");
            } catch (Exception whEx) {
                log.warn("Warning deleting webhook during configure: {}", whEx.getMessage());
            }

            String username = me.path("result").path("username").asText("");
            TelegramConfig config = pollerService.getConfig();
            config.setBotUsername(username);
            config.setIsEnabled(true);
            config.setLastError(null);
            config.setCircuitBreakerTripped(false);
            config.setConflictCount(0);
            config.setUpdatedAt(LocalDateTime.now());
            configRepo.save(config);

            // Register standard bot commands
            try {
                apiClient.setMyCommands(botToken.trim(), List.of(
                        Map.of("command", "start", "description", "Start or link account"),
                        Map.of("command", "menu", "description", "Main navigation menu"),
                        Map.of("command", "firm", "description", "Switch active firm"),
                        Map.of("command", "newinvoice", "description", "Create a new tax invoice"),
                        Map.of("command", "newestimate", "description", "Create a new quotation"),
                        Map.of("command", "dashboard", "description", "Business analytics overview"),
                        Map.of("command", "invoices", "description", "View recent invoices"),
                        Map.of("command", "quotations", "description", "View recent quotations"),
                        Map.of("command", "stock", "description", "Inventory stock lookup"),
                        Map.of("command", "customers", "description", "Customer directory"),
                        Map.of("command", "debtors", "description", "Debtors & collections summary"),
                        Map.of("command", "cancel", "description", "Cancel active workflow")));
            } catch (Exception cmdEx) {
                log.warn("Could not set bot commands: {}", cmdEx.getMessage());
            }

            // Persist token to local H2 database and start poller
            pollerService.setBotTokenAndPersist(botToken.trim());

            Map<String, Object> resp = new java.util.LinkedHashMap<>();
            resp.put("success", true);
            resp.put("botUsername", username);
            resp.put("isEnabled", true);
            resp.put("hasStoredToken", true);
            resp.put("maskedToken", pollerService.getMaskedToken());
            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            log.error("Failed configuring bot: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Telegram error: " + e.getMessage()));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus(HttpServletRequest request) {
        if (!validateBridgeAccess(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
        }

        TelegramConfig config = pollerService.getConfig();
        Map<String, Object> res = new java.util.LinkedHashMap<>();
        res.put("isEnabled", config.getIsEnabled());
        res.put("isRunning", pollerService.isRunning());
        res.put("botUsername", config.getBotUsername() != null ? config.getBotUsername() : "");
        res.put("hasStoredToken", pollerService.hasStoredToken());
        res.put("maskedToken", pollerService.getMaskedToken());
        res.put("circuitBreakerTripped", config.getCircuitBreakerTripped());
        res.put("circuitBreakerReason",
                config.getCircuitBreakerReason() != null ? config.getCircuitBreakerReason() : "");
        res.put("lastActiveAt", config.getLastActiveAt() != null ? config.getLastActiveAt().toString() : "");
        res.put("lastPollAt", config.getLastPollAt() != null ? config.getLastPollAt().toString() : "");
        res.put("lastUpdateId", config.getLastUpdateId() != null ? config.getLastUpdateId() : 0L);
        res.put("lastProcessedAt", config.getLastProcessedAt() != null ? config.getLastProcessedAt().toString() : "");
        res.put("lastSentAt", config.getLastSentAt() != null ? config.getLastSentAt().toString() : "");
        res.put("lastError", config.getLastError() != null ? config.getLastError() : "");
        return ResponseEntity.ok(res);
    }

    @PostMapping("/toggle")
    public ResponseEntity<?> toggleEnabled(@RequestBody Map<String, Boolean> payload, HttpServletRequest request) {
        if (!validateBridgeAccess(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
        }

        Boolean enabled = payload.get("enabled");
        TelegramConfig config = pollerService.getConfig();
        config.setIsEnabled(Boolean.TRUE.equals(enabled));
        configRepo.save(config);

        if (Boolean.TRUE.equals(enabled)) {
            pollerService.startPoller();
        } else {
            pollerService.stopPoller();
        }

        return ResponseEntity.ok(Map.of("isEnabled", config.getIsEnabled(), "isRunning", pollerService.isRunning()));
    }

    @PostMapping("/generate-challenge")
    public ResponseEntity<?> generateChallenge(@RequestBody(required = false) Map<String, Object> payload,
            HttpServletRequest request) {
        if (!validateBridgeAccess(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
        }

        Long targetUserId = null;
        if (payload != null && payload.get("targetUserId") != null) {
            try {
                String val = payload.get("targetUserId").toString().trim();
                if (!val.isBlank()) {
                    targetUserId = Long.valueOf(val);
                }
            } catch (Exception ignored) {
            }
        }

        String code = securityManager.generateLinkChallenge(targetUserId);
        return ResponseEntity.ok(Map.of(
                "challengeCode", code,
                "expiresInSeconds", 600,
                "targetUserId", targetUserId != null ? targetUserId.toString() : ""));
    }

    @GetMapping("/users")
    public ResponseEntity<?> listUsers(HttpServletRequest request) {
        if (!validateBridgeAccess(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
        }

        List<TelegramAuthorizedUser> users = securityManager.listAuthorizedUsers();
        return ResponseEntity.ok(users);
    }

    @PostMapping("/approve-user")
    public ResponseEntity<?> approveUser(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        if (!validateBridgeAccess(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
        }

        Object uidObj = payload.get("userId");
        if (uidObj != null) {
            Long userId = Long.valueOf(uidObj.toString());
            securityManager.approveUser(userId);
            return ResponseEntity.ok(Map.of("success", true, "userId", userId, "status", "ACTIVE"));
        }
        return ResponseEntity.badRequest().body(Map.of("error", "userId required"));
    }

    @PostMapping("/send-test-message")
    public ResponseEntity<?> sendTestMessage(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        if (!validateBridgeAccess(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
        }

        String token = pollerService.getInMemoryBotToken();
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Bot token is not active in memory."));
        }

        Object chatIdObj = payload.get("chatId");
        if (chatIdObj == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "chatId required"));
        }

        Long chatId = Long.valueOf(chatIdObj.toString());
        String text = payload.get("text") != null ? payload.get("text").toString()
                : "🧪 *RupeeCRM Diagnostic Test Message*\nBot communication is fully operational!";

        try {
            JsonNode res = apiClient.sendMessage(token, chatId, text, "Markdown", null);
            pollerService.recordSentDiagnostic();
            if (res.path("ok").asBoolean()) {
                return ResponseEntity
                        .ok(Map.of("success", true, "messageId", res.path("result").path("message_id").asLong()));
            } else {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", res.path("description").asText("Telegram send failed")));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/revoke-user")
    public ResponseEntity<?> revokeUser(@RequestBody Map<String, Long> payload, HttpServletRequest request) {
        if (!validateBridgeAccess(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
        }

        Long userId = payload.get("userId");
        if (userId != null) {
            securityManager.revokeUser(userId);
            return ResponseEntity.ok(Map.of("success", true, "userId", userId));
        }
        return ResponseEntity.badRequest().body(Map.of("error", "userId required"));
    }

    @PostMapping("/resume-poller")
    public ResponseEntity<?> resumePoller(HttpServletRequest request) {
        if (!validateBridgeAccess(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
        }

        pollerService.resumeCircuitBreaker();
        return ResponseEntity.ok(Map.of("success", true, "isRunning", pollerService.isRunning()));
    }

    @PostMapping("/disconnect")
    public ResponseEntity<?> disconnectBot(HttpServletRequest request) {
        if (!validateBridgeAccess(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
        }

        pollerService.disconnectBot();
        return ResponseEntity.ok(Map.of("success", true, "disconnected", true));
    }
}
