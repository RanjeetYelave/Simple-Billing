package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.BillsoftApplication;
import com.billing.simple.billsoft.service.BackupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.nio.file.Files;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BillsoftApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class BackupTestDatasetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BackupService backupService;

    private File datasetFile;

    @BeforeEach
    void setup() {
        backupService.factoryReset();
        File f1 = new File("RupeeCRM_10_Firms_Comprehensive_Test_Data.json");
        File f2 = new File("../RupeeCRM_10_Firms_Comprehensive_Test_Data.json");
        File f3 = new File("/Users/afk/Documents/GitHub/Simple-Billing/RupeeCRM_10_Firms_Comprehensive_Test_Data.json");
        if (f1.exists()) datasetFile = f1;
        else if (f2.exists()) datasetFile = f2;
        else datasetFile = f3;
    }

    @Test
    @DisplayName("Verify /api/backup/import/test-dataset endpoint using filePath")
    public void testImportViaFilePath() throws Exception {
        mockMvc.perform(post("/api/backup/import/test-dataset")
                .param("filePath", datasetFile.getAbsolutePath())
                .param("firmIds", "1,2")
                .param("mode", "clone")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.importedFirms.length()").value(2))
                .andExpect(jsonPath("$.totalStats.totalFirms").value(10))
                .andExpect(jsonPath("$.totalStats.totalCustomers").value(1500))
                .andExpect(jsonPath("$.totalStats.totalProducts").value(3000))
                .andExpect(jsonPath("$.totalStats.totalInvoices").value(5000));
    }
}
