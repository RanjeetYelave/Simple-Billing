package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.entities.AppConfig;
import com.billing.simple.billsoft.entities.Employee;
import com.billing.simple.billsoft.entities.EmployeeAdvance;
import com.billing.simple.billsoft.entities.SalaryRecord;
import com.billing.simple.billsoft.entities.PromotionRecord;
import com.billing.simple.billsoft.repo.PromotionRecordRepository;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import com.billing.simple.billsoft.repo.EmployeeAdvanceRepository;
import com.billing.simple.billsoft.repo.EmployeeRepository;
import com.billing.simple.billsoft.repo.SalaryRecordRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/employees")
@CrossOrigin
public class EmployeeController {

    private final EmployeeRepository employeeRepo;
    private final EmployeeAdvanceRepository advanceRepo;
    private final SalaryRecordRepository salaryRepo;
    private final AppConfigRepository appConfigRepo;
    private final PromotionRecordRepository promotionRepo;

    public EmployeeController(EmployeeRepository employeeRepo,
                              EmployeeAdvanceRepository advanceRepo,
                              SalaryRecordRepository salaryRepo,
                              AppConfigRepository appConfigRepo,
                              PromotionRecordRepository promotionRepo) {
        this.employeeRepo = employeeRepo;
        this.advanceRepo = advanceRepo;
        this.salaryRepo = salaryRepo;
        this.appConfigRepo = appConfigRepo;
        this.promotionRepo = promotionRepo;
    }

    private String getPin() {
        return appConfigRepo.findById("EMPLOYEE_MODULE_PIN")
                .map(AppConfig::getConfigValue)
                .orElse("0000");
    }

