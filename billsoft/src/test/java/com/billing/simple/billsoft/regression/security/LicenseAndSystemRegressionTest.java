package com.billing.simple.billsoft.regression.security;

import com.billing.simple.billsoft.entities.AppConfig;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import com.billing.simple.billsoft.service.BackupService;
import com.billing.simple.billsoft.service.FirmDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Tag("regression")
@Tag("integration")
@DisplayName("License, System, Analytics & Developer Log Regression Tests")
class LicenseAndSystemRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppConfigRepository configRepo;

    @Autowired
    private FirmDetailsService firmService;

    @Autowired
    private BackupService backupService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        backupService.factoryReset();

        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Apex Enterprise Solutions");
        firm.setGstin("27AAACA1234E1Z1");
        firm.setAddressLine1("Highway Hub, Mumbai");
        firmService.create(firm);
    }

    @Test
    @DisplayName("Should query license status, trial days, and validate machine info")
    void testLicenseStatusAndTrial() throws Exception {
        mockMvc.perform(get("/api/license/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasFirm").value(true))
                .andExpect(jsonPath("$.status").exists());

        // Update license to active
        AppConfig statusCfg = new AppConfig();
        statusCfg.setConfigKey("license_status");
        statusCfg.setConfigValue("active");
        configRepo.save(statusCfg);

        mockMvc.perform(get("/api/license/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("active"));
    }

    @Test
    @DisplayName("Should query firm analytics dashboard data")
    void testFirmAnalytics() throws Exception {
        mockMvc.perform(get("/api/analytics/firm?firmId=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBusiness").exists())
                .andExpect(jsonPath("$.totalPaid").exists());
    }

    @Test
    @DisplayName("Should manage developer logging status and export logs")
    void testDevLogManagement() throws Exception {
        // 1. Get status
        mockMvc.perform(get("/api/system/dev-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").exists());

        // 2. Enable logging
        mockMvc.perform(post("/api/system/dev-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        // 3. Export logs
        mockMvc.perform(get("/api/system/dev-logs/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/plain")));
    }

    @Test
    @DisplayName("Should submit immediate rollback request marker")
    void testRollbackNow() throws Exception {
        mockMvc.perform(post("/api/system/rollbackNow"))
                .andExpect(status().isOk());
    }
}
