package com.billing.simple.billsoft.entities;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "salary_records")
public class SalaryRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(nullable = false)
    private String monthYear; // e.g., "05-2026"

    @Column(nullable = false)
    private Double baseSalaryAtTime = 0.0;

    @Column(nullable = false)
    private Integer daysAbsent = 0;

    @Column(nullable = false)
    private Integer paidLeavesUsed = 0;

    @Column(nullable = false)
    private Integer unpaidLeaves = 0;

    @Column(nullable = false)
    private Double leaveDeductionAmount = 0.0;

    @Column(nullable = false)
    private Double bonusAmount = 0.0;

    @Column(nullable = false)
    private Double advanceDeducted = 0.0;

    @Column(nullable = false)
    private Double netPaid = 0.0;

    @Column(nullable = false)
    private LocalDate paymentDate;

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }
    public String getMonthYear() { return monthYear; }
    public void setMonthYear(String monthYear) { this.monthYear = monthYear; }
    public Double getBaseSalaryAtTime() { return baseSalaryAtTime; }
    public void setBaseSalaryAtTime(Double baseSalaryAtTime) { this.baseSalaryAtTime = baseSalaryAtTime; }
    public Integer getDaysAbsent() { return daysAbsent; }
    public void setDaysAbsent(Integer daysAbsent) { this.daysAbsent = daysAbsent; }
    public Integer getPaidLeavesUsed() { return paidLeavesUsed; }
    public void setPaidLeavesUsed(Integer paidLeavesUsed) { this.paidLeavesUsed = paidLeavesUsed; }
    public Integer getUnpaidLeaves() { return unpaidLeaves; }
    public void setUnpaidLeaves(Integer unpaidLeaves) { this.unpaidLeaves = unpaidLeaves; }
    public Double getLeaveDeductionAmount() { return leaveDeductionAmount; }
    public void setLeaveDeductionAmount(Double leaveDeductionAmount) { this.leaveDeductionAmount = leaveDeductionAmount; }
    public Double getBonusAmount() { return bonusAmount; }
    public void setBonusAmount(Double bonusAmount) { this.bonusAmount = bonusAmount; }
    public Double getAdvanceDeducted() { return advanceDeducted; }
    public void setAdvanceDeducted(Double advanceDeducted) { this.advanceDeducted = advanceDeducted; }
    public Double getNetPaid() { return netPaid; }
    public void setNetPaid(Double netPaid) { this.netPaid = netPaid; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
