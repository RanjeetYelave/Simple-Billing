package com.billing.simple.billsoft.kpi;

import com.billing.simple.billsoft.kpi.dto.CustomerKpiSummary;
import com.billing.simple.billsoft.kpi.dto.KpiDashboardResponse;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller exposing isolated read-only KPI endpoints for Dashboard and CustomerManager.
 */
@RestController
@RequestMapping("/api/kpis")
@CrossOrigin
public class KpiController {

    private final KpiService kpiService;

    public KpiController(KpiService kpiService) {
        this.kpiService = kpiService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<KpiDashboardResponse> getDashboardKpis() {
        Long authoritativeFirmId = TenantContext.getCurrentFirmId();
        if (authoritativeFirmId == null) {
            throw new TenantSecurityException("Active firm ID is required to access KPI dashboard.");
        }
        return ResponseEntity.ok(kpiService.getDashboardKpis(authoritativeFirmId));
    }

    @GetMapping("/customers")
    public ResponseEntity<List<CustomerKpiSummary>> getCustomerKpis() {
        Long authoritativeFirmId = TenantContext.getCurrentFirmId();
        if (authoritativeFirmId == null) {
            throw new TenantSecurityException("Active firm ID is required to access customer KPIs.");
        }
        return ResponseEntity.ok(kpiService.getCustomerKpiSummaries(authoritativeFirmId));
    }
}
