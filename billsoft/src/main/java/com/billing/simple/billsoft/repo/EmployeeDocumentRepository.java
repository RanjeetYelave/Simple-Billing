package com.billing.simple.billsoft.repo;

import com.billing.simple.billsoft.entities.EmployeeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument, Long> {
    @Query("SELECT d FROM EmployeeDocument d WHERE d.employee.id = :employeeId ORDER BY d.uploadedAt DESC")
    List<EmployeeDocument> findByEmployeeIdOrderByUploadedAtDesc(@Param("employeeId") Long employeeId);

    @Modifying
    @Transactional
    @Query("DELETE FROM EmployeeDocument d WHERE d.employee.id = :employeeId")
    void deleteByEmployeeId(@Param("employeeId") Long employeeId);
}