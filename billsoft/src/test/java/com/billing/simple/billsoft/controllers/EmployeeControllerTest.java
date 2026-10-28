package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.dtos.EmployeeDTO;
import com.billing.simple.billsoft.entities.AppConfig;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import com.billing.simple.billsoft.repo.EmployeeRepository;
import com.billing.simple.billsoft.repo.PromotionRecordRepository;
import com.billing.simple.billsoft.entities.Employee;
import com.billing.simple.billsoft.entities.PromotionRecord;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Basic integration tests for {@link EmployeeController}.
 * These tests run against the in‑memory H2 database configured for the test profile.
 */
@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@AutoConfigureMockMvc
public class EmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppConfigRepository appConfigRepo;

    @Autowired
    private EmployeeRepository employeeRepo;

    @Autowired
    private PromotionRecordRepository promotionRepo;

    @BeforeEach
    void setUp() {
        // Ensure default PIN is present for firm 1 and global fallback
        AppConfig pinConfig1 = new AppConfig();
        pinConfig1.setConfigKey("EMPLOYEE_MODULE_PIN_1");
        pinConfig1.setConfigValue("0000");
        appConfigRepo.save(pinConfig1);

        AppConfig pinConfig = new AppConfig();
        pinConfig.setConfigKey("EMPLOYEE_MODULE_PIN");
        pinConfig.setConfigValue("0000");
        appConfigRepo.save(pinConfig);
    }

    @Test
    void verifyPin_success() throws Exception {
        mockMvc.perform(post("/api/employees/verify-pin")
                .header("X-Firm-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pin\": \"0000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void accessEmployees_invalidPin_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/employees").param("firmId", "1")
                .header("X-Employee-Pin", "9999"))
                .andExpect(status().isForbidden());
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
                .header("X-Employee-Pin", "0000")
                .header("X-Firm-Id", "1")
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
                .header("X-Employee-Pin", "0000")
                .header("X-Firm-Id", "1")
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
                .header("X-Employee-Pin", "0000")
                .header("X-Firm-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andReturn();
        EmployeeDTO saved = objectMapper.readValue(createResult.getResponse().getContentAsString(), EmployeeDTO.class);

        // Add a promotion scheduled for today but save it as NOT applied
        Employee emp = employeeRepo.findById(saved.getId()).get();
        PromotionRecord record = new PromotionRecord();
        record.setEmployee(emp);
        record.setEffectiveDate(LocalDate.now());
        record.setType("BOTH");
        record.setNewRole("Senior Developer");
        record.setNewSalary(30000.0);
        record.setIsApplied(false);
        promotionRepo.save(record);

        // Apply pending promotions.
        mockMvc.perform(post("/api/employees/apply-promotions")
                .header("X-Employee-Pin", "0000")
                .header("X-Firm-Id", "1")
                .param("firmId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedCount").value(1));
    }

    @Test
    void addPromotion_successful() throws Exception {
        EmployeeDTO dto = new EmployeeDTO();
        dto.setFirmId(1L);
        dto.setName("Promotion Candidate");
        dto.setRole("Junior Dev");
        dto.setMonthlyBaseSalary(15000.0);
        dto.setDateOfJoining(LocalDate.now().minusMonths(3));
        String json = objectMapper.writeValueAsString(dto);

        MvcResult createResult = mockMvc.perform(post("/api/employees")
                .header("X-Employee-Pin", "0000")
                .header("X-Firm-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andReturn();
        EmployeeDTO saved = objectMapper.readValue(createResult.getResponse().getContentAsString(), EmployeeDTO.class);

        String promoJson = "{\"effectiveDate\":\"" + LocalDate.now() + "\",\"newRole\":\"Lead Dev\",\"newSalary\":25000.0,\"reason\":\"Outstanding performance\"}";

        mockMvc.perform(post("/api/employees/" + saved.getId() + "/promotions")
                .header("X-Employee-Pin", "0000")
                .header("X-Firm-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(promoJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.newRole").value("Lead Dev"))
                .andExpect(jsonPath("$.newSalary").value(25000.0))
                .andExpect(jsonPath("$.type").value("BOTH"))
                .andExpect(jsonPath("$.isApplied").value(true));

        Employee updated = employeeRepo.findById(saved.getId()).get();
        assertThat(updated.getRole()).isEqualTo("Lead Dev");
        assertThat(updated.getMonthlyBaseSalary()).isEqualTo(25000.0);
    }

    @Test
    void testPayrollPaisaPrecision_addAdvance_and_processSalary() throws Exception {
        // Create employee with decimal base salary
        Employee emp = new Employee();
        emp.setFirmId(1L);
        emp.setName("Precision Worker");
        emp.setMonthlyBaseSalary(25000.33);
        emp.setCurrentAdvanceBalance(0.0);
        emp.setIsActive(true);
        emp.setDateOfJoining(LocalDate.now());
        emp = employeeRepo.save(emp);

        // 1. Add Advance of 5000.55
        String advanceJson = "{\"amount\":5000.55,\"description\":\"Festival Advance\"}";
        mockMvc.perform(post("/api/employees/" + emp.getId() + "/advances")
                .header("X-Employee-Pin", "0000")
                .header("X-Firm-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(advanceJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(5000.55));

        Employee reloadedEmp = employeeRepo.findById(emp.getId()).get();
        assertThat(reloadedEmp.getCurrentAdvanceBalance()).isEqualTo(5000.55);

        // 2. Process Salary: Base=25000.33, Bonus=1234.56, LeaveDed=500.12, AdvDed=2000.44
        // Expected Net: 25000.33 + 1234.56 - 500.12 - 2000.44 = 23734.33
        String salaryJson = "{" +
                "\"monthYear\":\"09-2026\"," +
                "\"baseSalaryAtTime\":25000.33," +
                "\"bonusAmount\":1234.56," +
                "\"leaveDeductionAmount\":500.12," +
                "\"advanceDeducted\":2000.44" +
                "}";

        mockMvc.perform(post("/api/employees/" + emp.getId() + "/salaries")
                .header("X-Employee-Pin", "0000")
                .header("X-Firm-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(salaryJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.netPaid").value(23734.33))
                .andExpect(jsonPath("$.advanceDeducted").value(2000.44));

        reloadedEmp = employeeRepo.findById(emp.getId()).get();
        assertThat(reloadedEmp.getCurrentAdvanceBalance()).isEqualTo(3000.11);
    }

    @Test
    void getEmployees_withoutPinHeader_isRejectedWith403() throws Exception {
        // Fetch without X-Employee-Pin header — must be rejected with 403 Forbidden
        mockMvc.perform(get("/api/employees")
                .header("X-Firm-Id", "1")
                .param("firmId", "1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Missing Employee Module PIN header (X-Employee-Pin)"));
    }

    @Test
    void getEmployees_withWrongPinHeader_isRejectedWith403() throws Exception {
        // Fetch with incorrect PIN header — must be rejected with 403 Forbidden
        mockMvc.perform(get("/api/employees")
                .header("X-Firm-Id", "1")
                .header("X-Employee-Pin", "9999")
                .param("firmId", "1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Invalid Employee Module PIN"));
    }

    @Test
    void getEmployees_withDefaultPinHeader_succeedsWhenFirmOnDefaultPin() throws Exception {
        // Create an employee for firm 1
        Employee emp = new Employee();
        emp.setFirmId(1L);
        emp.setName("Default Pin Staff");
        emp.setMonthlyBaseSalary(30000.0);
        emp.setDateOfJoining(LocalDate.now());
        employeeRepo.save(emp);

        // Fetch with verified default PIN header — must succeed with 200 OK
        mockMvc.perform(get("/api/employees")
                .header("X-Firm-Id", "1")
                .header("X-Employee-Pin", "0000")
                .param("firmId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.name == 'Default Pin Staff')]").exists());
    }

    @Test
    void getEmployees_emptyList_returnsEmptyArray() throws Exception {
        // Firm 999 has no staff
        mockMvc.perform(get("/api/employees")
                .header("X-Firm-Id", "999")
                .header("X-Employee-Pin", "0000")
                .param("firmId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getEmployees_enforcesFirmDataIsolation() throws Exception {
        Employee empFirm1 = new Employee();
        empFirm1.setFirmId(1L);
        empFirm1.setName("Firm 1 Exclusive Employee");
        empFirm1.setMonthlyBaseSalary(32000.0);
        empFirm1.setDateOfJoining(LocalDate.now());
        employeeRepo.save(empFirm1);

        Employee empFirm2 = new Employee();
        empFirm2.setFirmId(2L);
        empFirm2.setName("Firm 2 Exclusive Employee");
        empFirm2.setMonthlyBaseSalary(42000.0);
        empFirm2.setDateOfJoining(LocalDate.now());
        employeeRepo.save(empFirm2);

        // Fetching for Firm 1 must not contain Firm 2 employee
        mockMvc.perform(get("/api/employees")
                .header("X-Firm-Id", "1")
                .header("X-Employee-Pin", "0000")
                .param("firmId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Firm 1 Exclusive Employee')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'Firm 2 Exclusive Employee')]").doesNotExist());
    }

    @Test
    void accessEmployees_withCustomConfiguredPin_rejectsMissingOrWrongPin() throws Exception {
        // Configure custom PIN for firm 2
        AppConfig customPin = new AppConfig();
        customPin.setConfigKey("EMPLOYEE_MODULE_PIN_2");
        customPin.setConfigValue("7777");
        appConfigRepo.save(customPin);

        // 1. Missing PIN header fails with 403
        mockMvc.perform(get("/api/employees")
                .header("X-Firm-Id", "2")
                .param("firmId", "2"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Missing Employee Module PIN header (X-Employee-Pin)"));

        // 2. Default PIN '0000' fails against custom PIN with 403
        mockMvc.perform(get("/api/employees")
                .header("X-Firm-Id", "2")
                .header("X-Employee-Pin", "0000")
                .param("firmId", "2"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Invalid Employee Module PIN"));

        // 3. Correct custom PIN succeeds
        mockMvc.perform(get("/api/employees")
                .header("X-Firm-Id", "2")
                .header("X-Employee-Pin", "7777")
                .param("firmId", "2"))
                .andExpect(status().isOk());
    }

    @Test
    void changePin_updatesConfiguredPin_andRejectsOldPin() throws Exception {
        // Change PIN for firm 1 from 0000 to 8888
        mockMvc.perform(post("/api/employees/change-pin")
                .header("X-Firm-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("oldPin", "0000", "newPin", "8888"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        // Old PIN '0000' must now be rejected
        mockMvc.perform(get("/api/employees")
                .header("X-Firm-Id", "1")
                .header("X-Employee-Pin", "0000"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Invalid Employee Module PIN"));

        // New PIN '8888' must succeed
        mockMvc.perform(get("/api/employees")
                .header("X-Firm-Id", "1")
                .header("X-Employee-Pin", "8888"))
                .andExpect(status().isOk());
    }

    @Test
    void createAndRetrieveEmployee_persistsExtendedAttributes() throws Exception {
        EmployeeDTO dto = new EmployeeDTO();
        dto.setFirmId(1L);
        dto.setName("Full Attribute Staff");
        dto.setPhone("9988776655");
        dto.setRole("Senior Logistics Lead");
        dto.setMonthlyBaseSalary(48000.0);
        dto.setAllowedPaidLeavesPerMonth(2);
        dto.setDateOfJoining(LocalDate.now());
        dto.setDepartment("Operations & Logistics");
        dto.setDesignation("Logistics Specialist");
        dto.setEmail("logistics@rupeecrm.local");
        dto.setAddress("42 Commercial Complex, Pune");
        dto.setEmergencyContactName("Rahul Contact");
        dto.setEmergencyContactPhone("9911223344");
        dto.setBankAccountName("Full Attribute Staff");
        dto.setBankAccountNumber("123456789012");
        dto.setBankIfscCode("HDFC0001234");
        dto.setBankName("HDFC Bank");

        String json = objectMapper.writeValueAsString(dto);
        MvcResult res = mockMvc.perform(post("/api/employees")
                .header("X-Firm-Id", "1")
                .header("X-Employee-Pin", "0000")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.department").value("Operations & Logistics"))
                .andExpect(jsonPath("$.designation").value("Logistics Specialist"))
                .andExpect(jsonPath("$.email").value("logistics@rupeecrm.local"))
                .andExpect(jsonPath("$.bankName").value("HDFC Bank"))
                .andReturn();

        EmployeeDTO saved = objectMapper.readValue(res.getResponse().getContentAsString(), EmployeeDTO.class);

        // Retrieve by ID
        mockMvc.perform(get("/api/employees/" + saved.getId())
                .header("X-Firm-Id", "1")
                .header("X-Employee-Pin", "0000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.department").value("Operations & Logistics"))
                .andExpect(jsonPath("$.designation").value("Logistics Specialist"))
                .andExpect(jsonPath("$.email").value("logistics@rupeecrm.local"))
                .andExpect(jsonPath("$.bankAccountNumber").value("123456789012"));
    }
}
