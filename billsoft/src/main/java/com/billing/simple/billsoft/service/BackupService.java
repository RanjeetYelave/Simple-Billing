package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.dto.BackupInspectionDTO;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.forecast.entity.ForecastProduct;
import com.billing.simple.billsoft.forecast.repository.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.repositories.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BackupService {

    private final FirmDetailsRepository firmDetailsRepo;
    private final CustomerRepository customerRepo;
    private final ProductRepository productRepo;
    private final StockMovementRepository stockMovementRepo;
    private final InvoiceRepository invoiceRepo;
    private final InvoiceItemRepository invoiceItemRepo;
    private final PartyRepository partyRepo;
    private final PartyPaymentRepository partyPaymentRepo;
    private final PurchaseOrderRepository purchaseOrderRepo;
    private final PurchaseOrderItemRepository purchaseOrderItemRepo;
    private final ReminderRepository reminderRepo;
    private final NoteRepository noteRepo;
    private final ExpenseRepository expenseRepo;
    private final EmployeeRepository employeeRepo;
    private final AttendanceRecordRepository attendanceRecordRepo;
    private final LeaveRecordRepository leaveRecordRepo;
    private final SalaryRecordRepository salaryRepo;
    private final EmployeeAdvanceRepository advanceRepo;
    private final PromotionRecordRepository promotionRepo;
    private final EmployeeDocumentRepository employeeDocumentRepo;
    private final BusinessLetterRepository businessLetterRepo;
    private final AppConfigRepository appConfigRepo;
    private final InvoicePaymentRepository invoicePaymentRepo;
    private final SalesReturnRepository salesReturnRepo;
    private final SalesReturnItemRepository salesReturnItemRepo;
    private final SavingRepository savingRepo;
    private final GoalRepository goalRepo;
    private final GoalLogRepository goalLogRepo;
    private final BackupEntityMappingRepository backupEntityMappingRepo;
    private final NotificationRepository notificationRepo;
    private final NotificationPreferenceRepository notificationPreferenceRepo;
    private final BackupValidationService backupValidationService;
    private final PricingProfileRepository pricingProfileRepo;
    private final CategoryPricingRuleRepository categoryPricingRuleRepo;
    private final SavedItemRepository savedItemRepo;
    private final ForecastProductRepository forecastProductRepo;
    private final ForecastSalesTransactionRepository forecastSalesTransactionRepo;
    private final ProductSalesMetricsRepository productSalesMetricsRepo;
    private final ForecastModelRunRepository forecastModelRunRepo;
    private final ProductDemandForecastRepository productDemandForecastRepo;
    private final ProductRestockRecommendationRepository productRestockRecommendationRepo;
    private final ItemMergeAuditRepository itemMergeAuditRepo;
    private final ItemDuplicateDismissalRepository itemDuplicateDismissalRepo;
    private final InvoiceMigrationRecordRepository invoiceMigrationRecordRepo;
    private final BackupRecoveryCoordinator coordinator;
    private final ObjectMapper objectMapper;

    public BackupService(FirmDetailsRepository firmDetailsRepo,
                         CustomerRepository customerRepo,
                         ProductRepository productRepo,
                         StockMovementRepository stockMovementRepo,
                         InvoiceRepository invoiceRepo,
                         InvoiceItemRepository invoiceItemRepo,
                         PartyRepository partyRepo,
                         PartyPaymentRepository partyPaymentRepo,
                         PurchaseOrderRepository purchaseOrderRepo,
                         PurchaseOrderItemRepository purchaseOrderItemRepo,
                         ReminderRepository reminderRepo,
                         NoteRepository noteRepo,
                         ExpenseRepository expenseRepo,
                         EmployeeRepository employeeRepo,
                         AttendanceRecordRepository attendanceRecordRepo,
                         LeaveRecordRepository leaveRecordRepo,
                         SalaryRecordRepository salaryRepo,
                         EmployeeAdvanceRepository advanceRepo,
                         PromotionRecordRepository promotionRepo,
                         EmployeeDocumentRepository employeeDocumentRepo,
                         BusinessLetterRepository businessLetterRepo,
                         AppConfigRepository appConfigRepo,
                         InvoicePaymentRepository invoicePaymentRepo,
                         SalesReturnRepository salesReturnRepo,
                         SalesReturnItemRepository salesReturnItemRepo,
                         SavingRepository savingRepo,
                         GoalRepository goalRepo,
                         GoalLogRepository goalLogRepo,
                         BackupEntityMappingRepository backupEntityMappingRepo,
                         NotificationRepository notificationRepo,
                         NotificationPreferenceRepository notificationPreferenceRepo,
                         BackupValidationService backupValidationService,
                         @org.springframework.beans.factory.annotation.Autowired(required = false) PricingProfileRepository pricingProfileRepo,
                         @org.springframework.beans.factory.annotation.Autowired(required = false) CategoryPricingRuleRepository categoryPricingRuleRepo,
                         @org.springframework.beans.factory.annotation.Autowired(required = false) SavedItemRepository savedItemRepo,
                         @org.springframework.beans.factory.annotation.Autowired(required = false) ForecastProductRepository forecastProductRepo,
                         @org.springframework.beans.factory.annotation.Autowired(required = false) ForecastSalesTransactionRepository forecastSalesTransactionRepo,
                         @org.springframework.beans.factory.annotation.Autowired(required = false) ProductSalesMetricsRepository productSalesMetricsRepo,
                         @org.springframework.beans.factory.annotation.Autowired(required = false) ForecastModelRunRepository forecastModelRunRepo,
                         @org.springframework.beans.factory.annotation.Autowired(required = false) ProductDemandForecastRepository productDemandForecastRepo,
                         @org.springframework.beans.factory.annotation.Autowired(required = false) ProductRestockRecommendationRepository productRestockRecommendationRepo,
                         @org.springframework.beans.factory.annotation.Autowired(required = false) ItemMergeAuditRepository itemMergeAuditRepo,
                         @org.springframework.beans.factory.annotation.Autowired(required = false) ItemDuplicateDismissalRepository itemDuplicateDismissalRepo,
                         @org.springframework.beans.factory.annotation.Autowired(required = false) InvoiceMigrationRecordRepository invoiceMigrationRecordRepo,
                         @org.springframework.beans.factory.annotation.Autowired(required = false) BackupRecoveryCoordinator coordinator,
                         @org.springframework.beans.factory.annotation.Autowired(required = false) ObjectMapper objectMapper) {
        this.firmDetailsRepo = firmDetailsRepo;
        this.customerRepo = customerRepo;
        this.productRepo = productRepo;
        this.stockMovementRepo = stockMovementRepo;
        this.invoiceRepo = invoiceRepo;
        this.invoiceItemRepo = invoiceItemRepo;
        this.partyRepo = partyRepo;
        this.partyPaymentRepo = partyPaymentRepo;
        this.purchaseOrderRepo = purchaseOrderRepo;
        this.purchaseOrderItemRepo = purchaseOrderItemRepo;
        this.reminderRepo = reminderRepo;
        this.noteRepo = noteRepo;
        this.expenseRepo = expenseRepo;
        this.employeeRepo = employeeRepo;
        this.attendanceRecordRepo = attendanceRecordRepo;
        this.leaveRecordRepo = leaveRecordRepo;
        this.salaryRepo = salaryRepo;
        this.advanceRepo = advanceRepo;
        this.promotionRepo = promotionRepo;
        this.employeeDocumentRepo = employeeDocumentRepo;
        this.businessLetterRepo = businessLetterRepo;
        this.appConfigRepo = appConfigRepo;
        this.invoicePaymentRepo = invoicePaymentRepo;
        this.salesReturnRepo = salesReturnRepo;
        this.salesReturnItemRepo = salesReturnItemRepo;
        this.savingRepo = savingRepo;
        this.goalRepo = goalRepo;
        this.goalLogRepo = goalLogRepo;
        this.backupEntityMappingRepo = backupEntityMappingRepo;
        this.notificationRepo = notificationRepo;
        this.notificationPreferenceRepo = notificationPreferenceRepo;
        this.backupValidationService = backupValidationService;
        this.pricingProfileRepo = pricingProfileRepo;
        this.categoryPricingRuleRepo = categoryPricingRuleRepo;
        this.savedItemRepo = savedItemRepo;
        this.forecastProductRepo = forecastProductRepo;
        this.forecastSalesTransactionRepo = forecastSalesTransactionRepo;
        this.productSalesMetricsRepo = productSalesMetricsRepo;
        this.forecastModelRunRepo = forecastModelRunRepo;
        this.productDemandForecastRepo = productDemandForecastRepo;
        this.productRestockRecommendationRepo = productRestockRecommendationRepo;
        this.itemMergeAuditRepo = itemMergeAuditRepo;
        this.itemDuplicateDismissalRepo = itemDuplicateDismissalRepo;
        this.invoiceMigrationRecordRepo = invoiceMigrationRecordRepo;
        this.coordinator = coordinator != null ? coordinator : new BackupRecoveryCoordinator();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Transactional(readOnly = true)
    public BackupDTO exportData(Long firmId) {
        BackupDTO backup = new BackupDTO();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("version", "2.0");
        metadata.put("type", "SINGLE_FIRM");
        metadata.put("sourceFirmId", firmId);
        metadata.put("backupSourceId", "firm_" + firmId);
        metadata.put("exportDate", LocalDateTime.now().toString());
        metadata.put("firmId", firmId);

        Optional<FirmDetails> firmOpt = firmDetailsRepo.findById(firmId);
        if (firmOpt.isPresent()) {
            backup.setFirmDetails(firmOpt.get());
            metadata.put("originalFirmName", firmOpt.get().getFirmName());
        }

        backup.setMetadata(metadata);
        backup.setCustomers(customerRepo.findByFirmIdOrderByNameAsc(firmId));
        backup.setProducts(productRepo.findByFirmId(firmId));
        backup.setStockMovements(stockMovementRepo.findByFirmIdOrderByCreatedAtDesc(firmId));
        backup.setInvoices(invoiceRepo.findAllByFirmIdWithItems(firmId));
        backup.setInvoicePayments(invoicePaymentRepo.findByFirmIdOrderByPaymentDateDescIdDesc(firmId));
        backup.setParties(partyRepo.findByFirmIdOrderByNameAsc(firmId));
        backup.setPartyPayments(partyPaymentRepo.findByFirmIdOrderByPaymentDateDescIdDesc(firmId));
        backup.setPurchaseOrders(purchaseOrderRepo.findByFirmIdOrderByPoDateDescIdDesc(firmId));
        backup.setReminders(reminderRepo.findByFirmId(firmId));
        backup.setNotes(noteRepo.findByFirmId(firmId));
        backup.setExpenses(expenseRepo.findByFirmIdOrderByExpenseDateDescIdDesc(firmId));
        backup.setSavings(savingRepo.findByFirmIdOrderBySavingDateDescIdDesc(firmId));
        backup.setGoals(goalRepo.findByFirmIdOrderByCreatedAtDesc(firmId));
        backup.setGoalLogs(goalLogRepo.findByFirmIdOrderByLogDateAscCreatedAtAsc(firmId));

        // Employees and employee sub-records
        List<Employee> employees = employeeRepo.findByFirmId(firmId);
        backup.setEmployees(employees);

        List<AttendanceRecord> attendances = new ArrayList<>();
        List<LeaveRecord> leaves = new ArrayList<>();
        List<SalaryRecord> salaries = new ArrayList<>();
        List<EmployeeAdvance> advances = new ArrayList<>();
        List<PromotionRecord> promotions = new ArrayList<>();

        for (Employee emp : employees) {
            attendances.addAll(attendanceRecordRepo.findByEmployeeIdOrderByDateDesc(emp.getId()));
            leaves.addAll(leaveRecordRepo.findByEmployeeIdOrderByStartDateDesc(emp.getId()));
            salaries.addAll(salaryRepo.findByEmployeeIdOrderByPaymentDateDesc(emp.getId()));
            advances.addAll(advanceRepo.findByEmployeeIdOrderByDateDesc(emp.getId()));
            promotions.addAll(promotionRepo.findByEmployeeIdOrderByEffectiveDateDesc(emp.getId()));
        }

        backup.setAttendanceRecords(attendances);
        backup.setLeaveRecords(leaves);
        backup.setSalaryRecords(salaries);
        backup.setAdvances(advances);
        backup.setPromotions(promotions);

        List<EmployeeDocument> documents = new ArrayList<>();
        for (Employee emp : employees) {
            documents.addAll(employeeDocumentRepo.findByEmployeeIdOrderByUploadedAtDesc(emp.getId()));
        }
        backup.setEmployeeDocuments(documents);

        backup.setBusinessLetters(businessLetterRepo.findByFirmIdOrderByLetterDateDescIdDesc(firmId));
        backup.setNotifications(notificationRepo.findByFirmId(firmId));
        notificationPreferenceRepo.findByFirmId(firmId).ifPresent(pref -> backup.setNotificationPreferences(Collections.singletonList(pref)));
        backup.setAppConfigs(appConfigRepo.findAll());
        backup.setSalesReturns(salesReturnRepo.findAllByFirmIdWithItems(firmId));
        if (pricingProfileRepo != null) {
            backup.setPricingProfiles(pricingProfileRepo.findByFirmIdOrderByIdAsc(firmId));
        }
        if (categoryPricingRuleRepo != null) {
            backup.setCategoryPricingRules(categoryPricingRuleRepo.findByFirmId(firmId));
        }
        if (savedItemRepo != null) {
            backup.setSavedItems(savedItemRepo.findByFirmId(firmId));
        }
        if (forecastProductRepo != null) {
            backup.setForecastProducts(forecastProductRepo.findByFirmId(firmId));
        }

        // Decision B1: Strict isolation - cross-firm migration history and source-firm snapshots are excluded from single-firm tenant exports
        backup.setInvoiceMigrationRecords(null);

        return backup;
    }

    @Transactional(readOnly = true)
    public BackupDTO exportAllData() {
        BackupDTO backup = new BackupDTO();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("version", "2.0");
        metadata.put("type", "FULL_SYSTEM_BACKUP");
        metadata.put("exportDate", LocalDateTime.now().toString());

        List<FirmDetails> allFirms = firmDetailsRepo.findAll();
        backup.setAllFirms(allFirms);
        if (!allFirms.isEmpty()) {
            backup.setFirmDetails(allFirms.get(0));
            metadata.put("originalFirmName", allFirms.get(0).getFirmName());
        }
        backup.setMetadata(metadata);

        backup.setCustomers(customerRepo.findAll());
        backup.setProducts(productRepo.findAll());
        backup.setStockMovements(stockMovementRepo.findAll());
        backup.setInvoices(invoiceRepo.findAllWithItems());
        backup.setInvoicePayments(invoicePaymentRepo.findAll());
        backup.setParties(partyRepo.findAll());
        backup.setPartyPayments(partyPaymentRepo.findAll());
        backup.setPurchaseOrders(purchaseOrderRepo.findAll());
        backup.setReminders(reminderRepo.findAll());
        backup.setNotes(noteRepo.findAll());
        backup.setExpenses(expenseRepo.findAll());
        backup.setSavings(savingRepo.findAll());
        backup.setGoals(goalRepo.findAll());
        backup.setGoalLogs(goalLogRepo.findAllByOrderByLogDateAscCreatedAtAsc());

        List<Employee> employees = employeeRepo.findAll();
        backup.setEmployees(employees);
        backup.setAttendanceRecords(attendanceRecordRepo.findAll());
        backup.setLeaveRecords(leaveRecordRepo.findAll());
        backup.setSalaryRecords(salaryRepo.findAll());
        backup.setAdvances(advanceRepo.findAll());
        backup.setPromotions(promotionRepo.findAll());
        backup.setEmployeeDocuments(employeeDocumentRepo.findAll());

        backup.setBusinessLetters(businessLetterRepo.findAll());
        backup.setNotifications(notificationRepo.findAll());
        backup.setNotificationPreferences(notificationPreferenceRepo.findAll());
        backup.setAppConfigs(appConfigRepo.findAll());
        backup.setSalesReturns(salesReturnRepo.findAllWithItems());
        if (pricingProfileRepo != null) {
            backup.setPricingProfiles(pricingProfileRepo.findAll());
        }
        if (categoryPricingRuleRepo != null) {
            backup.setCategoryPricingRules(categoryPricingRuleRepo.findAll());
        }
        if (savedItemRepo != null) {
            backup.setSavedItems(savedItemRepo.findAll());
        }
        if (forecastProductRepo != null) {
            backup.setForecastProducts(forecastProductRepo.findAll());
        }
        if (invoiceMigrationRecordRepo != null) {
            backup.setInvoiceMigrationRecords(invoiceMigrationRecordRepo.findAllByOrderByIdAsc());
        }

        return backup;
    }

    public BackupInspectionDTO inspectBackup(BackupDTO backup) {
        if (backup == null || backup.getMetadata() == null) {
            throw new RuntimeException("Invalid backup file: Missing metadata");
        }

        BackupInspectionDTO dto = new BackupInspectionDTO();
        Map<String, Object> meta = backup.getMetadata();
        dto.setVersion(meta.getOrDefault("version", "1.0").toString());
        dto.setExportDate(meta.get("exportDate") != null ? meta.get("exportDate").toString() : null);

        boolean isFullSystem = "FULL_SYSTEM_BACKUP".equalsIgnoreCase((String) meta.get("type"))
                || (backup.getAllFirms() != null && !backup.getAllFirms().isEmpty());
        dto.setBackupType(isFullSystem ? "FULL_SYSTEM_BACKUP" : "SINGLE_FIRM");

        List<FirmDetails> firmsInBackup = new ArrayList<>();
        if (isFullSystem && backup.getAllFirms() != null && !backup.getAllFirms().isEmpty()) {
            firmsInBackup.addAll(backup.getAllFirms());
        } else if (backup.getFirmDetails() != null) {
            firmsInBackup.add(backup.getFirmDetails());
        }

        for (FirmDetails f : firmsInBackup) {
            Long fId = f.getId();
            BackupInspectionDTO.FirmSummary summary = new BackupInspectionDTO.FirmSummary();
            summary.setFirmId(fId);
            summary.setFirmName(f.getFirmName() != null ? f.getFirmName() : "Firm " + (fId != null ? fId : ""));
            summary.setOwnerName(f.getOwnerName());
            summary.setGstin(f.getGstin());
            summary.setPhone(f.getPhone());
            summary.setEmail(f.getEmail());

            int custCount = 0;
            if (backup.getCustomers() != null) {
                custCount = (int) backup.getCustomers().stream()
                        .filter(c -> isFullSystem ? Objects.equals(c.getFirmId(), fId) : true)
                        .count();
            }
            summary.setCustomerCount(custCount);

            int prodCount = 0;
            if (backup.getProducts() != null) {
                prodCount = (int) backup.getProducts().stream()
                        .filter(p -> isFullSystem ? Objects.equals(p.getFirmId(), fId) : true)
                        .count();
            }
            summary.setProductCount(prodCount);

            int invCount = 0;
            if (backup.getInvoices() != null) {
                invCount = (int) backup.getInvoices().stream()
                        .filter(inv -> isFullSystem ? Objects.equals(inv.getFirmId(), fId) : true)
                        .count();
            }
            summary.setInvoiceCount(invCount);

            int poCount = 0;
            if (backup.getPurchaseOrders() != null) {
                poCount = (int) backup.getPurchaseOrders().stream()
                        .filter(po -> isFullSystem ? Objects.equals(po.getFirmId(), fId) : true)
                        .count();
            }
            summary.setPurchaseOrderCount(poCount);

            int empCount = 0;
            if (backup.getEmployees() != null) {
                empCount = (int) backup.getEmployees().stream()
                        .filter(e -> isFullSystem ? Objects.equals(e.getFirmId(), fId) : true)
                        .count();
            }
            summary.setEmployeeCount(empCount);

            int expCount = 0;
            if (backup.getExpenses() != null) {
                expCount = (int) backup.getExpenses().stream()
                        .filter(exp -> isFullSystem ? Objects.equals(exp.getFirmId(), fId) : true)
                        .count();
            }
            summary.setExpenseCount(expCount);

            int savCount = 0;
            if (backup.getSavings() != null) {
                savCount = (int) backup.getSavings().stream()
                        .filter(sav -> isFullSystem ? Objects.equals(sav.getFirmId(), fId) : true)
                        .count();
            }
            summary.setSavingCount(savCount);

            int goalCount = 0;
            if (backup.getGoals() != null) {
                goalCount = (int) backup.getGoals().stream()
                        .filter(g -> isFullSystem ? Objects.equals(g.getFirmId(), fId) : true)
                        .count();
            }
            summary.setGoalCount(goalCount);

            int goalLogCount = 0;
            if (backup.getGoalLogs() != null) {
                goalLogCount = (int) backup.getGoalLogs().stream()
                        .filter(gl -> isFullSystem ? Objects.equals(gl.getFirmId(), fId) : true)
                        .count();
            }
            summary.setGoalLogCount(goalLogCount);

            int letterCount = 0;
            if (backup.getBusinessLetters() != null) {
                letterCount = (int) backup.getBusinessLetters().stream()
                        .filter(bl -> isFullSystem ? Objects.equals(bl.getFirmId(), fId) : true)
                        .count();
            }
            summary.setLetterCount(letterCount);

            dto.getFirms().add(summary);
        }

        Map<String, Integer> totalStats = new HashMap<>();
        totalStats.put("totalFirms", firmsInBackup.size());
        totalStats.put("totalCustomers", backup.getCustomers() != null ? backup.getCustomers().size() : 0);
        totalStats.put("totalProducts", backup.getProducts() != null ? backup.getProducts().size() : 0);
        totalStats.put("totalInvoices", backup.getInvoices() != null ? backup.getInvoices().size() : 0);
        totalStats.put("totalPurchaseOrders", backup.getPurchaseOrders() != null ? backup.getPurchaseOrders().size() : 0);
        totalStats.put("totalEmployees", backup.getEmployees() != null ? backup.getEmployees().size() : 0);
        totalStats.put("totalExpenses", backup.getExpenses() != null ? backup.getExpenses().size() : 0);
        totalStats.put("totalSavings", backup.getSavings() != null ? backup.getSavings().size() : 0);
        totalStats.put("totalGoals", backup.getGoals() != null ? backup.getGoals().size() : 0);
        totalStats.put("totalGoalLogs", backup.getGoalLogs() != null ? backup.getGoalLogs().size() : 0);
        totalStats.put("totalLetters", backup.getBusinessLetters() != null ? backup.getBusinessLetters().size() : 0);
        totalStats.put("totalSavedItems", backup.getSavedItems() != null ? backup.getSavedItems().size() : 0);
        totalStats.put("totalForecastProducts", backup.getForecastProducts() != null ? backup.getForecastProducts().size() : 0);
        totalStats.put("totalMigrationRecords", backup.getInvoiceMigrationRecords() != null ? backup.getInvoiceMigrationRecords().size() : 0);
        dto.setTotalStats(totalStats);

        return dto;
    }

    @Transactional
    public List<FirmDetails> importSelectiveData(BackupDTO backup, Set<Long> selectedFirmIds, String mode, Long targetFirmId) {
        boolean locked;
        try {
            locked = coordinator.tryLock(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for backup/recovery lock", e);
        }
        if (!locked) {
            throw new IllegalStateException("A backup, recovery, or restore operation is currently in progress. Please retry shortly.");
        }
        try {
            if (backup == null || backup.getMetadata() == null) {
                throw new com.billing.simple.billsoft.exception.BackupValidationException("MISSING_METADATA", "Invalid backup file: Missing metadata");
            }

            // 0. Preflight validation - Ensures backup is structurally valid & conflict-free BEFORE touching database
            backupValidationService.validate(backup);

            boolean isFullSystem = "FULL_SYSTEM_BACKUP".equalsIgnoreCase((String) backup.getMetadata().get("type"))
                    || (backup.getAllFirms() != null && !backup.getAllFirms().isEmpty());

            List<FirmDetails> backupFirms = new ArrayList<>();
            if (isFullSystem && backup.getAllFirms() != null && !backup.getAllFirms().isEmpty()) {
                backupFirms.addAll(backup.getAllFirms());
            } else if (backup.getFirmDetails() != null) {
                backupFirms.add(backup.getFirmDetails());
            } else {
                FirmDetails fallback = new FirmDetails();
                fallback.setFirmName("Restored Firm");
                backupFirms.add(fallback);
            }

            // Filter firms by selectedFirmIds if provided
            List<FirmDetails> targetFirmsToProcess = new ArrayList<>();
            if (selectedFirmIds != null && !selectedFirmIds.isEmpty()) {
                Set<Long> availableFirmIds = new HashSet<>();
                for (FirmDetails f : backupFirms) {
                    if (f.getId() != null) {
                        availableFirmIds.add(f.getId());
                    }
                }
                Set<Long> missingFirmIds = new HashSet<>(selectedFirmIds);
                missingFirmIds.removeAll(availableFirmIds);
                if (!missingFirmIds.isEmpty()) {
                    throw new IllegalArgumentException("Selected source firm(s) " + missingFirmIds + " not found in backup file.");
                }
                for (FirmDetails f : backupFirms) {
                    if (f.getId() != null && selectedFirmIds.contains(f.getId())) {
                        targetFirmsToProcess.add(f);
                    }
                }
            } else {
                if (targetFirmId != null && backupFirms.size() > 1) {
                    boolean targetExistsInBackup = backupFirms.stream()
                            .anyMatch(f -> Objects.equals(f.getId(), targetFirmId));
                    if (targetExistsInBackup) {
                        for (FirmDetails f : backupFirms) {
                            if (Objects.equals(f.getId(), targetFirmId)) {
                                targetFirmsToProcess.add(f);
                                break;
                            }
                        }
                    } else {
                        throw new IllegalArgumentException("Multi-firm backup contains " + backupFirms.size()
                                + " firms. Explicit source firm selection is required to import into target firm " + targetFirmId + ".");
                    }
                } else {
                    targetFirmsToProcess.addAll(backupFirms);
                }
            }

            if (targetFirmId != null && targetFirmsToProcess.size() > 1) {
                throw new IllegalArgumentException("Cannot restore multiple source firms into a single target firm " + targetFirmId + ".");
            }

            // Safe first-run initialization: Uninitialized database with zero firms adapts to non-destructive clone restore.
            // Destructive clean wipe is otherwise strictly prohibited during restore operations.
            if ("clean_wipe".equalsIgnoreCase(mode) || "clean".equalsIgnoreCase(mode)) {
                if (firmDetailsRepo.count() == 0) {
                    mode = "clone";
                } else {
                    throw new IllegalArgumentException("Destructive clean_wipe mode is not permitted during restore operations. To reset the system, use the dedicated Factory Reset administrative procedure.");
                }
            }

            Map<Long, Long> oldToNewFirmIdMap = new HashMap<>();
            List<FirmDetails> restoredFirms = new ArrayList<>();
            Map<String, Set<Long>> claimedTargetEntitiesByType = new HashMap<>();
            List<PendingProductFk> pendingProductFks = new ArrayList<>();

        for (FirmDetails f : targetFirmsToProcess) {
            Long oldFirmId = f.getId() != null ? f.getId() : -1L;
            FirmDetails firmToUse = null;

            if ("merge".equalsIgnoreCase(mode)) {
                if (targetFirmId != null && targetFirmsToProcess.size() == 1) {
                    firmToUse = firmDetailsRepo.findById(targetFirmId).orElse(null);
                } else if (f.getGstin() != null && !f.getGstin().trim().isEmpty()) {
                    List<FirmDetails> existingFirms = firmDetailsRepo.findAll();
                    firmToUse = existingFirms.stream()
                            .filter(ef -> ef.getGstin() != null && ef.getGstin().equalsIgnoreCase(f.getGstin()))
                            .findFirst().orElse(null);
                }
                if (firmToUse == null && f.getFirmName() != null) {
                    List<FirmDetails> existingFirms = firmDetailsRepo.findAll();
                    firmToUse = existingFirms.stream()
                            .filter(ef -> ef.getFirmName() != null && ef.getFirmName().equalsIgnoreCase(f.getFirmName()))
                            .findFirst().orElse(null);
                }
            }

            if (firmToUse == null) {
                FirmDetails newFirm = new FirmDetails();
                String name = f.getFirmName() != null ? f.getFirmName() : "Restored Firm";
                if ("clone".equalsIgnoreCase(mode)) {
                    boolean exists = firmDetailsRepo.findAll().stream().anyMatch(ef -> name.equalsIgnoreCase(ef.getFirmName()));
                    newFirm.setFirmName(exists ? name + " (Restored)" : name);
                } else {
                    newFirm.setFirmName(name);
                }
                newFirm.setOwnerName(f.getOwnerName());
                newFirm.setAddressLine1(f.getAddressLine1());
                newFirm.setAddressLine2(f.getAddressLine2());
                newFirm.setCity(f.getCity());
                newFirm.setState(f.getState());
                newFirm.setPincode(f.getPincode());
                newFirm.setPhone(f.getPhone());
                newFirm.setEmail(f.getEmail());
                newFirm.setGstin(f.getGstin());
                newFirm.setLogoBase64(f.getLogoBase64());
                newFirm.setBankName(f.getBankName());
                newFirm.setBankAccount(f.getBankAccount());
                newFirm.setBankIfsc(f.getBankIfsc());
                newFirm.setFooterNote(f.getFooterNote());
                firmToUse = firmDetailsRepo.save(newFirm);
            }

            Long mappedId = (targetFirmId != null && targetFirmsToProcess.size() == 1) ? targetFirmId : ((firmToUse != null && firmToUse.getId() != null) ? firmToUse.getId() : (targetFirmId != null ? targetFirmId : (oldFirmId != -1L ? oldFirmId : 1L)));
            oldToNewFirmIdMap.put(oldFirmId, mappedId);
            if (firmToUse != null) {
                restoredFirms.add(firmToUse);
            }
        }

        if (targetFirmsToProcess.size() == 1 && backupFirms.size() <= 1) {
            // In single-firm backup, legacy entities may have null/unspecified firmId (-1L) or arbitrary source firmId
            Long singleMappedId = oldToNewFirmIdMap.values().iterator().next();
            oldToNewFirmIdMap.put(-1L, singleMappedId);
            if (backup.getSourceFirmId() != null) {
                oldToNewFirmIdMap.put(backup.getSourceFirmId(), singleMappedId);
            }
            if (backup.getFirmDetails() != null && backup.getFirmDetails().getId() != null) {
                oldToNewFirmIdMap.put(backup.getFirmDetails().getId(), singleMappedId);
            }
            if (targetFirmId != null) {
                oldToNewFirmIdMap.put(targetFirmId, singleMappedId);
            }
            // Auto-map all entity firm IDs in single-firm backup
            if (backup.getCustomers() != null) {
                for (Customer c : backup.getCustomers()) {
                    if (c.getFirmId() != null) oldToNewFirmIdMap.put(c.getFirmId(), singleMappedId);
                }
            }
            if (backup.getProducts() != null) {
                for (Product p : backup.getProducts()) {
                    if (p.getFirmId() != null) oldToNewFirmIdMap.put(p.getFirmId(), singleMappedId);
                }
            }
            if (backup.getInvoices() != null) {
                for (Invoice inv : backup.getInvoices()) {
                    if (inv.getFirmId() != null) oldToNewFirmIdMap.put(inv.getFirmId(), singleMappedId);
                }
            }
            if (backup.getNotifications() != null) {
                for (Notification n : backup.getNotifications()) {
                    if (n.getFirmId() != null) oldToNewFirmIdMap.put(n.getFirmId(), singleMappedId);
                }
            }
        }

        Long defaultTargetFirmId = targetFirmId != null ? targetFirmId : (!restoredFirms.isEmpty() && restoredFirms.get(0).getId() != null ? restoredFirms.get(0).getId() : 1L);

        String backupSourceId = "firm_" + (defaultTargetFirmId != null ? defaultTargetFirmId : "default");
        if (backup.getMetadata() != null) {
            if (backup.getMetadata().get("backupSourceId") != null) {
                backupSourceId = backup.getMetadata().get("backupSourceId").toString();
            } else if (backup.getMetadata().get("sourceFirmId") != null) {
                backupSourceId = "firm_" + backup.getMetadata().get("sourceFirmId").toString();
            } else if (backup.getMetadata().get("firmId") != null) {
                backupSourceId = "firm_" + backup.getMetadata().get("firmId").toString();
            } else if (backup.getFirmDetails() != null && backup.getFirmDetails().getId() != null) {
                backupSourceId = "firm_" + backup.getFirmDetails().getId();
            }
        }

        // 0.5. Pricing Profiles and Category Pricing Rules
        Map<Long, PricingProfile> oldToNewPricingProfileMap = new HashMap<>();
        if (pricingProfileRepo != null && backup.getPricingProfiles() != null) {
            for (PricingProfile pp : backup.getPricingProfiles()) {
                Long oldFid = pp.getFirmId() != null ? pp.getFirmId() : -1L;
                boolean shouldImport = oldToNewFirmIdMap.containsKey(oldFid);
                if (shouldImport) {
                    Long mappedFirmId = oldToNewFirmIdMap.get(oldFid);
                    Long mappedTargetEntityId = getMappedTargetEntityId(backupSourceId, pp.getId(), "PRICING_PROFILE", mappedFirmId);
                    PricingProfile profileToUse = null;

                    if (mappedTargetEntityId != null) {
                        profileToUse = pricingProfileRepo.findByIdAndFirmId(mappedTargetEntityId, mappedFirmId).orElse(null);
                    }
                    if (profileToUse == null && pp.getCode() != null) {
                        profileToUse = pricingProfileRepo.findByFirmIdAndCode(mappedFirmId, pp.getCode().trim().toUpperCase()).orElse(null);
                    }

                    if (profileToUse != null) {
                        if (pp.getName() != null) profileToUse.setName(pp.getName());
                        if (pp.getDescription() != null) profileToUse.setDescription(pp.getDescription());
                        if (pp.getIsDefault() != null) profileToUse.setIsDefault(pp.getIsDefault());
                        if (pp.getIsActive() != null) profileToUse.setIsActive(pp.getIsActive());
                        profileToUse = pricingProfileRepo.save(profileToUse);
                    } else {
                        PricingProfile newPp = PricingProfile.builder()
                                .firmId(mappedFirmId)
                                .name(pp.getName() != null ? pp.getName() : (pp.getCode() != null ? pp.getCode() : "Profile"))
                                .code(pp.getCode() != null ? pp.getCode().trim().toUpperCase() : ("PP_" + System.currentTimeMillis()))
                                .description(pp.getDescription())
                                .isDefault(Boolean.TRUE.equals(pp.getIsDefault()))
                                .isActive(pp.getIsActive() != null ? pp.getIsActive() : true)
                                .build();
                        profileToUse = pricingProfileRepo.save(newPp);
                    }

                    recordEntityMapping(backupSourceId, pp.getId(), "PRICING_PROFILE", mappedFirmId, profileToUse.getId());
                    markTargetEntityClaimed(claimedTargetEntitiesByType, "PRICING_PROFILE", profileToUse.getId());
                    if (pp.getId() != null) {
                        oldToNewPricingProfileMap.put(pp.getId(), profileToUse);
                    }
                }
            }
        }

        if (categoryPricingRuleRepo != null && backup.getCategoryPricingRules() != null) {
            for (CategoryPricingRule rule : backup.getCategoryPricingRules()) {
                Long oldFid = rule.getFirmId() != null ? rule.getFirmId() : -1L;
                boolean shouldImport = oldToNewFirmIdMap.containsKey(oldFid);
                if (shouldImport && rule.getCategoryName() != null) {
                    Long mappedFirmId = oldToNewFirmIdMap.get(oldFid);
                    Long mappedProfileId = null;
                    if (rule.getPricingProfileId() != null && oldToNewPricingProfileMap.containsKey(rule.getPricingProfileId())) {
                        mappedProfileId = oldToNewPricingProfileMap.get(rule.getPricingProfileId()).getId();
                    } else if (rule.getPricingProfileId() != null) {
                        mappedProfileId = rule.getPricingProfileId();
                    }

                    if (mappedProfileId != null) {
                        CategoryPricingRule cand = categoryPricingRuleRepo.findByFirmIdAndCategoryNameIgnoreCaseAndPricingProfileId(
                                mappedFirmId, rule.getCategoryName().trim(), mappedProfileId).orElse(null);
                        if (cand != null) {
                            cand.setAdjustmentType(rule.getAdjustmentType() != null ? rule.getAdjustmentType() : "PERCENT");
                            cand.setAdjustmentValue(rule.getAdjustmentValue() != null ? rule.getAdjustmentValue() : java.math.BigDecimal.ZERO);
                            categoryPricingRuleRepo.save(cand);
                        } else {
                            CategoryPricingRule newRule = CategoryPricingRule.builder()
                                    .firmId(mappedFirmId)
                                    .categoryName(rule.getCategoryName().trim())
                                    .pricingProfileId(mappedProfileId)
                                    .adjustmentType(rule.getAdjustmentType() != null ? rule.getAdjustmentType() : "PERCENT")
                                    .adjustmentValue(rule.getAdjustmentValue() != null ? rule.getAdjustmentValue() : java.math.BigDecimal.ZERO)
                                    .build();
                            categoryPricingRuleRepo.save(newRule);
                        }
                    }
                }
            }
        }

        // 1. Customers (Primary identity: Stable source ID; Secondary: Phone or Name)
        Map<Long, Customer> oldToNewCustomerMap = new HashMap<>();
        if (backup.getCustomers() != null) {
            for (Customer c : backup.getCustomers()) {
                Long oldFid = c.getFirmId() != null ? c.getFirmId() : -1L;
                boolean shouldImport = oldToNewFirmIdMap.containsKey(oldFid);
                if (shouldImport) {
                    Long mappedFirmId = oldToNewFirmIdMap.get(oldFid);
                    Long mappedTargetEntityId = getMappedTargetEntityId(backupSourceId, c.getId(), "CUSTOMER", mappedFirmId);
                    Customer customerToUse = null;

                    if (mappedTargetEntityId != null) {
                        customerToUse = customerRepo.findByIdAndFirmId(mappedTargetEntityId, mappedFirmId).orElse(null);
                    }
                    if (customerToUse == null && Objects.equals(oldFid, mappedFirmId) && c.getId() != null) {
                        if (isTargetEntityAvailable(claimedTargetEntitiesByType, "CUSTOMER", c.getId())) {
                            customerToUse = customerRepo.findByIdAndFirmId(c.getId(), mappedFirmId).orElse(null);
                        }
                    }
                    if (customerToUse == null && c.getPhone() != null && !c.getPhone().trim().isEmpty() && c.getName() != null && !c.getName().trim().isEmpty()) {
                        Customer cand = customerRepo.findFirstByFirmIdAndPhoneAndNameIgnoreCase(mappedFirmId, c.getPhone().trim(), c.getName().trim()).orElse(null);
                        if (cand != null && isTargetEntityAvailable(claimedTargetEntitiesByType, "CUSTOMER", cand.getId())) {
                            customerToUse = cand;
                        }
                    }
                    if (customerToUse == null && (c.getPhone() == null || c.getPhone().trim().isEmpty()) && c.getName() != null && !c.getName().trim().isEmpty()) {
                        Customer cand = customerRepo.findFirstByFirmIdAndNameIgnoreCaseAndPhoneIsNull(mappedFirmId, c.getName().trim()).orElse(null);
                        if (cand != null && isTargetEntityAvailable(claimedTargetEntitiesByType, "CUSTOMER", cand.getId())) {
                            customerToUse = cand;
                        }
                    }

                    if (customerToUse != null) {
                        // Conflict-Safe Update: Retain newer live values, fill missing fields safely
                        if (customerToUse.getEmail() == null && c.getEmail() != null) customerToUse.setEmail(c.getEmail());
                        if (customerToUse.getAddress() == null && c.getAddress() != null) customerToUse.setAddress(c.getAddress());
                        if (customerToUse.getGstin() == null && c.getGstin() != null) customerToUse.setGstin(c.getGstin());
                        customerToUse = customerRepo.save(customerToUse);
                    } else {
                        Customer newC = new Customer();
                        newC.setFirmId(mappedFirmId);
                        newC.setName(c.getName());
                        newC.setPhone(c.getPhone());
                        newC.setAddress(c.getAddress());
                        newC.setEmail(c.getEmail());
                        newC.setGstin(c.getGstin());
                        customerToUse = customerRepo.save(newC);
                    }

                    recordEntityMapping(backupSourceId, c.getId(), "CUSTOMER", mappedFirmId, customerToUse.getId());
                    markTargetEntityClaimed(claimedTargetEntitiesByType, "CUSTOMER", customerToUse.getId());
                    if (c.getId() != null) {
                        oldToNewCustomerMap.put(c.getId(), customerToUse);
                    }
                }
            }
        }

        // 2. Products (Primary: Stable source ID; Secondary: SKU, Tertiary: Name)
        Map<Long, Product> oldToNewProductMap = new HashMap<>();
        if (backup.getProducts() != null) {
            for (Product p : backup.getProducts()) {
                Long oldFid = p.getFirmId() != null ? p.getFirmId() : -1L;
                boolean shouldImport = oldToNewFirmIdMap.containsKey(oldFid);
                if (shouldImport) {
                    Long mappedFirmId = oldToNewFirmIdMap.get(oldFid);
                    Long mappedTargetEntityId = getMappedTargetEntityId(backupSourceId, p.getId(), "PRODUCT", mappedFirmId);
                    Product productToUse = null;

                    if (mappedTargetEntityId != null) {
                        productToUse = productRepo.findByIdAndFirmId(mappedTargetEntityId, mappedFirmId).orElse(null);
                    }
                    if (productToUse == null && Objects.equals(oldFid, mappedFirmId) && p.getId() != null) {
                        if (isTargetEntityAvailable(claimedTargetEntitiesByType, "PRODUCT", p.getId())) {
                            productToUse = productRepo.findByIdAndFirmId(p.getId(), mappedFirmId).orElse(null);
                        }
                    }
                    if (productToUse == null && p.getSku() != null && !p.getSku().trim().isEmpty()) {
                        Product cand = productRepo.findFirstByFirmIdAndSku(mappedFirmId, p.getSku().trim()).orElse(null);
                        if (cand != null && isTargetEntityAvailable(claimedTargetEntitiesByType, "PRODUCT", cand.getId())) {
                            productToUse = cand;
                        }
                    }
                    if (productToUse == null && (p.getSku() == null || p.getSku().trim().isEmpty()) && p.getName() != null && !p.getName().trim().isEmpty()) {
                        Product cand = productRepo.findFirstByFirmIdAndNameIgnoreCase(mappedFirmId, p.getName().trim()).orElse(null);
                        if (cand != null && isTargetEntityAvailable(claimedTargetEntitiesByType, "PRODUCT", cand.getId())) {
                            productToUse = cand;
                        }
                    }

                    if (productToUse != null) {
                        if (p.getPrice() != null && productToUse.getPrice() == null) productToUse.setPrice(p.getPrice());
                        if (p.getCostPrice() != null && productToUse.getCostPrice() == null) productToUse.setCostPrice(p.getCostPrice());
                        if (p.getHsnCode() != null && productToUse.getHsnCode() == null) productToUse.setHsnCode(p.getHsnCode());
                        if (p.getGstPercentage() != null && productToUse.getGstPercentage() == null) productToUse.setGstPercentage(p.getGstPercentage());
                        if (p.getInInventory() != null) productToUse.setInInventory(p.getInInventory());
                        if (p.getIsArchived() != null) productToUse.setIsArchived(p.getIsArchived());
                        productToUse = productRepo.save(productToUse);
                    } else {
                        Product newP = Product.builder()
                                .firmId(mappedFirmId)
                                .name(p.getName())
                                .price(p.getPrice())
                                .costPrice(p.getCostPrice())
                                .stockQuantity(p.getStockQuantity())
                                .minStockLevel(p.getMinStockLevel())
                                .sku(p.getSku())
                                .barcode(p.getBarcode())
                                .category(p.getCategory())
                                .itemType(p.getItemType())
                                .unit(p.getUnit())
                                .hsnCode(p.getHsnCode())
                                .gstPercentage(p.getGstPercentage())
                                .description(p.getDescription())
                                .inInventory(p.getInInventory() != null ? p.getInInventory() : true)
                                .isArchived(p.getIsArchived() != null ? p.getIsArchived() : false)
                                .build();
                        productToUse = productRepo.save(newP);
                    }

                    if (p.getPreferredPartyId() != null || p.getCanonicalProductId() != null) {
                        pendingProductFks.add(new PendingProductFk(productToUse.getId(), p.getPreferredPartyId(), p.getCanonicalProductId(), oldFid, mappedFirmId));
                    }

                    recordEntityMapping(backupSourceId, p.getId(), "PRODUCT", mappedFirmId, productToUse.getId());
                    markTargetEntityClaimed(claimedTargetEntitiesByType, "PRODUCT", productToUse.getId());
                    if (p.getId() != null) {
                        oldToNewProductMap.put(p.getId(), productToUse);
                    }
                }
            }
        }

        // 3. Stock Movements (Idempotent: check existing movement ID or key tuple)
        if (backup.getStockMovements() != null) {
            for (StockMovement sm : backup.getStockMovements()) {
                Long oldFid = sm.getFirmId() != null ? sm.getFirmId() : -1L;
                Long mappedFirmId = oldToNewFirmIdMap.get(oldFid);
                if (mappedFirmId == null && sm.getProductId() != null && oldToNewProductMap.containsKey(sm.getProductId())) {
                    mappedFirmId = oldToNewProductMap.get(sm.getProductId()).getFirmId();
                }
                boolean shouldImport = mappedFirmId != null;
                if (shouldImport) {
                    Long newProdId = sm.getProductId();
                    if (sm.getProductId() != null && oldToNewProductMap.containsKey(sm.getProductId())) {
                        newProdId = oldToNewProductMap.get(sm.getProductId()).getId();
                    }

                    Long mappedTargetEntityId = getMappedTargetEntityId(backupSourceId, sm.getId(), "STOCK_MOVEMENT", mappedFirmId);
                    StockMovement smToUse = null;

                    if (mappedTargetEntityId != null) {
                        smToUse = stockMovementRepo.findById(mappedTargetEntityId).orElse(null);
                    }
                    if (smToUse == null && Objects.equals(oldFid, mappedFirmId) && sm.getId() != null) {
                        if (isTargetEntityAvailable(claimedTargetEntitiesByType, "STOCK_MOVEMENT", sm.getId())) {
                            smToUse = stockMovementRepo.findById(sm.getId()).orElse(null);
                        }
                    }

                    if (smToUse == null && newProdId != null) {
                        List<StockMovement> existingList = stockMovementRepo.findByProductIdAndFirmIdOrderByCreatedAtDesc(newProdId, mappedFirmId);
                        for (StockMovement m : existingList) {
                            if (Objects.equals(m.getMovementType(), sm.getMovementType())
                                    && Objects.equals(m.getReferenceType(), sm.getReferenceType())
                                    && Objects.equals(m.getReferenceId(), sm.getReferenceId())
                                    && Objects.equals(m.getQuantityChange(), sm.getQuantityChange())
                                    && isTargetEntityAvailable(claimedTargetEntitiesByType, "STOCK_MOVEMENT", m.getId())) {
                                smToUse = m;
                                break;
                            }
                        }
                    }

                    if (smToUse == null) {
                        StockMovement newSm = StockMovement.builder()
                                .productId(newProdId != null ? newProdId : 0L)
                                .productName(sm.getProductName())
                                .firmId(mappedFirmId)
                                .movementType(sm.getMovementType())
                                .quantityChange(sm.getQuantityChange())
                                .previousStock(sm.getPreviousStock())
                                .newStock(sm.getNewStock())
                                .referenceType(sm.getReferenceType())
                                .referenceId(sm.getReferenceId())
                                .note(sm.getNote())
                                .createdAt(sm.getCreatedAt() != null ? sm.getCreatedAt() : LocalDateTime.now())
                                .build();
                        smToUse = stockMovementRepo.save(newSm);
                    }
                    recordEntityMapping(backupSourceId, sm.getId(), "STOCK_MOVEMENT", mappedFirmId, smToUse.getId());
                    markTargetEntityClaimed(claimedTargetEntitiesByType, "STOCK_MOVEMENT", smToUse.getId());
                }
            }
        }

        // 4. Invoices & Items (Primary: Stable Source ID; Secondary: True Business Key (firmId, invoiceNumber))
        Map<Long, Invoice> oldToNewInvoiceMap = new HashMap<>();
        if (backup.getInvoices() != null) {
            for (Invoice inv : backup.getInvoices()) {
                Long oldFid = inv.getFirmId() != null ? inv.getFirmId() : -1L;
                boolean shouldImport = oldToNewFirmIdMap.containsKey(oldFid);
                if (shouldImport) {
                    Long mappedFirmId = oldToNewFirmIdMap.get(oldFid);
                    Long mappedTargetEntityId = getMappedTargetEntityId(backupSourceId, inv.getId(), "INVOICE", mappedFirmId);
                    Invoice invoiceToUse = null;

                    if (mappedTargetEntityId != null) {
                        invoiceToUse = invoiceRepo.findByIdAndFirmId(mappedTargetEntityId, mappedFirmId).orElse(null);
                    }
                    if (invoiceToUse == null && Objects.equals(oldFid, mappedFirmId) && inv.getId() != null) {
                        if (isTargetEntityAvailable(claimedTargetEntitiesByType, "INVOICE", inv.getId())) {
                            invoiceToUse = invoiceRepo.findByIdAndFirmId(inv.getId(), mappedFirmId).orElse(null);
                        }
                    }
                    if (invoiceToUse == null && inv.getInvoiceNumber() != null && !inv.getInvoiceNumber().trim().isEmpty()) {
                        Invoice cand = invoiceRepo.findByInvoiceNumberAndFirmId(inv.getInvoiceNumber().trim(), mappedFirmId).orElse(null);
                        if (cand != null) {
                            if (isTargetEntityAvailable(claimedTargetEntitiesByType, "INVOICE", cand.getId())) {
                                invoiceToUse = cand;
                            } else {
                                throw new com.billing.simple.billsoft.exception.RestoreConflictException(
                                    "INVOICE", inv.getInvoiceNumber(),
                                    "Restore conflict: Target firm " + mappedFirmId + " already has invoice number '" 
                                    + inv.getInvoiceNumber() + "' claimed by another record. Cannot insert duplicate invoice."
                                );
                            }
                        }
                    }
                    if (invoiceToUse == null && inv.getEstimateNumber() != null && !inv.getEstimateNumber().trim().isEmpty()) {
                        Invoice cand = invoiceRepo.findByEstimateNumberAndFirmId(inv.getEstimateNumber().trim(), mappedFirmId).orElse(null);
                        if (cand != null) {
                            if (isTargetEntityAvailable(claimedTargetEntitiesByType, "INVOICE", cand.getId())) {
                                invoiceToUse = cand;
                            } else {
                                throw new com.billing.simple.billsoft.exception.RestoreConflictException(
                                    "INVOICE", inv.getEstimateNumber(),
                                    "Restore conflict: Target firm " + mappedFirmId + " already has estimate number '" 
                                    + inv.getEstimateNumber() + "' claimed by another record. Cannot insert duplicate estimate."
                                );
                            }
                        }
                    }

                    if (invoiceToUse != null) {
                        // Conflict Handling: Retain live database invoice and link to session mapping
                    } else {
                        Invoice newInv = new Invoice();
                        newInv.setFirmId(mappedFirmId);
                        newInv.setInvoiceNumber(inv.getInvoiceNumber());
                        newInv.setInvoiceDate(inv.getInvoiceDate());
                        newInv.setDueDate(inv.getDueDate());
                        newInv.setTotalAmount(inv.getTotalAmount());
                        newInv.setSubtotalWithoutTax(inv.getSubtotalWithoutTax());
                        newInv.setTotalTax(inv.getTotalTax());
                        newInv.setTotalDiscount(inv.getTotalDiscount());
                        newInv.setInvoiceDiscountType(inv.getInvoiceDiscountType());
                        newInv.setInvoiceDiscountValue(inv.getInvoiceDiscountValue());
                        newInv.setRoundOff(inv.getRoundOff());
                        newInv.setStatus(inv.getStatus());
                        newInv.setPaid(inv.getPaid());
                        newInv.setEstimateNumber(inv.getEstimateNumber());
                        newInv.setCustomerNote(inv.getCustomerNote());
                        newInv.setTermsAndConditions(inv.getTermsAndConditions());
                        newInv.setPaymentMethod(inv.getPaymentMethod());
                        newInv.setCurrency(inv.getCurrency() != null ? inv.getCurrency() : "INR");
                        newInv.setTags(inv.getTags());

                        // AUDIT-032: Place of Supply & Tax Decomposition
                        newInv.setPlaceOfSupply(inv.getPlaceOfSupply());
                        newInv.setCgstAmount(inv.getCgstAmount());
                        newInv.setSgstAmount(inv.getSgstAmount());
                        newInv.setIgstAmount(inv.getIgstAmount());

                        // AUDIT-036: Historical Master Data Snapshots
                        newInv.setCustomerNameSnapshot(inv.getCustomerNameSnapshot());
                        newInv.setCustomerGstinSnapshot(inv.getCustomerGstinSnapshot());
                        newInv.setCustomerAddressSnapshot(inv.getCustomerAddressSnapshot());
                        newInv.setCustomerPhoneSnapshot(inv.getCustomerPhoneSnapshot());
                        newInv.setFirmNameSnapshot(inv.getFirmNameSnapshot());
                        newInv.setFirmGstinSnapshot(inv.getFirmGstinSnapshot());
                        newInv.setFirmAddressSnapshot(inv.getFirmAddressSnapshot());
                        newInv.setFirmPhoneSnapshot(inv.getFirmPhoneSnapshot());
                        newInv.setFirmEmailSnapshot(inv.getFirmEmailSnapshot());

                        if (inv.getPricingProfileId() != null) {
                            if (oldToNewPricingProfileMap.containsKey(inv.getPricingProfileId())) {
                                newInv.setPricingProfileId(oldToNewPricingProfileMap.get(inv.getPricingProfileId()).getId());
                            } else {
                                newInv.setPricingProfileId(inv.getPricingProfileId());
                            }
                        }

                        if (inv.getCustomer() != null && oldToNewCustomerMap.containsKey(inv.getCustomer().getId())) {
                            newInv.setCustomer(oldToNewCustomerMap.get(inv.getCustomer().getId()));
                        }

                        if (inv.getItems() != null) {
                            for (InvoiceItem item : inv.getItems()) {
                                InvoiceItem newItem = new InvoiceItem();
                                newItem.setInvoice(newInv);
                                newItem.setQty(item.getQty());
                                newItem.setUnit(item.getUnit());
                                newItem.setPricePerUnit(item.getPricePerUnit());
                                newItem.setAmountWithoutTax(item.getAmountWithoutTax());
                                newItem.setDiscountType(item.getDiscountType());
                                newItem.setDiscountValue(item.getDiscountValue());
                                newItem.setDiscountPercent(item.getDiscountPercent());
                                newItem.setTaxableAmount(item.getTaxableAmount());
                                newItem.setGstPercent(item.getGstPercent());
                                newItem.setGstAmount(item.getGstAmount());
                                newItem.setCgstAmount(item.getCgstAmount());
                                newItem.setSgstAmount(item.getSgstAmount());
                                newItem.setIgstAmount(item.getIgstAmount());
                                newItem.setLineTotal(item.getLineTotal());

                                if (item.getProduct() != null && oldToNewProductMap.containsKey(item.getProduct().getId())) {
                                    newItem.setProduct(oldToNewProductMap.get(item.getProduct().getId()));
                                }
                                newInv.getItems().add(newItem);
                            }
                        }

                        invoiceToUse = invoiceRepo.save(newInv);
                    }

                    recordEntityMapping(backupSourceId, inv.getId(), "INVOICE", mappedFirmId, invoiceToUse.getId());
                    markTargetEntityClaimed(claimedTargetEntitiesByType, "INVOICE", invoiceToUse.getId());
                    if (inv.getId() != null) {
                        oldToNewInvoiceMap.put(inv.getId(), invoiceToUse);
                    }
                }
            }
        }

        // 4.1 Invoice Payments (Idempotent: Check source payment ID or natural business keys)
        if (backup.getInvoicePayments() != null) {
            for (InvoicePayment ip : backup.getInvoicePayments()) {
                Long oldFid = ip.getFirmId() != null ? ip.getFirmId() : -1L;
                Long mappedFirmId = oldToNewFirmIdMap.get(oldFid);
                if (mappedFirmId == null && ip.getInvoiceId() != null && oldToNewInvoiceMap.containsKey(ip.getInvoiceId())) {
                    mappedFirmId = oldToNewInvoiceMap.get(ip.getInvoiceId()).getFirmId();
                }
                boolean shouldImport = mappedFirmId != null;
                if (shouldImport) {
                    Long newInvoiceId = ip.getInvoiceId();
                    if (ip.getInvoiceId() != null && oldToNewInvoiceMap.containsKey(ip.getInvoiceId())) {
                        newInvoiceId = oldToNewInvoiceMap.get(ip.getInvoiceId()).getId();
                    }
                    Long newCustId = ip.getCustomerId();
                    if (ip.getCustomerId() != null && oldToNewCustomerMap.containsKey(ip.getCustomerId())) {
                        newCustId = oldToNewCustomerMap.get(ip.getCustomerId()).getId();
                    }

                    Long mappedTargetEntityId = getMappedTargetEntityId(backupSourceId, ip.getId(), "INVOICE_PAYMENT", mappedFirmId);
                    InvoicePayment ipToUse = null;

                    if (mappedTargetEntityId != null) {
                        ipToUse = invoicePaymentRepo.findByIdAndFirmId(mappedTargetEntityId, mappedFirmId).orElse(null);
                    }
                    if (ipToUse == null && Objects.equals(oldFid, mappedFirmId) && ip.getId() != null) {
                        if (isTargetEntityAvailable(claimedTargetEntitiesByType, "INVOICE_PAYMENT", ip.getId())) {
                            ipToUse = invoicePaymentRepo.findByIdAndFirmId(ip.getId(), mappedFirmId).orElse(null);
                        }
                    }

                    if (ipToUse == null && newInvoiceId != null && ip.getAmount() != null && ip.getPaymentDate() != null) {
                        InvoicePayment cand = invoicePaymentRepo.findFirstByFirmIdAndInvoiceIdAndAmountAndPaymentDate(mappedFirmId, newInvoiceId, ip.getAmount(), ip.getPaymentDate()).orElse(null);
                        if (cand != null && isTargetEntityAvailable(claimedTargetEntitiesByType, "INVOICE_PAYMENT", cand.getId())) {
                            ipToUse = cand;
                        }
                    }
                    if (ipToUse == null && newCustId != null && ip.getAmount() != null && ip.getPaymentDate() != null) {
                        InvoicePayment cand = invoicePaymentRepo.findFirstByFirmIdAndCustomerIdAndAmountAndPaymentDate(mappedFirmId, newCustId, ip.getAmount(), ip.getPaymentDate()).orElse(null);
                        if (cand != null && isTargetEntityAvailable(claimedTargetEntitiesByType, "INVOICE_PAYMENT", cand.getId())) {
                            ipToUse = cand;
                        }
                    }

                    if (ipToUse == null) {
                        InvoicePayment newIp = InvoicePayment.builder()
                                .firmId(mappedFirmId)
                                .invoiceId(newInvoiceId)
                                .customerId(newCustId)
                                .amount(ip.getAmount())
                                .paymentDate(ip.getPaymentDate())
                                .paymentMode(ip.getPaymentMode())
                                .referenceNumber(ip.getReferenceNumber())
                                .notes(ip.getNotes())
                                .build();
                        ipToUse = invoicePaymentRepo.save(newIp);
                    }
                    recordEntityMapping(backupSourceId, ip.getId(), "INVOICE_PAYMENT", mappedFirmId, ipToUse.getId());
                    markTargetEntityClaimed(claimedTargetEntitiesByType, "INVOICE_PAYMENT", ipToUse.getId());
                }
            }
        }

        // 4.2 Sales Returns & Return Items
        if (backup.getSalesReturns() != null) {
            for (SalesReturn sr : backup.getSalesReturns()) {
                Long oldFid = sr.getFirmId() != null ? sr.getFirmId() : -1L;
                Long mappedFirmId = oldToNewFirmIdMap.get(oldFid);
                if (mappedFirmId == null && sr.getInvoice() != null && oldToNewInvoiceMap.containsKey(sr.getInvoice().getId())) {
                    mappedFirmId = oldToNewInvoiceMap.get(sr.getInvoice().getId()).getFirmId();
                }
                boolean shouldImport = mappedFirmId != null;
                if (shouldImport) {
                    Long mappedTargetEntityId = getMappedTargetEntityId(backupSourceId, sr.getId(), "SALES_RETURN", mappedFirmId);
                    SalesReturn srToUse = null;

                    if (mappedTargetEntityId != null) {
                        srToUse = salesReturnRepo.findByIdAndFirmId(mappedTargetEntityId, mappedFirmId).orElse(null);
                    }
                    if (srToUse == null && Objects.equals(oldFid, mappedFirmId) && sr.getId() != null) {
                        if (isTargetEntityAvailable(claimedTargetEntitiesByType, "SALES_RETURN", sr.getId())) {
                            srToUse = salesReturnRepo.findByIdAndFirmId(sr.getId(), mappedFirmId).orElse(null);
                        }
                    }
                    if (srToUse == null && sr.getReturnNumber() != null && !sr.getReturnNumber().trim().isEmpty()) {
                        SalesReturn cand = salesReturnRepo.findFirstByFirmIdAndReturnNumber(mappedFirmId, sr.getReturnNumber().trim()).orElse(null);
                        if (cand != null) {
                            if (isTargetEntityAvailable(claimedTargetEntitiesByType, "SALES_RETURN", cand.getId())) {
                                srToUse = cand;
                            } else {
                                throw new com.billing.simple.billsoft.exception.RestoreConflictException(
                                    "SALES_RETURN", sr.getReturnNumber(),
                                    "Restore conflict: Target firm " + mappedFirmId + " already has sales return number '" 
                                    + sr.getReturnNumber() + "' claimed by another record."
                                );
                            }
                        }
                    }

                    if (srToUse == null) {
                        Invoice mappedInvoice = null;
                        if (sr.getInvoice() != null && oldToNewInvoiceMap.containsKey(sr.getInvoice().getId())) {
                            mappedInvoice = oldToNewInvoiceMap.get(sr.getInvoice().getId());
                        }
                        if (mappedInvoice == null && sr.getInvoice() != null && sr.getInvoice().getId() != null) {
                            mappedInvoice = invoiceRepo.findById(sr.getInvoice().getId()).orElse(null);
                        }
                        if (mappedInvoice == null) {
                            continue;
                        }
                        Customer mappedCustomer = null;
                        if (sr.getCustomer() != null && oldToNewCustomerMap.containsKey(sr.getCustomer().getId())) {
                            mappedCustomer = oldToNewCustomerMap.get(sr.getCustomer().getId());
                        } else if (mappedInvoice.getCustomer() != null) {
                            mappedCustomer = mappedInvoice.getCustomer();
                        }

                        SalesReturn newSr = SalesReturn.builder()
                                .firmId(mappedFirmId)
                                .returnNumber(sr.getReturnNumber())
                                .returnDate(sr.getReturnDate())
                                .invoice(mappedInvoice)
                                .customer(mappedCustomer)
                                .reason(sr.getReason())
                                .refundMode(sr.getRefundMode())
                                .subtotal(sr.getSubtotal())
                                .taxAmount(sr.getTaxAmount())
                                .excludedTaxAmount(sr.getExcludedTaxAmount())
                                .penaltyAmount(sr.getPenaltyAmount())
                                .penaltyReason(sr.getPenaltyReason())
                                .totalRefundAmount(sr.getTotalRefundAmount())
                                .cgstAmount(sr.getCgstAmount())
                                .sgstAmount(sr.getSgstAmount())
                                .igstAmount(sr.getIgstAmount())
                                .notes(sr.getNotes())
                                .items(new ArrayList<>())
                                .build();

                        if (sr.getItems() != null) {
                            for (SalesReturnItem item : sr.getItems()) {
                                Product mappedProd = null;
                                if (item.getProduct() != null && oldToNewProductMap.containsKey(item.getProduct().getId())) {
                                    mappedProd = oldToNewProductMap.get(item.getProduct().getId());
                                }
                                SalesReturnItem newItem = SalesReturnItem.builder()
                                        .salesReturn(newSr)
                                        .product(mappedProd)
                                        .productName(item.getProductName())
                                        .hsnCode(item.getHsnCode())
                                        .unit(item.getUnit())
                                        .returnQty(item.getReturnQty())
                                        .unitPrice(item.getUnitPrice())
                                        .discountValue(item.getDiscountValue())
                                        .gstPercent(item.getGstPercent())
                                        .gstAmount(item.getGstAmount())
                                        .cgstAmount(item.getCgstAmount())
                                        .sgstAmount(item.getSgstAmount())
                                        .igstAmount(item.getIgstAmount())
                                        .refundTotal(item.getRefundTotal())
                                        .build();
                                newSr.getItems().add(newItem);
                            }
                        }
                        srToUse = salesReturnRepo.save(newSr);
                    }

                    recordEntityMapping(backupSourceId, sr.getId(), "SALES_RETURN", mappedFirmId, srToUse.getId());
                    markTargetEntityClaimed(claimedTargetEntitiesByType, "SALES_RETURN", srToUse.getId());
                }
            }
        }

        // 5. Parties (Vendors)
        Map<Long, Party> oldToNewPartyMap = new HashMap<>();
        if (backup.getParties() != null) {
            for (Party party : backup.getParties()) {
                Long oldFid = party.getFirmId() != null ? party.getFirmId() : -1L;
                boolean shouldImport = oldToNewFirmIdMap.containsKey(oldFid);
                if (shouldImport) {
                    Long mappedFirmId = oldToNewFirmIdMap.get(oldFid);
                    Long mappedTargetEntityId = getMappedTargetEntityId(backupSourceId, party.getId(), "PARTY", mappedFirmId);
                    Party partyToUse = null;

                    if (mappedTargetEntityId != null) {
                        partyToUse = partyRepo.findByIdAndFirmId(mappedTargetEntityId, mappedFirmId).orElse(null);
                    }
                    if (partyToUse == null && Objects.equals(oldFid, mappedFirmId) && party.getId() != null) {
                        if (isTargetEntityAvailable(claimedTargetEntitiesByType, "PARTY", party.getId())) {
                            partyToUse = partyRepo.findByIdAndFirmId(party.getId(), mappedFirmId).orElse(null);
                        }
                    }
                    if (partyToUse == null && party.getPhone() != null && !party.getPhone().trim().isEmpty() && party.getName() != null && !party.getName().trim().isEmpty()) {
                        Party cand = partyRepo.findFirstByFirmIdAndPhoneAndNameIgnoreCase(mappedFirmId, party.getPhone().trim(), party.getName().trim()).orElse(null);
                        if (cand != null && isTargetEntityAvailable(claimedTargetEntitiesByType, "PARTY", cand.getId())) {
                            partyToUse = cand;
                        }
                    }
                    if (partyToUse == null && (party.getPhone() == null || party.getPhone().trim().isEmpty()) && party.getName() != null && !party.getName().trim().isEmpty()) {
                        Party cand = partyRepo.findFirstByFirmIdAndNameIgnoreCaseAndPhoneIsNull(mappedFirmId, party.getName().trim()).orElse(null);
                        if (cand != null && isTargetEntityAvailable(claimedTargetEntitiesByType, "PARTY", cand.getId())) {
                            partyToUse = cand;
                        }
                    }

                    if (partyToUse != null) {
                        if (partyToUse.getEmail() == null && party.getEmail() != null) partyToUse.setEmail(party.getEmail());
                        if (partyToUse.getAddress() == null && party.getAddress() != null) partyToUse.setAddress(party.getAddress());
                        if (partyToUse.getGstin() == null && party.getGstin() != null) partyToUse.setGstin(party.getGstin());
                        partyToUse = partyRepo.save(partyToUse);
                    } else {
                        Party newP = Party.builder()
                                .firmId(mappedFirmId)
                                .name(party.getName())
                                .contactPerson(party.getContactPerson())
                                .phone(party.getPhone())
                                .email(party.getEmail())
                                .gstin(party.getGstin())
                                .pan(party.getPan())
                                .address(party.getAddress())
                                .city(party.getCity())
                                .state(party.getState())
                                .pincode(party.getPincode())
                                .bankName(party.getBankName())
                                .bankAccount(party.getBankAccount())
                                .bankIfsc(party.getBankIfsc())
                                .upiId(party.getUpiId())
                                .openingBalance(party.getOpeningBalance())
                                .openingBalanceType(party.getOpeningBalanceType())
                                .notes(party.getNotes())
                                .build();
                        partyToUse = partyRepo.save(newP);
                    }

                    recordEntityMapping(backupSourceId, party.getId(), "PARTY", mappedFirmId, partyToUse.getId());
                    markTargetEntityClaimed(claimedTargetEntitiesByType, "PARTY", partyToUse.getId());
                    if (party.getId() != null) {
                        oldToNewPartyMap.put(party.getId(), partyToUse);
                    }
                }
            }
        }

        // 5.5 Second-pass FK resolution for restored Products (preferredPartyId and canonicalProductId)
        for (PendingProductFk pending : pendingProductFks) {
            Product prod = productRepo.findByIdAndFirmId(pending.newProductId, pending.mappedFirmId).orElse(null);
            if (prod != null) {
                boolean changed = false;
                if (pending.oldPreferredPartyId != null) {
                    Long mappedPartyId = null;
                    if (oldToNewPartyMap.containsKey(pending.oldPreferredPartyId)) {
                        mappedPartyId = oldToNewPartyMap.get(pending.oldPreferredPartyId).getId();
                    } else if (Objects.equals(pending.oldFirmId, pending.mappedFirmId)
                            && partyRepo.findByIdAndFirmId(pending.oldPreferredPartyId, pending.mappedFirmId).isPresent()) {
                        mappedPartyId = pending.oldPreferredPartyId;
                    }
                    prod.setPreferredPartyId(mappedPartyId);
                    changed = true;
                }
                if (pending.oldCanonicalProductId != null) {
                    Long mappedCanonicalId = null;
                    if (oldToNewProductMap.containsKey(pending.oldCanonicalProductId)) {
                        mappedCanonicalId = oldToNewProductMap.get(pending.oldCanonicalProductId).getId();
                    } else if (Objects.equals(pending.oldFirmId, pending.mappedFirmId)
                            && productRepo.findByIdAndFirmId(pending.oldCanonicalProductId, pending.mappedFirmId).isPresent()) {
                        mappedCanonicalId = pending.oldCanonicalProductId;
                    }
                    prod.setCanonicalProductId(mappedCanonicalId);
                    changed = true;
                }
                if (changed) {
                    productRepo.save(prod);
                }
            }
        }

        // 5.6 Saved Items (AUDIT-004)
        if (savedItemRepo != null && backup.getSavedItems() != null) {
            for (SavedItem si : backup.getSavedItems()) {
                Long oldFid = si.getFirmId() != null ? si.getFirmId() : -1L;
                boolean shouldImport = oldToNewFirmIdMap.containsKey(oldFid);
                if (shouldImport) {
                    Long mappedFirmId = oldToNewFirmIdMap.get(oldFid);
                    Long mappedCanonicalId = null;
                    if (si.getCanonicalProductId() != null && oldToNewProductMap.containsKey(si.getCanonicalProductId())) {
                        mappedCanonicalId = oldToNewProductMap.get(si.getCanonicalProductId()).getId();
                    } else if (Objects.equals(oldFid, mappedFirmId) && si.getCanonicalProductId() != null
                            && productRepo.findByIdAndFirmId(si.getCanonicalProductId(), mappedFirmId).isPresent()) {
                        mappedCanonicalId = si.getCanonicalProductId();
                    }

                    String norm = si.getNormalizedName();
                    if (norm == null || norm.trim().isEmpty()) {
                        norm = si.getName() != null ? si.getName().trim().toLowerCase() : "";
                    }

                    SavedItem existing = null;
                    if (!norm.isEmpty()) {
                        existing = savedItemRepo.findByFirmIdAndNormalizedName(mappedFirmId, norm).orElse(null);
                    }

                    if (existing != null) {
                        if (si.getSellingPrice() != null && existing.getSellingPrice() == null) existing.setSellingPrice(si.getSellingPrice());
                        if (si.getGstPercentage() != null && existing.getGstPercentage() == null) existing.setGstPercentage(si.getGstPercentage());
                        if (si.getHsnCode() != null && existing.getHsnCode() == null) existing.setHsnCode(si.getHsnCode());
                        if (mappedCanonicalId != null) existing.setCanonicalProductId(mappedCanonicalId);
                        if (si.getIsArchived() != null) existing.setIsArchived(si.getIsArchived());
                        savedItemRepo.save(existing);
                    } else {
                        SavedItem newSi = SavedItem.builder()
                                .firmId(mappedFirmId)
                                .name(si.getName() != null ? si.getName() : "Item")
                                .normalizedName(norm)
                                .unit(si.getUnit() != null ? si.getUnit() : "pcs")
                                .sellingPrice(si.getSellingPrice())
                                .gstPercentage(si.getGstPercentage())
                                .hsnCode(si.getHsnCode())
                                .category(si.getCategory())
                                .description(si.getDescription())
                                .usageCount(si.getUsageCount() != null ? si.getUsageCount() : 1)
                                .canonicalProductId(mappedCanonicalId)
                                .isArchived(Boolean.TRUE.equals(si.getIsArchived()))
                                .firstUsedAt(si.getFirstUsedAt())
                                .lastUsedAt(si.getLastUsedAt())
                                .build();
                        savedItemRepo.save(newSi);
                    }
                }
            }
        }

        // 5.7 Forecast Product User Overrides / Configuration (AUDIT-004)
        if (forecastProductRepo != null && backup.getForecastProducts() != null) {
            for (ForecastProduct fp : backup.getForecastProducts()) {
                Long oldFid = fp.getFirmId() != null ? fp.getFirmId() : -1L;
                boolean shouldImport = oldToNewFirmIdMap.containsKey(oldFid);
                if (shouldImport && fp.getProductId() != null) {
                    Long mappedFirmId = oldToNewFirmIdMap.get(oldFid);
                    Long mappedProdId = null;
                    if (oldToNewProductMap.containsKey(fp.getProductId())) {
                        mappedProdId = oldToNewProductMap.get(fp.getProductId()).getId();
                    } else if (Objects.equals(oldFid, mappedFirmId)
                            && productRepo.findByIdAndFirmId(fp.getProductId(), mappedFirmId).isPresent()) {
                        mappedProdId = fp.getProductId();
                    }

                    if (mappedProdId != null) {
                        ForecastProduct existing = forecastProductRepo.findByFirmIdAndProductId(mappedFirmId, mappedProdId).orElse(null);
                        if (existing != null) {
                            if (fp.getForecastEnabled() != null) existing.setForecastEnabled(fp.getForecastEnabled());
                            if (fp.getLeadTimeDays() != null) existing.setLeadTimeDays(fp.getLeadTimeDays());
                            if (fp.getReviewPeriodDays() != null) existing.setReviewPeriodDays(fp.getReviewPeriodDays());
                            if (fp.getMinimumOrderQty() != null) existing.setMinimumOrderQty(fp.getMinimumOrderQty());
                            if (fp.getPackSize() != null) existing.setPackSize(fp.getPackSize());
                            if (fp.getSafetyStockOverride() != null) existing.setSafetyStockOverride(fp.getSafetyStockOverride());
                            if (fp.getModelOverride() != null) existing.setModelOverride(fp.getModelOverride());
                            forecastProductRepo.save(existing);
                        } else {
                            ForecastProduct newFp = ForecastProduct.builder()
                                    .firmId(mappedFirmId)
                                    .productId(mappedProdId)
                                    .forecastEnabled(fp.getForecastEnabled() != null ? fp.getForecastEnabled() : true)
                                    .leadTimeDays(fp.getLeadTimeDays())
                                    .reviewPeriodDays(fp.getReviewPeriodDays())
                                    .minimumOrderQty(fp.getMinimumOrderQty())
                                    .packSize(fp.getPackSize())
                                    .safetyStockOverride(fp.getSafetyStockOverride())
                                    .modelOverride(fp.getModelOverride())
                                    .build();
                            forecastProductRepo.save(newFp);
                        }
                    }
                }
            }
        }

        // 6. Purchase Orders (Primary: Stable Source ID; Secondary: True Business Key poNumber)
        Map<Long, PurchaseOrder> oldToNewPoMap = new HashMap<>();
        if (backup.getPurchaseOrders() != null) {
            for (PurchaseOrder po : backup.getPurchaseOrders()) {
                Long oldFid = po.getFirmId() != null ? po.getFirmId() : -1L;
                boolean shouldImport = oldToNewFirmIdMap.containsKey(oldFid);
                if (shouldImport) {
                    Long mappedFirmId = oldToNewFirmIdMap.get(oldFid);
                    Long mappedTargetEntityId = getMappedTargetEntityId(backupSourceId, po.getId(), "PO", mappedFirmId);
                    PurchaseOrder poToUse = null;

                    if (mappedTargetEntityId != null) {
                        poToUse = purchaseOrderRepo.findByIdAndFirmId(mappedTargetEntityId, mappedFirmId).orElse(null);
                    }
                    if (poToUse == null && Objects.equals(oldFid, mappedFirmId) && po.getId() != null) {
                        if (isTargetEntityAvailable(claimedTargetEntitiesByType, "PO", po.getId())) {
                            poToUse = purchaseOrderRepo.findByIdAndFirmId(po.getId(), mappedFirmId).orElse(null);
                        }
                    }
                    if (poToUse == null && po.getPoNumber() != null && !po.getPoNumber().trim().isEmpty()) {
                        PurchaseOrder cand = purchaseOrderRepo.findFirstByFirmIdAndPoNumber(mappedFirmId, po.getPoNumber().trim()).orElse(null);
                        if (cand != null) {
                            if (isTargetEntityAvailable(claimedTargetEntitiesByType, "PO", cand.getId())) {
                                poToUse = cand;
                            } else {
                                throw new com.billing.simple.billsoft.exception.RestoreConflictException(
                                    "PO", po.getPoNumber(),
                                    "Restore conflict: Target firm " + mappedFirmId + " already has purchase order number '" 
                                    + po.getPoNumber() + "' claimed by another record."
                                );
                            }
                        }
                    }

                    if (poToUse != null) {
                        // Conflict-Safe: Retain existing live PO
                    } else {
                        PurchaseOrder newPo = PurchaseOrder.builder()
                                .firmId(mappedFirmId)
                                .poNumber(po.getPoNumber())
                                .poDate(po.getPoDate())
                                .expectedDeliveryDate(po.getExpectedDeliveryDate())
                                .status(po.getStatus())
                                .paymentStatus(po.getPaymentStatus())
                                .paidAmount(po.getPaidAmount())
                                .paymentMethod(po.getPaymentMethod())
                                .paymentTerms(po.getPaymentTerms())
                                .referenceNumber(po.getReferenceNumber())
                                .shippingAddress(po.getShippingAddress())
                                .notes(po.getNotes())
                                .termsAndConditions(po.getTermsAndConditions())
                                .partyName(po.getPartyName())
                                .partyContactPerson(po.getPartyContactPerson())
                                .partyPhone(po.getPartyPhone())
                                .partyEmail(po.getPartyEmail())
                                .partyGstin(po.getPartyGstin())
                                .partyPan(po.getPartyPan())
                                .partyAddress(po.getPartyAddress())
                                .subtotalWithoutTax(po.getSubtotalWithoutTax())
                                .totalGstAmount(po.getTotalGstAmount())
                                .totalDiscountAmount(po.getTotalDiscountAmount())
                                .roundOff(po.getRoundOff())
                                .totalAmount(po.getTotalAmount())
                                .items(new ArrayList<>())
                                .build();

                        if (po.getParty() != null && oldToNewPartyMap.containsKey(po.getParty().getId())) {
                            newPo.setParty(oldToNewPartyMap.get(po.getParty().getId()));
                        }

                        if (po.getItems() != null) {
                            for (PurchaseOrderItem item : po.getItems()) {
                                Long newProdId = item.getProductId();
                                if (item.getProductId() != null && oldToNewProductMap.containsKey(item.getProductId())) {
                                    newProdId = oldToNewProductMap.get(item.getProductId()).getId();
                                }
                                PurchaseOrderItem newItem = PurchaseOrderItem.builder()
                                        .purchaseOrder(newPo)
                                        .productId(newProdId)
                                        .productName(item.getProductName())
                                        .description(item.getDescription())
                                        .hsnCode(item.getHsnCode())
                                        .quantity(item.getQuantity())
                                        .unit(item.getUnit())
                                        .unitPrice(item.getUnitPrice())
                                        .discountValue(item.getDiscountValue())
                                        .gstPercent(item.getGstPercent())
                                        .taxableAmount(item.getTaxableAmount())
                                        .gstAmount(item.getGstAmount())
                                        .totalAmount(item.getTotalAmount())
                                        .build();
                                newPo.getItems().add(newItem);
                            }
                        }

                        poToUse = purchaseOrderRepo.save(newPo);
                    }

                    recordEntityMapping(backupSourceId, po.getId(), "PO", mappedFirmId, poToUse.getId());
                    markTargetEntityClaimed(claimedTargetEntitiesByType, "PO", poToUse.getId());
                    if (po.getId() != null) {
                        oldToNewPoMap.put(po.getId(), poToUse);
                    }
                }
            }
        }

        // 7. Party Payments
        if (backup.getPartyPayments() != null) {
            for (PartyPayment pp : backup.getPartyPayments()) {
                Long oldFid = pp.getFirmId() != null ? pp.getFirmId() : -1L;
                boolean shouldImport = oldToNewFirmIdMap.containsKey(oldFid);
                if (shouldImport) {
                    Long mappedFirmId = oldToNewFirmIdMap.get(oldFid);
                    Long newPartyId = pp.getPartyId();
                    if (pp.getPartyId() != null && oldToNewPartyMap.containsKey(pp.getPartyId())) {
                        newPartyId = oldToNewPartyMap.get(pp.getPartyId()).getId();
                    }
                    Long newPoId = pp.getPurchaseOrderId();
                    if (pp.getPurchaseOrderId() != null && oldToNewPoMap.containsKey(pp.getPurchaseOrderId())) {
                        newPoId = oldToNewPoMap.get(pp.getPurchaseOrderId()).getId();
                    }

                    Long mappedTargetEntityId = getMappedTargetEntityId(backupSourceId, pp.getId(), "PARTY_PAYMENT", mappedFirmId);
                    PartyPayment ppToUse = null;

                    if (mappedTargetEntityId != null) {
                        ppToUse = partyPaymentRepo.findById(mappedTargetEntityId).orElse(null);
                    }
                    if (ppToUse == null && Objects.equals(oldFid, mappedFirmId) && pp.getId() != null) {
                        if (isTargetEntityAvailable(claimedTargetEntitiesByType, "PARTY_PAYMENT", pp.getId())) {
                            ppToUse = partyPaymentRepo.findById(pp.getId()).orElse(null);
                        }
                    }

                    if (ppToUse == null && newPartyId != null && pp.getAmount() != null && pp.getPaymentDate() != null) {
                        PartyPayment cand = partyPaymentRepo.findFirstByFirmIdAndPartyIdAndAmountAndPaymentDate(mappedFirmId, newPartyId, pp.getAmount(), pp.getPaymentDate()).orElse(null);
                        if (cand != null && isTargetEntityAvailable(claimedTargetEntitiesByType, "PARTY_PAYMENT", cand.getId())) {
                            ppToUse = cand;
                        }
                    }
                    if (ppToUse == null && newPoId != null && pp.getAmount() != null && pp.getPaymentDate() != null) {
                        PartyPayment cand = partyPaymentRepo.findFirstByFirmIdAndPurchaseOrderIdAndAmountAndPaymentDate(mappedFirmId, newPoId, pp.getAmount(), pp.getPaymentDate()).orElse(null);
                        if (cand != null && isTargetEntityAvailable(claimedTargetEntitiesByType, "PARTY_PAYMENT", cand.getId())) {
                            ppToUse = cand;
                        }
                    }

                    if (ppToUse == null) {
                        PartyPayment newPp = PartyPayment.builder()
                                .firmId(mappedFirmId)
                                .partyId(newPartyId)
                                .purchaseOrderId(newPoId)
                                .amount(pp.getAmount())
                                .paymentDate(pp.getPaymentDate())
                                .paymentMode(pp.getPaymentMode())
                                .referenceNumber(pp.getReferenceNumber())
                                .notes(pp.getNotes())
                                .build();
                        ppToUse = partyPaymentRepo.save(newPp);
                    }
                    recordEntityMapping(backupSourceId, pp.getId(), "PARTY_PAYMENT", mappedFirmId, ppToUse.getId());
                    markTargetEntityClaimed(claimedTargetEntitiesByType, "PARTY_PAYMENT", ppToUse.getId());
                }
            }
        }

        // 8. Reminders & Tasks
        Map<Long, Reminder> oldToNewReminderMap = new HashMap<>();
        if (backup.getReminders() != null) {
            for (Reminder rem : backup.getReminders()) {
                Long oldFid = rem.getFirmId() != null ? rem.getFirmId() : -1L;
                boolean shouldImport = oldToNewFirmIdMap.containsKey(oldFid);
                if (shouldImport) {
                    Long mappedFirmId = oldToNewFirmIdMap.get(oldFid);
                    Long mappedTargetEntityId = getMappedTargetEntityId(backupSourceId, rem.getId(), "REMINDER", mappedFirmId);
                    Reminder remToUse = null;

                    if (mappedTargetEntityId != null) {
                        remToUse = reminderRepo.findById(mappedTargetEntityId).orElse(null);
                    }
                    if (remToUse == null && Objects.equals(oldFid, mappedFirmId) && rem.getId() != null) {
                        if (isTargetEntityAvailable(claimedTargetEntitiesByType, "REMINDER", rem.getId())) {
                            remToUse = reminderRepo.findById(rem.getId()).orElse(null);
                        }
                    }
                    if (remToUse == null && rem.getTitle() != null && !rem.getTitle().trim().isEmpty()) {
                        Reminder cand = reminderRepo.findFirstByFirmIdAndTitleIgnoreCase(mappedFirmId, rem.getTitle().trim()).orElse(null);
                        if (cand != null && isTargetEntityAvailable(claimedTargetEntitiesByType, "REMINDER", cand.getId())) {
                            remToUse = cand;
                        }
                    }

                    if (remToUse != null) {
                        if (rem.getInboxNotified() != null && rem.getInboxNotified()) {
                            remToUse.setInboxNotified(true);
                        }
                        remToUse = reminderRepo.save(remToUse);
                    } else {
                        Long newCustId = rem.getCustomerId();
                        if (rem.getCustomerId() != null && oldToNewCustomerMap.containsKey(rem.getCustomerId())) {
                            newCustId = oldToNewCustomerMap.get(rem.getCustomerId()).getId();
                        }
                        Reminder newRem = Reminder.builder()
                                .firmId(mappedFirmId)
                                .customerId(newCustId)
                                .title(rem.getTitle())
                                .dueDate(rem.getDueDate())
                                .type(rem.getType())
                                .note(rem.getNote())
                                .tags(rem.getTags())
                                .status(rem.getStatus())
                                .progress(rem.getProgress())
                                .completed(rem.isCompleted())
                                .completedAt(rem.getCompletedAt())
                                .inboxNotified(rem.getInboxNotified())
                                .build();
                        remToUse = reminderRepo.save(newRem);
                    }

                    recordEntityMapping(backupSourceId, rem.getId(), "REMINDER", mappedFirmId, remToUse.getId());
                    markTargetEntityClaimed(claimedTargetEntitiesByType, "REMINDER", remToUse.getId());
                    if (rem.getId() != null) {
                        oldToNewReminderMap.put(rem.getId(), remToUse);
                    }
                }
            }
        }

        // 9. Notes
        if (backup.getNotes() != null) {
            for (Note note : backup.getNotes()) {
                Long oldFid = note.getFirmId() != null ? note.getFirmId() : -1L;
                boolean shouldImport = oldToNewFirmIdMap.containsKey(oldFid);
                if (shouldImport) {
                    Long mappedFirmId = oldToNewFirmIdMap.get(oldFid);
                    Long mappedTargetEntityId = getMappedTargetEntityId(backupSourceId, note.getId(), "NOTE", mappedFirmId);
                    Note noteToUse = null;

                    if (mappedTargetEntityId != null) {
                        noteToUse = noteRepo.findById(mappedTargetEntityId).orElse(null);
                    }
                    if (noteToUse == null && Objects.equals(oldFid, mappedFirmId) && note.getId() != null) {
                        if (isTargetEntityAvailable(claimedTargetEntitiesByType, "NOTE", note.getId())) {
                            noteToUse = noteRepo.findById(note.getId()).orElse(null);
                        }
                    }
                    if (noteToUse == null && note.getTitle() != null && !note.getTitle().trim().isEmpty()) {
                        Note cand = noteRepo.findFirstByFirmIdAndTitleIgnoreCase(mappedFirmId, note.getTitle().trim()).orElse(null);
                        if (cand != null && isTargetEntityAvailable(claimedTargetEntitiesByType, "NOTE", cand.getId())) {
                            noteToUse = cand;
                        }
                    }

                    if (noteToUse == null) {
                        Long newCustId = note.getCustomerId();
                        if (note.getCustomerId() != null && oldToNewCustomerMap.containsKey(note.getCustomerId())) {
                            newCustId = oldToNewCustomerMap.get(note.getCustomerId()).getId();
                        }
                        Note newNote = Note.builder()
                                .firmId(mappedFirmId)
                                .customerId(newCustId)
                                .title(note.getTitle())
                                .content(note.getContent())
                                .build();
                        noteToUse = noteRepo.save(newNote);
                    }
                    recordEntityMapping(backupSourceId, note.getId(), "NOTE", mappedFirmId, noteToUse.getId());
                    markTargetEntityClaimed(claimedTargetEntitiesByType, "NOTE", noteToUse.getId());
                }
            }
        }

        // 10. Expenses
        if (backup.getExpenses() != null) {
            for (Expense exp : backup.getExpenses()) {
                Long oldFid = exp.getFirmId() != null ? exp.getFirmId() : -1L;
                boolean shouldImport = oldToNewFirmIdMap.containsKey(oldFid);
                if (shouldImport) {
                    Long mappedFirmId = oldToNewFirmIdMap.get(oldFid);
                    Long mappedTargetEntityId = getMappedTargetEntityId(backupSourceId, exp.getId(), "EXPENSE", mappedFirmId);
                    Expense expToUse = null;

                    if (mappedTargetEntityId != null) {
                        expToUse = expenseRepo.findByIdAndFirmId(mappedTargetEntityId, mappedFirmId).orElse(null);
                    }
                    if (expToUse == null && Objects.equals(oldFid, mappedFirmId) && exp.getId() != null) {
                        if (isTargetEntityAvailable(claimedTargetEntitiesByType, "EXPENSE", exp.getId())) {
                            expToUse = expenseRepo.findByIdAndFirmId(exp.getId(), mappedFirmId).orElse(null);
                        }
                    }
                    if (expToUse == null && exp.getExpenseDate() != null && exp.getAmount() != null && exp.getCategory() != null) {
                        Expense cand = expenseRepo.findFirstByFirmIdAndExpenseDateAndAmountAndCategory(mappedFirmId, exp.getExpenseDate(), exp.getAmount(), exp.getCategory()).orElse(null);
                        if (cand != null && isTargetEntityAvailable(claimedTargetEntitiesByType, "EXPENSE", cand.getId())) {
                            expToUse = cand;
                        }
                    }

                    if (expToUse == null) {
                        Expense newExp = Expense.builder()
                                .firmId(mappedFirmId)
                                .title(exp.getTitle())
                                .expenseDate(exp.getExpenseDate())
                                .category(exp.getCategory())
                                .amount(exp.getAmount())
                                .paymentMode(exp.getPaymentMode())
                                .notes(exp.getNotes())
                                .build();
                        expToUse = expenseRepo.save(newExp);
                    }
                    recordEntityMapping(backupSourceId, exp.getId(), "EXPENSE", mappedFirmId, expToUse.getId());
                    markTargetEntityClaimed(claimedTargetEntitiesByType, "EXPENSE", expToUse.getId());
                }
            }
        }

        // 10.1 Goals
        Map<Long, Long> oldToNewGoalMap = new HashMap<>();
        if (backup.getGoals() != null) {
            for (Goal g : backup.getGoals()) {
                Long oldFid = g.getFirmId() != null ? g.getFirmId() : -1L;
                boolean shouldImport = oldToNewFirmIdMap.containsKey(oldFid);
                if (shouldImport) {
                    Long mappedFirmId = oldToNewFirmIdMap.get(oldFid);
                    Long mappedTargetEntityId = getMappedTargetEntityId(backupSourceId, g.getId(), "GOAL", mappedFirmId);
                    Goal goalToUse = null;

                    if (mappedTargetEntityId != null) {
                        goalToUse = goalRepo.findById(mappedTargetEntityId).orElse(null);
                    }
                    if (goalToUse == null && Objects.equals(oldFid, mappedFirmId) && g.getId() != null) {
                        if (isTargetEntityAvailable(claimedTargetEntitiesByType, "GOAL", g.getId())) {
                            goalToUse = goalRepo.findById(g.getId()).orElse(null);
                        }
                    }
                    if (goalToUse == null && g.getTitle() != null && !g.getTitle().trim().isEmpty()) {
                        Goal cand = goalRepo.findFirstByFirmIdAndTitleIgnoreCase(mappedFirmId, g.getTitle().trim()).orElse(null);
                        if (cand != null && isTargetEntityAvailable(claimedTargetEntitiesByType, "GOAL", cand.getId())) {
                            goalToUse = cand;
                        }
                    }

                    if (goalToUse == null) {
                        Goal newGoal = Goal.builder()
                                .firmId(mappedFirmId)
                                .title(g.getTitle())
                                .goalType(g.getGoalType())
                                .targetValue(g.getTargetValue())
                                .currentValue(g.getCurrentValue())
                                .unit(g.getUnit())
                                .startDate(g.getStartDate())
                                .targetDate(g.getTargetDate())
                                .status(g.getStatus())
                                .currentStreak(g.getCurrentStreak())
                                .longestStreak(g.getLongestStreak())
                                .lastCheckInDate(g.getLastCheckInDate())
                                .icon(g.getIcon())
                                .color(g.getColor())
                                .notes(g.getNotes())
                                .tags(g.getTags())
                                .build();
                        goalToUse = goalRepo.save(newGoal);
                    }

                    recordEntityMapping(backupSourceId, g.getId(), "GOAL", mappedFirmId, goalToUse.getId());
                    markTargetEntityClaimed(claimedTargetEntitiesByType, "GOAL", goalToUse.getId());
                    if (g.getId() != null) {
                        oldToNewGoalMap.put(g.getId(), goalToUse.getId());
                    }
                }
            }
        }

        // 10.2 Savings
        if (backup.getSavings() != null) {
            for (SavingRecord sav : backup.getSavings()) {
                Long oldFid = sav.getFirmId() != null ? sav.getFirmId() : -1L;
                boolean shouldImport = oldToNewFirmIdMap.containsKey(oldFid);
                if (shouldImport) {
                    Long mappedFirmId = oldToNewFirmIdMap.get(oldFid);
                    Long mappedTargetEntityId = getMappedTargetEntityId(backupSourceId, sav.getId(), "SAVING", mappedFirmId);
                    SavingRecord savToUse = null;

                    if (mappedTargetEntityId != null) {
                        savToUse = savingRepo.findByIdAndFirmId(mappedTargetEntityId, mappedFirmId).orElse(null);
                    }
                    if (savToUse == null && Objects.equals(oldFid, mappedFirmId) && sav.getId() != null) {
                        if (isTargetEntityAvailable(claimedTargetEntitiesByType, "SAVING", sav.getId())) {
                            savToUse = savingRepo.findByIdAndFirmId(sav.getId(), mappedFirmId).orElse(null);
                        }
                    }
                    if (savToUse == null && sav.getSavingDate() != null && sav.getAmount() != null) {
                        SavingRecord cand = savingRepo.findFirstByFirmIdAndSavingDateAndAmount(mappedFirmId, sav.getSavingDate(), sav.getAmount()).orElse(null);
                        if (cand != null && isTargetEntityAvailable(claimedTargetEntitiesByType, "SAVING", cand.getId())) {
                            savToUse = cand;
                        }
                    }

                    if (savToUse == null) {
                        Long mappedGoalId = (sav.getGoalId() != null) ? oldToNewGoalMap.get(sav.getGoalId()) : null;
                        SavingRecord newSav = SavingRecord.builder()
                                .firmId(mappedFirmId)
                                .title(sav.getTitle())
                                .amount(sav.getAmount())
                                .category(sav.getCategory())
                                .savingDate(sav.getSavingDate())
                                .paymentMode(sav.getPaymentMode())
                                .goalId(mappedGoalId)
                                .notes(sav.getNotes())
                                .tags(sav.getTags())
                                .build();
                        savToUse = savingRepo.save(newSav);
                    }
                    recordEntityMapping(backupSourceId, sav.getId(), "SAVING", mappedFirmId, savToUse.getId());
                    markTargetEntityClaimed(claimedTargetEntitiesByType, "SAVING", savToUse.getId());
                }
            }
        }

        // 10.3 Goal Logs
        if (backup.getGoalLogs() != null) {
            for (GoalLog gl : backup.getGoalLogs()) {
                Long oldFid = gl.getFirmId() != null ? gl.getFirmId() : -1L;
                boolean shouldImport = oldToNewFirmIdMap.containsKey(oldFid);
                if (shouldImport) {
                    Long mappedFirmId = oldToNewFirmIdMap.get(oldFid);
                    Long mappedTargetEntityId = getMappedTargetEntityId(backupSourceId, gl.getId(), "GOAL_LOG", mappedFirmId);
                    GoalLog glToUse = null;

                    if (mappedTargetEntityId != null) {
                        glToUse = goalLogRepo.findById(mappedTargetEntityId).orElse(null);
                    }
                    if (glToUse == null && Objects.equals(oldFid, mappedFirmId) && gl.getId() != null) {
                        if (isTargetEntityAvailable(claimedTargetEntitiesByType, "GOAL_LOG", gl.getId())) {
                            glToUse = goalLogRepo.findById(gl.getId()).orElse(null);
                        }
                    }

                    Long mappedGoalId = (gl.getGoalId() != null) ? oldToNewGoalMap.get(gl.getGoalId()) : null;
                    if (glToUse == null && mappedGoalId != null && gl.getLogDate() != null && gl.getDeltaValue() != null) {
                        GoalLog cand = goalLogRepo.findFirstByGoalIdAndFirmIdAndLogDateAndDeltaValue(mappedGoalId, mappedFirmId, gl.getLogDate(), gl.getDeltaValue()).orElse(null);
                        if (cand != null && isTargetEntityAvailable(claimedTargetEntitiesByType, "GOAL_LOG", cand.getId())) {
                            glToUse = cand;
                        }
                    }

                    if (glToUse == null && mappedGoalId != null) {
                        GoalLog newGl = GoalLog.builder()
                                .firmId(mappedFirmId)
                                .goalId(mappedGoalId)
                                .actionType(gl.getActionType())
                                .deltaValue(gl.getDeltaValue())
                                .resultingValue(gl.getResultingValue())
                                .logDate(gl.getLogDate())
                                .notes(gl.getNotes())
                                .createdAt(gl.getCreatedAt())
                                .build();
                        glToUse = goalLogRepo.save(newGl);
                    }
                    if (glToUse != null) {
                        recordEntityMapping(backupSourceId, gl.getId(), "GOAL_LOG", mappedFirmId, glToUse.getId());
                        markTargetEntityClaimed(claimedTargetEntitiesByType, "GOAL_LOG", glToUse.getId());
                    }
                }
            }
        }

        // 11. Employees
        Map<Long, Employee> oldToNewEmpMap = new HashMap<>();
        if (backup.getEmployees() != null) {
            for (Employee emp : backup.getEmployees()) {
                Long oldFid = emp.getFirmId() != null ? emp.getFirmId() : -1L;
                boolean shouldImport = oldToNewFirmIdMap.containsKey(oldFid);
                if (shouldImport) {
                    Long mappedFirmId = oldToNewFirmIdMap.get(oldFid);
                    Long mappedTargetEntityId = getMappedTargetEntityId(backupSourceId, emp.getId(), "EMPLOYEE", mappedFirmId);
                    Employee empToUse = null;

                    if (mappedTargetEntityId != null) {
                        empToUse = employeeRepo.findById(mappedTargetEntityId).orElse(null);
                    }
                    if (empToUse == null && Objects.equals(oldFid, mappedFirmId) && emp.getId() != null) {
                        if (isTargetEntityAvailable(claimedTargetEntitiesByType, "EMPLOYEE", emp.getId())) {
                            empToUse = employeeRepo.findById(emp.getId()).orElse(null);
                        }
                    }
                    if (empToUse == null && emp.getPhone() != null && !emp.getPhone().trim().isEmpty() && emp.getName() != null && !emp.getName().trim().isEmpty()) {
                        Employee cand = employeeRepo.findFirstByFirmIdAndPhoneAndNameIgnoreCase(mappedFirmId, emp.getPhone().trim(), emp.getName().trim()).orElse(null);
                        if (cand != null && isTargetEntityAvailable(claimedTargetEntitiesByType, "EMPLOYEE", cand.getId())) {
                            empToUse = cand;
                        }
                    }
                    if (empToUse == null && (emp.getPhone() == null || emp.getPhone().trim().isEmpty()) && emp.getName() != null && !emp.getName().trim().isEmpty()) {
                        Employee cand = employeeRepo.findFirstByFirmIdAndNameIgnoreCaseAndPhoneIsNull(mappedFirmId, emp.getName().trim()).orElse(null);
                        if (cand != null && isTargetEntityAvailable(claimedTargetEntitiesByType, "EMPLOYEE", cand.getId())) {
                            empToUse = cand;
                        }
                    }

                    if (empToUse != null) {
                        if (empToUse.getEmail() == null && emp.getEmail() != null) empToUse.setEmail(emp.getEmail());
                        if (empToUse.getAddress() == null && emp.getAddress() != null) empToUse.setAddress(emp.getAddress());
                        empToUse = employeeRepo.save(empToUse);
                    } else {
                        Employee newEmp = new Employee();
                        newEmp.setFirmId(mappedFirmId);
                        newEmp.setName(emp.getName());
                        newEmp.setPhone(emp.getPhone());
                        newEmp.setRole(emp.getRole());
                        newEmp.setDateOfJoining(emp.getDateOfJoining());
                        newEmp.setIdProofNumber(emp.getIdProofNumber());
                        newEmp.setIsActive(emp.getIsActive());
                        newEmp.setMonthlyBaseSalary(emp.getMonthlyBaseSalary());
                        newEmp.setAllowedPaidLeavesPerMonth(emp.getAllowedPaidLeavesPerMonth());
                        newEmp.setCurrentAdvanceBalance(emp.getCurrentAdvanceBalance());
                        newEmp.setDepartment(emp.getDepartment());
                        newEmp.setDesignation(emp.getDesignation());
                        newEmp.setEmail(emp.getEmail());
                        newEmp.setAddress(emp.getAddress());
                        newEmp.setEmergencyContactName(emp.getEmergencyContactName());
                        newEmp.setEmergencyContactPhone(emp.getEmergencyContactPhone());
                        newEmp.setBankAccountName(emp.getBankAccountName());
                        newEmp.setBankAccountNumber(emp.getBankAccountNumber());
                        newEmp.setBankIfscCode(emp.getBankIfscCode());
                        newEmp.setBankName(emp.getBankName());

                        empToUse = employeeRepo.save(newEmp);
                    }

                    recordEntityMapping(backupSourceId, emp.getId(), "EMPLOYEE", mappedFirmId, empToUse.getId());
                    markTargetEntityClaimed(claimedTargetEntitiesByType, "EMPLOYEE", empToUse.getId());
                    if (emp.getId() != null) {
                        oldToNewEmpMap.put(emp.getId(), empToUse);
                    }
                }
            }
        }

        // 12. Employee Sub-records (Attendance, Leaves, Salary, Advances, Promotions, Documents)
        if (backup.getAttendanceRecords() != null) {
            for (AttendanceRecord att : backup.getAttendanceRecords()) {
                if (att.getEmployee() != null && oldToNewEmpMap.containsKey(att.getEmployee().getId())) {
                    Long mappedFirmId = oldToNewEmpMap.get(att.getEmployee().getId()).getFirmId();
                    Long oldFid = att.getEmployee().getFirmId() != null ? att.getEmployee().getFirmId() : backup.getSourceFirmId();
                    Long mappedTargetEntityId = getMappedTargetEntityId(backupSourceId, att.getId(), "ATTENDANCE", mappedFirmId);
                    AttendanceRecord attToUse = null;

                    if (mappedTargetEntityId != null) {
                        attToUse = attendanceRecordRepo.findById(mappedTargetEntityId).orElse(null);
                    }
                    if (attToUse == null && Objects.equals(oldFid, mappedFirmId) && att.getId() != null) {
                        if (isTargetEntityAvailable(claimedTargetEntitiesByType, "ATTENDANCE", att.getId())) {
                            attToUse = attendanceRecordRepo.findById(att.getId()).orElse(null);
                        }
                    }

                    Long targetEmpId = oldToNewEmpMap.get(att.getEmployee().getId()).getId();
                    if (attToUse == null && targetEmpId != null && att.getDate() != null) {
                        AttendanceRecord cand = attendanceRecordRepo.findByEmployeeIdAndDate(targetEmpId, att.getDate()).orElse(null);
                        if (cand != null) {
                            if (isTargetEntityAvailable(claimedTargetEntitiesByType, "ATTENDANCE", cand.getId())) {
                                attToUse = cand;
                            } else {
                                throw new com.billing.simple.billsoft.exception.RestoreConflictException(
                                    "ATTENDANCE", targetEmpId + ":" + att.getDate(),
                                    "Restore conflict: Employee ID " + targetEmpId + " already has an attendance record for date " + att.getDate()
                                );
                            }
                        }
                    }

                    if (attToUse == null) {
                        AttendanceRecord newAtt = new AttendanceRecord();
                        newAtt.setEmployee(oldToNewEmpMap.get(att.getEmployee().getId()));
                        newAtt.setDate(att.getDate());
                        newAtt.setStatus(att.getStatus());
                        newAtt.setRemarks(att.getRemarks());
                        newAtt.setLeaveType(att.getLeaveType());
                        newAtt.setApprovedBy(att.getApprovedBy());
                        attToUse = attendanceRecordRepo.save(newAtt);
                    }
                    recordEntityMapping(backupSourceId, att.getId(), "ATTENDANCE", mappedFirmId, attToUse.getId());
                    markTargetEntityClaimed(claimedTargetEntitiesByType, "ATTENDANCE", attToUse.getId());
                }
            }
        }

        if (backup.getLeaveRecords() != null) {
            for (LeaveRecord lr : backup.getLeaveRecords()) {
                if (lr.getEmployee() != null && oldToNewEmpMap.containsKey(lr.getEmployee().getId())) {
                    Long mappedFirmId = oldToNewEmpMap.get(lr.getEmployee().getId()).getFirmId();
                    Long oldFid = lr.getEmployee().getFirmId() != null ? lr.getEmployee().getFirmId() : backup.getSourceFirmId();
                    Long mappedTargetEntityId = getMappedTargetEntityId(backupSourceId, lr.getId(), "LEAVE", mappedFirmId);
                    LeaveRecord lrToUse = null;

                    if (mappedTargetEntityId != null) {
                        lrToUse = leaveRecordRepo.findById(mappedTargetEntityId).orElse(null);
                    }
                    if (lrToUse == null && Objects.equals(oldFid, mappedFirmId) && lr.getId() != null) {
                        if (isTargetEntityAvailable(claimedTargetEntitiesByType, "LEAVE", lr.getId())) {
                            lrToUse = leaveRecordRepo.findById(lr.getId()).orElse(null);
                        }
                    }

                    Long targetEmpId = oldToNewEmpMap.get(lr.getEmployee().getId()).getId();
                    if (lrToUse == null && targetEmpId != null && lr.getStartDate() != null && lr.getEndDate() != null) {
                        LeaveRecord cand = leaveRecordRepo.findFirstByEmployeeIdAndStartDateAndEndDate(targetEmpId, lr.getStartDate(), lr.getEndDate()).orElse(null);
                        if (cand != null && isTargetEntityAvailable(claimedTargetEntitiesByType, "LEAVE", cand.getId())) {
                            lrToUse = cand;
                        }
                    }

                    if (lrToUse == null) {
                        LeaveRecord newLr = new LeaveRecord();
                        newLr.setEmployee(oldToNewEmpMap.get(lr.getEmployee().getId()));
                        newLr.setStartDate(lr.getStartDate());
                        newLr.setEndDate(lr.getEndDate());
                        newLr.setType(lr.getType());
                        newLr.setStatus(lr.getStatus());
                        newLr.setTotalDays(lr.getTotalDays());
                        newLr.setReason(lr.getReason());
                        newLr.setApprovedBy(lr.getApprovedBy());
                        lrToUse = leaveRecordRepo.save(newLr);
                    }
                    recordEntityMapping(backupSourceId, lr.getId(), "LEAVE", mappedFirmId, lrToUse.getId());
                    markTargetEntityClaimed(claimedTargetEntitiesByType, "LEAVE", lrToUse.getId());
                }
            }
        }

        if (backup.getSalaryRecords() != null) {
            for (SalaryRecord sr : backup.getSalaryRecords()) {
                if (sr.getEmployee() != null && oldToNewEmpMap.containsKey(sr.getEmployee().getId())) {
                    Long mappedFirmId = oldToNewEmpMap.get(sr.getEmployee().getId()).getFirmId();
                    Long oldFid = sr.getEmployee().getFirmId() != null ? sr.getEmployee().getFirmId() : backup.getSourceFirmId();
                    Long mappedTargetEntityId = getMappedTargetEntityId(backupSourceId, sr.getId(), "SALARY", mappedFirmId);
                    SalaryRecord srToUse = null;

                    if (mappedTargetEntityId != null) {
                        srToUse = salaryRepo.findById(mappedTargetEntityId).orElse(null);
                    }
                    if (srToUse == null && Objects.equals(oldFid, mappedFirmId) && sr.getId() != null) {
                        if (isTargetEntityAvailable(claimedTargetEntitiesByType, "SALARY", sr.getId())) {
                            srToUse = salaryRepo.findById(sr.getId()).orElse(null);
                        }
                    }

                    Long targetEmpId = oldToNewEmpMap.get(sr.getEmployee().getId()).getId();
                    if (srToUse == null && targetEmpId != null && sr.getMonthYear() != null) {
                        SalaryRecord cand = salaryRepo.findByEmployeeIdAndMonthYear(targetEmpId, sr.getMonthYear()).orElse(null);
                        if (cand != null && isTargetEntityAvailable(claimedTargetEntitiesByType, "SALARY", cand.getId())) {
                            srToUse = cand;
                        }
                    }

                    if (srToUse == null) {
                        SalaryRecord newSr = new SalaryRecord();
                        newSr.setEmployee(oldToNewEmpMap.get(sr.getEmployee().getId()));
                        newSr.setMonthYear(sr.getMonthYear());
                        newSr.setBaseSalaryAtTime(sr.getBaseSalaryAtTime());
                        newSr.setDaysAbsent(sr.getDaysAbsent());
                        newSr.setPaidLeavesUsed(sr.getPaidLeavesUsed());
                        newSr.setUnpaidLeaves(sr.getUnpaidLeaves());
                        newSr.setLeaveDeductionAmount(sr.getLeaveDeductionAmount());
                        newSr.setBonusAmount(sr.getBonusAmount());
                        newSr.setAdvanceDeducted(sr.getAdvanceDeducted());
                        newSr.setNetPaid(sr.getNetPaid());
                        newSr.setPaymentDate(sr.getPaymentDate());
                        srToUse = salaryRepo.save(newSr);
                    }
                    recordEntityMapping(backupSourceId, sr.getId(), "SALARY", mappedFirmId, srToUse.getId());
                    markTargetEntityClaimed(claimedTargetEntitiesByType, "SALARY", srToUse.getId());
                }
            }
        }

        if (backup.getAdvances() != null) {
            for (EmployeeAdvance adv : backup.getAdvances()) {
                if (adv.getEmployee() != null && oldToNewEmpMap.containsKey(adv.getEmployee().getId())) {
                    Long mappedFirmId = oldToNewEmpMap.get(adv.getEmployee().getId()).getFirmId();
                    Long oldFid = adv.getEmployee().getFirmId() != null ? adv.getEmployee().getFirmId() : backup.getSourceFirmId();
                    Long mappedTargetEntityId = getMappedTargetEntityId(backupSourceId, adv.getId(), "ADVANCE", mappedFirmId);
                    EmployeeAdvance advToUse = null;

                    if (mappedTargetEntityId != null) {
                        advToUse = advanceRepo.findById(mappedTargetEntityId).orElse(null);
                    }
                    if (advToUse == null && Objects.equals(oldFid, mappedFirmId) && adv.getId() != null) {
                        if (isTargetEntityAvailable(claimedTargetEntitiesByType, "ADVANCE", adv.getId())) {
                            advToUse = advanceRepo.findById(adv.getId()).orElse(null);
                        }
                    }

                    Long targetEmpId = oldToNewEmpMap.get(adv.getEmployee().getId()).getId();
                    if (advToUse == null && targetEmpId != null && adv.getDate() != null && adv.getAmount() != null) {
                        EmployeeAdvance cand = advanceRepo.findFirstByEmployeeIdAndDateAndAmount(targetEmpId, adv.getDate(), adv.getAmount()).orElse(null);
                        if (cand != null && isTargetEntityAvailable(claimedTargetEntitiesByType, "ADVANCE", cand.getId())) {
                            advToUse = cand;
                        }
                    }

                    if (advToUse == null) {
                        EmployeeAdvance newAdv = new EmployeeAdvance();
                        newAdv.setEmployee(oldToNewEmpMap.get(adv.getEmployee().getId()));
                        newAdv.setDate(adv.getDate());
                        newAdv.setAmount(adv.getAmount());
                        newAdv.setDescription(adv.getDescription());
                        advToUse = advanceRepo.save(newAdv);
                    }
                    recordEntityMapping(backupSourceId, adv.getId(), "ADVANCE", mappedFirmId, advToUse.getId());
                    markTargetEntityClaimed(claimedTargetEntitiesByType, "ADVANCE", advToUse.getId());
                }
            }
        }

        if (backup.getPromotions() != null) {
            for (PromotionRecord pr : backup.getPromotions()) {
                if (pr.getEmployee() != null && oldToNewEmpMap.containsKey(pr.getEmployee().getId())) {
                    Long mappedFirmId = oldToNewEmpMap.get(pr.getEmployee().getId()).getFirmId();
                    Long oldFid = pr.getEmployee().getFirmId() != null ? pr.getEmployee().getFirmId() : backup.getSourceFirmId();
                    Long mappedTargetEntityId = getMappedTargetEntityId(backupSourceId, pr.getId(), "PROMOTION", mappedFirmId);
                    PromotionRecord prToUse = null;

                    if (mappedTargetEntityId != null) {
                        prToUse = promotionRepo.findById(mappedTargetEntityId).orElse(null);
                    }
                    if (prToUse == null && Objects.equals(oldFid, mappedFirmId) && pr.getId() != null) {
                        if (isTargetEntityAvailable(claimedTargetEntitiesByType, "PROMOTION", pr.getId())) {
                            prToUse = promotionRepo.findById(pr.getId()).orElse(null);
                        }
                    }

                    Long targetEmpId = oldToNewEmpMap.get(pr.getEmployee().getId()).getId();
                    if (prToUse == null && targetEmpId != null && pr.getEffectiveDate() != null && pr.getNewRole() != null) {
                        PromotionRecord cand = promotionRepo.findFirstByEmployeeIdAndEffectiveDateAndNewRole(targetEmpId, pr.getEffectiveDate(), pr.getNewRole()).orElse(null);
                        if (cand != null && isTargetEntityAvailable(claimedTargetEntitiesByType, "PROMOTION", cand.getId())) {
                            prToUse = cand;
                        }
                    }

                    if (prToUse == null) {
                        PromotionRecord newPr = new PromotionRecord();
                        newPr.setEmployee(oldToNewEmpMap.get(pr.getEmployee().getId()));
                        newPr.setEffectiveDate(pr.getEffectiveDate());
                        newPr.setType(pr.getType());
                        newPr.setPreviousRole(pr.getPreviousRole());
                        newPr.setNewRole(pr.getNewRole());
                        newPr.setPreviousSalary(pr.getPreviousSalary());
                        newPr.setNewSalary(pr.getNewSalary());
                        newPr.setReason(pr.getReason());
                        newPr.setIsApplied(pr.getIsApplied());
                        prToUse = promotionRepo.save(newPr);
                    }
                    recordEntityMapping(backupSourceId, pr.getId(), "PROMOTION", mappedFirmId, prToUse.getId());
                    markTargetEntityClaimed(claimedTargetEntitiesByType, "PROMOTION", prToUse.getId());
                }
            }
        }

        if (backup.getEmployeeDocuments() != null) {
            for (EmployeeDocument doc : backup.getEmployeeDocuments()) {
                Long empId = doc.getEmployee() != null ? doc.getEmployee().getId() : doc.getEmployeeId();
                if (empId != null && oldToNewEmpMap.containsKey(empId)) {
                    Long mappedFirmId = oldToNewEmpMap.get(empId).getFirmId();
                    Long oldFid = doc.getEmployee() != null && doc.getEmployee().getFirmId() != null ? doc.getEmployee().getFirmId() : backup.getSourceFirmId();
                    Long mappedTargetEntityId = getMappedTargetEntityId(backupSourceId, doc.getId(), "EMPLOYEE_DOCUMENT", mappedFirmId);
                    EmployeeDocument docToUse = null;

                    if (mappedTargetEntityId != null) {
                        docToUse = employeeDocumentRepo.findById(mappedTargetEntityId).orElse(null);
                    }
                    if (docToUse == null && Objects.equals(oldFid, mappedFirmId) && doc.getId() != null) {
                        if (isTargetEntityAvailable(claimedTargetEntitiesByType, "EMPLOYEE_DOCUMENT", doc.getId())) {
                            docToUse = employeeDocumentRepo.findById(doc.getId()).orElse(null);
                        }
                    }

                    Long targetEmpId = oldToNewEmpMap.get(empId).getId();
                    if (docToUse == null && targetEmpId != null && doc.getFileName() != null) {
                        EmployeeDocument cand = employeeDocumentRepo.findFirstByEmployee_IdAndFileName(targetEmpId, doc.getFileName()).orElse(null);
                        if (cand != null && isTargetEntityAvailable(claimedTargetEntitiesByType, "EMPLOYEE_DOCUMENT", cand.getId())) {
                            docToUse = cand;
                        }
                    }

                    if (docToUse == null) {
                        EmployeeDocument newDoc = new EmployeeDocument();
                        newDoc.setEmployee(oldToNewEmpMap.get(empId));
                        newDoc.setType(doc.getType());
                        newDoc.setFileName(doc.getFileName());
                        newDoc.setDataBase64(doc.getDataBase64());
                        newDoc.setUploadedAt(doc.getUploadedAt());
                        docToUse = employeeDocumentRepo.save(newDoc);
                    }
                    recordEntityMapping(backupSourceId, doc.getId(), "EMPLOYEE_DOCUMENT", mappedFirmId, docToUse.getId());
                    markTargetEntityClaimed(claimedTargetEntitiesByType, "EMPLOYEE_DOCUMENT", docToUse.getId());
                }
            }
        }

        // 13. Business Letters
        if (backup.getBusinessLetters() != null) {
            for (BusinessLetter bl : backup.getBusinessLetters()) {
                Long oldFid = bl.getFirmId() != null ? bl.getFirmId() : -1L;
                boolean shouldImport = oldToNewFirmIdMap.containsKey(oldFid);
                if (shouldImport) {
                    Long mappedFirmId = oldToNewFirmIdMap.get(oldFid);
                    Long mappedTargetEntityId = getMappedTargetEntityId(backupSourceId, bl.getId(), "BUSINESS_LETTER", mappedFirmId);
                    BusinessLetter blToUse = null;

                    if (mappedTargetEntityId != null) {
                        blToUse = businessLetterRepo.findById(mappedTargetEntityId).orElse(null);
                    }
                    if (blToUse == null && Objects.equals(oldFid, mappedFirmId) && bl.getId() != null) {
                        if (isTargetEntityAvailable(claimedTargetEntitiesByType, "BUSINESS_LETTER", bl.getId())) {
                            blToUse = businessLetterRepo.findById(bl.getId()).orElse(null);
                        }
                    }
                    if (blToUse == null && bl.getLetterNumber() != null && !bl.getLetterNumber().trim().isEmpty()) {
                        BusinessLetter cand = businessLetterRepo.findFirstByFirmIdAndLetterNumberIgnoreCase(mappedFirmId, bl.getLetterNumber().trim()).orElse(null);
                        if (cand != null && isTargetEntityAvailable(claimedTargetEntitiesByType, "BUSINESS_LETTER", cand.getId())) {
                            blToUse = cand;
                        }
                    }

                    if (blToUse == null) {
                        Long newPartyId = bl.getPartyId();
                        if (bl.getPartyId() != null && oldToNewPartyMap.containsKey(bl.getPartyId())) {
                            newPartyId = oldToNewPartyMap.get(bl.getPartyId()).getId();
                        }
                        Long newCustId = bl.getCustomerId();
                        if (bl.getCustomerId() != null && oldToNewCustomerMap.containsKey(bl.getCustomerId())) {
                            newCustId = oldToNewCustomerMap.get(bl.getCustomerId()).getId();
                        }

                        String rName = (bl.getRecipientName() != null && !bl.getRecipientName().trim().isEmpty()) ? bl.getRecipientName().trim() : "Valued Recipient";
                        String sub = (bl.getSubject() != null && !bl.getSubject().trim().isEmpty()) ? bl.getSubject().trim() : "Official Communication";
                        String cnt = bl.getContent() != null ? bl.getContent() : "";
                        LocalDate lDate = bl.getLetterDate() != null ? bl.getLetterDate() : LocalDate.now();
                        String lNum = (bl.getLetterNumber() != null && !bl.getLetterNumber().trim().isEmpty()) ? bl.getLetterNumber().trim() : "LTR-" + System.currentTimeMillis();

                        BusinessLetter newBl = BusinessLetter.builder()
                                .firmId(mappedFirmId)
                                .letterNumber(lNum)
                                .letterDate(lDate)
                                .senderType(bl.getSenderType() != null ? bl.getSenderType() : "FIRM")
                                .senderName(bl.getSenderName())
                                .senderCompany(bl.getSenderCompany())
                                .senderAddress(bl.getSenderAddress())
                                .senderPhone(bl.getSenderPhone())
                                .senderEmail(bl.getSenderEmail())
                                .senderGstin(bl.getSenderGstin())
                                .recipientType(bl.getRecipientType() != null ? bl.getRecipientType() : LetterRecipientType.CUSTOM)
                                .partyId(newPartyId)
                                .customerId(newCustId)
                                .recipientName(rName)
                                .recipientDesignation(bl.getRecipientDesignation())
                                .recipientCompany(bl.getRecipientCompany())
                                .recipientAddress(bl.getRecipientAddress())
                                .recipientPhone(bl.getRecipientPhone())
                                .recipientEmail(bl.getRecipientEmail())
                                .subject(sub)
                                .category(bl.getCategory() != null ? bl.getCategory() : "GENERAL")
                                .content(cnt)
                                .signatoryName(bl.getSignatoryName())
                                .signatoryDesignation(bl.getSignatoryDesignation())
                                .status(bl.getStatus() != null ? bl.getStatus() : LetterStatus.ISSUED)
                                .includeHeader(bl.getIncludeHeader() != null ? bl.getIncludeHeader() : true)
                                .includeFooter(bl.getIncludeFooter() != null ? bl.getIncludeFooter() : true)
                                .build();
                        blToUse = businessLetterRepo.save(newBl);
                    }
                    recordEntityMapping(backupSourceId, bl.getId(), "BUSINESS_LETTER", mappedFirmId, blToUse.getId());
                    markTargetEntityClaimed(claimedTargetEntitiesByType, "BUSINESS_LETTER", blToUse.getId());
                }
            }
        }

        // 14. Notifications
        if (backup.getNotifications() != null) {
            for (Notification notif : backup.getNotifications()) {
                Long oldFid = notif.getFirmId() != null ? notif.getFirmId() : -1L;
                boolean shouldImport = oldToNewFirmIdMap.containsKey(oldFid);
                if (shouldImport) {
                    Long mappedFirmId = oldToNewFirmIdMap.get(oldFid);
                    Long mappedTargetEntityId = getMappedTargetEntityId(backupSourceId, notif.getId(), "NOTIFICATION", mappedFirmId);
                    Notification notifToUse = null;

                    if (mappedTargetEntityId != null) {
                        notifToUse = notificationRepo.findByIdAndFirmId(mappedTargetEntityId, mappedFirmId).orElse(null);
                    }
                    if (notifToUse == null && notif.getEventKey() != null && !notif.getEventKey().trim().isEmpty()) {
                        notifToUse = notificationRepo.findByFirmIdAndEventKey(mappedFirmId, notif.getEventKey().trim()).orElse(null);
                    }
                    if (notifToUse == null && Objects.equals(oldFid, mappedFirmId) && notif.getId() != null) {
                        if (isTargetEntityAvailable(claimedTargetEntitiesByType, "NOTIFICATION", notif.getId())) {
                            notifToUse = notificationRepo.findByIdAndFirmId(notif.getId(), mappedFirmId).orElse(null);
                        }
                    }

                    if (notifToUse != null) {
                        if (notif.getStatus() != null) {
                            notifToUse.setStatus(notif.getStatus());
                        }
                        if (notif.getTitle() != null) {
                            notifToUse.setTitle(notif.getTitle());
                        }
                        if (notif.getBody() != null) {
                            notifToUse.setBody(notif.getBody());
                        }
                        if (notif.getPriority() != null) {
                            notifToUse.setPriority(notif.getPriority());
                        }
                        if (notif.getSender() != null) {
                            notifToUse.setSender(notif.getSender());
                        }
                        notifToUse.setSnoozedUntil(notif.getSnoozedUntil());
                        notifToUse.setExpiresAt(notif.getExpiresAt());
                        notifToUse.setUpdatedAt(LocalDateTime.now());
                        notifToUse = notificationRepo.save(notifToUse);
                    } else {
                        Notification newNotif = Notification.builder()
                                .firmId(mappedFirmId)
                                .category(notif.getCategory() != null ? notif.getCategory() : NotificationCategory.SYSTEM)
                                .title(notif.getTitle())
                                .body(notif.getBody())
                                .sender(notif.getSender())
                                .eventKey(notif.getEventKey())
                                .primaryActionLabel(notif.getPrimaryActionLabel())
                                .primaryActionType(notif.getPrimaryActionType())
                                .primaryActionTarget(notif.getPrimaryActionTarget())
                                .secondaryActionLabel(notif.getSecondaryActionLabel())
                                .secondaryActionType(notif.getSecondaryActionType())
                                .secondaryActionTarget(notif.getSecondaryActionTarget())
                                .priority(notif.getPriority() != null ? notif.getPriority() : NotificationPriority.NORMAL)
                                .status(notif.getStatus() != null ? notif.getStatus() : NotificationStatus.UNREAD)
                                .snoozedUntil(notif.getSnoozedUntil())
                                .expiresAt(notif.getExpiresAt())
                                .createdAt(notif.getCreatedAt() != null ? notif.getCreatedAt() : LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();
                        notifToUse = notificationRepo.save(newNotif);
                    }
                    recordEntityMapping(backupSourceId, notif.getId(), "NOTIFICATION", mappedFirmId, notifToUse.getId());
                    markTargetEntityClaimed(claimedTargetEntitiesByType, "NOTIFICATION", notifToUse.getId());
                }
            }
        }

        // 14c. Notification Preferences
        if (backup.getNotificationPreferences() != null) {
            for (NotificationPreference pref : backup.getNotificationPreferences()) {
                Long oldFid = pref.getFirmId() != null ? pref.getFirmId() : -1L;
                boolean shouldImport = oldToNewFirmIdMap.containsKey(oldFid);
                if (shouldImport) {
                    Long mappedFirmId = oldToNewFirmIdMap.get(oldFid);
                    NotificationPreference prefToUse = notificationPreferenceRepo.findByFirmId(mappedFirmId).orElse(null);
                    if (prefToUse != null) {
                        prefToUse.setEnabled(pref.isEnabled());
                        prefToUse.setBellEnabled(pref.isBellEnabled());
                        prefToUse.setInboxEnabled(pref.isInboxEnabled());
                        prefToUse.setDefaultSnooze(pref.getDefaultSnooze());
                        prefToUse.setLicensingEnabled(pref.isLicensingEnabled());
                        prefToUse.setBillingEnabled(pref.isBillingEnabled());
                        prefToUse.setInventoryEnabled(pref.isInventoryEnabled());
                        prefToUse.setPurchaseEnabled(pref.isPurchaseEnabled());
                        prefToUse.setPlannerEnabled(pref.isPlannerEnabled());
                        prefToUse.setHrEnabled(pref.isHrEnabled());
                        prefToUse.setSystemEnabled(pref.isSystemEnabled());
                        prefToUse.setUpdatedAt(LocalDateTime.now());
                        notificationPreferenceRepo.save(prefToUse);
                    } else {
                        NotificationPreference newPref = NotificationPreference.builder()
                                .firmId(mappedFirmId)
                                .enabled(pref.isEnabled())
                                .bellEnabled(pref.isBellEnabled())
                                .inboxEnabled(pref.isInboxEnabled())
                                .defaultSnooze(pref.getDefaultSnooze())
                                .licensingEnabled(pref.isLicensingEnabled())
                                .billingEnabled(pref.isBillingEnabled())
                                .inventoryEnabled(pref.isInventoryEnabled())
                                .purchaseEnabled(pref.isPurchaseEnabled())
                                .plannerEnabled(pref.isPlannerEnabled())
                                .hrEnabled(pref.isHrEnabled())
                                .systemEnabled(pref.isSystemEnabled())
                                .updatedAt(LocalDateTime.now())
                                .build();
                        notificationPreferenceRepo.save(newPref);
                    }
                }
            }
        }

        // 15. App Configs (AUDIT-033 & Snake lifecycle)
        if (backup.getAppConfigs() != null) {
            for (AppConfig ac : backup.getAppConfigs()) {
                if (ac.getConfigKey() == null) continue;
                String rawKey = ac.getConfigKey().trim();

                // AUDIT-035: Never restore sensitive keys or factory reset epoch from backup
                if (isSensitiveConfigKey(rawKey) || "FACTORY_RESET_EPOCH".equalsIgnoreCase(rawKey)) {
                    continue;
                }

                // Remap firm-scoped keys if needed
                Long oldFid = extractFirmIdFromConfigKey(rawKey);
                String targetKey = rawKey;
                if (oldFid != null) {
                    if (!oldToNewFirmIdMap.containsKey(oldFid)) {
                        continue;
                    }
                    Long newFid = oldToNewFirmIdMap.get(oldFid);
                    if (rawKey.startsWith("LAST_") && rawKey.contains("_SEQ_")) {
                        String[] parts = rawKey.split("_");
                        if (parts.length >= 4 && parts[3].equals(String.valueOf(oldFid))) {
                            parts[3] = String.valueOf(newFid);
                            targetKey = String.join("_", parts);
                        }
                    } else {
                        int lastUnderscore = rawKey.lastIndexOf('_');
                        if (lastUnderscore >= 0) {
                            targetKey = rawKey.substring(0, lastUnderscore + 1) + newFid;
                        }
                    }
                }

                if ("SNAKE_GAME_STATE".equals(targetKey)) {
                    // Revision-aware Snake restore: older backup must not overwrite newer progression
                    mergeSnakeGameState(ac.getConfigValue());
                } else if (isSequenceCounterKey(targetKey)) {
                    // Sequence counter: mergedValue = max(existingValue, backupValue)
                    Optional<AppConfig> existingOpt = appConfigRepo.findById(targetKey);
                    if (existingOpt.isPresent()) {
                        AppConfig existing = existingOpt.get();
                        long existingVal = parseLongSafe(existing.getConfigValue());
                        long backupVal = parseLongSafe(ac.getConfigValue());
                        long mergedVal = Math.max(existingVal, backupVal);
                        existing.setConfigValue(String.valueOf(mergedVal));
                        appConfigRepo.save(existing);
                    } else {
                        AppConfig newAc = new AppConfig();
                        newAc.setConfigKey(targetKey);
                        newAc.setConfigValue(ac.getConfigValue());
                        appConfigRepo.save(newAc);
                    }
                } else {
                    // Normal configuration key: in clone/clean mode or if not present in DB, save
                    if ("clean_wipe".equalsIgnoreCase(mode) || "clean".equalsIgnoreCase(mode) || !appConfigRepo.existsById(targetKey)) {
                        AppConfig newAc = new AppConfig();
                        newAc.setConfigKey(targetKey);
                        newAc.setConfigValue(ac.getConfigValue());
                        appConfigRepo.save(newAc);
                    }
                }
            }
        }

        // 17. Invoice Migration Records Lifecycle & Clean-up (Decisions A1, B1 & Audit 10B)
        if (invoiceMigrationRecordRepo != null && !oldToNewInvoiceMap.isEmpty()) {
            // Requirement 2: Clean up obsolete/stale migration records for any invoice actually restored or replaced
            // by the current import, even when the backup contains no migration records (e.g., tenant exports).
            // Unrelated invoice migration history in the database is strictly preserved.
            Set<Long> targetRestoredInvoiceIds = oldToNewInvoiceMap.values().stream()
                    .map(Invoice::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            for (Long invId : targetRestoredInvoiceIds) {
                List<InvoiceMigrationRecord> existing = invoiceMigrationRecordRepo.findByInvoiceIdOrderByCreatedAtDesc(invId);
                if (existing != null && !existing.isEmpty()) {
                    invoiceMigrationRecordRepo.deleteAll(existing);
                }
            }
        }

        if (invoiceMigrationRecordRepo != null && backup.getInvoiceMigrationRecords() != null && !backup.getInvoiceMigrationRecords().isEmpty()) {
            List<InvoiceMigrationRecord> rawRecords = backup.getInvoiceMigrationRecords();

            // Requirement 3: Deterministic dependency ordering (topological parent-before-child resolution)
            List<InvoiceMigrationRecord> sortedRecords = new ArrayList<>(rawRecords);
            sortedRecords.sort(Comparator.comparing(InvoiceMigrationRecord::getId, Comparator.nullsLast(Long::compareTo))
                    .thenComparing(InvoiceMigrationRecord::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)));

            Set<Long> backupRecordIds = rawRecords.stream()
                    .map(InvoiceMigrationRecord::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            List<InvoiceMigrationRecord> orderedRecords = new ArrayList<>();
            Set<Long> resolvedOldIds = new HashSet<>();
            Set<InvoiceMigrationRecord> remaining = new LinkedHashSet<>(sortedRecords);

            int maxIterations = remaining.size() + 1;
            while (!remaining.isEmpty() && maxIterations-- > 0) {
                List<InvoiceMigrationRecord> readyInThisPass = new ArrayList<>();
                for (InvoiceMigrationRecord rec : remaining) {
                    Long parentId = rec.getParentMigrationId();
                    if (parentId == null || !backupRecordIds.contains(parentId) || resolvedOldIds.contains(parentId)) {
                        readyInThisPass.add(rec);
                    }
                }
                if (readyInThisPass.isEmpty()) {
                    // Cyclic or broken chain fallback: append remaining in deterministic sorted order
                    orderedRecords.addAll(remaining);
                    break;
                }
                for (InvoiceMigrationRecord r : readyInThisPass) {
                    orderedRecords.add(r);
                    if (r.getId() != null) {
                        resolvedOldIds.add(r.getId());
                    }
                    remaining.remove(r);
                }
            }

            Map<Long, Long> oldToNewMigrationRecordIdMap = new HashMap<>();
            com.fasterxml.jackson.databind.ObjectMapper migMapper = new com.fasterxml.jackson.databind.ObjectMapper()
                    .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

            for (InvoiceMigrationRecord rec : orderedRecords) {
                Long oldInvoiceId = rec.getInvoiceId();
                if (oldInvoiceId == null || !oldToNewInvoiceMap.containsKey(oldInvoiceId)) {
                    // Invoice was not mapped or not imported, skip orphaned migration record
                    continue;
                }

                Long oldSourceFid = rec.getSourceFirmId();
                Long oldTargetFid = rec.getTargetFirmId();

                // Requirement 4: Selective restore safety - both participating firms must be mapped
                if (oldSourceFid == null || !oldToNewFirmIdMap.containsKey(oldSourceFid)
                        || oldTargetFid == null || !oldToNewFirmIdMap.containsKey(oldTargetFid)) {
                    // Fail-safe: Skip records whose cross-firm relationship cannot be faithfully represented
                    continue;
                }

                Long mappedInvoiceId = oldToNewInvoiceMap.get(oldInvoiceId).getId();
                Long mappedSourceFirmId = oldToNewFirmIdMap.get(oldSourceFid);
                Long mappedTargetFirmId = oldToNewFirmIdMap.get(oldTargetFid);

                // Remap preMigrationSnapshot JSON if firm IDs or invoice IDs have changed
                String updatedSnapshot = rec.getPreMigrationSnapshot();
                if (updatedSnapshot != null && !updatedSnapshot.isBlank()) {
                    try {
                        com.billing.simple.billsoft.dtos.migration.InvoiceSnapshotDTO snapshotObj =
                                migMapper.readValue(updatedSnapshot, com.billing.simple.billsoft.dtos.migration.InvoiceSnapshotDTO.class);
                        boolean snapshotChanged = false;
                        if (snapshotObj.getSourceFirmId() != null && oldToNewFirmIdMap.containsKey(snapshotObj.getSourceFirmId())) {
                            snapshotObj.setSourceFirmId(oldToNewFirmIdMap.get(snapshotObj.getSourceFirmId()));
                            snapshotChanged = true;
                        }
                        if (snapshotObj.getInvoiceId() != null && oldToNewInvoiceMap.containsKey(snapshotObj.getInvoiceId())) {
                            snapshotObj.setInvoiceId(oldToNewInvoiceMap.get(snapshotObj.getInvoiceId()).getId());
                            snapshotChanged = true;
                        }
                        if (snapshotChanged) {
                            updatedSnapshot = migMapper.writeValueAsString(snapshotObj);
                        }
                    } catch (Exception e) {
                        org.slf4j.LoggerFactory.getLogger(BackupService.class).warn("Snapshot remapping exception: {}", e.getMessage(), e);
                    }
                }

                Long oldParentId = rec.getParentMigrationId();
                Long mappedParentId = null;
                if (oldParentId != null) {
                    if (oldToNewMigrationRecordIdMap.containsKey(oldParentId)) {
                        mappedParentId = oldToNewMigrationRecordIdMap.get(oldParentId);
                    } else {
                        // Safe missing parent handling: never attach to mismatched or unrelated parent ID
                        mappedParentId = null;
                    }
                }

                InvoiceMigrationRecord newRec = InvoiceMigrationRecord.builder()
                        .invoiceId(mappedInvoiceId)
                        .sourceFirmId(mappedSourceFirmId)
                        .targetFirmId(mappedTargetFirmId)
                        .sourceInvoiceNumber(rec.getSourceInvoiceNumber())
                        .targetInvoiceNumber(rec.getTargetInvoiceNumber())
                        .isRevert(rec.getIsRevert())
                        .parentMigrationId(mappedParentId)
                        .status(rec.getStatus())
                        .preMigrationSnapshot(updatedSnapshot)
                        .migratedBy(rec.getMigratedBy())
                        .createdAt(rec.getCreatedAt())
                        .build();

                InvoiceMigrationRecord savedRec = invoiceMigrationRecordRepo.save(newRec);
                if (rec.getId() != null) {
                    oldToNewMigrationRecordIdMap.put(rec.getId(), savedRec.getId());
                }
            }
        }

        // Post-Restore Integrity Verification
        for (FirmDetails rf : restoredFirms) {
            if (rf.getId() != null) {
                List<String> invNums = invoiceRepo.findInvoiceNumbersByFirmId(rf.getId());
                if (invNums != null) {
                    Set<String> uniqueInvNums = new HashSet<>();
                    for (String num : invNums) {
                        if (num != null && !num.trim().isEmpty() && !uniqueInvNums.add(num.trim().toUpperCase())) {
                            throw new com.billing.simple.billsoft.exception.BackupValidationException(
                                    "POST_RESTORE_INTEGRITY_FAILURE",
                                    "Post-restore integrity check failed: duplicate invoice number '" + num + "' detected in firm " + rf.getId()
                            );
                        }
                    }
                }
            }
        }

        return restoredFirms;
        } finally {
            coordinator.unlock();
        }
    }

    @Transactional
    public void importData(BackupDTO backup, Long targetFirmId, boolean merge) {
        importSelectiveData(backup, null, merge ? "merge" : "clone", targetFirmId);
    }

    @Transactional
    public void factoryReset() {
        boolean locked = coordinator.tryLock();
        if (!locked) {
            throw new IllegalStateException("A backup, recovery, or restore operation is currently in progress. Please wait.");
        }
        try {
            // Stage 1: Forecasting derived & user tables (FK parents to Stage 6 products)
            if (productDemandForecastRepo != null) productDemandForecastRepo.deleteAllInBatch();
            if (productRestockRecommendationRepo != null) productRestockRecommendationRepo.deleteAllInBatch();
            if (forecastModelRunRepo != null) forecastModelRunRepo.deleteAllInBatch();
            if (productSalesMetricsRepo != null) productSalesMetricsRepo.deleteAllInBatch();
            if (forecastSalesTransactionRepo != null) forecastSalesTransactionRepo.deleteAllInBatch();
            if (forecastProductRepo != null) forecastProductRepo.deleteAllInBatch();

            // Stage 2: Invoice migration records, pricing profiles & rules, deduplication, saved items
            if (invoiceMigrationRecordRepo != null) invoiceMigrationRecordRepo.deleteAllInBatch();
            if (categoryPricingRuleRepo != null) categoryPricingRuleRepo.deleteAllInBatch();
            if (pricingProfileRepo != null) pricingProfileRepo.deleteAllInBatch();
            if (itemMergeAuditRepo != null) itemMergeAuditRepo.deleteAllInBatch();
            if (itemDuplicateDismissalRepo != null) itemDuplicateDismissalRepo.deleteAllInBatch();
            if (savedItemRepo != null) savedItemRepo.deleteAllInBatch();

            // Stage 3: Child tables referencing returns & invoices
            salesReturnItemRepo.deleteAllInBatch();
            salesReturnRepo.deleteAllInBatch();
            invoicePaymentRepo.deleteAllInBatch();
            invoiceItemRepo.deleteAllInBatch();
            invoiceRepo.deleteAllInBatch();

            // Stage 4: Stock Movements & PO records & Parties
            stockMovementRepo.deleteAllInBatch();
            partyPaymentRepo.deleteAllInBatch();
            purchaseOrderItemRepo.deleteAllInBatch();
            purchaseOrderRepo.deleteAllInBatch();
            partyRepo.deleteAllInBatch();

            // Stage 5: Business Letters, HR/Employees, Operations
            businessLetterRepo.deleteAllInBatch();
            attendanceRecordRepo.deleteAllInBatch();
            leaveRecordRepo.deleteAllInBatch();
            employeeDocumentRepo.deleteAllInBatch();
            salaryRepo.deleteAllInBatch();
            promotionRepo.deleteAllInBatch();
            advanceRepo.deleteAllInBatch();
            employeeRepo.deleteAllInBatch();

            goalLogRepo.deleteAllInBatch();
            savingRepo.deleteAllInBatch();
            goalRepo.deleteAllInBatch();
            expenseRepo.deleteAllInBatch();
            reminderRepo.deleteAllInBatch();
            notificationRepo.deleteAllInBatch();
            notificationPreferenceRepo.deleteAllInBatch();
            noteRepo.deleteAllInBatch();

            // Stage 6: Core catalogs & firms & configs
            productRepo.deleteAllInBatch();
            customerRepo.deleteAllInBatch();
            firmDetailsRepo.deleteAllInBatch();
            appConfigRepo.deleteAllInBatch();
            if (backupEntityMappingRepo != null) {
                backupEntityMappingRepo.deleteAllInBatch();
            }

            // Post-reset epoch marker to invalidate stale client-side localStorage
            AppConfig epoch = new AppConfig();
            epoch.setConfigKey("FACTORY_RESET_EPOCH");
            epoch.setConfigValue(String.valueOf(System.currentTimeMillis()));
            appConfigRepo.save(epoch);
        } finally {
            coordinator.unlock();
        }
    }

    private boolean isSensitiveConfigKey(String key) {
        if (key == null) return false;
        String upper = key.trim().toUpperCase();
        if (upper.startsWith("EMPLOYEE_MODULE_PIN_")) {
            return false;
        }
        if (upper.equals("GLOBAL_PASSWORD") || upper.equals("BACKUP_CLOUD_CREDENTIALS")
                || upper.equals("BACKUP_CLOUD_SECRET") || upper.equals("BACKUP_CLOUD_ACCESS_KEY")
                || upper.equals("LICENSE_KEY") || upper.equals("FACTORY_RESET_EPOCH")) {
            return true;
        }
        if (key.trim().toLowerCase().startsWith("session_")) return true;
        if (upper.contains("PASSWORD")) return true;
        if (upper.contains("SECRET")) return true;
        if (upper.contains("CREDENTIAL")) return true;
        if (upper.contains("PIN")) return true;
        if (upper.contains("HASH")) return true;
        return upper.contains("TOKEN") && !upper.equals("SNAKE_GAME_STATE");
    }

    private boolean isSequenceCounterKey(String key) {
        if (key == null) return false;
        String upper = key.trim().toUpperCase();
        return upper.startsWith("LAST_") || upper.contains("_SEQ_");
    }

    private Long extractFirmIdFromConfigKey(String key) {
        if (key == null) return null;
        if (key.startsWith("LAST_") && key.contains("_SEQ_")) {
            String[] parts = key.split("_");
            if (parts.length >= 4) {
                try {
                    return Long.parseLong(parts[3]);
                } catch (NumberFormatException ignored) {}
            }
        }
        int lastUnderscore = key.lastIndexOf('_');
        if (lastUnderscore >= 0 && lastUnderscore < key.length() - 1) {
            String suffix = key.substring(lastUnderscore + 1);
            try {
                return Long.parseLong(suffix);
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private long parseLongSafe(String str) {
        if (str == null || str.trim().isEmpty()) return 0L;
        try {
            return Long.parseLong(str.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private void mergeSnakeGameState(String backupJson) {
        if (backupJson == null || backupJson.trim().isEmpty()) return;
        Optional<AppConfig> existingOpt = appConfigRepo.findById("SNAKE_GAME_STATE");
        if (!existingOpt.isPresent() || existingOpt.get().getConfigValue() == null || existingOpt.get().getConfigValue().trim().isEmpty()) {
            AppConfig sc = new AppConfig();
            sc.setConfigKey("SNAKE_GAME_STATE");
            sc.setConfigValue(backupJson);
            appConfigRepo.save(sc);
            return;
        }

        try {
            long backupRev = extractSnakeRevision(backupJson);
            long existingRev = extractSnakeRevision(existingOpt.get().getConfigValue());
            if (backupRev > existingRev) {
                AppConfig sc = existingOpt.get();
                sc.setConfigValue(backupJson);
                appConfigRepo.save(sc);
            }
        } catch (Exception ignored) {}
    }

    private long extractSnakeRevision(String json) {
        if (json == null || json.trim().isEmpty()) return 0L;
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(json);
            if (root.has("stateRevision")) {
                return root.get("stateRevision").asLong(0L);
            }
            if (root.has("updatedAt")) {
                return root.get("updatedAt").asLong(0L);
            }
        } catch (Exception ignored) {}
        return 0L;
    }

    private static class PendingProductFk {
        final Long newProductId;
        final Long oldPreferredPartyId;
        final Long oldCanonicalProductId;
        final Long oldFirmId;
        final Long mappedFirmId;

        PendingProductFk(Long newProductId, Long oldPreferredPartyId, Long oldCanonicalProductId, Long oldFirmId, Long mappedFirmId) {
            this.newProductId = newProductId;
            this.oldPreferredPartyId = oldPreferredPartyId;
            this.oldCanonicalProductId = oldCanonicalProductId;
            this.oldFirmId = oldFirmId;
            this.mappedFirmId = mappedFirmId;
        }
    }

    private Long getMappedTargetEntityId(String backupSourceId, Long sourceEntityId, String entityType, Long targetFirmId) {
        if (backupEntityMappingRepo == null || backupSourceId == null || sourceEntityId == null || entityType == null || targetFirmId == null) {
            return null;
        }
        return backupEntityMappingRepo.findMappedTargetEntityId(backupSourceId, sourceEntityId, entityType, targetFirmId).orElse(null);
    }

    private void recordEntityMapping(String backupSourceId, Long sourceEntityId, String entityType, Long targetFirmId, Long targetEntityId) {
        if (backupEntityMappingRepo == null || backupSourceId == null || sourceEntityId == null || entityType == null || targetFirmId == null || targetEntityId == null) {
            return;
        }
        Optional<BackupEntityMapping> existing = backupEntityMappingRepo.findByBackupSourceIdAndSourceEntityIdAndEntityTypeAndTargetFirmId(
                backupSourceId, sourceEntityId, entityType, targetFirmId);
        if (existing.isPresent()) {
            BackupEntityMapping m = existing.get();
            m.setTargetEntityId(targetEntityId);
            backupEntityMappingRepo.save(m);
        } else {
            BackupEntityMapping m = BackupEntityMapping.builder()
                    .backupSourceId(backupSourceId)
                    .sourceEntityId(sourceEntityId)
                    .entityType(entityType)
                    .targetFirmId(targetFirmId)
                    .targetEntityId(targetEntityId)
                    .build();
            backupEntityMappingRepo.save(m);
        }
    }

    private boolean isTargetEntityAvailable(Map<String, Set<Long>> claimedMap, String entityType, Long targetId) {
        if (targetId == null || claimedMap == null) return true;
        Set<Long> set = claimedMap.get(entityType);
        return set == null || !set.contains(targetId);
    }

    private void markTargetEntityClaimed(Map<String, Set<Long>> claimedMap, String entityType, Long targetId) {
        if (targetId != null && claimedMap != null) {
            claimedMap.computeIfAbsent(entityType, k -> new HashSet<>()).add(targetId);
        }
    }
}
