package com.billing.simple.billsoft.dataprotection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Storage provider targeting a private GitHub repository vault.
 * Follows strict low-frequency rules:
 * - Minimum necessary remote calls (1 on creation, 2 on replacement, max 1 retry on 409 conflict).
 * - Timeouts: 5000ms connect, 10000ms read.
 * - Zero background polling or pinging.
 * - Never leaks secrets or raw GitHub error bodies to customer messages.
 */
public class GitHubStorageProvider implements BackupStorageProvider {

    private static final Logger log = LoggerFactory.getLogger(GitHubStorageProvider.class);
    private static final String DEFAULT_REPO = "crucified1215/rupeecrm-dataprotection-vault";
    private static final String API_BASE = "https://api.github.com";

    private final String repo;
    private final DataProtectionCredentialStore credentialStore;
    private final String explicitAuthToken;
    private final ObjectMapper mapper;

    public GitHubStorageProvider() {
        this(new DataProtectionCredentialStore());
    }

    public GitHubStorageProvider(DataProtectionCredentialStore credentialStore) {
        this(resolveRepo(), null, credentialStore);
    }

    public GitHubStorageProvider(String repo, String authToken) {
        this(repo, authToken, null);
    }

    public GitHubStorageProvider(String repo, String explicitAuthToken, DataProtectionCredentialStore credentialStore) {
        this.repo = (repo != null && !repo.isBlank()) ? repo.trim() : resolveRepo();
        this.explicitAuthToken = explicitAuthToken;
        this.credentialStore = (credentialStore != null) ? credentialStore : new DataProtectionCredentialStore();
        this.mapper = new ObjectMapper();
    }

    private static String resolveRepo() {
        String prop = System.getProperty("rupeecrm.dataprotection.repo");
        if (prop != null && !prop.isBlank()) return prop.trim();
        String env = System.getenv("RUPEECRM_DATA_PROTECTION_REPO");
        if (env != null && !env.isBlank()) return env.trim();
        return DEFAULT_REPO;
    }

    private String getAuthToken() {
        if (explicitAuthToken != null && !explicitAuthToken.isBlank()) {
            return explicitAuthToken.trim();
        }
        return credentialStore.getCurrentToken();
    }

    @Override
    public UploadResult uploadBackup(String machineId, byte[] encryptedData, String lastKnownSha) {
        if (machineId == null || machineId.isBlank() || encryptedData == null || encryptedData.length == 0) {
            return UploadResult.error("Invalid parameters for upload", 400, DataProtectionErrorCode.DP_005);
        }

        String path = "backups/" + machineId.trim() + ".enc";
        String sha = lastKnownSha;

        // If sha is unknown, attempt to fetch current SHA or create directly
        if (sha == null || sha.isBlank()) {
            sha = fetchFileSha(path);
        }

        // Attempt upload (Call 1 or 2)
        UploadResult result = executePutContent(path, encryptedData, sha, machineId);
        if (result.isSuccess()) {
            return result;
        }

        // Bounded retry (max 1) on 409 Conflict OR 422 Unprocessable Entity
        if (result.getStatusCode() == 409 || result.getStatusCode() == 422) {
            String freshSha = fetchFileSha(path);
            return executePutContent(path, encryptedData, freshSha, machineId);
        }

        return result;
    }

    @Override
    public byte[] downloadBackup(String machineId) {
        if (machineId == null || machineId.isBlank()) {
            return null;
        }
        String path = "backups/" + machineId.trim() + ".enc";
        String urlStr = API_BASE + "/repos/" + repo + "/contents/" + path;

        HttpURLConnection conn = null;
        try {
            URL url = URI.create(urlStr).toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "RupeeCRM-Desktop/1.0");
            conn.setRequestProperty("Accept", "application/vnd.github.v3.raw");
            String token = getAuthToken();
            if (token != null && !token.isBlank()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            int code = conn.getResponseCode();
            if (code == 200) {
                try (InputStream in = conn.getInputStream()) {
                    return in.readAllBytes();
                }
            } else {
                log.warn("Cloud vault download failed with HTTP status {}", code);
            }
        } catch (Exception e) {
            log.warn("Cloud vault download network error: {}", e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return null;
    }

    private String fetchFileSha(String path) {
        String urlStr = API_BASE + "/repos/" + repo + "/contents/" + path;
        HttpURLConnection conn = null;
        try {
            URL url = URI.create(urlStr).toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "RupeeCRM-Desktop/1.0");
            conn.setRequestProperty("Accept", "application/json");
            String token = getAuthToken();
            if (token != null && !token.isBlank()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            int code = conn.getResponseCode();
            if (code == 200) {
                try (InputStream in = conn.getInputStream()) {
                    JsonNode node = mapper.readTree(in);
                    if (node.has("sha")) {
                        return node.get("sha").asText();
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return null;
    }

    private UploadResult executePutContent(String path, byte[] data, String sha, String machineId) {
        String urlStr = API_BASE + "/repos/" + repo + "/contents/" + path;
        HttpURLConnection conn = null;
        try {
            URL url = URI.create(urlStr).toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PUT");
            conn.setRequestProperty("User-Agent", "RupeeCRM-Desktop/1.0");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "application/json");
            String token = getAuthToken();
            if (token != null && !token.isBlank()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            conn.setDoOutput(true);

            Map<String, Object> reqBody = new HashMap<>();
            reqBody.put("message", "Auto Data Protection backup for machine: " + machineId);
            reqBody.put("content", Base64.getEncoder().encodeToString(data));
            if (sha != null && !sha.isBlank()) {
                reqBody.put("sha", sha);
            }

            byte[] jsonBytes = mapper.writeValueAsBytes(reqBody);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(jsonBytes);
            }

            int code = conn.getResponseCode();
            if (code == 200 || code == 201) {
                try (InputStream in = conn.getInputStream()) {
                    JsonNode node = mapper.readTree(in);
                    String newSha = node.path("content").path("sha").asText(null);
                    return UploadResult.ok(newSha);
                }
            } else {
                DataProtectionErrorCode errorCode = DataProtectionErrorCode.fromHttpStatus(code);
                log.warn("Cloud vault upload failed: HTTP {} ({})", code, errorCode.getCode());
                return UploadResult.error(errorCode.formatMessage(), code, errorCode);
            }
        } catch (Exception e) {
            log.warn("Cloud vault upload exception: {}", e.getMessage());
            return UploadResult.error(DataProtectionErrorCode.DP_004.formatMessage(), 0, DataProtectionErrorCode.DP_004);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
