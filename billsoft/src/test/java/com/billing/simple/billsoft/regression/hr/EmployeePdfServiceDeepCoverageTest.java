package com.billing.simple.billsoft.regression.hr;

import com.billing.simple.billsoft.entities.Employee;
import com.billing.simple.billsoft.entities.EmployeeAdvance;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.SalaryRecord;
import com.billing.simple.billsoft.service.BackupService;
import com.billing.simple.billsoft.service.EmployeePdfService;
import com.billing.simple.billsoft.service.FirmDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Tag("regression")
@Tag("unit")
@DisplayName("Employee PDF Generation Deep Coverage Tests")
class EmployeePdfServiceDeepCoverageTest {

    @Autowired
    private EmployeePdfService employeePdfService;

    @Autowired
    private FirmDetailsService firmService;

    @Autowired
    private BackupService backupService;

    private FirmDetails testFirm;
    private Employee testEmployee;

    @BeforeEach
    void setUp() {
        backupService.factoryReset();

        testFirm = new FirmDetails();
        testFirm.setFirmName("Apex Global Systems");
        testFirm.setGstin("27AAACA1234D1Z5");
        testFirm.setAddressLine1("Tech Park Tower B");
        testFirm.setPhone("9811002233");
        testFirm.setEmail("hr@apexsystems.com");
        testFirm = firmService.create(testFirm);

        testEmployee = new Employee();
        testEmployee.setName("Pooja Sharma");
        testEmployee.setPhone("9822990011");
        testEmployee.setRole("Senior Software Engineer");
        testEmployee.setDateOfJoining(LocalDate.now().minusYears(2));
        testEmployee.setMonthlyBaseSalary(85000.0);
        testEmployee.setAllowedPaidLeavesPerMonth(2);
        testEmployee.setFirmId(testFirm.getId());
    }

    @Test
    @DisplayName("Should generate detailed payslip PDF with bonuses, deductions, and tax fields")
    void testGeneratePayslip() throws Exception {
        SalaryRecord salary = new SalaryRecord();
        salary.setEmployee(testEmployee);
        salary.setMonthYear("08-2026");
        salary.setPaymentDate(LocalDate.now());
        salary.setBaseSalaryAtTime(85000.0);
        salary.setBonusAmount(10000.0);
        salary.setAdvanceDeducted(5000.0);
        salary.setLeaveDeductionAmount(2000.0);
        salary.setNetPaid(88000.0);

        SalaryRecord pastSal = new SalaryRecord();
        pastSal.setMonthYear("07-2026");
        pastSal.setNetPaid(85000.0);
        pastSal.setPaymentDate(LocalDate.now().minusMonths(1));

        byte[] payslip = employeePdfService.generatePayslip(testEmployee, salary, testFirm, List.of(salary, pastSal));
        assertThat(payslip).isNotNull();
        assertThat(payslip.length).isGreaterThan(1000);
    }

    @Test
    @DisplayName("Should generate multi-month employee financial statement PDF")
    void testGenerateEmployeeStatement() throws Exception {
        SalaryRecord s1 = new SalaryRecord();
        s1.setMonthYear("06-2026");
        s1.setNetPaid(80000.0);
        s1.setPaymentDate(LocalDate.now().minusMonths(2));

        SalaryRecord s2 = new SalaryRecord();
        s2.setMonthYear("07-2026");
        s2.setNetPaid(85000.0);
        s2.setPaymentDate(LocalDate.now().minusMonths(1));

        EmployeeAdvance adv = new EmployeeAdvance();
        adv.setAmount(10000.0);
        adv.setDate(LocalDate.now().minusMonths(1));
        adv.setDescription("Medical assistance");

        byte[] stmt = employeePdfService.generateEmployeeStatement(
                testEmployee,
                List.of(s1, s2),
                List.of(adv),
                LocalDate.now().minusMonths(3),
                LocalDate.now(),
                testFirm
        );
        assertThat(stmt).isNotNull();
        assertThat(stmt.length).isGreaterThan(1000);
    }
}
