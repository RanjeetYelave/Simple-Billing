package com.billing.simple.billsoft.regression.system;

import com.billing.simple.billsoft.controllers.HealthController;
import com.billing.simple.billsoft.dtos.ApiDiagnosticsResponse;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;
import com.billing.simple.billsoft.service.ApiDiagnosticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Tag("regression")
@Tag("system")
@DisplayName("RupeeCRM Diagnostics & Telemetry: All APIs 200 OK Health-Check Suite")
public class AllApiEndpointsHealthCheckTest {

    @Autowired
    private ApiDiagnosticsService apiDiagnosticsService;

    @Autowired
    private HealthController healthController;

    @Autowired
    private FirmDetailsRepository firmRepo;

    private Long activeFirmId;

    @BeforeEach
    void setUp() {
        FirmDetails firm = firmRepo.findAll().stream().findFirst().orElseGet(() -> {
            FirmDetails f = new FirmDetails();
            f.setFirmName("RupeeCRM Telemetry Test Firm");
            f.setAddressLine1("Tech Hub");
            f.setPhone("9876543210");
            f.setEmail("telemetry@rupeecrm.com");
            return firmRepo.save(f);
        });
        this.activeFirmId = firm.getId();
    }

    @Test
    @DisplayName("Should execute Full API Diagnostic Suite and achieve 100% 200 OK status across all endpoints")
    void testAllApiEndpointsReturn200Ok() {
        ApiDiagnosticsResponse response = apiDiagnosticsService.runFullApiSuite(activeFirmId);

        assertThat(response).isNotNull();
        assertThat(response.getOverallStatus()).isEqualTo("HEALTHY");
        assertThat(response.getFailedEndpoints()).isEqualTo(0);
        assertThat(response.getPassedEndpoints()).isGreaterThanOrEqualTo(50);
        assertThat(response.getTotalEndpoints()).isEqualTo(response.getPassedEndpoints());
        assertThat(response.getPassRatePercent()).isEqualTo(100.0);

        // Verify each endpoint
        for (ApiDiagnosticsResponse.EndpointResult result : response.getResults()) {
            assertThat(result.getStatusCode())
                    .withFailMessage("Endpoint %s %s failed with code %d: %s",
                            result.getMethod(), result.getEndpoint(), result.getStatusCode(), result.getMessage())
                    .isEqualTo(200);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).isEqualTo("HTTP 200 OK");
            assertThat(result.getLatencyMs()).isGreaterThanOrEqualTo(0);
        }

        // Verify category groupings
        assertThat(response.getCategorySummaries()).isNotEmpty();
        for (Map.Entry<String, ApiDiagnosticsResponse.CategorySummary> entry : response.getCategorySummaries().entrySet()) {
            ApiDiagnosticsResponse.CategorySummary cat = entry.getValue();
            assertThat(cat.getFailed()).isEqualTo(0);
            assertThat(cat.getPassed()).isEqualTo(cat.getTotal());
        }
    }

    @Test
    @DisplayName("Should expose /api/diagnostics/api-suite via HealthController with HTTP 200")
    void testHealthControllerApiSuiteEndpoint() {
        ResponseEntity<ApiDiagnosticsResponse> responseEntity = healthController.runApiSuite(activeFirmId);

        assertThat(responseEntity.getStatusCode().value()).isEqualTo(200);
        ApiDiagnosticsResponse body = responseEntity.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getOverallStatus()).isEqualTo("HEALTHY");
        assertThat(body.getFailedEndpoints()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should include API diagnostics capability in /api/health/diagnostics probe")
    void testHealthDiagnosticsProbeIncludesApiDiagnostics() {
        ResponseEntity<Map<String, Object>> responseEntity = healthController.diagnostics();

        assertThat(responseEntity.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> body = responseEntity.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo("UP");
        assertThat(body.get("apiDiagnostics")).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> diag = (Map<String, Object>) body.get("apiDiagnostics");
        assertThat(diag.get("status")).isEqualTo("ARMED");
        assertThat(diag.get("endpoint")).isEqualTo("/api/diagnostics/api-suite");
    }
}
