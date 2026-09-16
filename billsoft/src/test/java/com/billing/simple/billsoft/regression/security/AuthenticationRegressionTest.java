package com.billing.simple.billsoft.regression.security;

import com.billing.simple.billsoft.controllers.AuthController;
import com.billing.simple.billsoft.entities.AppConfig;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import com.billing.simple.billsoft.util.PasswordUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Tag("regression")
@Tag("integration")
@DisplayName("Authentication & Password Security Regression Tests")
class AuthenticationRegressionTest {

    @Autowired
    private AuthController authController;

    @Autowired
    private AppConfigRepository configRepo;

    @BeforeEach
    void setUp() {
        configRepo.deleteAll();
    }

    @Test
    @DisplayName("Should report authEnabled false initially, then allow setup and login with salted hash")
    void shouldHandleFullAuthLifecycle() {
        // 1. Initial status: auth not enabled
        ResponseEntity<?> initialStatus = authController.status();
        assertThat(initialStatus.getBody()).isInstanceOf(Map.class);
        Map<?, ?> statusMap = (Map<?, ?>) initialStatus.getBody();
        assertThat(statusMap.get("authEnabled")).isEqualTo(false);

        // 2. Setup password
        Map<String, String> setupReq = Map.of("password", "SecurePass#2026");
        ResponseEntity<?> setupRes = authController.enableAuth(setupReq);
        assertThat(setupRes.getStatusCode().is2xxSuccessful()).isTrue();
        Map<?, ?> setupBody = (Map<?, ?>) setupRes.getBody();
        assertThat(setupBody.get("token")).isNotNull();

        // 3. Verify password hash is stored with salt:hash format
        AppConfig hashConfig = configRepo.findById("global_password").orElseThrow();
        assertThat(hashConfig.getConfigValue()).contains(":");

        // 4. Test Login Success
        Map<String, String> loginReq = Map.of("password", "SecurePass#2026");
        ResponseEntity<?> loginRes = authController.login(loginReq);
        assertThat(loginRes.getStatusCode().is2xxSuccessful()).isTrue();
        Map<?, ?> loginBody = (Map<?, ?>) loginRes.getBody();
        assertThat(loginBody.get("token")).isNotNull();

        // 5. Test Login Failure with wrong password
        Map<String, String> badLoginReq = Map.of("password", "WrongPassword");
        ResponseEntity<?> badLoginRes = authController.login(badLoginReq);
        assertThat(badLoginRes.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    @DisplayName("Should auto-upgrade legacy plain text password to salted hash on successful login")
    void shouldAutoUpgradeLegacyHashOnLogin() {
        String rawPassword = "LegacyPassword123";
        // Simulate legacy plain text password
        AppConfig pCfg = new AppConfig();
        pCfg.setConfigKey("global_password");
        pCfg.setConfigValue(rawPassword);
        configRepo.save(pCfg);

        AppConfig aCfg = new AppConfig();
        aCfg.setConfigKey("auth_enabled");
        aCfg.setConfigValue("true");
        configRepo.save(aCfg);

        // Login with correct password
        Map<String, String> loginReq = Map.of("password", rawPassword);
        ResponseEntity<?> loginRes = authController.login(loginReq);
        assertThat(loginRes.getStatusCode().is2xxSuccessful()).isTrue();

        // Verify stored password has been auto-upgraded to salted hash format
        AppConfig upgradedConfig = configRepo.findById("global_password").orElseThrow();
        assertThat(upgradedConfig.getConfigValue()).contains(":");
        assertThat(PasswordUtil.checkPassword(rawPassword, upgradedConfig.getConfigValue())).isTrue();
    }
}
