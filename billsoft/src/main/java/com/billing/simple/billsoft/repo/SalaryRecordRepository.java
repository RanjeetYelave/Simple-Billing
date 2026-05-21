package com.billing.simple.billsoft.repo;

import com.billing.simple.billsoft.entities.SalaryRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SalaryRecordRepository extends JpaRepository<SalaryRecord, Long> {
    List<SalaryRecord> findByEmployeeIdOrderByPaymentDateDesc(Long employeeId);
    void deleteByEmployeeId(Long employeeId);
}
