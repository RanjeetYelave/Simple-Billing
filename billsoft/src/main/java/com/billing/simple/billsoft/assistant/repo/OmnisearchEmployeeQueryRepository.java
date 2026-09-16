package com.billing.simple.billsoft.assistant.repo;

import com.billing.simple.billsoft.entities.Employee;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Dedicated read-only query repository for Employee entity resolution and attendance/salary lookup.
 */
@Transactional(readOnly = true)
public interface OmnisearchEmployeeQueryRepository extends Repository<Employee, Long> {

    interface EmployeeCandidateProjection {
        Long getId();
        String getName();
        String getPhone();
        String getRole();
        Double getMonthlyBaseSalary();
        Double getCurrentAdvanceBalance();
        Boolean getIsActive();
    }

    @Query("SELECT e.id AS id, e.name AS name, e.phone AS phone, e.role AS role, " +
           "e.monthlyBaseSalary AS monthlyBaseSalary, e.currentAdvanceBalance AS currentAdvanceBalance, e.isActive AS isActive " +
           "FROM Employee e WHERE e.firmId = :firmId AND (LOWER(e.name) LIKE LOWER(CONCAT('%', :query, '%')) OR e.phone LIKE CONCAT('%', :query, '%'))")
    List<EmployeeCandidateProjection> searchCandidates(@Param("firmId") Long firmId, @Param("query") String query);
}
