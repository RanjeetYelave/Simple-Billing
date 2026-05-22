package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.dtos.EmployeeDTO;
import com.billing.simple.billsoft.entities.AppConfig;
import com.billing.simple.billsoft.entities.Employee;
import com.billing.simple.billsoft.entities.EmployeeAdvance;
import com.billing.simple.billsoft.entities.SalaryRecord;
import com.billing.simple.billsoft.entities.PromotionRecord;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.repo.PromotionRecordRepository;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import com.billing.simple.billsoft.repo.EmployeeAdvanceRepository;
import com.billing.simple.billsoft.repo.EmployeeRepository;
import com.billing.simple.billsoft.repo.SalaryRecordRepository;
import com.billing.simple.billsoft.service.EmployeePdfService;
import com.billing.simple.billsoft.service.FirmDetailsService;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/employees")
@CrossOrigin
public class EmployeeController {

    private final EmployeeRepository employeeRepo;
    private final EmployeeAdvanceRepository advanceRepo;
    private final SalaryRecordRepository salaryRepo;
    private final AppConfigRepository appConfigRepo;
    private final PromotionRecordRepository promotionRepo;
    private final EmployeePdfService pdfService;
    private final FirmDetailsService firmService;

    public EmployeeController(EmployeeRepository employeeRepo,
                              EmployeeAdvanceRepository advanceRepo,
                              SalaryRecordRepository salaryRepo,
                              AppConfigRepository appConfigRepo,
                              PromotionRecordRepository promotionRepo,
                              EmployeePdfService pdfService,
                              FirmDetailsService firmService) {
        this.employeeRepo = employeeRepo;
        this.advanceRepo = advanceRepo;
        this.salaryRepo = salaryRepo;
        this.appConfigRepo = appConfigRepo;
        this.promotionRepo = promotionRepo;
        this.pdfService = pdfService;
        this.firmService = firmService;
    }

    private String getPin() {
        return appConfigRepo.findById("EMPLOYEE_MODULE_PIN")
                .map(AppConfig::getConfigValue)
                .orElse("0000");
    }

    // ─── PIN Endpoints ───

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

    // ─── Employee CRUD ───

