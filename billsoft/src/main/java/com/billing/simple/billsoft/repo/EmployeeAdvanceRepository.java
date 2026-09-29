package com.billing.simple.billsoft.repo;

import com.billing.simple.billsoft.entities.EmployeeAdvance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EmployeeAdvanceRepository extends JpaRepository<EmployeeAdvance, Long> {
    List<EmployeeAdvance> findByEmployeeIdOrderByDateDesc(Long employeeId);
    java.util.Optional<EmployeeAdvance> findFirstByEmployeeIdAndDateAndAmount(Long employeeId, java.time.LocalDate date, Double amount);
    List<EmployeeAdvance> findByEmployee_FirmIdOrderByDateDesc(Long firmId);
    void deleteByEmployeeId(Long employeeId);
}
