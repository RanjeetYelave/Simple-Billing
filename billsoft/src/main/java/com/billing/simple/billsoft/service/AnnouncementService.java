package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.licensing.LicensingConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Sandboxed service for fetching, parsing, and caching global announcements from the remote repository.
 * Operates offline-first with 48-hour caching, failure retry cooldown, and resilient disk/in-memory fallback.
 */
@Service
public class AnnouncementService {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementService.class);

    public static final String DEFAULT_RAW_URL =
            "https://raw.githubusercontent.com/RanjeetYelave/license-registry/main/announcements.txt";
    public static final String DEFAULT_API_URL =
            "https://api.github.com/repos/RanjeetYelave/license-registry/contents/announcements.txt";

    public static final String SEPARATOR = "@@@";
    public static final int MAX_FILE_SIZE_BYTES = 32 * 1024; // 32 KB limit
    public static final int MAX_ANNOUNCEMENTS = 10;
    public static final int MAX_ANNOUNCEMENT_LENGTH = 1000;
    public static final long DEFAULT_TTL_HOURS = 48; // Exactly 48 hours cache TTL
    public static final long DEFAULT_RETRY_COOLDOWN_MINUTES = 30; // 30 minutes cooldown on network failure

    private static final String CACHE_FILE_NAME = "announcements_cache.json";

    private final ObjectMapper mapper = new ObjectMapper();
    private final File cacheFile;

    private final AtomicReference<List<String>> memoryCache = new AtomicReference<>(null);
    private volatile Instant lastFetchTime = null;
    private volatile Instant lastFailureTime = null;

    public AnnouncementService() {
        this(new File(LicensingConfig.getStorageDirectory(), CACHE_FILE_NAME));
    }

    public AnnouncementService(File cacheFile) {
        this.cacheFile = cacheFile;
        loadDiskCache();
    }

    /**
     * Gets the active list of announcements. Returns cached version if fresh (< 48h),
     * or queries the remote repository on-demand if expired.
     * On failure, retains stale cache and enforces a retry cooldown.
     */
    public synchronized List<String> getAnnouncements() {
        return getAnnouncements(false);
    }

    /**
     * Gets announcements with optional force parameter to bypass cache TTL and retry cooldown.
     */
    public synchronized List<String> getAnnouncements(boolean force) {
        if (!force) {
            if (isCacheFresh()) {
                return getCachedOrEmpty();
            }

            if (isFailureCooldownActive()) {
                return getCachedOrEmpty();
            }
        }

        try {
            String rawContent = fetchRemoteContent();
            if (rawContent != null) {
                List<String> parsed = parseAnnouncements(rawContent);
                updateCache(parsed);
                this.lastFailureTime = null;
                return parsed;
            } else {
                this.lastFailureTime = Instant.now();
                log.debug("Announcements remote fetch returned empty/null body. Retaining existing cache.");
            }
        } catch (Exception e) {
            this.lastFailureTime = Instant.now();
            log.debug("Failed to refresh announcements from remote: {}", e.getMessage());
        }

        return getCachedOrEmpty();
    }

    /**
     * Parses plain text content separated by @@@ into a cleaned list of announcements.
     * Safely ignores empty entries, trims whitespace, enforces max count and explicit truncation indicator.
     */
    public List<String> parseAnnouncements(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return Collections.emptyList();
        }

        String[] parts = rawContent.split(SEPARATOR);
        List<String> result = new ArrayList<>();

        for (String part : parts) {
            if (part == null) continue;
            String cleaned = part.trim();
            if (!cleaned.isEmpty()) {
                // Normalize CRLF to LF
                cleaned = cleaned.replace("\r\n", "\n").replace("\r", "\n");
                // Explicit user-safe truncation if content exceeds limit
                if (cleaned.length() > MAX_ANNOUNCEMENT_LENGTH) {
                    cleaned = cleaned.substring(0, MAX_ANNOUNCEMENT_LENGTH).trim() + "… [Truncated: message exceeds 1,000 characters]";
                }
                result.add(cleaned);
                if (result.size() >= MAX_ANNOUNCEMENTS) {
                    break;
                }
            }
        }

        return Collections.unmodifiableList(result);
    }

    public boolean isCacheFresh() {
        if (memoryCache.get() == null || lastFetchTime == null) {
            return false;
        }
        long ttlHours = Long.getLong("rupeecrm.announcements.ttl.hours", DEFAULT_TTL_HOURS);
        Duration age = Duration.between(lastFetchTime, Instant.now());
        return age.toHours() < ttlHours && !age.isNegative();
    }

    public boolean isFailureCooldownActive() {
        if (lastFailureTime == null) {
            return false;
        }
        long cooldownMinutes = Long.getLong("rupeecrm.announcements.retry.cooldown.minutes", DEFAULT_RETRY_COOLDOWN_MINUTES);
        Duration age = Duration.between(lastFailureTime, Instant.now());
        return age.toMinutes() < cooldownMinutes && !age.isNegative();
    }

    private List<String> getCachedOrEmpty() {
        List<String> cached = memoryCache.get();
        return cached != null ? cached : Collections.emptyList();
    }

    private String fetchRemoteContent() {
        // Strategy 1: Raw URL (Primary)
        String rawUrl = System.getProperty("rupeecrm.announcements.url", DEFAULT_RAW_URL);
        String body = fetchHttpText(rawUrl);
        if (body != null && !body.isBlank()) {
            return extractContentText(body);
        }

        // Strategy 2: GitHub API fallback
        String apiUrl = System.getProperty("rupeecrm.announcements.api.url", DEFAULT_API_URL);
        String apiBody = fetchHttpText(apiUrl);
        if (apiBody != null && !apiBody.isBlank()) {
            return extractContentText(apiBody);
        }

        return null;
    }

    private String extractContentText(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return null;
        }
        String trimmed = rawResponse.trim();
        // If GitHub API returned JSON payload with base64 encoded content
        if (trimmed.startsWith("{") && trimmed.contains("\"content\"")) {
            try {
                JsonNode node = mapper.readTree(trimmed);
                if (node.has("content") && node.get("content").isTextual()) {
                    String base64 = node.get("content").asText().replaceAll("\\s+", "");
                    byte[] decoded = Base64.getDecoder().decode(base64);
                    return new String(decoded, StandardCharsets.UTF_8);
                }
            } catch (Exception ignored) {
            }
        }
        return rawResponse;
    }

    private String fetchHttpText(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = URI.create(urlStr).toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "RupeeCRM-Desktop/1.0");
            conn.setRequestProperty("Accept", "application/vnd.github.v3.raw, text/plain, application/json, */*");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setInstanceFollowRedirects(true);

            int code = conn.getResponseCode();
            if (code == 200) {
                try (InputStream in = conn.getInputStream()) {
                    byte[] buffer = in.readNBytes(MAX_FILE_SIZE_BYTES);
                    return new String(buffer, StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            log.debug("HTTP fetch error for {}: {}", urlStr, e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return null;
    }

    private void updateCache(List<String> announcements) {
        Instant now = Instant.now();
        this.memoryCache.set(announcements);
        this.lastFetchTime = now;
        saveDiskCache(now, announcements);
    }

    private void saveDiskCache(Instant timestamp, List<String> announcements) {
        if (cacheFile == null) return;
        try {
            File parent = cacheFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            CacheRecord record = new CacheRecord(timestamp.toEpochMilli(), announcements);
            mapper.writeValue(cacheFile, record);
        } catch (Exception e) {
            log.debug("Could not write announcements cache file: {}", e.getMessage());
        }
    }

    private void loadDiskCache() {
        if (cacheFile == null || !cacheFile.exists() || !cacheFile.isFile()) {
            return;
        }
        try {
            JsonNode root = mapper.readTree(cacheFile);
            if (root.isArray()) {
                // Legacy list format
                List<String> loaded = mapper.convertValue(root, new TypeReference<List<String>>() {});
                if (loaded != null) {
                    this.memoryCache.set(Collections.unmodifiableList(loaded));
                    this.lastFetchTime = Instant.ofEpochMilli(cacheFile.lastModified());
                }
            } else if (root.isObject() && root.has("announcements")) {
                // Modern structured cache record format
                CacheRecord record = mapper.treeToValue(root, CacheRecord.class);
                if (record != null && record.announcements != null) {
                    this.memoryCache.set(Collections.unmodifiableList(record.announcements));
                    this.lastFetchTime = Instant.ofEpochMilli(record.timestamp);
                }
            }
        } catch (Exception e) {
            log.debug("Could not read announcements cache file: {}", e.getMessage());
        }
    }

    public Instant getLastFetchTime() {
        return lastFetchTime;
    }

    public Instant getLastFailureTime() {
        return lastFailureTime;
    }

    public void setMemoryCacheForTesting(List<String> announcements, Instant fetchTime) {
        this.memoryCache.set(announcements != null ? Collections.unmodifiableList(announcements) : Collections.emptyList());
        this.lastFetchTime = fetchTime;
    }

    public void setLastFailureTimeForTesting(Instant failureTime) {
        this.lastFailureTime = failureTime;
    }

    public void clearCacheForTesting() {
        this.memoryCache.set(null);
        this.lastFetchTime = null;
        this.lastFailureTime = null;
        if (cacheFile != null && cacheFile.exists()) {
            cacheFile.delete();
        }
    }

    public static class CacheRecord {
        public long timestamp;
        public List<String> announcements;

        public CacheRecord() {}

        public CacheRecord(long timestamp, List<String> announcements) {
            this.timestamp = timestamp;
            this.announcements = announcements;
        }
    }
}
