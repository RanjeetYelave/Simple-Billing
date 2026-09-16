package com.billing.simple.billsoft.repo;

import com.billing.simple.billsoft.entities.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    List<Employee> findByFirmId(Long firmId);

    List<Employee> findByFirmIdOrderByNameAsc(Long firmId);

    List<Employee> findTop5ByFirmIdAndNameContainingIgnoreCaseOrderByNameAsc(Long firmId, String name);

    Optional<Employee> findByIdAndFirmId(Long id, Long firmId);

    boolean existsByIdAndFirmId(Long id, Long firmId);

    void deleteByIdAndFirmId(Long id, Long firmId);

    long countByFirmId(Long firmId);
}

