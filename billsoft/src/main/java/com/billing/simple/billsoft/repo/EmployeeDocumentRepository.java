package com.billing.simple.billsoft.repo;

import com.billing.simple.billsoft.entities.EmployeeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument, Long> {
    List<EmployeeDocument> findByEmployeeIdOrderByUploadedAtDesc(Long employeeId);
    void deleteByEmployeeId(Long employeeId);
}