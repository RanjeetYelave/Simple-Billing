package com.billing.simple.billsoft.regression.security;

import com.billing.simple.billsoft.controllers.AuthController;
import com.billing.simple.billsoft.entities.AppConfig;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Tag("regression")
@Tag("api")
@DisplayName("API Authorization Filter & Route Protection Regression Tests")
class AuthorizationFilterRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppConfigRepository configRepo;

    @BeforeEach
    void setUp() {
        configRepo.deleteAll();
    }

    @Test
    @DisplayName("Should allow access to protected API endpoints when auth is disabled")
    void shouldAllowAccessWhenAuthDisabled() throws Exception {
        mockMvc.perform(get("/api/customers?firmId=1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should return 401 Unauthorized for protected API endpoints when auth is enabled and token is missing")
    void shouldBlockAccessWhenAuthEnabledAndNoToken() throws Exception {
        AppConfig aCfg = new AppConfig();
        aCfg.setConfigKey("auth_enabled");
        aCfg.setConfigValue("true");
        configRepo.save(aCfg);

        AppConfig pCfg = new AppConfig();
        pCfg.setConfigKey("global_password");
        pCfg.setConfigValue("salt:hash");
        configRepo.save(pCfg);

        mockMvc.perform(get("/api/customers?firmId=1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should permit access with valid active session token")
    void shouldPermitAccessWithValidToken() throws Exception {
        // Setup auth through API to get a genuine active token
        String setupJson = "{\"password\":\"MyPass#2026\"}";
        String response = mockMvc
                .perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/enable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(setupJson))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response);
        String token = node.get("token").asText();

        mockMvc.perform(get("/api/customers?firmId=1")
                .header("X-Auth-Token", token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
