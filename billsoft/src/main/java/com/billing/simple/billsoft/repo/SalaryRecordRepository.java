package com.billing.simple.billsoft.repo;

import com.billing.simple.billsoft.entities.SalaryRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SalaryRecordRepository extends JpaRepository<SalaryRecord, Long> {
    List<SalaryRecord> findByEmployeeIdOrderByPaymentDateDesc(Long employeeId);
    Optional<SalaryRecord> findByEmployeeIdAndMonthYear(Long employeeId, String monthYear);
    List<SalaryRecord> findByEmployee_FirmIdAndMonthYear(Long firmId, String monthYear);
    List<SalaryRecord> findByEmployee_FirmIdAndPaymentDateBetween(Long firmId, java.time.LocalDate start, java.time.LocalDate end);
    List<SalaryRecord> findByEmployee_FirmIdOrderByPaymentDateDesc(Long firmId);
    void deleteByEmployeeId(Long employeeId);
}