package com.billing.simple.billsoft.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class UpdateService {

    @Value("${app.version:v1.0.0}")
    private String defaultVersion;

    private static final String GITHUB_API_URL = "https://api.github.com/repos/RanjeetYelave/Simple-Billing/releases/latest";
    private static final String UPDATE_MARKER_FILE = ".billsoft-updated";
    private static final String VERSION_FILE = ".billsoft-version";

    // --- Progress tracking ---
    private final AtomicReference<String> progressStatus = new AtomicReference<>("idle");
    private final AtomicInteger progressPercent = new AtomicInteger(0);
    private final AtomicReference<String> progressMessage = new AtomicReference<>("");
    private final AtomicReference<String> progressLatestVersion = new AtomicReference<>("");
    private final AtomicReference<String> progressSpeed = new AtomicReference<>("");
    private final AtomicReference<String> progressSize = new AtomicReference<>("");
    private final AtomicReference<String> cachedDownloadUrl = new AtomicReference<>("");
    // Emitter used for Server‑Sent Events
    private SseEmitter progressEmitter;

    /**
     * Get the current version from the persisted version file, falling back to the
     * default version baked into application.properties.
     */
    private String getCurrentVersion() {
        try {
            Path versionFile = Paths.get(VERSION_FILE);
            if (Files.exists(versionFile)) {
                String version = new String(Files.readAllBytes(versionFile)).trim();
                if (!version.isEmpty()) {
                    return version;
                }
            }
        } catch (Exception ignored) {
        }
        return defaultVersion;
    }

    /**
     * Persist the new version to the version file so it survives WAR replacement.
     */
    private void saveCurrentVersion(String version) {
        try {
            Files.write(Paths.get(VERSION_FILE), version.getBytes());
        } catch (Exception ignored) {
        }
    }

    public SseEmitter getProgressEmitter() {
        progressEmitter = new SseEmitter(Long.MAX_VALUE);
        return progressEmitter;
    }

    /**
     * Setter for SSE emitter used to push progress updates.
     */
    public void setProgressEmitter(SseEmitter emitter) {
        this.progressEmitter = emitter;
    }

    private void sendProgressEvent() {
        if (progressEmitter != null) {
            try {
                progressEmitter
                        .send(SseEmitter.event().name("progress").data(getProgress(), MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                progressEmitter = null;
            }
        }
    }

    public Map<String, Object> getProgress() {
        Map<String, Object> p = new HashMap<>();
        p.put("status", progressStatus.get());
        p.put("percent", progressPercent.get());
        p.put("message", progressMessage.get());
        p.put("latestVersion", progressLatestVersion.get());
        p.put("speed", progressSpeed.get());
        p.put("size", progressSize.get());
        return p;
    }

    private void setProgress(String status, int percent, String message) {
        setProgress(status, percent, message, progressLatestVersion.get(), "", "");
    }

    private void setProgress(String status, int percent, String message, String latestVersion) {
        setProgress(status, percent, message, latestVersion, "", "");
    }

    private void setProgress(String status, int percent, String message, String latestVersion, String speed,
            String size) {
        progressStatus.set(status);
        progressPercent.set(percent);
        progressMessage.set(message);
        progressLatestVersion.set(latestVersion);
        progressSpeed.set(speed);
        progressSize.set(size);
        sendProgressEvent();
    }

    /**
     * Check if an update was just completed (marker file exists).
     * This survives restarts so the UI can show "Update Complete!".
     */
    public Map<String, Object> checkForUpdateComplete() {
        Map<String, Object> result = new HashMap<>();
        Path marker = getMarkerPath();
        if (Files.exists(marker)) {
            try {
                String version = new String(Files.readAllBytes(marker));
                result.put("justUpdated", true);
                result.put("updatedVersion", version);
                // Delete the marker so it only shows once
                Files.deleteIfExists(marker);
            } catch (Exception e) {
                result.put("justUpdated", false);
            }
        } else {
            result.put("justUpdated", false);
        }
        return result;
    }

    private Path getMarkerPath() {
        return Paths.get(UPDATE_MARKER_FILE);
    }

    public Map<String, Object> checkUpdate() {
        Map<String, Object> response = new HashMap<>();
        String currentVersion = getCurrentVersion();
        response.put("currentVersion", currentVersion);

        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Billsoft-App");
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> githubResponse = restTemplate.exchange(GITHUB_API_URL, HttpMethod.GET, entity, Map.class);
            Map<String, Object> githubRelease = githubResponse.getBody();

            if (githubRelease != null && githubRelease.containsKey("tag_name")) {
                String latestVersion = (String) githubRelease.get("tag_name");
                response.put("latestVersion", latestVersion);

                // Version comparison: strip leading 'v' if present, compare strings
                boolean updateAvailable = !normalizeVersion(currentVersion).equals(normalizeVersion(latestVersion));
                response.put("updateAvailable", updateAvailable);
                response.put("htmlUrl", githubRelease.get("html_url"));
                response.put("releaseNotes", githubRelease.get("body"));

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> assets = (List<Map<String, Object>>) githubRelease.get("assets");
                if (assets != null && !assets.isEmpty()) {
                    String url = (String) assets.get(0).get("browser_download_url");
                    cachedDownloadUrl.set(url);
                    // Don't send the URL to the frontend to hide the GitHub source
                    response.put("downloadUrl", "internal");
                }
            } else {
                response.put("updateAvailable", false);
                response.put("error", "No releases found");
            }
        } catch (Exception e) {
            response.put("updateAvailable", false);
            response.put("error", e.getMessage());
        }

        return response;
    }

    /**
     * Normalize version string by stripping leading 'v' or 'V' for comparison.
     */
    private String normalizeVersion(String version) {
        if (version == null)
            return "";
        return version.replaceAll("^[vV]", "").trim();
    }

    public boolean applyUpdate() {
        String downloadUrl = cachedDownloadUrl.get();
        if (downloadUrl == null || downloadUrl.isEmpty()) {
            setProgress("error", 0, "Update failed: No download URL found. Please check for updates again.");
            return false;
        }
        try {
            String latestVersion = "";
            // Try to get the latest version from the release for display purposes
            try {
                RestTemplate restTemplate = new RestTemplate();
                HttpHeaders headers = new HttpHeaders();
                headers.set("User-Agent", "Billsoft-App");
                HttpEntity<String> entity = new HttpEntity<>(headers);
                
                ResponseEntity<Map> githubResponse = restTemplate.exchange(GITHUB_API_URL, HttpMethod.GET, entity, Map.class);
                Map<String, Object> githubRelease = githubResponse.getBody();
                
                if (githubRelease != null && githubRelease.containsKey("tag_name")) {
                    latestVersion = (String) githubRelease.get("tag_name");
                }
            } catch (Exception ignored) {
            }

            setProgress("downloading", 0, "Starting download...", latestVersion);
            // Ensure SSE client receives the initial state
            sendProgressEvent();
            java.net.URL url = new java.net.URI(downloadUrl).toURL();
            String basePath = System.getProperty("user.dir");
            Path targetPath = Paths.get(basePath, "billsoft.war.update");

            // Cleanup: Delete any old or partial update file before starting a new download
            java.io.File oldUpdate = targetPath.toFile();
            if (oldUpdate.exists()) {
                oldUpdate.delete();
            }

            // Download the file with progress tracking
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
            conn.connect();

            int fileSize = conn.getContentLength();
            long totalBytesRead = 0;
            int lastPercent = 0;
            long startTime = System.currentTimeMillis();

            try (java.io.InputStream in = conn.getInputStream();
                    java.io.FileOutputStream out = new java.io.FileOutputStream(targetPath.toFile())) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                long lastUpdateTime = System.currentTimeMillis();

                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;

                    long now = System.currentTimeMillis();
                    // Update progress every 200ms for smoothness
                    if (now - lastUpdateTime > 200) {
                        lastUpdateTime = now;
                        int percent = (fileSize > 0) ? (int) (totalBytesRead * 100 / fileSize) : 0;

                        long elapsed = (now - startTime) / 1000;
                        double downloadMb = totalBytesRead / (1024.0 * 1024.0);
                        double totalMb = fileSize / (1024.0 * 1024.0);
                        double speedMbps = elapsed > 0 ? (totalBytesRead / (1024.0 * 1024.0)) / elapsed : 0;

                        String speedStr = String.format("%.2f MB/s", speedMbps);
                        String sizeStr = (fileSize > 0)
                                ? String.format("%.1f MB / %.1f MB", downloadMb, totalMb)
                                : String.format("%.1f MB", downloadMb);

                        String eta = "";
                        if (fileSize > 0 && speedMbps > 0) {
                            int remainingSec = (int) ((totalMb - downloadMb) / speedMbps);
                            if (remainingSec > 0)
                                eta = " | ~" + remainingSec + "s remaining";
                        }

                        setProgress("downloading", percent,
                                "Downloading update..." + eta,
                                latestVersion, speedStr, sizeStr);
                    }
                }
                // Final 100% progress for download
                String finalSizeStr = (fileSize > 0)
                        ? String.format("%.1f MB / %.1f MB", totalBytesRead / (1024.0 * 1024.0),
                                fileSize / (1024.0 * 1024.0))
                        : String.format("%.1f MB", totalBytesRead / (1024.0 * 1024.0));
                setProgress("downloading", 100, "Download complete", latestVersion, "", finalSizeStr);
            }

            // Verify the downloaded file exists and has content
            if (!Files.exists(targetPath) || Files.size(targetPath) == 0) {
                setProgress("error", 0, "Update failed: Downloaded file is empty");
                return false;
            }

            // Installation phase
            setProgress("installing", 90, "Installing update...", latestVersion);
            Thread.sleep(500);

            setProgress("installing", 95, "Finalizing installation...", latestVersion);
            // Notify SSE client about installation stage
            sendProgressEvent();

            // Persist the new version BEFORE writing the marker file.
            // This version file lives outside the WAR and survives replacement.
            if (latestVersion != null && !latestVersion.isEmpty()) {
                saveCurrentVersion(latestVersion);
            }

            // Write a marker file so after restart the UI can show "Update Complete!"
            try {
                Files.write(getMarkerPath(), latestVersion.getBytes());
            } catch (Exception ignored) {
            }

            Thread.sleep(500);

            // Shutdown phase
            setProgress("restarting", 100, "Restarting application...", latestVersion);
            // Notify SSE client that we are about to restart
            sendProgressEvent();

            // Spin up a separate thread to shutdown the application after a brief delay
            // This gives the HTTP response time to reach the client
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    System.exit(0);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            setProgress("error", 0, "Update failed: " + e.getMessage());
            return false;
        }
    }
}