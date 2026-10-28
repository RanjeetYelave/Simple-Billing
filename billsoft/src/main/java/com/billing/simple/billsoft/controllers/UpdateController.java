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
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "Cannot start update: another update transaction is in progress or no verified update was found"
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
}
