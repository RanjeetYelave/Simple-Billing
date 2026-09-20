package com.billing.simple.launcher;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class LauncherConfigTest {

    @Test
    void testTargetUrlAndPortConfiguration() {
        assertEquals(28080, LauncherMain.PORT, "Port must be set to 28080");
        assertEquals("http://management.rupeecrm.local:28080/", LauncherMain.APP_URL, "APP_URL must point to management.rupeecrm.local:28080");
        assertEquals("http://127.0.0.1:28080/api/health", LauncherMain.HEALTH_URL, "HEALTH_URL must point to loopback 127.0.0.1:28080");
    }
}
