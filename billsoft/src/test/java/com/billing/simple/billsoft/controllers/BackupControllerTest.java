package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.service.BackupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@AutoConfigureMockMvc
class BackupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BackupService service;

    @Test
    void testExport() throws Exception {
        BackupDTO backup = new BackupDTO();
        backup.setMetadata(new HashMap<>());
        when(service.exportData(1L)).thenReturn(backup);

        mockMvc.perform(get("/api/backup/export").param("firmId", "1"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Disposition"));
    }

    @Test
    void testExportAll() throws Exception {
        BackupDTO backup = new BackupDTO();
        backup.setMetadata(new HashMap<>());
        when(service.exportAllData()).thenReturn(backup);

        mockMvc.perform(get("/api/backup/export/all"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Disposition"));
    }

    @Test
    void testAutoBackupStatus() throws Exception {
        mockMvc.perform(get("/api/backup/auto/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backupDir").exists());
    }

    @Test
    void testRunAutoBackupNow() throws Exception {
        BackupDTO backup = new BackupDTO();
        backup.setMetadata(new HashMap<>());
        when(service.exportAllData()).thenReturn(backup);

        mockMvc.perform(post("/api/backup/auto/run-now"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HEALTHY"));
    }

    @Test
    void testAutoInspectAndRestore() throws Exception {
        BackupDTO backup = new BackupDTO();
        backup.setMetadata(new HashMap<>());
        com.billing.simple.billsoft.entities.FirmDetails firm = new com.billing.simple.billsoft.entities.FirmDetails();
        firm.setId(1L);
        firm.setFirmName("Auto Firm");
        backup.setFirmDetails(firm);
        when(service.exportAllData()).thenReturn(backup);

        // Ensure auto backup exists
        mockMvc.perform(post("/api/backup/auto/run-now"))
                .andExpect(status().isOk());

        com.billing.simple.billsoft.dto.BackupInspectionDTO mockInspection = new com.billing.simple.billsoft.dto.BackupInspectionDTO();
        mockInspection.setBackupType("SINGLE_FIRM");
        when(service.inspectBackup(any())).thenReturn(mockInspection);
        when(service.importSelectiveData(any(), any(), any(), any())).thenReturn(java.util.Collections.singletonList(firm));

        // Test auto inspect
        mockMvc.perform(get("/api/backup/auto/inspect"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backupType").value("SINGLE_FIRM"));

        // Test auto restore
        mockMvc.perform(post("/api/backup/auto/restore")
                .param("mode", "clone")
                .param("firmIds", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    void testImport() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.json", "application/json", "{\"metadata\":{}}".getBytes());
        doNothing().when(service).importData(any(), any(), anyBoolean());

        mockMvc.perform(multipart("/api/backup/import")
                .file(file)
                .param("mode", "merge"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    void testImport_MultiFirmWithoutSourceFirmId_ReturnsHttp400BadRequest() throws Exception {
        String multiFirmJson = "{\"allFirms\":[{\"id\":1,\"firmName\":\"F1\"},{\"id\":2,\"firmName\":\"F2\"}]}";
        MockMultipartFile file = new MockMultipartFile("file", "multifirm.json", "application/json", multiFirmJson.getBytes());

        mockMvc.perform(multipart("/api/backup/import")
                .file(file)
                .param("firmId", "1")
                .param("mode", "merge"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.errorCode").value("AMBIGUOUS_MULTI_FIRM_BACKUP"));
    }

    @Test
    void testImport_WhenRestoreConflictException_ReturnsHttp409ConflictWithStructuredJson() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "conflict.json", "application/json", "{\"metadata\":{}}".getBytes());
        org.mockito.Mockito.doThrow(new com.billing.simple.billsoft.exception.RestoreConflictException(
                "INVOICE", "INV-2026-001", "Invoice number INV-2026-001 already exists in target firm"
        )).when(service).importData(any(), any(), anyBoolean());

        mockMvc.perform(multipart("/api/backup/import")
                .file(file)
                .param("mode", "merge"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("conflict"))
                .andExpect(jsonPath("$.entityType").value("INVOICE"))
                .andExpect(jsonPath("$.conflictKey").value("INV-2026-001"))
                .andExpect(jsonPath("$.error").value("Invoice number INV-2026-001 already exists in target firm"));
    }
}
