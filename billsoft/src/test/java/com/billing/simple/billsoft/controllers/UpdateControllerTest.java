package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.service.UpdateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@AutoConfigureMockMvc
class UpdateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UpdateService service;

    @Test
    void testCheckUpdate() throws Exception {
        Map<String, Object> response = new HashMap<>();
        response.put("updateAvailable", true);
        when(service.checkUpdate(false)).thenReturn(response);

        mockMvc.perform(get("/api/system/update-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updateAvailable").value(true));
    }

    @Test
    void testCheckUpdateForceRefresh() throws Exception {
        Map<String, Object> response = new HashMap<>();
        response.put("updateAvailable", true);
        response.put("latestVersion", "v1.2.0");
        when(service.checkUpdate(true)).thenReturn(response);

        mockMvc.perform(get("/api/system/update-status?force=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updateAvailable").value(true))
                .andExpect(jsonPath("$.latestVersion").value("v1.2.0"));
    }

    @Test
    void testApplyUpdate() throws Exception {
        when(service.applyUpdate(nullable(String.class))).thenReturn(true);

        mockMvc.perform(post("/api/system/apply-update"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    void testApplyUpdateWithTargetVersion() throws Exception {
        when(service.applyUpdate("1.0.327")).thenReturn(true);

        mockMvc.perform(post("/api/system/apply-update?targetVersion=1.0.327"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    void testApplyUpdateConflict() throws Exception {
        when(service.applyUpdate(nullable(String.class))).thenReturn(false);

        mockMvc.perform(post("/api/system/apply-update"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testRemindLater() throws Exception {
        when(service.remindLater()).thenReturn(true);

        mockMvc.perform(post("/api/system/remind-later"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("deferred"));
    }

    @Test
    void testInstallUpdate() throws Exception {
        when(service.installStagedUpdate()).thenReturn(true);

        mockMvc.perform(post("/api/system/install-update"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("restarting"));
    }

    @Test
    void testCreateProgressTicket() throws Exception {
        mockMvc.perform(post("/api/system/update-progress-ticket"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket").isNotEmpty())
                .andExpect(jsonPath("$.expiresInSeconds").value(60));
    }

    @Test
    void testUpdateProgressWithValidTicket() throws Exception {
        when(service.createProgressEmitter()).thenReturn(new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(5000L));

        // 1. Create a ticket
        String responseStr = mockMvc.perform(post("/api/system/update-progress-ticket"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String ticket = new com.fasterxml.jackson.databind.ObjectMapper().readTree(responseStr).get("ticket").asText();

        // 2. Access /update-progress with ticket
        mockMvc.perform(get("/api/system/update-progress?ticket=" + ticket))
                .andExpect(status().isOk());

        // 3. Single-use consumption: same ticket again MUST fail with 401
        mockMvc.perform(get("/api/system/update-progress?ticket=" + ticket))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testUpdateProgressWithInvalidTicketReturns401() throws Exception {
        mockMvc.perform(get("/api/system/update-progress?ticket=invalid-or-forged-ticket-12345"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testCancelUpdateSuccess() throws Exception {
        when(service.cancelUpdate()).thenReturn(true);

        mockMvc.perform(post("/api/system/cancel-update"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"))
                .andExpect(jsonPath("$.message").value("Update download cancelled successfully"));
    }

    @Test
    void testCancelUpdateNoActiveUpdate() throws Exception {
        when(service.cancelUpdate()).thenReturn(false);

        mockMvc.perform(post("/api/system/cancel-update"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testManualUpdateUploadSuccess() throws Exception {
        Map<String, Object> stageRes = new HashMap<>();
        stageRes.put("status", "success");
        stageRes.put("stagedVersion", "1.0.350");
        when(service.stageManualUpdatePackage(any(InputStream.class), anyString(), anyBoolean())).thenReturn(stageRes);

        MockMultipartFile file = new MockMultipartFile("file", "update.zip", "application/zip", "PK-DUMMY".getBytes());

        mockMvc.perform(multipart("/api/system/manual-update/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.stagedVersion").value("1.0.350"));
    }

    @Test
    void testManualUpdateUploadNonZipFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "update.tar.gz", "application/gzip", "DUMMY".getBytes());

        mockMvc.perform(multipart("/api/system/manual-update/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Selected file must be a .zip update package."));
    }

    @Test
    void testManualUpdateStageLocalLoopbackSuccess() throws Exception {
        Path tempZip = Files.createTempFile("rupeecrm-manual-test-", ".zip");
        try {
            Map<String, Object> stageRes = new HashMap<>();
            stageRes.put("status", "success");
            stageRes.put("stagedVersion", "1.0.350");
            when(service.stageManualUpdatePackage(any(Path.class), anyBoolean())).thenReturn(stageRes);

            mockMvc.perform(post("/api/system/manual-update/stage-local")
                            .param("packagePath", tempZip.toAbsolutePath().toString())
                            .with(request -> { request.setRemoteAddr("127.0.0.1"); return request; }))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.stagedVersion").value("1.0.350"));
        } finally {
            Files.deleteIfExists(tempZip);
        }
    }

    @Test
    void testManualUpdateStageLocalNonLoopbackForbidden() throws Exception {
        mockMvc.perform(post("/api/system/manual-update/stage-local")
                        .param("packagePath", "/tmp/any.zip")
                        .with(request -> { request.setRemoteAddr("192.168.1.100"); return request; }))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Local file staging is restricted to loopback connections."));
    }

    @Test
    void testGetUpdateDiagnosticsForBackgroundDownload() throws Exception {
        Map<String, Object> diag = new HashMap<>();
        diag.put("currentVersion", "1.0.329");
        diag.put("updateInProgress", true);
        diag.put("targetVersion", "1.0.335");
        diag.put("status", "DOWNLOADING");

        when(service.getUpdateDiagnostics()).thenReturn(diag);

        mockMvc.perform(get("/api/system/update-diagnostics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersion").value("1.0.329"))
                .andExpect(jsonPath("$.updateInProgress").value(true))
                .andExpect(jsonPath("$.targetVersion").value("1.0.335"))
                .andExpect(jsonPath("$.status").value("DOWNLOADING"));
    }
}
