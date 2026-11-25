package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.util.DataDirectoryResolver;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Centralized, version-based release notes service for RupeeCRM.
 * Resolves exact version-matched release notes from local cache, bundled classpath,
 * or trusted remote repository sources.
 *
 * Guarantees:
 * - Exact version matching without inferring notes across releases
 * - Strict HTML/script sanitization preventing XSS injection
 * - Offline-first operation with persistent disk caching
 * - Non-blocking fail-safe operation: errors never block update downloads or installations
 */
@Service
public class ReleaseNotesService {

    private static final Logger log = LoggerFactory.getLogger(ReleaseNotesService.class);

    public static final String DEFAULT_NO_NOTES = "Release notes are not available for this version.";
    public static final String DEFAULT_REMOTE_URL = "https://raw.githubusercontent.com/RanjeetYelave/Simple-Billing/PROD/release-notes.json";
    public static final int MAX_FILE_SIZE_BYTES = 256 * 1024; // 256 KB safety limit
    public static final int CONNECT_TIMEOUT_MS = 3000;
    public static final int READ_TIMEOUT_MS = 5000;
    public static final long CACHE_TTL_MS = 6 * 60 * 60 * 1000L; // 6 hours

    private static final Pattern SCRIPT_OR_HTML_TAG = Pattern.compile("<[^>]*>", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, List<String>> memoryStore = new ConcurrentHashMap<>();
    private final File cacheFile;

    @Value("${app.update.release-notes.url:" + DEFAULT_REMOTE_URL + "}")
    private String remoteUrl = DEFAULT_REMOTE_URL;

    private volatile long lastFetchTime = 0L;

    public ReleaseNotesService() {
        File dataDir = DataDirectoryResolver.resolveDataDirectory();
        this.cacheFile = new File(dataDir, "release_notes_cache.json");
    }

    public ReleaseNotesService(File customCacheFile, String customRemoteUrl) {
        this.cacheFile = customCacheFile;
        this.remoteUrl = customRemoteUrl != null ? customRemoteUrl : DEFAULT_REMOTE_URL;
    }

    @PostConstruct
    public void initialize() {
        loadBundledNotes();
        loadDiskCache();
        // Background asynchronous attempt to refresh from trusted remote
        try {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    refreshFromRemote(false);
                } catch (Exception e) {
                    log.debug("Initial background release-notes sync skipped: {}", e.getMessage());
                }
            });
        } catch (Exception ignored) {}
    }

    /**
     * Cleans and normalizes a version string (e.g. "v1.0.389" -> "1.0.389").
     */
    public static String normalizeVersion(String version) {
        if (version == null) return "";
        return version.trim().replaceAll("^[vV]", "").trim();
    }

    /**
     * Strips dangerous HTML / script tags from release note text.
     */
    public static String sanitizeText(String input) {
        if (input == null) return "";
        String clean = SCRIPT_OR_HTML_TAG.matcher(input).replaceAll("").trim();
        return clean.replace("\r", "");
    }

    /**
     * Checks whether release notes exist for the exact specified version.
     */
    public boolean hasReleaseNotesForVersion(String version) {
        String key = normalizeVersion(version);
        return memoryStore.containsKey(key) && !memoryStore.get(key).isEmpty();
    }

    /**
     * Resolves human-readable release notes for the exact specified version.
     * Returns bullet-formatted string or neutral fallback.
     */
    public String getReleaseNotesForVersion(String version) {
        String key = normalizeVersion(version);
        List<String> items = memoryStore.get(key);
        if (items == null || items.isEmpty()) {
            return DEFAULT_NO_NOTES;
        }

        StringBuilder sb = new StringBuilder();
        for (String item : items) {
            String trimmed = item.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ")) {
                sb.append(trimmed).append("\n");
            } else {
                sb.append("- ").append(trimmed).append("\n");
            }
        }
        String res = sb.toString().trim();
        return res.isEmpty() ? DEFAULT_NO_NOTES : res;
    }

    /**
     * Resolves bullet items list for the exact specified version.
     */
    public List<String> getReleaseNoteItemsForVersion(String version) {
        String key = normalizeVersion(version);
        List<String> items = memoryStore.get(key);
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(items));
    }

    /**
     * Returns a snapshot of all version release notes currently loaded.
     */
    public Map<String, Object> getAllReleaseNotes() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        memoryStore.forEach((ver, items) -> {
            if (items.size() == 1) {
                snapshot.put(ver, items.get(0));
            } else {
                snapshot.put(ver, new ArrayList<>(items));
            }
        });
        return snapshot;
    }

    /**
     * Refreshes release notes from the trusted remote URL if due or forced.
     */
    public synchronized boolean refreshFromRemote(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && (now - lastFetchTime) < CACHE_TTL_MS) {
            return false;
        }

        if (remoteUrl == null || remoteUrl.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(remoteUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                log.warn("Refusing to fetch release-notes over insecure scheme: {}", remoteUrl);
                return false;
            }

            URL url = uri.toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", "RupeeCRM-ReleaseNotesClient");
            conn.setRequestProperty("Accept", "application/json");

            int status = conn.getResponseCode();
            if (status != 200) {
                log.debug("Remote release-notes returned HTTP {}. Keeping cached notes.", status);
                return false;
            }

            int contentLength = conn.getContentLength();
            if (contentLength > MAX_FILE_SIZE_BYTES) {
                log.warn("Remote release-notes exceeds max size ({} bytes). Rejecting.", contentLength);
                return false;
            }

            byte[] bytes;
            try (InputStream in = conn.getInputStream()) {
                bytes = in.readNBytes(MAX_FILE_SIZE_BYTES + 1);
            }
            if (bytes.length > MAX_FILE_SIZE_BYTES) {
                log.warn("Remote release-notes payload exceeded {} bytes. Rejecting.", MAX_FILE_SIZE_BYTES);
                return false;
            }

            String json = new String(bytes, StandardCharsets.UTF_8);
            Map<String, List<String>> parsed = parseAndValidateJson(json);
            if (!parsed.isEmpty()) {
                memoryStore.putAll(parsed);
                lastFetchTime = now;
                saveDiskCache(json);
                log.info("Successfully refreshed release notes from remote ({} versions loaded).", parsed.size());
                return true;
            }
        } catch (Exception e) {
            log.debug("Could not refresh release-notes from remote ({}): {}", remoteUrl, e.getMessage());
        }
        return false;
    }

    /**
     * Loads bundled release-notes.json from classpath.
     */
    public void loadBundledNotes() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("release-notes.json")) {
            if (in != null) {
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                Map<String, List<String>> parsed = parseAndValidateJson(json);
                memoryStore.putAll(parsed);
                log.debug("Loaded {} bundled release note versions from classpath.", parsed.size());
            } else {
                log.debug("No bundled release-notes.json found in classpath.");
            }
        } catch (Exception e) {
            log.warn("Failed to load bundled release-notes.json: {}", e.getMessage());
        }
    }

    /**
     * Loads persisted release notes from disk cache.
     */
    private void loadDiskCache() {
        if (cacheFile == null || !cacheFile.exists() || !cacheFile.isFile()) {
            return;
        }
        try {
            String json = Files.readString(cacheFile.toPath(), StandardCharsets.UTF_8);
            Map<String, List<String>> parsed = parseAndValidateJson(json);
            memoryStore.putAll(parsed);
            log.debug("Loaded {} release note versions from disk cache.", parsed.size());
        } catch (Exception e) {
            log.warn("Failed to read release notes disk cache: {}", e.getMessage());
        }
    }

    /**
     * Atomically saves valid release notes to disk cache.
     */
    private void saveDiskCache(String json) {
        if (cacheFile == null) return;
        try {
            File parent = cacheFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            File tempFile = new File(parent, cacheFile.getName() + ".tmp");
            Files.writeString(tempFile.toPath(), json, StandardCharsets.UTF_8);
            Files.move(tempFile.toPath(), cacheFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            log.warn("Failed to save release notes disk cache: {}", e.getMessage());
        }
    }

    /**
     * Parses and strictly validates a release-notes JSON payload.
     * Expects a JSON object where each key is a semantic version and each value
     * is either a String or a List of Strings.
     */
    public Map<String, List<String>> parseAndValidateJson(String json) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return result;
        }

        try {
            Map<String, Object> rawMap = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            if (rawMap == null) return result;

            for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
                String rawVer = entry.getKey();
                if (rawVer == null || rawVer.isBlank()) continue;
                String normalizedVer = normalizeVersion(rawVer);
                if (normalizedVer.isEmpty()) continue;

                Object val = entry.getValue();
                List<String> items = new ArrayList<>();

                if (val instanceof String s) {
                    String clean = sanitizeText(s);
                    if (!clean.isEmpty()) {
                        // Split multiline string if contains newlines
                        String[] lines = clean.split("\n");
                        for (String line : lines) {
                            String trimmed = line.trim();
                            if (!trimmed.isEmpty()) {
                                items.add(trimmed.replaceAll("^[-*•]\\s*", ""));
                            }
                        }
                    }
                } else if (val instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof String itemStr) {
                            String clean = sanitizeText(itemStr);
                            if (!clean.isEmpty()) {
                                items.add(clean.replaceAll("^[-*•]\\s*", ""));
                            }
                        }
                    }
                }

                if (!items.isEmpty()) {
                    result.put(normalizedVer, items);
                }
            }
        } catch (Exception e) {
            log.warn("Invalid release-notes JSON format: {}", e.getMessage());
        }
        return result;
    }
}
