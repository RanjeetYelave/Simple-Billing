const fs = require('fs');
let code = fs.readFileSync('src/main/java/com/billing/simple/billsoft/controllers/EmployeeController.java', 'utf8');

const imports = `import com.billing.simple.billsoft.entities.PromotionRecord;
import com.billing.simple.billsoft.repo.PromotionRecordRepository;`;
code = code.replace('import com.billing.simple.billsoft.entities.SalaryRecord;', `import com.billing.simple.billsoft.entities.SalaryRecord;\n${imports}`);

const deps = `private final SalaryRecordRepository salaryRepo;
    private final AppConfigRepository appConfigRepo;
    private final PromotionRecordRepository promotionRepo;`;
code = code.replace(`private final SalaryRecordRepository salaryRepo;
    private final AppConfigRepository appConfigRepo;`, deps);

const ctorArgs = `SalaryRecordRepository salaryRepo,
                              AppConfigRepository appConfigRepo,
                              PromotionRecordRepository promotionRepo) {`;
code = code.replace(`SalaryRecordRepository salaryRepo,
                              AppConfigRepository appConfigRepo) {`, ctorArgs);

const ctorBody = `this.salaryRepo = salaryRepo;
        this.appConfigRepo = appConfigRepo;
        this.promotionRepo = promotionRepo;`;
code = code.replace(`this.salaryRepo = salaryRepo;
        this.appConfigRepo = appConfigRepo;`, ctorBody);

const getEmp = `    @GetMapping
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
    }`;
code = code.replace(`    @GetMapping
    public List<Employee> getEmployees(@RequestParam("firmId") Long firmId) {
        return employeeRepo.findByFirmId(firmId);
    }`, getEmp);

const updateEmp = `    @PutMapping("/{id}")
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
    }`;
code = code.replace(/    @PutMapping\("\/\{id\}"\)([\s\S]*?)orElse\(ResponseEntity\.notFound\(\)\.build\(\)\);\n    }/, updateEmp);

const extraEndpoints = `
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
}`;
code = code.replace(/}\s*$/, extraEndpoints);

fs.writeFileSync('src/main/java/com/billing/simple/billsoft/controllers/EmployeeController.java', code);
console.log("Patched EmployeeController");
