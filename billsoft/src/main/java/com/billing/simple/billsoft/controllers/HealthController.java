package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.service.SystemMetricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Health check and system diagnostic metrics endpoint.
 */
@RestController
public class HealthController {

    private final SystemMetricsService systemMetricsService;

    public HealthController(SystemMetricsService systemMetricsService) {
        this.systemMetricsService = systemMetricsService;
    }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "timestamp", System.currentTimeMillis()
        ));
    }

    @GetMapping("/api/health/metrics")
    public ResponseEntity<Map<String, Object>> metrics() {
        return ResponseEntity.ok(systemMetricsService.getMetricsSnapshot());
    }
}
