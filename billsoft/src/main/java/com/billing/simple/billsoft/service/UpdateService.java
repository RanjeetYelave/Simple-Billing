package com.billing.simple.billsoft.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UpdateService {

    @Value("${app.version:v1.0.0}")
    private String currentVersion;

    private static final String GITHUB_API_URL = "https://api.github.com/repos/RanjeetYelave/Simple-Billing/releases/latest";

    public Map<String, Object> checkUpdate() {
        Map<String, Object> response = new HashMap<>();
        response.put("currentVersion", currentVersion);
        
        try {
            RestTemplate restTemplate = new RestTemplate();
            @SuppressWarnings("unchecked")
            Map<String, Object> githubRelease = restTemplate.getForObject(GITHUB_API_URL, Map.class);
            
            if (githubRelease != null && githubRelease.containsKey("tag_name")) {
                String latestVersion = (String) githubRelease.get("tag_name");
                response.put("latestVersion", latestVersion);
                
                boolean updateAvailable = !currentVersion.equals(latestVersion);
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

    public boolean applyUpdate(String downloadUrl) {
        try {
            java.net.URL url = new java.net.URI(downloadUrl).toURL();
            java.nio.file.Path targetPath = java.nio.file.Paths.get("billsoft-update.war");
            
            // Download the file
            try (java.io.InputStream in = url.openStream()) {
                java.nio.file.Files.copy(in, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            
            // Spin up a separate thread to shutdown the application after a brief delay
            // This gives the HTTP response time to reach the client
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    System.exit(0);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
            
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
