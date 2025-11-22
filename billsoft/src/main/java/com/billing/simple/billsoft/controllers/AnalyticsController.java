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
    public ResponseEntity<FirmAnalyticsResponse> getFirmAnalytics() {
        return ResponseEntity.ok(invoiceService.getFirmAnalytics());
    }
}
