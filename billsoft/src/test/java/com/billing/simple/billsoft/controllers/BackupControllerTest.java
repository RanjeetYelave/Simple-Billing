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
                .andExpect(jsonPath("$.status").value("SUCCESS"));
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
}
