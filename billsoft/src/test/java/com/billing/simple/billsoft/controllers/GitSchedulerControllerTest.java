package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.service.scheduler.CheckType;
import com.billing.simple.billsoft.service.scheduler.GitCheckExecutionResult;
import com.billing.simple.billsoft.service.scheduler.GitCheckSchedulerService;
import com.billing.simple.billsoft.service.scheduler.GitCheckState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class GitSchedulerControllerTest {

    private GitCheckSchedulerService scheduler;
    private GitSchedulerController controller;

    @Test
    void testGetStatus_ReturnsAllChecksWithIntervalsAndStatus() {
        scheduler = new GitCheckSchedulerService(Clock.fixed(Instant.parse("2026-11-19T12:00:00Z"), ZoneOffset.UTC), null, null);
        controller = new GitSchedulerController(scheduler);

        ResponseEntity<Map<String, Object>> response = controller.getSchedulerStatus();
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());

        Map<String, Object> body = response.getBody();
        assertTrue(body.containsKey("timestamp"));
        assertTrue(body.containsKey("checks"));

        @SuppressWarnings("unchecked")
        Map<String, Object> checks = (Map<String, Object>) body.get("checks");
        assertTrue(checks.containsKey("LICENSE"));
        assertTrue(checks.containsKey("ANNOUNCEMENTS"));
        assertTrue(checks.containsKey("DATA_PROTECTION"));
        assertTrue(checks.containsKey("APP_UPDATES"));

        @SuppressWarnings("unchecked")
        Map<String, Object> ann = (Map<String, Object>) checks.get("ANNOUNCEMENTS");
        assertEquals(24L, ((Number) ann.get("intervalHours")).longValue());
        assertTrue((Boolean) ann.get("isDue"));
    }

    @Test
    void testRefreshCheck_ValidType_InvokesHandler() {
        scheduler = new GitCheckSchedulerService(Clock.fixed(Instant.parse("2026-11-19T12:00:00Z"), ZoneOffset.UTC), null, null);
        scheduler.registerHandler(CheckType.ANNOUNCEMENTS, (type, etag) -> GitCheckExecutionResult.success("etag-abc"));
        controller = new GitSchedulerController(scheduler);

        ResponseEntity<Map<String, Object>> response = controller.refreshCheck("ANNOUNCEMENTS", true);
        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertTrue((Boolean) body.get("success"));
        assertEquals("SUCCESS", body.get("resultStatus"));
        assertEquals("etag-abc", body.get("etag"));
    }

    @Test
    void testRefreshCheck_InvalidType_ReturnsBadRequest() {
        scheduler = new GitCheckSchedulerService(Clock.fixed(Instant.parse("2026-11-19T12:00:00Z"), ZoneOffset.UTC), null, null);
        controller = new GitSchedulerController(scheduler);

        ResponseEntity<Map<String, Object>> response = controller.refreshCheck("UNKNOWN_TYPE", true);
        assertEquals(400, response.getStatusCode().value());
        assertFalse((Boolean) response.getBody().get("success"));
    }
}

