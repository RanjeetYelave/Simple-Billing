package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.entities.AppConfig;
import com.billing.simple.billsoft.licensing.LicenseCoordinator;
import com.billing.simple.billsoft.licensing.LicensingConfig;
import com.billing.simple.billsoft.licensing.model.CustomerMessage;
import com.billing.simple.billsoft.licensing.model.LicensePayload;
import com.billing.simple.billsoft.licensing.model.ValidationResult;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API for frontend UI integration with the licensing coordinator and trial engine.
 */
@RestController
@RequestMapping("/api/licensing")
@CrossOrigin
public class LicensingController {

    private final LicenseCoordinator coordinator;
    private final AppConfigRepository appConfigRepo;
    private final FirmDetailsRepository firmDetailsRepo;

    public LicensingController() {
        this(new LicenseCoordinator(), null, null);
    }

    public LicensingController(LicenseCoordinator coordinator) {
        this(coordinator, null, null);
    }

    @Autowired
    public LicensingController(LicenseCoordinator coordinator, AppConfigRepository appConfigRepo, FirmDetailsRepository firmDetailsRepo) {
        this.coordinator = coordinator != null ? coordinator : new LicenseCoordinator();
        this.appConfigRepo = appConfigRepo;
        this.firmDetailsRepo = firmDetailsRepo;
        // Trigger fast-path startup check on controller bean creation
        this.coordinator.validateOnStartup();
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> resp = new HashMap<>();
        String machineId = coordinator.getMachineIdentity().getMachineId();
        LicensePayload license = coordinator.getActiveLicense();
        ValidationResult validation = coordinator.getCurrentValidationResult();

        resp.put("machineId", machineId);
        resp.put("validationResult", validation.name());
        resp.put("validationDescription", validation.getDescription());
        resp.put("isValid", validation.isValid());

        boolean hasFirm = firmDetailsRepo != null && firmDetailsRepo.count() > 0;
        resp.put("hasFirm", hasFirm);

        long trialDaysRemaining = 30;
        boolean trialStarted = false;
        if (appConfigRepo != null) {
            String trialStartStr = appConfigRepo.findById("trial_start_date")
                    .map(AppConfig::getConfigValue)
                    .orElse(null);

            if (trialStartStr != null) {
                trialStarted = true;
                try {
                    LocalDate startDate = LocalDate.parse(trialStartStr);
                    LocalDate today = LocalDate.now();
                    long daysPassed = ChronoUnit.DAYS.between(startDate, today);
                    trialDaysRemaining = 30 - daysPassed;
                    if (trialDaysRemaining < 0) {
                        trialDaysRemaining = 0;
                    }
                } catch (Exception ignored) {
                }
            }
        }

        resp.put("trialDaysRemaining", trialDaysRemaining);
        resp.put("trialStarted", trialStarted);

        if (license != null) {
            resp.put("licenseId", license.getLicenseId());
            resp.put("customerName", license.getCustomerName());
            resp.put("product", license.getProduct());
            resp.put("edition", license.getEdition());
            resp.put("plan", license.getPlan() != null ? license.getPlan().name() : null);
            resp.put("status", license.getStatus() != null ? license.getStatus().name() : null);
            resp.put("revision", license.getRevision());
            resp.put("issuedAt", license.getIssuedAt());
            resp.put("expiresAt", license.getExpiresAt());
            resp.put("statusReason", license.getStatusReason());

            boolean expired = coordinator.getLicenseVerifier().isExpired(license, Instant.now());
            resp.put("isExpired", expired);

            Long daysRemaining = coordinator.getLicenseDaysRemaining();
            resp.put("daysRemaining", daysRemaining);
            resp.put("isTrial", false);

            // Data Protection fields
            boolean dpEnabled = Boolean.TRUE.equals(license.getDataProtectionEnabled());
            boolean dpActive = coordinator.isDataProtectionActive();
            Long dpDaysRemaining = coordinator.getDataProtectionDaysRemaining();
            resp.put("dataProtectionEnabled", dpEnabled);
            resp.put("dataProtectionExpiresAt", license.getDataProtectionExpiresAt());
            resp.put("dataProtectionDaysRemaining", dpDaysRemaining);
            resp.put("dpDaysRemaining", dpDaysRemaining);
            resp.put("dataProtectionActive", dpActive);
            resp.put("isDataProtectionActive", dpActive);

            // Snooze state
            boolean licenseSnoozed = coordinator.getSnoozeManager().isLicenseSnoozed(license.getRevision());
            boolean dpSnoozed = coordinator.getSnoozeManager().isDataProtectionSnoozed(license.getRevision());
            resp.put("licenseSnoozed", licenseSnoozed);
            resp.put("licenseSnoozedUntil", coordinator.getSnoozeManager().getCurrentState().getLicenseSnoozedUntil());
            resp.put("dpSnoozed", dpSnoozed);
            resp.put("dpSnoozedUntil", coordinator.getSnoozeManager().getCurrentState().getDpSnoozedUntil());
            resp.put("dpPermanentlySnoozed", coordinator.getSnoozeManager().getCurrentState().isDpPermanentlySnoozed());

            // Popup trigger flags for frontend
            boolean showLicensePopup = !licenseSnoozed && daysRemaining != null && daysRemaining <= 7 && daysRemaining >= 0;
            boolean showDpPopup = dpEnabled && !dpSnoozed && dpDaysRemaining != null && dpDaysRemaining <= 7 && dpDaysRemaining >= 0;
            resp.put("showLicensePopup", showLicensePopup);
            resp.put("showDpPopup", showDpPopup);
        } else {
            resp.put("plan", null);
            resp.put("status", null);
            resp.put("statusReason", null);
            resp.put("isExpired", false);
            resp.put("daysRemaining", null);
            resp.put("isTrial", true);
            resp.put("dataProtectionEnabled", false);
            resp.put("dataProtectionExpiresAt", null);
            resp.put("dataProtectionDaysRemaining", null);
            resp.put("dpDaysRemaining", null);
            resp.put("dataProtectionActive", false);
            resp.put("isDataProtectionActive", false);
            resp.put("licenseSnoozed", false);
            resp.put("dpSnoozed", false);
            resp.put("showLicensePopup", false);
            resp.put("showDpPopup", false);
        }

        List<CustomerMessage> msgs = coordinator.getLicenseStorage().loadInboxMessages();
        long unreadCount = msgs.stream().filter(m -> !m.isRead()).count();
        resp.put("inboxUnreadCount", unreadCount);
        if (!msgs.isEmpty()) {
            CustomerMessage latestMsg = msgs.get(msgs.size() - 1);
            resp.put("latestMessageTitle", latestMsg.getTitle());
            resp.put("latestMessageBody", latestMsg.getBody());
            resp.put("latestMessageDate", latestMsg.getCreatedAt());
        } else {
            resp.put("latestMessageTitle", null);
            resp.put("latestMessageBody", null);
            resp.put("latestMessageDate", null);
        }

        return ResponseEntity.ok(resp);
    }

