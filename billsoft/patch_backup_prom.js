const fs = require('fs');
let code = fs.readFileSync('src/main/java/com/billing/simple/billsoft/service/BackupService.java', 'utf8');

const importReplacement = `import com.billing.simple.billsoft.repo.SalaryRecordRepository;
import com.billing.simple.billsoft.repo.PromotionRecordRepository;`;
code = code.replace('import com.billing.simple.billsoft.repo.SalaryRecordRepository;', importReplacement);

const depsReplacement = `private final SalaryRecordRepository salaryRepo;
    private final PromotionRecordRepository promotionRepo;`;
code = code.replace('private final SalaryRecordRepository salaryRepo;', depsReplacement);

const ctorArgsReplacement = `SalaryRecordRepository salaryRepo,
                         PromotionRecordRepository promotionRepo) {`;
code = code.replace('SalaryRecordRepository salaryRepo) {', ctorArgsReplacement);

const ctorBodyReplacement = `this.salaryRepo = salaryRepo;
        this.promotionRepo = promotionRepo;`;
code = code.replace('this.salaryRepo = salaryRepo;', ctorBodyReplacement);

const resetReplacement = `salaryRepo.deleteAll();
        promotionRepo.deleteAll();`;
code = code.replace('salaryRepo.deleteAll();', resetReplacement);

fs.writeFileSync('src/main/java/com/billing/simple/billsoft/service/BackupService.java', code);
console.log("Patched BackupService");
