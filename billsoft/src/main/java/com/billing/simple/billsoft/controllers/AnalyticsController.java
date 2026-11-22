package com.billing.simple.billsoft.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.billing.simple.billsoft.dtos.FirmAnalyticsResponse;
import com.billing.simple.billsoft.service.InvoiceService;

@RestController
@RequestMapping("/api/analytics")
@CrossOrigin
public class AnalyticsController {

    private final InvoiceService invoiceService;

    public AnalyticsController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping("/firm")
    public ResponseEntity<FirmAnalyticsResponse> getFirmAnalytics(
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(required = false) Long firmId) {
        Long authoritativeFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        Long clientFirmId = firmIdHeader != null ? firmIdHeader : firmId;
        if (authoritativeFirmId != null && clientFirmId != null && !authoritativeFirmId.equals(clientFirmId)) {
            throw new com.billing.simple.billsoft.security.TenantSecurityException("Cross-firm access prohibited");
        }
        Long target = authoritativeFirmId != null ? authoritativeFirmId : clientFirmId;
        if (target == null) {
            throw new com.billing.simple.billsoft.security.TenantSecurityException("Active firm context is required to generate firm analytics.");
        }
        return ResponseEntity.ok(invoiceService.getFirmAnalytics(target));
    }
}
