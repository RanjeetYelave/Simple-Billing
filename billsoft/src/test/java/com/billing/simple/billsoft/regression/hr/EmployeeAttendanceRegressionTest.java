package com.billing.simple.billsoft.regression.hr;

import com.billing.simple.billsoft.entities.AttendanceRecord;
import com.billing.simple.billsoft.entities.Employee;
import com.billing.simple.billsoft.entities.LeaveRecord;
import com.billing.simple.billsoft.repo.AttendanceRecordRepository;
import com.billing.simple.billsoft.repo.EmployeeRepository;
import com.billing.simple.billsoft.repo.LeaveRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Tag("regression")
@Tag("integration")
@DisplayName("Employee Attendance & Leave Management Regression Tests")
class EmployeeAttendanceRegressionTest {

    @Autowired
    private EmployeeRepository employeeRepo;

    @Autowired
    private AttendanceRecordRepository attendanceRepo;

    @Autowired
    private LeaveRecordRepository leaveRepo;

    @Autowired
    private com.billing.simple.billsoft.service.BackupService backupService;

    private Employee testEmployee;

    @BeforeEach
    void setUp() {
        backupService.factoryReset();

        Employee emp = new Employee();
        emp.setName("Pooja Nair");
        emp.setDesignation("Operations Associate");
        emp.setDepartment("Logistics");
        emp.setMonthlyBaseSalary(40000.00);
        emp.setFirmId(1L);
        emp.setIsActive(true);
        emp.setDateOfJoining(LocalDate.now().minusMonths(6));
        testEmployee = employeeRepo.save(emp);
    }

    @Test
    @DisplayName("Should record daily attendance status and punch times")
    void shouldRecordDailyAttendance() {
        AttendanceRecord att = new AttendanceRecord();
        att.setEmployee(testEmployee);
        att.setDate(LocalDate.now());
        att.setStatus("PRESENT");
        att.setRemarks("On-time attendance");

        AttendanceRecord saved = attendanceRepo.save(att);
        assertThat(saved.getId()).isNotNull();

        List<AttendanceRecord> records = attendanceRepo.findByEmployeeIdOrderByDateDesc(testEmployee.getId());
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getStatus()).isEqualTo("PRESENT");
    }

    @Test
    @DisplayName("Should approve leave and update leave records")
    void shouldHandleLeaveApplication() {
        LeaveRecord leave = new LeaveRecord();
        leave.setEmployee(testEmployee);
        leave.setType("CASUAL");
        leave.setStartDate(LocalDate.now().plusDays(2));
        leave.setEndDate(LocalDate.now().plusDays(3));
        leave.setTotalDays(2);
        leave.setReason("Family function");
        leave.setStatus("APPROVED");

        LeaveRecord saved = leaveRepo.save(leave);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo("APPROVED");

        List<LeaveRecord> employeeLeaves = leaveRepo.findByEmployeeIdOrderByStartDateDesc(testEmployee.getId());
        assertThat(employeeLeaves).hasSize(1);
        assertThat(employeeLeaves.get(0).getTotalDays()).isEqualTo(2);
    }
}
