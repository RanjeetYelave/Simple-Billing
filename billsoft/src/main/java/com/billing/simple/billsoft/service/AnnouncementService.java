package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.licensing.LicensingConfig;
import com.billing.simple.billsoft.licensing.MachineIdentity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Sandboxed service for fetching, parsing, and caching global announcements
 * from the remote repository.
 * Operates offline-first with 48-hour caching, failure retry cooldown, and
 * resilient disk/in-memory fallback.
 */
@Service
public class AnnouncementService {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementService.class);

    public static final String DEFAULT_RAW_URL = "https://raw.githubusercontent.com/RanjeetYelave/announcements/PROD/announcements.txt";
    public static final String DEFAULT_API_URL = "https://api.github.com/repos/RanjeetYelave/announcements/contents/announcements.txt?ref=PROD";

    public static final String SEPARATOR = "@@@";
    public static final int MAX_FILE_SIZE_BYTES = 32 * 1024; // 32 KB limit
    public static final int MAX_ANNOUNCEMENTS = 10;
    public static final int MAX_ANNOUNCEMENT_LENGTH = 1000;
    public static final long DEFAULT_TTL_HOURS = 1; // 1 hour internal memory cache TTL
    public static final long DEFAULT_RETRY_COOLDOWN_MINUTES = 5; // 5 minutes cooldown on network failure
    public static final long DEFAULT_OFFLINE_CACHE_MAX_AGE_HOURS = 24; // 24 hours max age for offline disk cache
                                                                       // fallback

    private static final String CACHE_FILE_NAME = "announcements_cache.json";

    private final ObjectMapper mapper = new ObjectMapper();
    private final File cacheFile;

    @Autowired(required = false)
    private MachineIdentity machineIdentity;

    private com.billing.simple.billsoft.service.scheduler.GitCheckSchedulerService schedulerService;

    private String machineIdOverride = null;

    private final AtomicReference<List<String>> memoryCache = new AtomicReference<>(null);
    private final AtomicReference<List<AnnouncementItem>> itemCache = new AtomicReference<>(null);
    private volatile Instant lastFetchTime = null;
    private volatile Instant lastFailureTime = null;

    public AnnouncementService() {
        this(new File(LicensingConfig.getStorageDirectory(), CACHE_FILE_NAME));
    }

    public AnnouncementService(File cacheFile) {
        this.cacheFile = cacheFile;
        loadDiskCache();
    }

    public String getMachineId() {
        if (machineIdOverride != null) return machineIdOverride;
        if (machineIdentity != null) {
            try {
                return machineIdentity.getMachineId();
            } catch (Exception ignored) {}
        }
        return null;
    }

    public void setMachineIdForTesting(String machineId) {
        this.machineIdOverride = machineId;
    }

    public void setSchedulerService(com.billing.simple.billsoft.service.scheduler.GitCheckSchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    /**
     * Gets the active list of announcements. Returns cached version if fresh (<
     * 48h),
     * or queries the remote repository on-demand if expired.
     * On failure, retains stale cache and enforces a retry cooldown.
     */
    public synchronized List<String> getAnnouncements() {
        return getAnnouncements(false);
    }

    /**
     * Gets announcements with optional force parameter to bypass cache TTL and
     * retry cooldown.
     */
    public synchronized List<String> getAnnouncements(boolean force) {
        if (!force) {
            if (schedulerService != null && !schedulerService.isCheckDue(com.billing.simple.billsoft.service.scheduler.CheckType.ANNOUNCEMENTS)) {
                return getCachedOrEmpty();
            }

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
                List<AnnouncementItem> allItems = parseAnnouncementItems(rawContent);
                List<String> parsed = filterEligibleAnnouncements(allItems, Instant.now(), getMachineId());
                updateCache(parsed, allItems);
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

    public synchronized com.billing.simple.billsoft.service.scheduler.GitCheckExecutionResult refreshFromRemote(String etag) {
        try {
            String rawContent = fetchRemoteContent();
            if (rawContent != null) {
                List<AnnouncementItem> allItems = parseAnnouncementItems(rawContent);
                List<String> parsed = filterEligibleAnnouncements(allItems, Instant.now(), getMachineId());
                updateCache(parsed, allItems);
                this.lastFailureTime = null;
                return com.billing.simple.billsoft.service.scheduler.GitCheckExecutionResult.success(null);
            } else {
                this.lastFailureTime = Instant.now();
                return com.billing.simple.billsoft.service.scheduler.GitCheckExecutionResult.networkError("Announcements remote fetch returned empty/null body");
            }
        } catch (Exception e) {
            this.lastFailureTime = Instant.now();
            return com.billing.simple.billsoft.service.scheduler.GitCheckExecutionResult.networkError(e.getMessage());
        }
    }

    /**
     * Parses plain text content into a list of AnnouncementItem objects.
     */
    public List<AnnouncementItem> parseAnnouncementItems(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return Collections.emptyList();
        }

        String[] parts;
        if (rawContent.contains(SEPARATOR)) {
            parts = rawContent.split(SEPARATOR);
        } else if (rawContent.contains("\n[") || rawContent.trim().startsWith("[")) {
            // Split on newline preceding a bracket tag like [INFO], [INFO|id=ANN-1|...], etc.
            parts = rawContent.split("(?m)^(?=\\[[A-Za-z0-9|:=_.-]+\\])");
        } else {
            parts = new String[] { rawContent };
        }

        List<AnnouncementItem> items = new ArrayList<>();
        for (String part : parts) {
            if (part == null)
                continue;
            String cleaned = part.trim();
            if (!cleaned.isEmpty()) {
                AnnouncementItem item = parseSingleItem(cleaned);
                if (item != null) {
                    items.add(item);
                }
            }
        }
        return Collections.unmodifiableList(items);
    }

    /**
     * Parses plain text content separated by @@@ (or bracket format like [INFO] Title: Body) into a cleaned list of announcements.
     * Safely ignores empty entries, trims whitespace, preserves multiline bodies, enforces max count and explicit truncation indicator.
     * Evaluates scheduling (publishAt), expiration (expiresAt), and machine ID targeting against Instant.now().
     */
    public List<String> parseAnnouncements(String rawContent) {
        List<AnnouncementItem> items = parseAnnouncementItems(rawContent);
        return filterEligibleAnnouncements(items, Instant.now(), getMachineId());
    }

    public List<String> filterEligibleAnnouncements(List<AnnouncementItem> items, Instant now, String machineId) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (AnnouncementItem item : items) {
            if (item.isEligible(now, machineId)) {
                result.add(item.displayText != null ? item.displayText : item.rawText);
                if (result.size() >= MAX_ANNOUNCEMENTS) {
                    break;
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    public static AnnouncementItem parseSingleItem(String rawPart) {
        if (rawPart == null || rawPart.isBlank()) return null;
        String cleaned = rawPart.trim().replace("\r\n", "\n").replace("\r", "\n");
        if (cleaned.length() > MAX_ANNOUNCEMENT_LENGTH) {
            cleaned = cleaned.substring(0, MAX_ANNOUNCEMENT_LENGTH).trim() + "… [Truncated: message exceeds 1,000 characters]";
        }

        String bannerType = "INFO";
        String id = null;
        Instant publishAt = null;
        Instant expiresAt = null;
        String target = "ALL";
        String displayText = cleaned;

        // Check for bracket tag at start: [TYPE|id=...|publishAt=...|expiresAt=...|target=...] Content
        // or [TYPE] Content
        if (cleaned.startsWith("[")) {
            int closeBracket = cleaned.indexOf(']');
            if (closeBracket > 1) {
                String tag = cleaned.substring(1, closeBracket).trim();
                String content = cleaned.substring(closeBracket + 1).trim();

                if (tag.contains("|")) {
                    String[] segments = tag.split("\\|");
                    if (segments.length > 0 && !segments[0].isBlank()) {
                        bannerType = segments[0].trim();
                    }
                    for (int i = 1; i < segments.length; i++) {
                        String seg = segments[i].trim();
                        int eq = seg.indexOf('=');
                        if (eq > 0) {
                            String k = seg.substring(0, eq).trim();
                            String v = seg.substring(eq + 1).trim();
                            if ("id".equalsIgnoreCase(k)) id = v;
                            else if ("target".equalsIgnoreCase(k)) target = v;
                            else if ("publishAt".equalsIgnoreCase(k) && !v.isBlank()) {
                                try { publishAt = Instant.parse(v); } catch (Exception ignored) {}
                            }
                            else if ("expiresAt".equalsIgnoreCase(k) && !v.isBlank()) {
                                try { expiresAt = Instant.parse(v); } catch (Exception ignored) {}
                            }
                        }
                    }
                    displayText = "[" + bannerType + "] " + content;
                } else {
                    bannerType = tag;
                    displayText = cleaned;
                }
            }
        }

        return new AnnouncementItem(id, bannerType, null, null, target, publishAt, expiresAt, cleaned, displayText);
    }

    public boolean isCacheFresh() {
        if (memoryCache.get() == null || lastFetchTime == null) {
            return false;
        }
        long ttlMinutes = Long.getLong("rupeecrm.announcements.ttl.minutes",
                Long.getLong("rupeecrm.announcements.ttl.hours", DEFAULT_TTL_HOURS) * 60);
        Duration age = Duration.between(lastFetchTime, Instant.now());
        return age.toMinutes() < ttlMinutes && !age.isNegative();
    }

    public boolean isFailureCooldownActive() {
        if (lastFailureTime == null) {
            return false;
        }
        long cooldownMinutes = Long.getLong("rupeecrm.announcements.retry.cooldown.minutes",
                DEFAULT_RETRY_COOLDOWN_MINUTES);
        Duration age = Duration.between(lastFailureTime, Instant.now());
        return age.toMinutes() < cooldownMinutes && !age.isNegative();
    }

    private List<String> getCachedOrEmpty() {
        List<AnnouncementItem> items = itemCache.get();
        if (items != null && !items.isEmpty()) {
            return filterEligibleAnnouncements(items, Instant.now(), getMachineId());
        }
        List<String> cached = memoryCache.get();
        return cached != null ? cached : Collections.emptyList();
    }

    private String fetchRemoteContent() {
        // Strategy 1: Raw URL (Primary)
        String rawUrl = System.getProperty("rupeecrm.announcements.url", DEFAULT_RAW_URL);
        String body = fetchHttpText(rawUrl);
        if (body != null) {
            return extractContentText(body);
        }

        // Strategy 2: GitHub API fallback
        String apiUrl = System.getProperty("rupeecrm.announcements.api.url", DEFAULT_API_URL);
        String apiBody = fetchHttpText(apiUrl);
        if (apiBody != null) {
            return extractContentText(apiBody);
        }

        return null;
    }

    private String extractContentText(String rawResponse) {
        if (rawResponse == null) {
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

    private void updateCache(List<String> announcements, List<AnnouncementItem> items) {
        Instant now = Instant.now();
        this.memoryCache.set(announcements);
        this.itemCache.set(items);
        this.lastFetchTime = now;
        saveDiskCache(now, announcements, items);
    }

    private void saveDiskCache(Instant timestamp, List<String> announcements, List<AnnouncementItem> items) {
        if (cacheFile == null)
            return;
        try {
            File parent = cacheFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            CacheRecord record = new CacheRecord(timestamp.toEpochMilli(), announcements, items);
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
            long maxAgeMillis = DEFAULT_OFFLINE_CACHE_MAX_AGE_HOURS * 3600 * 1000L;
            long fileAge = System.currentTimeMillis() - cacheFile.lastModified();
            if (fileAge > maxAgeMillis) {
                log.info("Announcements disk cache is older than {} hours ({} ms). Discarding stale cache.",
                        DEFAULT_OFFLINE_CACHE_MAX_AGE_HOURS, fileAge);
                cacheFile.delete();
                return;
            }

            JsonNode root = mapper.readTree(cacheFile);
            if (root.isArray()) {
                // Legacy list format
                List<String> loaded = mapper.convertValue(root, new TypeReference<List<String>>() {
                });
                if (loaded != null) {
                    this.memoryCache.set(Collections.unmodifiableList(loaded));
                    this.lastFetchTime = Instant.ofEpochMilli(cacheFile.lastModified());
                }
            } else if (root.isObject() && (root.has("announcements") || root.has("items"))) {
                // Modern structured cache record format
                CacheRecord record = mapper.treeToValue(root, CacheRecord.class);
                if (record != null) {
                    long recordAge = System.currentTimeMillis() - record.timestamp;
                    if (recordAge > maxAgeMillis) {
                        log.info("Announcements record timestamp is older than {} hours. Discarding stale cache.",
                                DEFAULT_OFFLINE_CACHE_MAX_AGE_HOURS);
                        cacheFile.delete();
                        return;
                    }

                    if (record.items != null && !record.items.isEmpty()) {
                        this.itemCache.set(Collections.unmodifiableList(record.items));
                        List<String> eligible = filterEligibleAnnouncements(record.items, Instant.now(), getMachineId());
                        this.memoryCache.set(eligible);
                    } else if (record.announcements != null) {
                        this.memoryCache.set(Collections.unmodifiableList(record.announcements));
                    }
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
        List<String> list = announcements != null ? Collections.unmodifiableList(announcements) : Collections.emptyList();
        this.memoryCache.set(list);
        List<AnnouncementItem> items = new ArrayList<>();
        for (String a : list) {
            AnnouncementItem item = parseSingleItem(a);
            if (item != null) items.add(item);
        }
        this.itemCache.set(Collections.unmodifiableList(items));
        this.lastFetchTime = fetchTime;
    }

    public void setLastFailureTimeForTesting(Instant failureTime) {
        this.lastFailureTime = failureTime;
    }

    public void clearCacheForTesting() {
        this.memoryCache.set(null);
        this.itemCache.set(null);
        this.lastFetchTime = null;
        this.lastFailureTime = null;
        if (cacheFile != null && cacheFile.exists()) {
            cacheFile.delete();
        }
    }

    public static class AnnouncementItem {
        public String id;
        public String bannerType = "INFO";
        public String title;
        public String body;
        public String target = "ALL";
        public Instant publishAt;
        public Instant expiresAt;
        public String rawText;
        public String displayText;

        public AnnouncementItem() {}

        public AnnouncementItem(String id, String bannerType, String title, String body,
                                String target, Instant publishAt, Instant expiresAt,
                                String rawText, String displayText) {
            this.id = id;
            this.bannerType = bannerType != null ? bannerType : "INFO";
            this.title = title;
            this.body = body;
            this.target = target != null ? target : "ALL";
            this.publishAt = publishAt;
            this.expiresAt = expiresAt;
            this.rawText = rawText;
            this.displayText = displayText != null ? displayText : rawText;
        }

        public boolean isEligible(Instant now, String machineId) {
            if (publishAt != null && now.isBefore(publishAt)) {
                return false;
            }
            if (expiresAt != null && !now.isBefore(expiresAt)) {
                return false;
            }
            if (target != null && !target.isBlank() && !"ALL".equalsIgnoreCase(target.trim())) {
                if (machineId != null && !machineId.isBlank() && !target.trim().equalsIgnoreCase(machineId.trim())) {
                    return false;
                }
            }
            return true;
        }
    }

    public static class CacheRecord {
        public long timestamp;
        public List<String> announcements;
        public List<AnnouncementItem> items;

        public CacheRecord() {
        }

        public CacheRecord(long timestamp, List<String> announcements, List<AnnouncementItem> items) {
            this.timestamp = timestamp;
            this.announcements = announcements;
            this.items = items;
        }
    }
}
