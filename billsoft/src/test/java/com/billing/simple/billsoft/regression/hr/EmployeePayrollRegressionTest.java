package com.billing.simple.billsoft.regression.hr;

import com.billing.simple.billsoft.controllers.EmployeeController;
import com.billing.simple.billsoft.entities.Employee;
import com.billing.simple.billsoft.entities.EmployeeAdvance;
import com.billing.simple.billsoft.entities.SalaryRecord;
import com.billing.simple.billsoft.repo.EmployeeAdvanceRepository;
import com.billing.simple.billsoft.repo.EmployeeRepository;
import com.billing.simple.billsoft.repo.SalaryRecordRepository;
import com.billing.simple.billsoft.service.EmployeePdfService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Tag("regression")
@Tag("integration")
@DisplayName("Employee Payroll & Advance Deduction Regression Tests")
class EmployeePayrollRegressionTest {

    @Autowired
    private EmployeeController employeeController;

    @Autowired
    private EmployeePdfService employeePdfService;

    @Autowired
    private EmployeeRepository employeeRepo;

    @Autowired
    private SalaryRecordRepository salaryRepo;

    @Autowired
    private EmployeeAdvanceRepository advanceRepo;

    @Autowired
    private com.billing.simple.billsoft.service.BackupService backupService;

    private Employee testEmployee;
    private final Long testFirmId = 1L;

    @BeforeEach
    void setUp() {
        backupService.factoryReset();

        Employee emp = new Employee();
        emp.setName("Vikramaditya Sharma");
        emp.setDesignation("Senior Software Engineer");
        emp.setDepartment("Engineering");
        emp.setPhone("9988776655");
        emp.setEmail("vikram@company.com");
        emp.setMonthlyBaseSalary(75000.00);
        emp.setFirmId(testFirmId);
        emp.setIsActive(true);
        emp.setDateOfJoining(LocalDate.now().minusYears(1));
        testEmployee = employeeRepo.save(emp);
    }

    @Test
    @DisplayName("Should record employee advance and process salary calculation")
    void shouldRecordAdvanceAndSalary() throws Exception {
        // 1. Give Advance of 15000
        EmployeeAdvance advance = new EmployeeAdvance();
        advance.setEmployee(testEmployee);
        advance.setAmount(15000.00);
        advance.setDate(LocalDate.now().minusDays(10));
        advance.setDescription("Festival advance");
        advanceRepo.save(advance);

        // 2. Record Monthly Salary
        SalaryRecord salary = new SalaryRecord();
        salary.setEmployee(testEmployee);
        salary.setMonthYear(String.format("%02d-%d", LocalDate.now().getMonthValue(), LocalDate.now().getYear()));
        salary.setBaseSalaryAtTime(75000.00);
        salary.setAdvanceDeducted(15000.00);
        salary.setNetPaid(60000.00);
        salary.setPaymentDate(LocalDate.now());

        SalaryRecord savedSalary = salaryRepo.save(salary);
        assertThat(savedSalary.getId()).isNotNull();
        assertThat(savedSalary.getNetPaid()).isEqualTo(60000.00);

        // 3. Generate Payslip PDF
        com.billing.simple.billsoft.entities.FirmDetails firm = new com.billing.simple.billsoft.entities.FirmDetails();
        firm.setFirmName("Test Enterprise");
        byte[] pdf = employeePdfService.generatePayslip(testEmployee, savedSalary, firm, java.util.List.of(savedSalary));
        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(500);

        String header = new String(pdf, 0, Math.min(pdf.length, 8), StandardCharsets.US_ASCII);
        assertThat(header).startsWith("%PDF-");
    }
}
