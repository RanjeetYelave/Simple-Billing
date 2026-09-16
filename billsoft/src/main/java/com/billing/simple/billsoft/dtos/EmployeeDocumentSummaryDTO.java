package com.billing.simple.billsoft.dtos;

import com.billing.simple.billsoft.entities.EmployeeDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class EmployeeDocumentSummaryDTO {
    private Long id;
    private Long employeeId;
    private String type;
    private String fileName;
    private LocalDateTime uploadedAt;

    public EmployeeDocumentSummaryDTO() {}

    public EmployeeDocumentSummaryDTO(Long id, Long employeeId, String type, String fileName, LocalDateTime uploadedAt) {
        this.id = id;
        this.employeeId = employeeId;
        this.type = type;
        this.fileName = fileName;
        this.uploadedAt = uploadedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }

    public static EmployeeDocumentSummaryDTO fromEntity(EmployeeDocument doc) {
        if (doc == null) return null;
        EmployeeDocumentSummaryDTO dto = new EmployeeDocumentSummaryDTO();
        dto.setId(doc.getId());
        dto.setEmployeeId(doc.getEmployeeId());
        dto.setType(doc.getType());
        dto.setFileName(doc.getFileName());
        dto.setUploadedAt(doc.getUploadedAt());
        return dto;
    }
}

