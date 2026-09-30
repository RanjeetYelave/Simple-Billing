package com.billing.simple.billsoft.regression.circuit;

import com.billing.simple.billsoft.dtos.ApiDiagnosticsResponse;
import com.billing.simple.billsoft.entities.AppConfig;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import com.billing.simple.billsoft.service.ApiDiagnosticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Diagnostics integration test for Circuit Connect.
 * Validates non-critical reporting and read-only non-mutating invariant.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class CircuitConnectDiagnosticsTest {

    @Autowired
    private ApiDiagnosticsService diagnosticsService;

    @Autowired
    private AppConfigRepository appConfigRepo;

    @Test
    @DisplayName("Diagnostics suite includes Circuit Connect and reports HEALTHY overall")
    public void testDiagnosticsIncludesCircuitConnect() {
        ApiDiagnosticsResponse response = diagnosticsService.runFullApiSuite(null);
        assertNotNull(response);
        assertEquals("HEALTHY", response.getOverallStatus(), "Overall diagnostics status must be HEALTHY");

        boolean hasCircuitEndpoint = response.getResults().stream()
                .anyMatch(ep -> ep.getName().contains("Circuit Connect") || (ep.getEndpoint() != null && ep.getEndpoint().contains("CIRCUIT_CONNECT_STATE")));

        assertTrue(hasCircuitEndpoint, "Circuit Connect state check must be included in diagnostics suite");
    }

    @Test
    @DisplayName("Diagnostics execution NEVER mutates player stars, level, or progress")
    public void testDiagnosticsNeverMutatesUserProgress() {
        String pristineState = "{\"schemaVersion\":1,\"currentLevel\":77,\"totalStars\":215,\"completedCount\":76}";
        appConfigRepo.save(new AppConfig("CIRCUIT_CONNECT_STATE", pristineState));

        // Run diagnostics 5 consecutive times
        for (int i = 0; i < 5; i++) {
            diagnosticsService.runFullApiSuite(null);
        }

        AppConfig after = appConfigRepo.findById("CIRCUIT_CONNECT_STATE").orElse(null);
        assertNotNull(after);
        assertEquals(pristineState, after.getConfigValue(), "Game state must remain completely untouched by diagnostics");
    }
}
