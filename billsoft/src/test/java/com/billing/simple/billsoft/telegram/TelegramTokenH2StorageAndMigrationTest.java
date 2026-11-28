package com.billing.simple.billsoft.telegram;

import com.billing.simple.billsoft.controllers.AppConfigController;
import com.billing.simple.billsoft.controllers.AuthController;
import com.billing.simple.billsoft.entities.AppConfig;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import com.billing.simple.billsoft.service.BackupService;
import com.billing.simple.billsoft.telegram.client.TelegramApiClient;
import com.billing.simple.billsoft.telegram.entities.TelegramConfig;
import com.billing.simple.billsoft.telegram.repo.TelegramConfigRepository;
import com.billing.simple.billsoft.telegram.repo.TelegramUpdateInboxRepository;
import com.billing.simple.billsoft.telegram.service.TelegramDeliveryService;
import com.billing.simple.billsoft.telegram.service.TelegramPollerService;
import com.billing.simple.billsoft.telegram.service.TelegramUpdateDispatcher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TelegramTokenH2StorageAndMigrationTest {

    @Mock
    private TelegramConfigRepository configRepo;

    @Mock
    private TelegramUpdateInboxRepository inboxRepo;

    @Mock
    private TelegramApiClient apiClient;

    @Mock
    private TelegramUpdateDispatcher dispatcher;

    @Mock
    private TelegramDeliveryService deliveryService;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private AppConfigRepository appConfigRepo;

    @Mock
    private AuthController authController;

    private ObjectMapper objectMapper = new ObjectMapper();
    private TelegramPollerService pollerService;
    private TelegramConfig config;

    @BeforeEach
    void setUp() {
        config = new TelegramConfig();
        config.setId(1L);
        config.setInstallationId("TEST-INST-TOKEN");
        config.setIsEnabled(true);
        config.setLastPollingOffset(0L);
        config.setPollingTimeoutSeconds(30);
        config.setCircuitBreakerTripped(false);
        config.setConflictCount(0);

        lenient().when(configRepo.findById(1L)).thenReturn(Optional.of(config));
        lenient().when(configRepo.save(any(TelegramConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));

        pollerService = new TelegramPollerService(
                configRepo, inboxRepo, apiClient, dispatcher, deliveryService, transactionManager, objectMapper, appConfigRepo
        );
    }

    @Test
    @DisplayName("Persist Telegram token to H2 database and verify it can be read")
    void testSetBotTokenAndPersist_StoresInH2() {
        String testToken = "123456789:ABCdefGHIjklMNOpqrSTUvwxyz12345";
        when(appConfigRepo.findById(TelegramPollerService.TELEGRAM_BOT_TOKEN_KEY)).thenReturn(Optional.empty());

        pollerService.setBotTokenAndPersist(testToken);

        ArgumentCaptor<AppConfig> captor = ArgumentCaptor.forClass(AppConfig.class);
        verify(appConfigRepo).save(captor.capture());

        AppConfig saved = captor.getValue();
        assertEquals(TelegramPollerService.TELEGRAM_BOT_TOKEN_KEY, saved.getConfigKey());
        assertEquals(testToken, saved.getConfigValue());
        assertEquals(testToken, pollerService.getInMemoryBotToken());
        assertTrue(pollerService.hasStoredToken());

        // Verify masked token never exposes full secret
        String masked = pollerService.getMaskedToken();
        assertTrue(masked.startsWith("123456789:"));
        assertTrue(masked.contains("••••••••"));
        assertFalse(masked.contains("GHIjklMNOpqrSTU"));
    }

    @Test
    @DisplayName("Application restart automatically loads token from H2 and starts poller")
    void testInitFromDatabase_LoadsTokenAndStartsPoller() {
        String testToken = "987654321:ZYXcbaWVUtmrqponmlkji123456789";
        AppConfig storedConfig = new AppConfig(TelegramPollerService.TELEGRAM_BOT_TOKEN_KEY, testToken);
        when(appConfigRepo.findById(TelegramPollerService.TELEGRAM_BOT_TOKEN_KEY)).thenReturn(Optional.of(storedConfig));

        pollerService.initFromDatabase();

        assertEquals(testToken, pollerService.getInMemoryBotToken());
        assertTrue(pollerService.hasStoredToken());
        assertTrue(pollerService.isRunning(), "Poller must start automatically when token is in DB and isEnabled is true");
    }

    @Test
    @DisplayName("Malformed token in database is safely rejected without starting poller")
    void testInitFromDatabase_MalformedToken_Rejected() {
        AppConfig malformedConfig = new AppConfig(TelegramPollerService.TELEGRAM_BOT_TOKEN_KEY, "invalid-token-without-colon");
        when(appConfigRepo.findById(TelegramPollerService.TELEGRAM_BOT_TOKEN_KEY)).thenReturn(Optional.of(malformedConfig));

        pollerService.initFromDatabase();

        assertNull(pollerService.getInMemoryBotToken());
        assertFalse(pollerService.isRunning());
    }

    @Test
    @DisplayName("Disconnect removes token from H2 database and stops poller")
    void testDisconnectBot_RemovesTokenFromH2() {
        when(appConfigRepo.existsById(TelegramPollerService.TELEGRAM_BOT_TOKEN_KEY)).thenReturn(true);

        pollerService.disconnectBot();

        verify(appConfigRepo).deleteById(TelegramPollerService.TELEGRAM_BOT_TOKEN_KEY);
        assertNull(pollerService.getInMemoryBotToken());
        assertFalse(pollerService.hasStoredToken());
        assertFalse(pollerService.isRunning());
        assertFalse(config.getIsEnabled());
        assertNull(config.getBotUsername());
    }

    @Test
    @DisplayName("Empty or blank token throws IllegalArgumentException and never overwrites DB")
    void testInvalidToken_ThrowsAndDoesNotOverwrite() {
        assertThrows(IllegalArgumentException.class, () -> pollerService.setBotTokenAndPersist(""));
        assertThrows(IllegalArgumentException.class, () -> pollerService.setBotTokenAndPersist("   "));
        assertThrows(IllegalArgumentException.class, () -> pollerService.setBotTokenAndPersist(null));

        verify(appConfigRepo, never()).save(any());
    }

    @Test
    @DisplayName("Error messages are sanitized so raw tokens are never recorded in logs or database")
    void testSanitizeErrorMessage() {
        String rawError = "Request failed for https://api.telegram.org/bot123456789:ABCdefGHIjklMNOpqrSTUvwxyz12345/getUpdates: timed out";
        String sanitized = TelegramPollerService.sanitizeErrorMessage(rawError);

        assertNotNull(sanitized);
        assertFalse(sanitized.contains("123456789:ABCdefGHIjklMNOpqrSTUvwxyz12345"));
        assertTrue(sanitized.contains("[PROTECTED_BOT_TOKEN]"));
    }

    @Test
    @DisplayName("AppConfigController blocks TELEGRAM_BOT_TOKEN from frontend access with 403 Forbidden")
    void testAppConfigControllerBlocksTelegramToken() {
        AppConfigController controller = new AppConfigController(appConfigRepo, authController);

        ResponseEntity<Map<String, Object>> response = controller.getConfig("TELEGRAM_BOT_TOKEN");
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().get("error").toString().contains("prohibited"));
    }

    @Test
    @DisplayName("BackupService permits TELEGRAM_BOT_TOKEN in backups while blocking passwords and secrets")
    void testBackupServiceAllowsTelegramBotToken() {
        assertFalse(BackupService.isSensitiveConfigKey("TELEGRAM_BOT_TOKEN"),
                "TELEGRAM_BOT_TOKEN must NOT be filtered out from database backups");

        // Verify other credentials remain blocked
        assertTrue(BackupService.isSensitiveConfigKey("GLOBAL_PASSWORD"));
        assertTrue(BackupService.isSensitiveConfigKey("BACKUP_CLOUD_SECRET"));
        assertTrue(BackupService.isSensitiveConfigKey("EMPLOYEE_MODULE_PIN_1"));
    }
}