    @PostMapping("/verify-pin")
    public ResponseEntity<Map<String, Boolean>> verifyPin(@RequestBody Map<String, String> body) {
        String pin = body.get("pin");
        boolean valid = getPin().equals(pin);
        Map<String, Boolean> res = new HashMap<>();
        res.put("valid", valid);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/change-pin")
    public ResponseEntity<Map<String, String>> changePin(@RequestBody Map<String, String> body) {
        String oldPin = body.get("oldPin");
        String newPin = body.get("newPin");
        if (!getPin().equals(oldPin)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Incorrect old PIN"));
        }
        AppConfig config = appConfigRepo.findById("EMPLOYEE_MODULE_PIN").orElseGet(() -> {
            AppConfig newConfig = new AppConfig();
            newConfig.setConfigKey("EMPLOYEE_MODULE_PIN");
            return newConfig;
        });
        config.setConfigValue(newPin);
        appConfigRepo.save(config);
        return ResponseEntity.ok(Map.of("status", "success"));
    }

    @GetMapping
    @Transactional
    public List<Employee> getEmployees(@RequestParam("firmId") Long firmId) {
        List<PromotionRecord> unapplied = promotionRepo.findByIsAppliedFalse();
        LocalDate today = LocalDate.now();
        for (PromotionRecord p : unapplied) {
            if (!p.getEffectiveDate().isAfter(today)) {
                Employee emp = p.getEmployee();
                if (p.getNewRole() != null && !p.getNewRole().isEmpty()) emp.setRole(p.getNewRole());
                if (p.getNewSalary() != null) emp.setMonthlyBaseSalary(p.getNewSalary());
                employeeRepo.save(emp);
                p.setIsApplied(true);
                promotionRepo.save(p);
            }
        }
        return employeeRepo.findByFirmId(firmId);
    }

    @PostMapping
    public Employee createEmployee(@RequestBody Employee emp) {
        emp.setCurrentAdvanceBalance(0.0);
        return employeeRepo.save(emp);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Employee> updateEmployee(@PathVariable Long id, @RequestBody Employee updated) {
        return employeeRepo.findById(id).map(emp -> {
            emp.setName(updated.getName());
            emp.setPhone(updated.getPhone());
            emp.setRole(updated.getRole());
            emp.setIdProofNumber(updated.getIdProofNumber());
            emp.setIsActive(updated.getIsActive());
            emp.setMonthlyBaseSalary(updated.getMonthlyBaseSalary());
            emp.setAllowedPaidLeavesPerMonth(updated.getAllowedPaidLeavesPerMonth());
            emp.setDateOfJoining(updated.getDateOfJoining());
            return ResponseEntity.ok(employeeRepo.save(emp));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/advances")
    public List<EmployeeAdvance> getAdvances(@PathVariable Long id) {
        return advanceRepo.findByEmployeeIdOrderByDateDesc(id);
    }

    @PostMapping("/{id}/advances")
    @Transactional
    public ResponseEntity<EmployeeAdvance> addAdvance(@PathVariable Long id, @RequestBody EmployeeAdvance advance) {
        Optional<Employee> empOpt = employeeRepo.findById(id);
        if (empOpt.isEmpty()) return ResponseEntity.notFound().build();
        Employee emp = empOpt.get();

        advance.setEmployee(emp);
        if (advance.getDate() == null) advance.setDate(LocalDate.now());
        
        // Update balance
        emp.setCurrentAdvanceBalance(emp.getCurrentAdvanceBalance() + advance.getAmount());
        employeeRepo.save(emp);

        return ResponseEntity.ok(advanceRepo.save(advance));
    }

    @GetMapping("/{id}/salaries")
    public List<SalaryRecord> getSalaries(@PathVariable Long id) {
        return salaryRepo.findByEmployeeIdOrderByPaymentDateDesc(id);
    }

    @PostMapping("/{id}/salaries")
    @Transactional
    public ResponseEntity<SalaryRecord> processSalary(@PathVariable Long id, @RequestBody SalaryRecord record) {
        Optional<Employee> empOpt = employeeRepo.findById(id);
        if (empOpt.isEmpty()) return ResponseEntity.notFound().build();
        Employee emp = empOpt.get();

        record.setEmployee(emp);
        if (record.getPaymentDate() == null) record.setPaymentDate(LocalDate.now());

        // Process advance deduction if any
        if (record.getAdvanceDeducted() > 0) {
            EmployeeAdvance deduction = new EmployeeAdvance();
            deduction.setEmployee(emp);
            deduction.setDate(record.getPaymentDate());
            deduction.setAmount(-record.getAdvanceDeducted());
            deduction.setDescription("Salary Deduction for " + record.getMonthYear());
            advanceRepo.save(deduction);

            emp.setCurrentAdvanceBalance(emp.getCurrentAdvanceBalance() - record.getAdvanceDeducted());
            employeeRepo.save(emp);
        }

        return ResponseEntity.ok(salaryRepo.save(record));
    }

    @GetMapping("/{id}/promotions")
    public List<PromotionRecord> getPromotions(@PathVariable Long id) {
        return promotionRepo.findByEmployeeIdOrderByEffectiveDateDesc(id);
    }

    @PostMapping("/{id}/promotions")
    @Transactional
    public ResponseEntity<PromotionRecord> addPromotion(@PathVariable Long id, @RequestBody PromotionRecord record) {
        Optional<Employee> empOpt = employeeRepo.findById(id);
        if (empOpt.isEmpty()) return ResponseEntity.notFound().build();
        Employee emp = empOpt.get();

        record.setEmployee(emp);
        if (record.getEffectiveDate() == null) record.setEffectiveDate(LocalDate.now());
        
        if (!record.getEffectiveDate().isAfter(LocalDate.now())) {
            if (record.getNewRole() != null && !record.getNewRole().isEmpty()) emp.setRole(record.getNewRole());
            if (record.getNewSalary() != null) emp.setMonthlyBaseSalary(record.getNewSalary());
            employeeRepo.save(emp);
            record.setIsApplied(true);
        } else {
            record.setIsApplied(false);
        }

        return ResponseEntity.ok(promotionRepo.save(record));
    }
}