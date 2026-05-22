package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.dtos.EmployeeDTO;
import com.billing.simple.billsoft.entities.AppConfig;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Basic integration tests for {@link EmployeeController}.
 * These tests run against the in‑memory H2 database configured for the test profile.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class EmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppConfigRepository appConfigRepo;

    @BeforeEach
    void setUp() {
        // Ensure default PIN is present for the PIN endpoints.
        AppConfig pinConfig = new AppConfig();
        pinConfig.setConfigKey("EMPLOYEE_MODULE_PIN");
        pinConfig.setConfigValue("0000");
        appConfigRepo.save(pinConfig);
    }

    @Test
    void verifyPin_success() throws Exception {
        mockMvc.perform(post("/api/employees/verify-pin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pin\": \"0000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void createEmployee_missingName_returnsBadRequest() throws Exception {
        EmployeeDTO dto = new EmployeeDTO();
        dto.setFirmId(1L);
        dto.setName(""); // blank name
        dto.setMonthlyBaseSalary(20000.0);
        dto.setDateOfJoining(LocalDate.now());
        String json = objectMapper.writeValueAsString(dto);
        mockMvc.perform(post("/api/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createEmployee_successful() throws Exception {
        EmployeeDTO dto = new EmployeeDTO();
        dto.setFirmId(1L);
        dto.setName("John Doe");
        dto.setPhone("+919876543210");
        dto.setRole("Developer");
        dto.setMonthlyBaseSalary(25000.0);
        dto.setAllowedPaidLeavesPerMonth(2);
        dto.setDateOfJoining(LocalDate.now());
        String json = objectMapper.writeValueAsString(dto);
        MvcResult result = mockMvc.perform(post("/api/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andReturn();
        // Verify that the returned DTO contains the same name.
        String response = result.getResponse().getContentAsString();
        EmployeeDTO saved = objectMapper.readValue(response, EmployeeDTO.class);
        assertThat(saved.getName()).isEqualTo("John Doe");
        assertThat(saved.getDateOfJoining()).isEqualTo(LocalDate.now());
    }

    @Test
    void applyPendingPromotions_returnsCount() throws Exception {
        // Create an employee first.
        EmployeeDTO dto = new EmployeeDTO();
        dto.setFirmId(1L);
        dto.setName("Jane Smith");
        dto.setMonthlyBaseSalary(22000.0);
        dto.setDateOfJoining(LocalDate.now().minusMonths(6));
        String json = objectMapper.writeValueAsString(dto);
        MvcResult createResult = mockMvc.perform(post("/api/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andReturn();
        EmployeeDTO saved = objectMapper.readValue(createResult.getResponse().getContentAsString(), EmployeeDTO.class);

        // Add a promotion scheduled for today.
        String promotionJson = "{\"effectiveDate\": \"" + LocalDate.now().toString() + "\", \"type\": \"BOTH\", \"newRole\": \"Senior Developer\", \"newSalary\": 30000, \"reason\": \"Annual Increment\"}";
        mockMvc.perform(post("/api/employees/" + saved.getId() + "/promotions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(promotionJson))
                .andExpect(status().isOk());

        // Apply pending promotions.
        mockMvc.perform(post("/api/employees/apply-promotions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedCount").value(1));
    }
}
