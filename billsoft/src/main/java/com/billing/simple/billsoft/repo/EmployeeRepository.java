package com.billing.simple.billsoft.repo;

import com.billing.simple.billsoft.entities.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    List<Employee> findByFirmId(Long firmId);
}
