package com.billing.simple.billsoft.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class UpdateServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private UpdateService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new UpdateService(restTemplate);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "defaultVersion", "v1.0.0");
    }

    @Test
    void testCheckUpdate() {
        Map<String, Object> mockRelease = new HashMap<>();
        mockRelease.put("tag_name", "v1.1.0");
        mockRelease.put("body", "## What's New\nAdded Smart Reorder.\n\n## Regression Test Summary\n| Total | 277 |");
        mockRelease.put("assets", Collections.singletonList(
                new HashMap<String, Object>() {{
                    put("name", "billsoft.war");
                    put("browser_download_url", "http://example.com/billsoft.war");
                }}
        ));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Object.class)))
                .thenReturn(ResponseEntity.ok(mockRelease));
        Map<String, Object> result = service.checkUpdate();
        assertNotNull(result);
        assertEquals("v1.1.0", result.get("latestVersion"));
        assertTrue((Boolean) result.get("updateAvailable"));
        assertEquals("Added Smart Reorder.", result.get("releaseNotes"));
    }

    @Test
    void testCheckUpdateSkippedReleasesDirectJump() {
        // Customer installed v1.0.0, available: v1.1.0, v1.2.0, v1.3.0
        Map<String, Object> rel1 = new HashMap<>();
        rel1.put("tag_name", "v1.1.0");
        rel1.put("body", "## What's New\nv1.1 features");
        rel1.put("assets", Collections.singletonList(
                new HashMap<String, Object>() {{
                    put("name", "billsoft.war");
                    put("browser_download_url", "http://example.com/v1.1/billsoft.war");
                }}
        ));

        Map<String, Object> rel2 = new HashMap<>();
        rel2.put("tag_name", "v1.2.0");
        rel2.put("body", "## What's New\nv1.2 features");
        rel2.put("assets", Collections.singletonList(
                new HashMap<String, Object>() {{
                    put("name", "billsoft.war");
                    put("browser_download_url", "http://example.com/v1.2/billsoft.war");
                }}
        ));

        Map<String, Object> rel3 = new HashMap<>();
        rel3.put("tag_name", "v1.3.0");
        rel3.put("body", "## What's New\n* Added Smart Reorder\n* Improved PDF generation\n\n## Regression Test Summary\n| Total | 570 |");
        rel3.put("assets", Collections.singletonList(
                new HashMap<String, Object>() {{
                    put("name", "billsoft.war");
                    put("browser_download_url", "http://example.com/v1.3/billsoft.war");
                }}
        ));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Object.class)))
                .thenReturn(ResponseEntity.ok(java.util.Arrays.asList(rel1, rel2, rel3)));

        Map<String, Object> result = service.checkUpdate();
        assertNotNull(result);
        assertEquals("v1.3.0", result.get("latestVersion"));
        assertTrue((Boolean) result.get("updateAvailable"));
        assertEquals("* Added Smart Reorder\n* Improved PDF generation", result.get("releaseNotes"));
    }

    @Test
    void testCheckUpdateNoNewerInstalledEqualsLatest() {
        Map<String, Object> mockRelease = new HashMap<>();
        mockRelease.put("tag_name", "v1.0.0");
        mockRelease.put("body", "Initial Release");
        mockRelease.put("assets", Collections.singletonList(
                new HashMap<String, Object>() {{
                    put("name", "billsoft.war");
                    put("browser_download_url", "http://example.com/billsoft.war");
                }}
        ));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Object.class)))
                .thenReturn(ResponseEntity.ok(mockRelease));
        Map<String, Object> result = service.checkUpdate();
        assertNotNull(result);
        assertFalse((Boolean) result.get("updateAvailable"));
        assertEquals("v1.0.0", result.get("latestVersion"));
    }

    @Test
    void testCheckUpdateDowngradePreventionInstalledNewerThanLatest() {
        // Installed is v1.0.0 (or simulated higher), release is v0.9.5
        Map<String, Object> mockRelease = new HashMap<>();
        mockRelease.put("tag_name", "v0.9.5");
        mockRelease.put("body", "Older version");
        mockRelease.put("assets", Collections.singletonList(
                new HashMap<String, Object>() {{
                    put("name", "billsoft.war");
                    put("browser_download_url", "http://example.com/billsoft.war");
                }}
        ));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Object.class)))
                .thenReturn(ResponseEntity.ok(mockRelease));
        Map<String, Object> result = service.checkUpdate();
        assertNotNull(result);
        assertFalse((Boolean) result.get("updateAvailable"));
    }

    @Test
    void testCompareVersionsSemantics() {
        assertTrue(UpdateService.compareVersions("v1.0.10", "v1.0.9") > 0);
        assertTrue(UpdateService.compareVersions("v1.1.0", "v1.0.9") > 0);
        assertTrue(UpdateService.compareVersions("2.0.0", "1.9.99") > 0);
        assertTrue(UpdateService.compareVersions("v1.0.0", "1.0.0") == 0);
        assertTrue(UpdateService.compareVersions("v1.2", "v1.2.0") == 0);
        assertTrue(UpdateService.compareVersions("v1.0.1", "v1.0.2") < 0);
        assertTrue(UpdateService.compareVersions("v1.0.0", "v1.0.0-SNAPSHOT") > 0);
        assertTrue(UpdateService.compareVersions("v1.0.0-SNAPSHOT", "v1.0.0") < 0);
    }

    @Test
    void testExtractCustomerChangelog() {
        String fullReleaseBody = "## What's New\n\nAdded Smart Reorder with multi-vendor PO support.\n\nImproved invoice PDF generation.\nFixed stock calculation issue.\n\n## Regression Test Summary\n| Test Area | Tests | Passed |\n|---|---:|---:|\n| Total | 570 | 570 |\n\n### 📦 Distribution Artifacts\n- RupeeCRM-Setup.msi";
        String extracted = UpdateService.extractCustomerChangelog(fullReleaseBody);
        assertEquals("Added Smart Reorder with multi-vendor PO support.\n\nImproved invoice PDF generation.\nFixed stock calculation issue.", extracted);

        // Fallback when no markdown headers
        String simpleBody = "Bugfixes and performance improvements.";
        assertEquals("Bugfixes and performance improvements.", UpdateService.extractCustomerChangelog(simpleBody));

        // Empty body
        assertEquals("", UpdateService.extractCustomerChangelog(""));
        assertEquals("", UpdateService.extractCustomerChangelog(null));
    }

    @Test
    void testCheckUpdateInvalidReleaseSkippedIfNoWarAsset() {
        Map<String, Object> invalidRel = new HashMap<>();
        invalidRel.put("tag_name", "v2.0.0");
        invalidRel.put("body", "Draft release without assets");
        invalidRel.put("assets", Collections.emptyList());

        Map<String, Object> validRel = new HashMap<>();
        validRel.put("tag_name", "v1.2.0");
        validRel.put("body", "Valid release");
        validRel.put("assets", Collections.singletonList(
                new HashMap<String, Object>() {{
                    put("name", "billsoft.war");
                    put("browser_download_url", "http://example.com/v1.2/billsoft.war");
                }}
        ));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Object.class)))
                .thenReturn(ResponseEntity.ok(java.util.Arrays.asList(invalidRel, validRel)));

        Map<String, Object> result = service.checkUpdate();
        assertNotNull(result);
        assertEquals("v1.2.0", result.get("latestVersion"));
        assertTrue((Boolean) result.get("updateAvailable"));
    }

    @Test
    void testCheckUpdateError() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Object.class)))
                .thenThrow(new RuntimeException("API error"));
        Map<String, Object> result = service.checkUpdate();
        assertNotNull(result);
        assertFalse((Boolean) result.get("updateAvailable"));
        assertTrue(result.containsKey("error"));
    }
}
