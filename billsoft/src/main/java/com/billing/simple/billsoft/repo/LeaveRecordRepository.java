package com.billing.simple.billsoft.repo;

import com.billing.simple.billsoft.entities.LeaveRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LeaveRecordRepository extends JpaRepository<LeaveRecord, Long> {
    List<LeaveRecord> findByEmployeeIdOrderByStartDateDesc(Long employeeId);
    List<LeaveRecord> findByEmployeeIdAndStatus(Long employeeId, String status);
    void deleteByEmployeeId(Long employeeId);
}