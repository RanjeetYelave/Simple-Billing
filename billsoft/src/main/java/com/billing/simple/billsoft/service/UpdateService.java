package com.billing.simple.billsoft.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
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
        } catch (Exception ignored) {}
        return defaultVersion;
    }

    /**
     * Persist the new version to the version file so it survives WAR replacement.
     */
    private void saveCurrentVersion(String version) {
        try {
            Files.write(Paths.get(VERSION_FILE), version.getBytes());
        } catch (Exception ignored) {}
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
                progressEmitter.send(SseEmitter.event().name("progress").data(getProgress(), MediaType.APPLICATION_JSON));
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
        return p;
    }

    private void setProgress(String status, int percent, String message) {
        progressStatus.set(status);
        progressPercent.set(percent);
        progressMessage.set(message);
        // Push update to any listening SSE client
        sendProgressEvent();
    }

    private void setProgress(String status, int percent, String message, String latestVersion) {
        progressStatus.set(status);
        progressPercent.set(percent);
        progressMessage.set(message);
        progressLatestVersion.set(latestVersion);
        // Push update to any listening SSE client
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
            @SuppressWarnings("unchecked")
            Map<String, Object> githubRelease = restTemplate.getForObject(GITHUB_API_URL, Map.class);

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
                    response.put("downloadUrl", assets.get(0).get("browser_download_url"));
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
        if (version == null) return "";
        return version.replaceAll("^[vV]", "").trim();
    }

    public boolean applyUpdate(String downloadUrl) {
        try {
            String latestVersion = "";
            // Try to get the latest version from the release for display purposes
            try {
                RestTemplate restTemplate = new RestTemplate();
                @SuppressWarnings("unchecked")
                Map<String, Object> githubRelease = restTemplate.getForObject(GITHUB_API_URL, Map.class);
                if (githubRelease != null && githubRelease.containsKey("tag_name")) {
                    latestVersion = (String) githubRelease.get("tag_name");
                }
            } catch (Exception ignored) {}

            setProgress("downloading", 0, "Starting download...", latestVersion);
            // Ensure SSE client receives the initial state
            sendProgressEvent();
            java.net.URL url = new java.net.URI(downloadUrl).toURL();
            Path targetPath = Paths.get("billsoft-update.war");

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
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;

                    if (fileSize > 0) {
                        int percent = (int) (totalBytesRead * 100 / fileSize);
                        if (percent != lastPercent && percent % 5 == 0) {
                            lastPercent = percent;
                            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                            int downloadKb = (int) (totalBytesRead / 1024);
                            int totalKb = fileSize / 1024;
                            int speedKbps = elapsed > 0 ? downloadKb / (int) elapsed : 0;
                            int remainingSec = speedKbps > 0 ? (totalKb - downloadKb) / speedKbps : 0;
                            String eta = remainingSec > 0 ? " | ~" + remainingSec + "s remaining" : "";
                            setProgress("downloading", percent,
                                    "Downloading... " + percent + "% (" + downloadKb + "KB / " + totalKb + "KB" + eta + ")");
                        }
                    }
                }
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
            } catch (Exception ignored) {}

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