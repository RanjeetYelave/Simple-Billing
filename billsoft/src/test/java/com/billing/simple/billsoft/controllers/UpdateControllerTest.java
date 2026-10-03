package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.service.UpdateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@AutoConfigureMockMvc
class UpdateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UpdateService service;

    @Test
    void testCheckUpdate() throws Exception {
        Map<String, Object> response = new HashMap<>();
        response.put("updateAvailable", true);
        when(service.checkUpdate(false)).thenReturn(response);

        mockMvc.perform(get("/api/system/update-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updateAvailable").value(true));
    }

    @Test
    void testCheckUpdateForceRefresh() throws Exception {
        Map<String, Object> response = new HashMap<>();
        response.put("updateAvailable", true);
        response.put("latestVersion", "v1.2.0");
        when(service.checkUpdate(true)).thenReturn(response);

        mockMvc.perform(get("/api/system/update-status?force=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updateAvailable").value(true))
                .andExpect(jsonPath("$.latestVersion").value("v1.2.0"));
    }

    @Test
    void testApplyUpdate() throws Exception {
        when(service.startUpdateAsync()).thenReturn(true);

        mockMvc.perform(post("/api/system/apply-update"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }
}