    @PostMapping("/snooze/license")
    public ResponseEntity<Map<String, Object>> snoozeLicense(@RequestBody Map<String, String> body) {
        String duration = body != null ? body.get("duration") : "1d";
        Instant until = coordinator.getSnoozeManager().snoozeLicense(duration, coordinator.getActiveLicense());
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("snoozedUntil", until);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/snooze/dataprotection")
    public ResponseEntity<Map<String, Object>> snoozeDataProtection(@RequestBody Map<String, String> body) {
        String duration = body != null ? body.get("duration") : "1d";
        Instant until = coordinator.getSnoozeManager().snoozeDataProtection(duration, coordinator.getActiveLicense());
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("snoozedUntil", until);
        resp.put("permanent", "permanent".equalsIgnoreCase(duration));
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/qr")
    public ResponseEntity<Map<String, Object>> getQrData() {
        Map<String, Object> qr = new HashMap<>();
        qr.put("app", LicensingConfig.PRODUCT_NAME);
        qr.put("ver", LicensingConfig.QR_VERSION);
        qr.put("mid", coordinator.getMachineIdentity().getMachineId());
        return ResponseEntity.ok(qr);
    }

    @PostMapping("/check-online")
    public ResponseEntity<Map<String, Object>> checkOnline() {
        coordinator.syncWithRegistry(true);
        return getStatus();
    }

    @PostMapping("/init-trial")
    public ResponseEntity<Map<String, Object>> initTrial() {
        if (appConfigRepo != null) {
            AppConfig startConfig = appConfigRepo.findById("trial_start_date").orElse(new AppConfig());
            if (startConfig.getConfigValue() == null) {
                startConfig.setConfigKey("trial_start_date");
                startConfig.setConfigValue(LocalDate.now().toString());
                appConfigRepo.save(startConfig);
            }

            AppConfig statusConfig = appConfigRepo.findById("license_status").orElse(new AppConfig());
            if (statusConfig.getConfigValue() == null) {
                statusConfig.setConfigKey("license_status");
                statusConfig.setConfigValue("trial");
                appConfigRepo.save(statusConfig);
            }
        }
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("trialDaysRemaining", 30);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/activate")
    public ResponseEntity<Map<String, Object>> activate(@RequestBody LicensePayload payload) {
        Map<String, Object> resp = new HashMap<>();
        ValidationResult result = coordinator.activateLicense(payload);
        resp.put("result", result.name());
        resp.put("description", result.getDescription());
        resp.put("success", result.isValid());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/messages")
    public ResponseEntity<List<CustomerMessage>> getMessages() {
        return ResponseEntity.ok(coordinator.getLicenseStorage().loadInboxMessages());
    }

    @PostMapping("/messages/{messageId}/read")
    public ResponseEntity<Map<String, Object>> markMessageRead(@PathVariable String messageId) {
        String machineId = coordinator.getMachineIdentity().getMachineId();
        List<CustomerMessage> msgs = coordinator.getLicenseStorage().loadInboxMessages();
        for (CustomerMessage msg : msgs) {
            if (messageId.equalsIgnoreCase(msg.getMessageId())) {
                msg.setRead(true);
            }
        }
        coordinator.getLicenseStorage().saveInboxMessages(machineId, msgs);
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        return ResponseEntity.ok(resp);
    }
}
