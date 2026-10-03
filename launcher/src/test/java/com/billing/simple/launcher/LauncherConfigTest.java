package com.billing.simple.launcher;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class LauncherConfigTest {

    @Test
    void testTargetUrlAndPortConfiguration() {
        assertEquals(28080, LauncherMain.PORT, "Port must be set to 28080");
        assertEquals("http://localhost:28080/", LauncherMain.APP_URL, "APP_URL must point to localhost:28080");
        assertEquals("http://127.0.0.1:28080/api/health", LauncherMain.HEALTH_URL, "HEALTH_URL must point to loopback 127.0.0.1:28080");
    }

    @Test
    void testStartupNotificationContent() {
        assertEquals("RupeeCRM", LauncherMain.STARTUP_NOTIF_TITLE);
        assertEquals("App started successfully\nStatus: Healthy\nReady to use", LauncherMain.STARTUP_NOTIF_MESSAGE);
    }

    @Test
    void testReadinessProbeWhenUnreachable() {
        assertFalse(LauncherMain.isBackendHealthy("http://127.0.0.1:59999/api/health", 50), "Should report false when probing unreachable port");
    }
}
