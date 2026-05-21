const fs = require('fs');
let code = fs.readFileSync('src/main/java/com/billing/simple/billsoft/service/BackupService.java', 'utf8');

const imports = `import com.billing.simple.billsoft.repo.EmployeeRepository;
import com.billing.simple.billsoft.repo.EmployeeAdvanceRepository;
import com.billing.simple.billsoft.repo.SalaryRecordRepository;`;

code = code.replace('import com.billing.simple.billsoft.repo.ProductRepository;', `import com.billing.simple.billsoft.repo.ProductRepository;\n${imports}`);

code = code.replace('private final AppConfigRepository appConfigRepo;', `private final AppConfigRepository appConfigRepo;
    private final EmployeeRepository employeeRepo;
    private final EmployeeAdvanceRepository advanceRepo;
    private final SalaryRecordRepository salaryRepo;`);

code = code.replace('AppConfigRepository appConfigRepo) {', `AppConfigRepository appConfigRepo,
                         EmployeeRepository employeeRepo,
                         EmployeeAdvanceRepository advanceRepo,
                         SalaryRecordRepository salaryRepo) {`);

code = code.replace('this.appConfigRepo = appConfigRepo;', `this.appConfigRepo = appConfigRepo;
        this.employeeRepo = employeeRepo;
        this.advanceRepo = advanceRepo;
        this.salaryRepo = salaryRepo;`);

code = code.replace('appConfigRepo.deleteAll();', `appConfigRepo.deleteAll();
        salaryRepo.deleteAll();
        advanceRepo.deleteAll();
        employeeRepo.deleteAll();`);

fs.writeFileSync('src/main/java/com/billing/simple/billsoft/service/BackupService.java', code);
