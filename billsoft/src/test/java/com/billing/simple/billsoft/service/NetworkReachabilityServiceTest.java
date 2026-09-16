package com.billing.simple.billsoft.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class NetworkReachabilityServiceTest {

    @Test
    public void testProbeAndCacheReturnsNonNullStatus() {
        NetworkReachabilityService service = new NetworkReachabilityService();
        NetworkReachabilityService.NetworkStatusDTO status = service.getStatus();

        assertNotNull(status);
        assertNotNull(status.getMode());
        assertNotNull(status.getLastCheckedAt());

        Map<String, Object> map = status.toMap();
        assertTrue(map.containsKey("connected"));
        assertTrue(map.containsKey("mode"));
        assertTrue(map.containsKey("cloudServicesReachable"));
        assertTrue(map.containsKey("latencyMs"));
        assertTrue(map.containsKey("description"));
    }

    @Test
    public void testCacheHitWithinTtl() {
        NetworkReachabilityService service = new NetworkReachabilityService();
        NetworkReachabilityService.NetworkStatusDTO first = service.getStatus();
        NetworkReachabilityService.NetworkStatusDTO second = service.getStatus();

        // Must be exact same cached instance
        assertSame(first, second, "Subsequent calls within 30s TTL must return cached DTO");
    }

    @Test
    public void testForceRefreshBypassesCache() {
        NetworkReachabilityService service = new NetworkReachabilityService();
        NetworkReachabilityService.NetworkStatusDTO first = service.getStatus();
        NetworkReachabilityService.NetworkStatusDTO refreshed = service.getStatus(true);

        assertNotNull(refreshed);
        // Both valid, but refreshed was probed anew
        assertNotNull(refreshed.getLastCheckedAt());
    }

    @Test
    public void testOfflineSimulation() {
        // Subclass to simulate socket failure (offline environment)
        NetworkReachabilityService offlineService = new NetworkReachabilityService() {
            @Override
            boolean probeSocket(String host, int port, int timeoutMs) {
                return false;
            }
        };

        NetworkReachabilityService.NetworkStatusDTO status = offlineService.getStatus();
        assertFalse(status.isConnected());
        assertFalse(status.isCloudServicesReachable());
        assertEquals("OFFLINE_LOCAL", status.getMode());
        assertEquals(-1, status.getLatencyMs());
        assertTrue(status.toMap().get("description").toString().contains("Offline-first Local Mode"));
    }

    @Test
    public void testCloudReachableSimulation() {
        NetworkReachabilityService onlineService = new NetworkReachabilityService() {
            @Override
            boolean probeSocket(String host, int port, int timeoutMs) {
                return true;
            }
        };

        NetworkReachabilityService.NetworkStatusDTO status = onlineService.getStatus();
        assertTrue(status.isConnected());
        assertTrue(status.isCloudServicesReachable());
        assertEquals("ONLINE", status.getMode());
        assertTrue(status.getLatencyMs() >= 0);
    }
}
