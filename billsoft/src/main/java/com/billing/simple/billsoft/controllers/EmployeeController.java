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
import com.billing.simple.billsoft.repo.AttendanceRecordRepository;
import com.billing.simple.billsoft.repo.EmployeeDocumentRepository;
import com.billing.simple.billsoft.repo.LeaveRecordRepository;
import com.billing.simple.billsoft.service.EmployeePdfService;
import com.billing.simple.billsoft.service.FirmDetailsService;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private static final double MAX_ADVANCE_MULTIPLIER = 3.0; // Max advance is 3x monthly salary

    private final EmployeeRepository employeeRepo;
    private final EmployeeAdvanceRepository advanceRepo;
    private final SalaryRecordRepository salaryRepo;
    private final AppConfigRepository appConfigRepo;
    private final PromotionRecordRepository promotionRepo;
    private final EmployeePdfService pdfService;
    private final FirmDetailsService firmService;
    private final AttendanceRecordRepository attendanceRepo;
    private final EmployeeDocumentRepository documentRepo;
    private final LeaveRecordRepository leaveRepo;

    public EmployeeController(EmployeeRepository employeeRepo,
                              EmployeeAdvanceRepository advanceRepo,
                              SalaryRecordRepository salaryRepo,
                              AppConfigRepository appConfigRepo,
                              PromotionRecordRepository promotionRepo,
                              EmployeePdfService pdfService,
                              FirmDetailsService firmService,
                              AttendanceRecordRepository attendanceRepo,
                              EmployeeDocumentRepository documentRepo,
                              LeaveRecordRepository leaveRepo) {
        this.employeeRepo = employeeRepo;
        this.advanceRepo = advanceRepo;
        this.salaryRepo = salaryRepo;
        this.appConfigRepo = appConfigRepo;
        this.promotionRepo = promotionRepo;
        this.pdfService = pdfService;
        this.firmService = firmService;
        this.attendanceRepo = attendanceRepo;
        this.documentRepo = documentRepo;
        this.leaveRepo = leaveRepo;
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

    // ─── Dashboard Analytics ───

    @GetMapping("/analytics")
    public ResponseEntity<Map<String, Object>> getEmployeeAnalytics(@RequestParam("firmId") Long firmId) {
        List<Employee> employees = employeeRepo.findByFirmId(firmId);
        int activeCount = 0;
        double totalPayroll = 0;
        double totalAdvances = 0;
        int totalSalaryThisMonth = 0;
        double totalPaidThisMonth = 0;

        for (Employee e : employees) {
            if (e.getIsActive()) {
                activeCount++;
                totalPayroll += e.getMonthlyBaseSalary() != null ? e.getMonthlyBaseSalary() : 0;
            }
            totalAdvances += e.getCurrentAdvanceBalance() != null ? e.getCurrentAdvanceBalance() : 0;
        }

        // Current month salary records
        YearMonth currentMonth = YearMonth.now();
        String monthYear = String.format("%02d-%04d", currentMonth.getMonthValue(), currentMonth.getYear());
        for (Employee e : employees) {
            List<SalaryRecord> sals = salaryRepo.findByEmployeeIdOrderByPaymentDateDesc(e.getId());
            for (SalaryRecord s : sals) {
                if (monthYear.equals(s.getMonthYear())) {
                    totalSalaryThisMonth++;
                    totalPaidThisMonth += s.getNetPaid() != null ? s.getNetPaid() : 0;
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("activeEmployees", activeCount);
        result.put("inactiveEmployees", employees.size() - activeCount);
        result.put("totalEmployees", employees.size());
        result.put("totalMonthlyPayroll", Math.round(totalPayroll * 100.0) / 100.0);
        result.put("totalOutstandingAdvances", Math.round(totalAdvances * 100.0) / 100.0);
        result.put("salariesThisMonth", totalSalaryThisMonth);
        result.put("totalPaidThisMonth", Math.round(totalPaidThisMonth * 100.0) / 100.0);
        return ResponseEntity.ok(result);
    }

    /**
     * Apply pending promotions - now scoped to a specific firm.
     */
    @PostMapping("/apply-promotions")
    @Transactional
    public ResponseEntity<Map<String, Integer>> applyPendingPromotions(@RequestParam("firmId") Long firmId) {
        List<PromotionRecord> unapplied = promotionRepo.findByIsAppliedFalse();
        LocalDate today = LocalDate.now();
        int count = 0;
        for (PromotionRecord p : unapplied) {
            Employee emp = p.getEmployee();
            // FIX B2: Only apply promotions for employees in the requested firm
            if (emp == null || !emp.getFirmId().equals(firmId)) continue;
            if (!p.getEffectiveDate().isAfter(today)) {
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
            Double newSalary = body.get("newSalary") instanceof Number ? ((Number) body.get("newSalary")).doubleValue() : null;
            String newRole = body.get("newRole") instanceof String ? (String) body.get("newRole") : null;

            if (isActive != null) {
                Double previousSalary = emp.getMonthlyBaseSalary();
                String previousRole = emp.getRole();

                emp.setIsActive(isActive);
                if (isActive) {
                    if (newSalary != null) emp.setMonthlyBaseSalary(newSalary);
                    if (newRole != null && !newRole.isBlank()) emp.setRole(newRole);
                }
                employeeRepo.save(emp);

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
        promotionRepo.deleteByEmployeeId(id);
        advanceRepo.deleteByEmployeeId(id);
        salaryRepo.deleteByEmployeeId(id);
        attendanceRepo.deleteByEmployeeId(id);
        documentRepo.deleteByEmployeeId(id);
        leaveRepo.deleteByEmployeeId(id);
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
    public ResponseEntity<?> addAdvance(@PathVariable Long id, @RequestBody EmployeeAdvance advance) {
        Optional<Employee> empOpt = employeeRepo.findById(id);
        if (empOpt.isEmpty()) return ResponseEntity.notFound().build();
        Employee emp = empOpt.get();

        advance.setEmployee(emp);
        if (advance.getDate() == null) advance.setDate(LocalDate.now());

        // Validate: advance amount must be positive
        if (advance.getAmount() == null || advance.getAmount() <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Advance amount must be positive"));
        }

        // FIX B4: Enforce max advance limit (3x monthly salary)
        double maxAdvance = emp.getMonthlyBaseSalary() * MAX_ADVANCE_MULTIPLIER;
        if (advance.getAmount() > maxAdvance) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Advance amount (" + String.format("%.2f", advance.getAmount()) +
                            ") exceeds maximum limit of " + String.format("%.2f", maxAdvance) +
                            " (3x monthly salary)"
            ));
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

        // FIX B3: Prevent duplicate salary for same month/year
        Optional<SalaryRecord> existing = salaryRepo.findByEmployeeIdAndMonthYear(id, record.getMonthYear());
        if (existing.isPresent()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Salary for " + record.getMonthYear() + " has already been processed for this employee"
            ));
        }

        // --- BACKEND VALIDATION ---
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

        // FIX B1: Server-side net salary computation (ignore client-provided netPaid)
        double baseSal = record.getBaseSalaryAtTime() != null ? record.getBaseSalaryAtTime() : 0.0;
        double bonusAmt = record.getBonusAmount() != null ? record.getBonusAmount() : 0.0;
        double leaveDed = record.getLeaveDeductionAmount() != null ? record.getLeaveDeductionAmount() : 0.0;
        double advDed = record.getAdvanceDeducted() != null ? record.getAdvanceDeducted() : 0.0;
        double calculatedNet = baseSal + bonusAmt - leaveDed - advDed;
        record.setNetPaid(Math.max(0, Math.round(calculatedNet * 100.0) / 100.0));

        return ResponseEntity.ok(salaryRepo.save(record));
    }

    // ─── Bulk Salary Processing ───

    @PostMapping("/bulk-salary")
    @Transactional
    public ResponseEntity<?> processBulkSalary(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> employeeIds = (List<Integer>) body.get("employeeIds");
        String monthYear = (String) body.get("monthYear");
        Integer daysAbsent = body.get("daysAbsent") instanceof Number ? ((Number) body.get("daysAbsent")).intValue() : 0;
        Double bonusAmount = body.get("bonusAmount") instanceof Number ? ((Number) body.get("bonusAmount")).doubleValue() : 0.0;
        Double advanceDeduct = body.get("advanceDeduct") instanceof Number ? ((Number) body.get("advanceDeduct")).doubleValue() : 0.0;
        LocalDate paymentDate = body.get("paymentDate") != null ? LocalDate.parse((String) body.get("paymentDate")) : LocalDate.now();

        if (employeeIds == null || employeeIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No employees selected"));
        }
        if (monthYear == null || monthYear.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Month/Year is required"));
        }

        int processed = 0;
        int skipped = 0;
        List<String> errors = new java.util.ArrayList<>();

        for (Integer empId : employeeIds) {
            Optional<Employee> empOpt = employeeRepo.findById(empId.longValue());
            if (empOpt.isEmpty()) {
                skipped++;
                continue;
            }
            Employee emp = empOpt.get();

            // Skip if already processed for this month
            Optional<SalaryRecord> existing = salaryRepo.findByEmployeeIdAndMonthYear(emp.getId(), monthYear);
            if (existing.isPresent()) {
                skipped++;
                continue;
            }

            // Calculate days in month from monthYear
            int daysInMonth = 30;
            try {
                String[] parts = monthYear.split("-");
                if (parts.length == 2) {
                    YearMonth ym = YearMonth.of(Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
                    daysInMonth = ym.lengthOfMonth();
                }
            } catch (Exception ignored) {}

            double perDaySalary = daysInMonth > 0 ? emp.getMonthlyBaseSalary() / daysInMonth : 0;
            int unpaidLeaves = Math.max(0, daysAbsent - emp.getAllowedPaidLeavesPerMonth());
            int paidLeavesUsed = Math.min(daysAbsent, emp.getAllowedPaidLeavesPerMonth());
            double leaveDeduction = unpaidLeaves * perDaySalary;

            // Cap advance deduction
            double actualAdvDed = Math.min(advanceDeduct, emp.getCurrentAdvanceBalance());

            double netPaid = emp.getMonthlyBaseSalary() + bonusAmount - leaveDeduction - actualAdvDed;

            SalaryRecord record = new SalaryRecord();
            record.setEmployee(emp);
            record.setMonthYear(monthYear);
            record.setBaseSalaryAtTime(emp.getMonthlyBaseSalary());
            record.setDaysAbsent(daysAbsent);
            record.setPaidLeavesUsed(paidLeavesUsed);
            record.setUnpaidLeaves(unpaidLeaves);
            record.setLeaveDeductionAmount(Math.max(0, leaveDeduction));
            record.setBonusAmount(bonusAmount);
            record.setAdvanceDeducted(actualAdvDed);
            record.setNetPaid(Math.max(0, Math.round(netPaid * 100.0) / 100.0));
            record.setPaymentDate(paymentDate);

            salaryRepo.save(record);

            // Handle advance deduction
            if (actualAdvDed > 0) {
                EmployeeAdvance deduction = new EmployeeAdvance();
                deduction.setEmployee(emp);
                deduction.setDate(paymentDate);
                deduction.setAmount(-actualAdvDed);
                deduction.setDescription("Salary Deduction for " + monthYear);
                advanceRepo.save(deduction);
                emp.setCurrentAdvanceBalance(emp.getCurrentAdvanceBalance() - actualAdvDed);
                employeeRepo.save(emp);
            }

            processed++;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("processed", processed);
        result.put("skipped", skipped);
        return ResponseEntity.ok(result);
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
                } catch (NumberFormatException ignored) {}
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

    // ─── CSV Export Endpoints (F5) ───

    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportEmployeesCsv(@RequestParam("firmId") Long firmId) {
        List<Employee> employees = employeeRepo.findByFirmId(firmId);
        StringBuilder csv = new StringBuilder();
        csv.append("ID,Name,Phone,Role,DateOfJoining,IDProof,IsActive,BaseSalary,AllowedLeaves,AdvanceBalance\n");
        for (Employee e : employees) {
            csv.append(e.getId()).append(",");
            csv.append(escapeCsv(e.getName())).append(",");
            csv.append(escapeCsv(e.getPhone())).append(",");
            csv.append(escapeCsv(e.getRole())).append(",");
            csv.append(e.getDateOfJoining() != null ? e.getDateOfJoining() : "").append(",");
            csv.append(escapeCsv(e.getIdProofNumber())).append(",");
            csv.append(e.getIsActive() ? "Active" : "Inactive").append(",");
            csv.append(e.getMonthlyBaseSalary()).append(",");
            csv.append(e.getAllowedPaidLeavesPerMonth()).append(",");
            csv.append(e.getCurrentAdvanceBalance()).append("\n");
        }
        byte[] bytes = csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("filename", "employees-export.csv");
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }

    @GetMapping("/{id}/salaries/export/csv")
    public ResponseEntity<byte[]> exportSalariesCsv(@PathVariable Long id) {
        List<SalaryRecord> salaries = salaryRepo.findByEmployeeIdOrderByPaymentDateDesc(id);
        StringBuilder csv = new StringBuilder();
        csv.append("MonthYear,BaseSalary,DaysAbsent,PaidLeaves,UnpaidLeaves,LeaveDeduction,Bonus,AdvanceDeducted,NetPaid,PaymentDate\n");
        for (SalaryRecord s : salaries) {
            csv.append(escapeCsv(s.getMonthYear())).append(",");
            csv.append(s.getBaseSalaryAtTime()).append(",");
            csv.append(s.getDaysAbsent()).append(",");
            csv.append(s.getPaidLeavesUsed()).append(",");
            csv.append(s.getUnpaidLeaves()).append(",");
            csv.append(s.getLeaveDeductionAmount()).append(",");
            csv.append(s.getBonusAmount()).append(",");
            csv.append(s.getAdvanceDeducted()).append(",");
            csv.append(s.getNetPaid()).append(",");
            csv.append(s.getPaymentDate() != null ? s.getPaymentDate() : "").append("\n");
        }
        byte[] bytes = csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("filename", "salaries-export-" + id + ".csv");
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }

    @GetMapping("/{id}/advances/export/csv")
    public ResponseEntity<byte[]> exportAdvancesCsv(@PathVariable Long id) {
        List<EmployeeAdvance> advances = advanceRepo.findByEmployeeIdOrderByDateDesc(id);
        StringBuilder csv = new StringBuilder();
        csv.append("Date,Description,Amount,Type\n");
        for (EmployeeAdvance a : advances) {
            csv.append(a.getDate() != null ? a.getDate() : "").append(",");
            csv.append(escapeCsv(a.getDescription())).append(",");
            csv.append(a.getAmount()).append(",");
            csv.append(a.getAmount() > 0 ? "Advance Given" : "Deduction").append("\n");
        }
        byte[] bytes = csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("filename", "advances-export-" + id + ".csv");
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }

    // ─── Helper ───

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

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