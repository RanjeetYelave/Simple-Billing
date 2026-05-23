package com.billing.simple.billsoft.repo;

import com.billing.simple.billsoft.entities.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {
    List<AttendanceRecord> findByEmployeeIdOrderByDateDesc(Long employeeId);
    Optional<AttendanceRecord> findByEmployeeIdAndDate(Long employeeId, LocalDate date);
    List<AttendanceRecord> findByEmployeeIdAndDateBetween(Long employeeId, LocalDate start, LocalDate end);
    void deleteByEmployeeId(Long employeeId);
}