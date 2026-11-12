package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.service.ApiDiagnosticsService;
import com.billing.simple.billsoft.service.AutoBackupService;
import com.billing.simple.billsoft.service.SystemMetricsService;
import com.billing.simple.billsoft.service.TenantDataIntegrityAuditService;
import com.billing.simple.billsoft.service.NetworkReachabilityService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.sql.DataSource;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class HealthControllerLifecycleTest {

    private HealthController controller;
    private Environment environment;
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        SystemMetricsService metrics = Mockito.mock(SystemMetricsService.class);
        AutoBackupService backup = Mockito.mock(AutoBackupService.class);
        ApiDiagnosticsService diag = Mockito.mock(ApiDiagnosticsService.class);
        dataSource = Mockito.mock(DataSource.class);
        TenantDataIntegrityAuditService audit = Mockito.mock(TenantDataIntegrityAuditService.class);
        NetworkReachabilityService reach = Mockito.mock(NetworkReachabilityService.class);
        environment = Mockito.mock(Environment.class);

        when(environment.getProperty("app.version", "v1.0.0")).thenReturn("v1.0.308");
        when(environment.getProperty("app.build.commit", "HEAD")).thenReturn("f8b2320");

        controller = new HealthController(
                metrics, backup, diag, dataSource, audit, reach, null, environment
        );
    }

    @Test
    void testHealthEndpointIncludesAuthoritativeVersionAndIdentity() {
        ResponseEntity<Map<String, Object>> resp = controller.health();
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> body = resp.getBody();
        assertNotNull(body);
        assertEquals("UP", body.get("status"));
        assertEquals("v1.0.308", body.get("version"));
        assertEquals("RupeeCRM", body.get("service"));
        assertEquals("f8b2320", body.get("commit"));
        assertNotNull(body.get("pid"));
    }

    @Test
    void testRuntimeIdentityPermittedFromLocalLoopback() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        ResponseEntity<Map<String, Object>> resp = controller.runtimeIdentity(request);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> body = resp.getBody();
        assertNotNull(body);
        assertEquals("v1.0.308", body.get("version"));
        assertEquals("f8b2320", body.get("commit"));
        assertEquals("RupeeCRM", body.get("service"));
        assertEquals(true, body.get("authoritative"));
        assertNotNull(body.get("jvmVersion"));
    }

    @Test
    void testRuntimeIdentityDeniedFromRemoteAddress() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");

        ResponseEntity<Map<String, Object>> resp = controller.runtimeIdentity(request);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        assertTrue(resp.getBody().containsKey("error"));
    }

    @Test
    void testShutdownPermittedFromLocalLoopback() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        ResponseEntity<Map<String, Object>> resp = controller.shutdown(request);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> body = resp.getBody();
        assertNotNull(body);
        assertEquals("SHUTTING_DOWN", body.get("status"));
        assertNotNull(body.get("pid"));
    }

    @Test
    void testShutdownDeniedFromRemoteAddress() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("10.0.0.5");

        ResponseEntity<Map<String, Object>> resp = controller.shutdown(request);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        assertTrue(resp.getBody().containsKey("error"));
    }
}

