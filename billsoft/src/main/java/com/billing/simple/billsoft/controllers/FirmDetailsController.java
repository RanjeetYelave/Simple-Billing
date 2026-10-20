package com.billing.simple.billsoft.controllers;

import java.util.List;
import java.util.Map;

import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.billing.simple.billsoft.dtos.PrintPreferencesRequest;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.service.FirmDetailsService;

@RestController
@RequestMapping("/api/firm")
@CrossOrigin
public class FirmDetailsController {

    private final FirmDetailsService service;
    private final AuthController authController;

    public FirmDetailsController(FirmDetailsService service, AuthController authController) {
        this.service = service;
        this.authController = authController;
    }

    @GetMapping
    public List<FirmDetails> list() {
        return service.list();
    }

    @PostMapping
    public ResponseEntity<FirmDetails> create(@RequestBody(required = false) FirmDetails details) {
        // First-run bootstrap exception: zero firms exist in database
        if (service.count() == 0) {
            if (details != null) {
                return ResponseEntity.ok(service.create(details));
            }
            return ResponseEntity.ok(service.create());
        }

        // Once firms exist, creating subsequent firms is supported for authenticated sessions
        if (details != null) {
            return ResponseEntity.ok(service.create(details));
        }
        return ResponseEntity.ok(service.create());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FirmDetails> get(@PathVariable Long id) {
        Long currentFirmId = TenantContext.getCurrentFirmId();
        if (currentFirmId != null && !currentFirmId.equals(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        FirmDetails f = service.get(id);
        return f == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(f);
    }

    @PutMapping("/{id}")
    public ResponseEntity<FirmDetails> update(@PathVariable Long id, @RequestBody FirmDetails details) {
        Long currentFirmId = TenantContext.getCurrentFirmId();
        if (currentFirmId != null && !currentFirmId.equals(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(service.update(id, details));
    }

    @PatchMapping("/{id}/print-preferences")
    public ResponseEntity<FirmDetails> updatePrintPreferences(
            @PathVariable Long id,
            @RequestBody(required = false) PrintPreferencesRequest req) {
        if (req == null) {
            return ResponseEntity.badRequest().build();
        }
        Long currentFirmId = TenantContext.getCurrentFirmId();
        if (currentFirmId != null && !currentFirmId.equals(id)) {
            throw new TenantSecurityException("Cross-firm print preferences update prohibited");
        }
        FirmDetails updated = service.updatePrintPreferences(id, req.getTheme(), req.getColor(), req.getFormat());
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, HttpServletRequest request) {
        Long currentFirmId = TenantContext.getCurrentFirmId();
        if (currentFirmId != null && !currentFirmId.equals(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Cross-firm deletion prohibited"));
        }
        if (service.count() <= 1) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot delete the only configured firm"));
        }

        String masterPassword = request.getHeader("X-Master-Password");
        if (masterPassword == null || masterPassword.isBlank()) {
            masterPassword = request.getParameter("masterPassword");
        }
        if (authController.isAuthEnabled() && !authController.verifyMasterPassword(masterPassword)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Firm deletion requires master password authorization"));
        }

        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
