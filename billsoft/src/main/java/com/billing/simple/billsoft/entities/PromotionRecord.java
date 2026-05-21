package com.billing.simple.billsoft.entities;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "promotion_records")
public class PromotionRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(nullable = false)
    private LocalDate effectiveDate;

    @Column(nullable = false)
    private String type; // "INCREMENT", "ROLE_CHANGE", "BOTH"

    private String previousRole;
    private String newRole;

    private Double previousSalary;
    private Double newSalary;

    private String reason;

    @Column(nullable = false)
    private Boolean isApplied = false;

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getPreviousRole() { return previousRole; }
    public void setPreviousRole(String previousRole) { this.previousRole = previousRole; }
    public String getNewRole() { return newRole; }
    public void setNewRole(String newRole) { this.newRole = newRole; }
    public Double getPreviousSalary() { return previousSalary; }
    public void setPreviousSalary(Double previousSalary) { this.previousSalary = previousSalary; }
    public Double getNewSalary() { return newSalary; }
    public void setNewSalary(Double newSalary) { this.newSalary = newSalary; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Boolean getIsApplied() { return isApplied; }
    public void setIsApplied(Boolean isApplied) { this.isApplied = isApplied; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
