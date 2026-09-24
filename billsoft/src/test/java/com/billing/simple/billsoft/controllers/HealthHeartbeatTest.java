package com.billing.simple.billsoft.controllers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@AutoConfigureMockMvc
public class HealthHeartbeatTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/system/heartbeat returns status UP, metrics, backup, diagnostics, and messages")
    void testSystemHeartbeatEndpoint() throws Exception {
        mockMvc.perform(get("/api/system/heartbeat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.timestamp").isNumber())
                .andExpect(jsonPath("$.metrics").isMap())
                .andExpect(jsonPath("$.metrics.ramUsedMb").isNumber())
                .andExpect(jsonPath("$.backup").isMap())
                .andExpect(jsonPath("$.diagnostics").isMap())
                .andExpect(jsonPath("$.diagnostics.database.status").value("ONLINE"))
                .andExpect(jsonPath("$.messages").isArray());
    }

    @Test
    @DisplayName("GET /api/system/heartbeat?firmId=100 returns firm-specific messages")
    void testSystemHeartbeatWithFirmId() throws Exception {
        mockMvc.perform(get("/api/system/heartbeat").param("firmId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.messages").isArray());
    }
}
