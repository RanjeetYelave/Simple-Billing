package com.billing.simple.billsoft.dtos;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class EmployeeDTO {
    private Long id;
    private Long firmId;
    private String name;
    private String phone;
    private String role;
    private LocalDate dateOfJoining;
    private String idProofNumber;
    private Boolean isActive;
    private Double monthlyBaseSalary;
    private Integer allowedPaidLeavesPerMonth;
    private Double currentAdvanceBalance;
    private LocalDateTime createdAt;

    // Constructors
    public EmployeeDTO() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFirmId() { return firmId; }
    public void setFirmId(Long firmId) { this.firmId = firmId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public LocalDate getDateOfJoining() { return dateOfJoining; }
    public void setDateOfJoining(LocalDate dateOfJoining) { this.dateOfJoining = dateOfJoining; }
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