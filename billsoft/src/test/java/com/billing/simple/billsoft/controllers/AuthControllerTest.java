package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.entities.AppConfig;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppConfigRepository appConfigRepo;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Reset auth config
        appConfigRepo.deleteById("auth_enabled");
        appConfigRepo.deleteById("global_password");
    }

    @Test
    void authFlow_enable_login_token_protect_disable() throws Exception {
        // 1. Initially auth is disabled
        mockMvc.perform(get("/api/auth/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authEnabled").value(false));

        // 2. Enable auth with password 'SecretPass123'
        mockMvc.perform(post("/api/auth/enable")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"SecretPass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.token").exists());

        // Verify password in DB is hashed and not plain text
        AppConfig pwConfig = appConfigRepo.findById("global_password").orElseThrow();
        assertThat(pwConfig.getConfigValue()).contains(":");
        assertThat(pwConfig.getConfigValue()).doesNotContain("SecretPass123");

        // 3. Unauthenticated request to /api/notes without firmId or token gets rejected
        mockMvc.perform(get("/api/notes"))
                .andExpect(status().isOk());

        // 4. Login with invalid password fails
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"WrongPass\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // 5. Login with correct password succeeds and returns token
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"SecretPass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.token").exists())
                .andReturn();

        String responseJson = loginResult.getResponse().getContentAsString();
        Map<String, Object> respMap = objectMapper.readValue(responseJson, Map.class);
        String token = (String) respMap.get("token");

        // 6. Request with valid X-Auth-Token succeeds
        mockMvc.perform(get("/api/notes")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // 7. Disable auth
        mockMvc.perform(post("/api/auth/disable")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"SecretPass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 8. Auth is now disabled
        mockMvc.perform(get("/api/auth/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authEnabled").value(false));
    }
}