    @GetMapping
    public List<EmployeeDTO> getEmployees(@RequestParam("firmId") Long firmId) {
        return employeeRepo.findByFirmId(firmId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Apply pending promotions (separate endpoint - not a side effect of GET).
     */
    @PostMapping("/apply-promotions")
    @Transactional
    public ResponseEntity<Map<String, Integer>> applyPendingPromotions() {
        List<PromotionRecord> unapplied = promotionRepo.findByIsAppliedFalse();
        LocalDate today = LocalDate.now();
        int count = 0;
        for (PromotionRecord p : unapplied) {
            if (!p.getEffectiveDate().isAfter(today)) {
                Employee emp = p.getEmployee();
                if (p.getNewRole() != null && !p.getNewRole().isEmpty()) emp.setRole(p.getNewRole());
                if (p.getNewSalary() != null) emp.setMonthlyBaseSalary(p.getNewSalary());
                employeeRepo.save(emp);
                p.setIsApplied(true);
                promotionRepo.save(p);
                count++;
            }
        }
        return ResponseEntity.ok(Map.of("appliedCount", count));
    }

    @PostMapping
    public ResponseEntity<EmployeeDTO> createEmployee(@Valid @RequestBody EmployeeDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Employee emp = new Employee();
        emp.setFirmId(dto.getFirmId());
        emp.setName(dto.getName().trim());
        emp.setPhone(dto.getPhone());
        emp.setRole(dto.getRole());
        emp.setIdProofNumber(dto.getIdProofNumber());
        emp.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        emp.setMonthlyBaseSalary(dto.getMonthlyBaseSalary() != null ? dto.getMonthlyBaseSalary() : 0.0);
        emp.setAllowedPaidLeavesPerMonth(dto.getAllowedPaidLeavesPerMonth() != null ? dto.getAllowedPaidLeavesPerMonth() : 0);
        emp.setCurrentAdvanceBalance(0.0);
        emp.setDateOfJoining(dto.getDateOfJoining() != null ? dto.getDateOfJoining() : LocalDate.now());
        Employee saved = employeeRepo.save(emp);
        return ResponseEntity.ok(toDTO(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EmployeeDTO> updateEmployee(@PathVariable Long id, @Valid @RequestBody EmployeeDTO dto) {
        return employeeRepo.findById(id).map(emp -> {
            if (dto.getName() != null && !dto.getName().isBlank()) emp.setName(dto.getName().trim());
            if (dto.getPhone() != null) emp.setPhone(dto.getPhone());
            if (dto.getRole() != null) emp.setRole(dto.getRole());
            if (dto.getIdProofNumber() != null) emp.setIdProofNumber(dto.getIdProofNumber());
            if (dto.getIsActive() != null) emp.setIsActive(dto.getIsActive());
            if (dto.getMonthlyBaseSalary() != null) emp.setMonthlyBaseSalary(dto.getMonthlyBaseSalary());
            if (dto.getAllowedPaidLeavesPerMonth() != null) emp.setAllowedPaidLeavesPerMonth(dto.getAllowedPaidLeavesPerMonth());
            if (dto.getDateOfJoining() != null) emp.setDateOfJoining(dto.getDateOfJoining());
            return ResponseEntity.ok(toDTO(employeeRepo.save(emp)));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Change employee status (retire/rejoin) with timeline entry.
     */
    @PutMapping("/{id}/status")
    @Transactional
    public ResponseEntity<EmployeeDTO> changeEmployeeStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return employeeRepo.findById(id).map(emp -> {
            Boolean isActive = body.get("isActive") instanceof Boolean ? (Boolean) body.get("isActive") : null;
            String reason = body.get("reason") instanceof String ? (String) body.get("reason") : "";
            // Optional new salary/role for rejoining
            Double newSalary = body.get("newSalary") instanceof Number ? ((Number) body.get("newSalary")).doubleValue() : null;
            String newRole = body.get("newRole") instanceof String ? (String) body.get("newRole") : null;

            if (isActive != null) {
                // Capture previous values before any change
                Double previousSalary = emp.getMonthlyBaseSalary();
                String previousRole = emp.getRole();

                // Update active flag
                emp.setIsActive(isActive);
                // Apply optional updates only on re‑join
                if (isActive) {
                    if (newSalary != null) emp.setMonthlyBaseSalary(newSalary);
                    if (newRole != null && !newRole.isBlank()) emp.setRole(newRole);
                }
                employeeRepo.save(emp);

                // Create timeline entry
                PromotionRecord timelineEntry = new PromotionRecord();
                timelineEntry.setEmployee(emp);
                timelineEntry.setEffectiveDate(LocalDate.now());
                timelineEntry.setType(isActive ? "REJOIN" : "RETIREMENT");
                timelineEntry.setPreviousRole(previousRole);
                timelineEntry.setNewRole(emp.getRole());
                timelineEntry.setPreviousSalary(previousSalary);
                timelineEntry.setNewSalary(emp.getMonthlyBaseSalary());
                timelineEntry.setReason((reason != null && !reason.isBlank()) ? reason : (isActive ? "Employee rejoined" : "Employee retired"));
                timelineEntry.setIsApplied(true);
                promotionRepo.save(timelineEntry);
            }

            return ResponseEntity.ok(toDTO(emp));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete employee with cascade cleanup of all related records.
     */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Map<String, String>> deleteEmployee(@PathVariable Long id) {
        if (!employeeRepo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        // Delete child records first to avoid FK violations
        promotionRepo.deleteByEmployeeId(id);
        advanceRepo.deleteByEmployeeId(id);
        salaryRepo.deleteByEmployeeId(id);
        employeeRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    // ─── Advance Endpoints ───

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

        // Validate: advance amount must be positive
        if (advance.getAmount() == null || advance.getAmount() <= 0) {
            return ResponseEntity.badRequest().body(null);
        }

        // Update balance
        emp.setCurrentAdvanceBalance(emp.getCurrentAdvanceBalance() + advance.getAmount());
        employeeRepo.save(emp);

        return ResponseEntity.ok(advanceRepo.save(advance));
    }

    // ─── Salary Endpoints ───

    @GetMapping("/{id}/salaries")
    public List<SalaryRecord> getSalaries(@PathVariable Long id) {
        return salaryRepo.findByEmployeeIdOrderByPaymentDateDesc(id);
    }

    @PostMapping("/{id}/salaries")
    @Transactional
    public ResponseEntity<?> processSalary(@PathVariable Long id, @RequestBody SalaryRecord record) {
        Optional<Employee> empOpt = employeeRepo.findById(id);
        if (empOpt.isEmpty()) return ResponseEntity.notFound().build();
        Employee emp = empOpt.get();

        record.setEmployee(emp);
        if (record.getPaymentDate() == null) record.setPaymentDate(LocalDate.now());

        // --- BACKEND VALIDATION ---
        // Validate advance deduction is non-negative and does not exceed current balance
        if (record.getAdvanceDeducted() != null) {
            if (record.getAdvanceDeducted() < 0) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Advance deduction cannot be negative"
                ));
            }
            if (record.getAdvanceDeducted() > emp.getCurrentAdvanceBalance()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Advance deduction amount (" + record.getAdvanceDeducted() +
                                ") exceeds current advance balance (" + emp.getCurrentAdvanceBalance() + ")"
                ));
            }
            if (record.getAdvanceDeducted() > 0) {
                // Process advance deduction
                EmployeeAdvance deduction = new EmployeeAdvance();
                deduction.setEmployee(emp);
                deduction.setDate(record.getPaymentDate());
                deduction.setAmount(-record.getAdvanceDeducted());
                deduction.setDescription("Salary Deduction for " + record.getMonthYear());
                advanceRepo.save(deduction);

                emp.setCurrentAdvanceBalance(emp.getCurrentAdvanceBalance() - record.getAdvanceDeducted());
                employeeRepo.save(emp);
            }
        }

        return ResponseEntity.ok(salaryRepo.save(record));
    }

    // ─── Promotion Endpoints ───

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

        // Apply immediately if effective date is today or past
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

    // ─── YTD Endpoint ───

    @GetMapping("/{id}/ytd")
    public ResponseEntity<Map<String, Object>> getYearToDate(@PathVariable Long id) {
        Optional<Employee> empOpt = employeeRepo.findById(id);
        if (empOpt.isEmpty()) return ResponseEntity.notFound().build();

        List<SalaryRecord> salaries = salaryRepo.findByEmployeeIdOrderByPaymentDateDesc(id);
        int currentYear = LocalDate.now().getYear();

        double ytdGross = 0;
        double ytdBonus = 0;
        double ytdDeductions = 0;
        double ytdNet = 0;
        int salaryCount = 0;

        for (SalaryRecord s : salaries) {
            String monthYear = s.getMonthYear();
            if (monthYear != null && monthYear.contains("-")) {
                try {
                    // Expected format "MM-YYYY" (e.g., "03-2024")
                    String[] parts = monthYear.split("-");
                    if (parts.length != 2) continue;
                    int yearPart = Integer.parseInt(parts[1].trim());
                    if (yearPart == currentYear) {
                        ytdGross += (s.getBaseSalaryAtTime() != null ? s.getBaseSalaryAtTime() : 0)
                                  + (s.getBonusAmount() != null ? s.getBonusAmount() : 0);
                        ytdBonus += s.getBonusAmount() != null ? s.getBonusAmount() : 0;
                        ytdDeductions += (s.getLeaveDeductionAmount() != null ? s.getLeaveDeductionAmount() : 0)
                                        + (s.getAdvanceDeducted() != null ? s.getAdvanceDeducted() : 0);
                        ytdNet += s.getNetPaid() != null ? s.getNetPaid() : 0;
                        salaryCount++;
                    }
                } catch (NumberFormatException ignored) {
                    // If parsing fails, skip this record.
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("year", currentYear);
        result.put("salaryCount", salaryCount);
        result.put("ytdGross", Math.round(ytdGross * 100.0) / 100.0);
        result.put("ytdBonus", Math.round(ytdBonus * 100.0) / 100.0);
        result.put("ytdDeductions", Math.round(ytdDeductions * 100.0) / 100.0);
        result.put("ytdNet", Math.round(ytdNet * 100.0) / 100.0);
        return ResponseEntity.ok(result);
    }

    // ─── PDF Endpoints ───

    /**
     * Download payslip PDF for a specific salary record.
     */
    @GetMapping("/{id}/salaries/{salaryId}/payslip")
    public ResponseEntity<byte[]> downloadPayslip(@PathVariable Long id, @PathVariable Long salaryId) {
        Optional<Employee> empOpt = employeeRepo.findById(id);
        if (empOpt.isEmpty()) return ResponseEntity.notFound().build();

        Optional<SalaryRecord> salOpt = salaryRepo.findById(salaryId);
        if (salOpt.isEmpty() || !salOpt.get().getEmployee().getId().equals(id)) {
            return ResponseEntity.notFound().build();
        }

        try {
            FirmDetails firm = firmService.getFirst();
            byte[] pdf = pdfService.generatePayslip(empOpt.get(), salOpt.get(), firm);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("filename", "payslip-" + id + "-" + salaryId + ".pdf");
            return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Download full employee statement PDF (with YTD, salary history, advance ledger).
     */
    @GetMapping("/{id}/statement")
    public ResponseEntity<byte[]> downloadStatement(
            @PathVariable Long id,
            @RequestParam(value = "from", required = false) String fromStr,
            @RequestParam(value = "to", required = false) String toStr) {
        Optional<Employee> empOpt = employeeRepo.findById(id);
        if (empOpt.isEmpty()) return ResponseEntity.notFound().build();

        LocalDate from = fromStr != null ? LocalDate.parse(fromStr) : null;
        LocalDate to = toStr != null ? LocalDate.parse(toStr) : null;

        try {
            List<SalaryRecord> salaries = salaryRepo.findByEmployeeIdOrderByPaymentDateDesc(id);
            List<EmployeeAdvance> advances = advanceRepo.findByEmployeeIdOrderByDateDesc(id);
            FirmDetails firm = firmService.getFirst();
            byte[] pdf = pdfService.generateEmployeeStatement(empOpt.get(), salaries, advances, from, to, firm);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("filename", "employee-statement-" + id + ".pdf");
            return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // ─── Helper ───

    private EmployeeDTO toDTO(Employee emp) {
        EmployeeDTO dto = new EmployeeDTO();
        dto.setId(emp.getId());
        dto.setFirmId(emp.getFirmId());
        dto.setName(emp.getName());
        dto.setPhone(emp.getPhone());
        dto.setRole(emp.getRole());
        dto.setDateOfJoining(emp.getDateOfJoining());
        dto.setIdProofNumber(emp.getIdProofNumber());
        dto.setIsActive(emp.getIsActive());
        dto.setMonthlyBaseSalary(emp.getMonthlyBaseSalary());
        dto.setAllowedPaidLeavesPerMonth(emp.getAllowedPaidLeavesPerMonth());
        dto.setCurrentAdvanceBalance(emp.getCurrentAdvanceBalance());
        dto.setCreatedAt(emp.getCreatedAt());
        return dto;
    }
}