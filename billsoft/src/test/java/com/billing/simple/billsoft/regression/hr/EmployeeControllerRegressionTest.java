package com.billing.simple.billsoft.regression.hr;

import com.billing.simple.billsoft.dtos.EmployeeDTO;
import com.billing.simple.billsoft.entities.Employee;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.repo.EmployeeRepository;
import com.billing.simple.billsoft.service.BackupService;
import com.billing.simple.billsoft.service.FirmDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Tag("regression")
@Tag("integration")
@DisplayName("Employee Controller & HR Module API Regression Tests")
class EmployeeControllerRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeRepository employeeRepo;

    @Autowired
    private FirmDetailsService firmService;

    @Autowired
    private BackupService backupService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String PIN_HEADER = "X-Employee-Pin";
    private static final String DEFAULT_PIN = "0000";
    private final Long testFirmId = 1L;

    @BeforeEach
    void setUp() {
        backupService.factoryReset();

        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Global Tech Industries");
        firm.setGstin("27AAACG1234D1Z5");
        firm.setAddressLine1("Tech Park Phase 1");
        firmService.create(firm);
    }

    @Test
    @DisplayName("Should verify and change employee module PIN")
    void testPinManagement() throws Exception {
        // 1. Verify correct PIN
        mockMvc.perform(post("/api/employees/verify-pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("pin", "0000"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));

        // 2. Change PIN
        mockMvc.perform(post("/api/employees/change-pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(PIN_HEADER, "0000")
                        .content(objectMapper.writeValueAsString(Map.of("oldPin", "0000", "newPin", "1234"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        // 3. Verify with new PIN
        mockMvc.perform(post("/api/employees/verify-pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("pin", "1234"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    @DisplayName("Should perform complete Employee CRUD operations")
    void testEmployeeCrudOperations() throws Exception {
        EmployeeDTO dto = new EmployeeDTO();
        dto.setName("Aditi Rao");
        dto.setPhone("9876501234");
        dto.setRole("Accountant");
        dto.setDateOfJoining(LocalDate.now().minusMonths(3));
        dto.setMonthlyBaseSalary(45000.0);
        dto.setAllowedPaidLeavesPerMonth(2);
        dto.setFirmId(testFirmId);

        // 1. Create Employee
        String createRes = mockMvc.perform(post("/api/employees")
                        .header(PIN_HEADER, DEFAULT_PIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Aditi Rao"))
                .andReturn().getResponse().getContentAsString();

        Long empId = objectMapper.readTree(createRes).get("id").asLong();

        // 2. Get Employees
        mockMvc.perform(get("/api/employees?firmId=" + testFirmId)
                        .header(PIN_HEADER, DEFAULT_PIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].monthlyBaseSalary").value(45000.0));

        // 3. Update Employee
        dto.setMonthlyBaseSalary(50000.0);
        mockMvc.perform(put("/api/employees/" + empId)
                        .header(PIN_HEADER, DEFAULT_PIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyBaseSalary").value(50000.0));

        // 4. List all employees for firm
        mockMvc.perform(get("/api/employees?firmId=" + testFirmId)
                        .header(PIN_HEADER, DEFAULT_PIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("Aditi Rao"));

        // 5. Delete Employee
        mockMvc.perform(delete("/api/employees/" + empId)
                        .header(PIN_HEADER, DEFAULT_PIN))
                .andExpect(status().isOk());

        assertThat(employeeRepo.existsById(empId)).isFalse();
    }

    @Test
    @DisplayName("Should record daily attendance, mark leaves, grant advances, and compute salary")
    void testAttendanceAdvanceAndSalaryLifecycle() throws Exception {
        // Setup Employee
        Employee emp = new Employee();
        emp.setName("Rohan Kulkarni");
        emp.setPhone("9822001122");
        emp.setMonthlyBaseSalary(60000.00);
        emp.setAllowedPaidLeavesPerMonth(2);
        emp.setFirmId(testFirmId);
        emp.setIsActive(true);
        emp.setDateOfJoining(LocalDate.now().minusYears(1));
        emp = employeeRepo.save(emp);
        Long empId = emp.getId();

        // 1. Record Attendance
        Map<String, Object> attPayload = Map.of(
                "date", LocalDate.now().toString(),
                "status", "PRESENT",
                "remarks", "On-time arrival"
        );
        mockMvc.perform(post("/api/employees/" + empId + "/attendance")
                        .header(PIN_HEADER, DEFAULT_PIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(attPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PRESENT"));

        // 2. Query Monthly Attendance
        int year = LocalDate.now().getYear();
        int month = LocalDate.now().getMonthValue();
        mockMvc.perform(get("/api/employees/" + empId + "/attendance/month/" + year + "/" + month)
                        .header(PIN_HEADER, DEFAULT_PIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // 3. Grant Salary Advance
        Map<String, Object> advanceReq = Map.of(
                "amount", 10000.0,
                "date", LocalDate.now().minusDays(5).toString(),
                "description", "Medical advance"
        );
        mockMvc.perform(post("/api/employees/" + empId + "/advances")
                        .header(PIN_HEADER, DEFAULT_PIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(advanceReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(10000.0));

        // Verify balance updated
        mockMvc.perform(get("/api/employees?firmId=" + testFirmId)
                        .header(PIN_HEADER, DEFAULT_PIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currentAdvanceBalance").value(10000.0));

        // 4. Record Salary Payout
        Map<String, Object> payoutReq = Map.of(
                "monthYear", String.format("%02d-%d", month, year),
                "baseSalaryAtTime", 60000.0,
                "advanceDeducted", 5000.0,
                "bonusAmount", 2000.0,
                "paymentDate", LocalDate.now().toString()
        );
        String payoutRes = mockMvc.perform(post("/api/employees/" + empId + "/salaries")
                        .header(PIN_HEADER, DEFAULT_PIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payoutReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.netPaid").value(57000.0))
                .andReturn().getResponse().getContentAsString();

        Long salaryId = objectMapper.readTree(payoutRes).get("id").asLong();

        // 5. Download Payslip PDF
        mockMvc.perform(get("/api/employees/" + empId + "/salaries/" + salaryId + "/payslip")
                        .header(PIN_HEADER, DEFAULT_PIN))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));

        // 6. Record Promotion
        Map<String, Object> promoReq = Map.of(
                "date", LocalDate.now().toString(),
                "previousSalary", 60000.0,
                "newSalary", 75000.0,
                "previousDesignation", "Junior Accountant",
                "newDesignation", "Senior Accountant",
                "notes", "Annual Appraisal"
        );
        mockMvc.perform(post("/api/employees/" + empId + "/promotions")
                        .header(PIN_HEADER, DEFAULT_PIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(promoReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newSalary").value(75000.0));

        // 8. Apply Leave
        Map<String, Object> leaveReq = Map.of(
                "startDate", LocalDate.now().plusDays(1).toString(),
                "endDate", LocalDate.now().plusDays(2).toString(),
                "type", "CASUAL",
                "reason", "Personal work",
                "totalDays", 2
        );
        mockMvc.perform(post("/api/employees/" + empId + "/leaves")
                        .header(PIN_HEADER, DEFAULT_PIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(leaveReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDays").value(2));
    }
}
