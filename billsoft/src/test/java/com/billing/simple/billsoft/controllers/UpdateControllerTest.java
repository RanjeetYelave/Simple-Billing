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
        when(service.checkUpdate()).thenReturn(response);

        mockMvc.perform(get("/api/system/update-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updateAvailable").value(true));
    }

    @Test
    void testApplyUpdate() throws Exception {
        when(service.applyUpdate()).thenReturn(true);

        mockMvc.perform(post("/api/system/apply-update"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }
}
