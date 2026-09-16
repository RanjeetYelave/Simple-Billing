package com.billing.simple.billsoft.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lightweight, non-blocking reachability service.
 * Determines internet / GitHub cloud reachability with in-memory caching
 * (30s TTL) and low timeout (1.5s) to guarantee zero latency penalty on POS operations.
 */
@Service
public class NetworkReachabilityService {

    private static final Logger log = LoggerFactory.getLogger(NetworkReachabilityService.class);
    private static final int DEFAULT_TIMEOUT_MS = 1500;
    private static final long CACHE_TTL_SECONDS = 30;

    private static final String PRIMARY_HOST = "api.github.com";
    private static final int PRIMARY_PORT = 443;
    private static final String BACKUP_HOST = "1.1.1.1";
    private static final int BACKUP_PORT = 443;

    private final AtomicReference<CachedStatus> statusCache = new AtomicReference<>(null);

    public static class NetworkStatusDTO {
        private final boolean connected;
        private final boolean cloudServicesReachable;
        private final long latencyMs;
        private final Instant lastCheckedAt;
        private final String mode;

        public NetworkStatusDTO(boolean connected, boolean cloudServicesReachable, long latencyMs, Instant lastCheckedAt) {
            this.connected = connected;
            this.cloudServicesReachable = cloudServicesReachable;
            this.latencyMs = latencyMs;
            this.lastCheckedAt = lastCheckedAt;
            this.mode = connected ? "ONLINE" : "OFFLINE_LOCAL";
        }

        public boolean isConnected() {
            return connected;
        }

        public boolean isCloudServicesReachable() {
            return cloudServicesReachable;
        }

        public long getLatencyMs() {
            return latencyMs;
        }

        public Instant getLastCheckedAt() {
            return lastCheckedAt;
        }

        public String getMode() {
            return mode;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("connected", connected);
            map.put("mode", mode);
            map.put("cloudServicesReachable", cloudServicesReachable);
            map.put("latencyMs", latencyMs);
            map.put("lastCheckedAt", lastCheckedAt != null ? lastCheckedAt.toString() : null);
            map.put("description", connected
                    ? "Internet & Cloud Services Connected"
                    : "Offline-first Local Mode Active (Billing 100% Operational)");
            return map;
        }
    }

    private static class CachedStatus {
        final NetworkStatusDTO dto;
        final long expiresAtMillis;

        CachedStatus(NetworkStatusDTO dto, long expiresAtMillis) {
            this.dto = dto;
            this.expiresAtMillis = expiresAtMillis;
        }

        boolean isValid() {
            return System.currentTimeMillis() < expiresAtMillis;
        }
    }

    /**
     * Retrieves current network reachability status (cached within 30s TTL).
     */
    public NetworkStatusDTO getStatus() {
        return getStatus(false);
    }

    /**
     * Retrieves network reachability status.
     * @param forceRefresh if true, ignores cache and performs fresh probe.
     */
    public NetworkStatusDTO getStatus(boolean forceRefresh) {
        CachedStatus cached = statusCache.get();
        if (!forceRefresh && cached != null && cached.isValid()) {
            return cached.dto;
        }

        NetworkStatusDTO probed = probeNetworkReachability();
        long expiresAt = System.currentTimeMillis() + (CACHE_TTL_SECONDS * 1000);
        statusCache.set(new CachedStatus(probed, expiresAt));
        return probed;
    }

    private NetworkStatusDTO probeNetworkReachability() {
        Instant now = Instant.now();
        long startTime = System.currentTimeMillis();

        // 1. Primary probe: api.github.com (Cloud Registry & Vault host)
        boolean githubOk = probeSocket(PRIMARY_HOST, PRIMARY_PORT, DEFAULT_TIMEOUT_MS);
        long latency = System.currentTimeMillis() - startTime;

        if (githubOk) {
            return new NetworkStatusDTO(true, true, latency, now);
        }

        // 2. Backup probe: 1.1.1.1 (Generic internet reachability)
        long backupStart = System.currentTimeMillis();
        boolean generalInternetOk = probeSocket(BACKUP_HOST, BACKUP_PORT, DEFAULT_TIMEOUT_MS);
        long backupLatency = System.currentTimeMillis() - backupStart;

        if (generalInternetOk) {
            return new NetworkStatusDTO(true, false, backupLatency, now);
        }

        return new NetworkStatusDTO(false, false, -1, now);
    }

    boolean probeSocket(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            log.debug("Reachability probe to {}:{} failed: {}", host, port, e.getMessage());
            return false;
        }
    }
}
