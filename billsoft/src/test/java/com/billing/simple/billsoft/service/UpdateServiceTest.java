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
        mockRelease.put("body", "Release notes");
        mockRelease.put("assets", Collections.singletonList(
                Collections.singletonMap("browser_download_url", "http://example.com/update.war")
        ));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(mockRelease));
        Map<String, Object> result = service.checkUpdate();
        assertNotNull(result);
        assertEquals("v1.1.0", result.get("latestVersion"));
        assertTrue((Boolean) result.get("updateAvailable"));
    }

    @Test
    void testCheckUpdateNoNewer() {
        Map<String, Object> mockRelease = new HashMap<>();
        mockRelease.put("tag_name", "v1.0.0");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(mockRelease));
        Map<String, Object> result = service.checkUpdate();
        assertNotNull(result);
        assertFalse((Boolean) result.get("updateAvailable"));
    }

    @Test
    void testCheckUpdateError() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("API error"));
        Map<String, Object> result = service.checkUpdate();
        assertNotNull(result);
        assertFalse((Boolean) result.get("updateAvailable"));
        assertTrue(result.containsKey("error"));
    }
}
