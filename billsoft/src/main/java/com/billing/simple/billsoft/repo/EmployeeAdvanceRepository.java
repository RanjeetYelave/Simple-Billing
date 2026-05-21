package com.billing.simple.billsoft.repo;

import com.billing.simple.billsoft.entities.EmployeeAdvance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EmployeeAdvanceRepository extends JpaRepository<EmployeeAdvance, Long> {
    List<EmployeeAdvance> findByEmployeeIdOrderByDateDesc(Long employeeId);
    void deleteByEmployeeId(Long employeeId);
}
