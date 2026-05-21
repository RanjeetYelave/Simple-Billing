package com.billing.simple.billsoft.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "employees")
public class Employee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long firmId;

    @Column(nullable = false)
    private String name;

    private String phone;
    private String role;
    
    @Column(nullable = false)
    private java.time.LocalDate dateOfJoining;
    private String idProofNumber;

    @Column(nullable = false)
    private Boolean isActive = true;

    @Column(nullable = false)
    private Double monthlyBaseSalary = 0.0;

    @Column(nullable = false)
    private Integer allowedPaidLeavesPerMonth = 0;

    @Column(nullable = false)
    private Double currentAdvanceBalance = 0.0;

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFirmId() { return firmId; }
    public void setFirmId(Long firmId) { this.firmId = firmId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public java.time.LocalDate getDateOfJoining() { return dateOfJoining; }
    public void setDateOfJoining(java.time.LocalDate dateOfJoining) { this.dateOfJoining = dateOfJoining; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getIdProofNumber() { return idProofNumber; }
    public void setIdProofNumber(String idProofNumber) { this.idProofNumber = idProofNumber; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public Double getMonthlyBaseSalary() { return monthlyBaseSalary; }
    public void setMonthlyBaseSalary(Double monthlyBaseSalary) { this.monthlyBaseSalary = monthlyBaseSalary; }
    public Integer getAllowedPaidLeavesPerMonth() { return allowedPaidLeavesPerMonth; }
    public void setAllowedPaidLeavesPerMonth(Integer allowedPaidLeavesPerMonth) { this.allowedPaidLeavesPerMonth = allowedPaidLeavesPerMonth; }
    public Double getCurrentAdvanceBalance() { return currentAdvanceBalance; }
    public void setCurrentAdvanceBalance(Double currentAdvanceBalance) { this.currentAdvanceBalance = currentAdvanceBalance; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
