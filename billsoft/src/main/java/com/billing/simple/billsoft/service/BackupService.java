package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.dto.BackupInspectionDTO;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.repositories.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

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
    private final InboxMessageRepository inboxMessageRepo;
    private final AppConfigRepository appConfigRepo;
    private final InvoicePaymentRepository invoicePaymentRepo;

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
                         InboxMessageRepository inboxMessageRepo,
                         AppConfigRepository appConfigRepo,
                         InvoicePaymentRepository invoicePaymentRepo) {
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
        this.inboxMessageRepo = inboxMessageRepo;
        this.appConfigRepo = appConfigRepo;
        this.invoicePaymentRepo = invoicePaymentRepo;
    }

    public BackupDTO exportData(Long firmId) {
        BackupDTO backup = new BackupDTO();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("version", "2.0");
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
        backup.setInvoices(invoiceRepo.findAllByFirmId(firmId));
        backup.setInvoicePayments(invoicePaymentRepo.findByFirmIdOrderByPaymentDateDescIdDesc(firmId));
        backup.setParties(partyRepo.findByFirmIdOrderByNameAsc(firmId));
        backup.setPartyPayments(partyPaymentRepo.findByFirmIdOrderByPaymentDateDescIdDesc(firmId));
        backup.setPurchaseOrders(purchaseOrderRepo.findByFirmIdOrderByPoDateDescIdDesc(firmId));
        backup.setReminders(reminderRepo.findByFirmId(firmId));
        backup.setNotes(noteRepo.findByFirmId(firmId));
        backup.setExpenses(expenseRepo.findByFirmIdOrderByExpenseDateDescIdDesc(firmId));

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
        backup.setInboxMessages(inboxMessageRepo.findByFirmIdOrderByCreatedAtDesc(firmId));
        backup.setAppConfigs(appConfigRepo.findAll());

        return backup;
    }

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
        backup.setInvoices(invoiceRepo.findAll());
        backup.setInvoicePayments(invoicePaymentRepo.findAll());
        backup.setParties(partyRepo.findAll());
        backup.setPartyPayments(partyPaymentRepo.findAll());
        backup.setPurchaseOrders(purchaseOrderRepo.findAll());
        backup.setReminders(reminderRepo.findAll());
        backup.setNotes(noteRepo.findAll());
        backup.setExpenses(expenseRepo.findAll());

        List<Employee> employees = employeeRepo.findAll();
        backup.setEmployees(employees);
        backup.setAttendanceRecords(attendanceRecordRepo.findAll());
        backup.setLeaveRecords(leaveRecordRepo.findAll());
        backup.setSalaryRecords(salaryRepo.findAll());
        backup.setAdvances(advanceRepo.findAll());
        backup.setPromotions(promotionRepo.findAll());
        backup.setEmployeeDocuments(employeeDocumentRepo.findAll());

        backup.setBusinessLetters(businessLetterRepo.findAll());
        backup.setInboxMessages(inboxMessageRepo.findAll());
        backup.setAppConfigs(appConfigRepo.findAll());

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
        totalStats.put("totalLetters", backup.getBusinessLetters() != null ? backup.getBusinessLetters().size() : 0);
        dto.setTotalStats(totalStats);

        return dto;
    }

    @Transactional
    public List<FirmDetails> importSelectiveData(BackupDTO backup, Set<Long> selectedFirmIds, String mode, Long targetFirmId) {
        if (backup == null || backup.getMetadata() == null) {
            throw new RuntimeException("Invalid backup file: Missing metadata");
        }

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
            for (FirmDetails f : backupFirms) {
                if (f.getId() != null && selectedFirmIds.contains(f.getId())) {
                    targetFirmsToProcess.add(f);
                }
            }
        }
        if (targetFirmsToProcess.isEmpty()) {
            targetFirmsToProcess.addAll(backupFirms);
        }

        // Clean wipe mode: Factory reset first
        if ("clean_wipe".equalsIgnoreCase(mode) || "clean".equalsIgnoreCase(mode)) {
            factoryReset();
        }

        Map<Long, Long> oldToNewFirmIdMap = new HashMap<>();
        List<FirmDetails> restoredFirms = new ArrayList<>();

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

            if (firmToUse == null && !"merge".equalsIgnoreCase(mode)) {
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

            Long mappedId = (firmToUse != null && firmToUse.getId() != null) ? firmToUse.getId() : (targetFirmId != null ? targetFirmId : (oldFirmId != -1L ? oldFirmId : 1L));
            oldToNewFirmIdMap.put(oldFirmId, mappedId);
            if (firmToUse != null) {
                restoredFirms.add(firmToUse);
            }
        }

        Long defaultTargetFirmId = restoredFirms.isEmpty() ? (targetFirmId != null ? targetFirmId : 1L) : (restoredFirms.get(0).getId() != null ? restoredFirms.get(0).getId() : (targetFirmId != null ? targetFirmId : 1L));

        // 1. Customers
        Map<Long, Customer> oldToNewCustomerMap = new HashMap<>();
        if (backup.getCustomers() != null) {
            for (Customer c : backup.getCustomers()) {
                Long oldFid = c.getFirmId() != null ? c.getFirmId() : -1L;
                boolean shouldImport = !isFullSystem || oldToNewFirmIdMap.containsKey(oldFid);
                if (shouldImport) {
                    Long mappedFirmId = !isFullSystem ? defaultTargetFirmId : oldToNewFirmIdMap.getOrDefault(oldFid, defaultTargetFirmId);
                    Customer newC = new Customer();
                    newC.setFirmId(mappedFirmId);
                    newC.setName(c.getName());
                    newC.setPhone(c.getPhone());
                    newC.setAddress(c.getAddress());
                    newC.setEmail(c.getEmail());
                    newC.setGstin(c.getGstin());
                    newC = customerRepo.save(newC);
                    if (c.getId() != null) {
                        oldToNewCustomerMap.put(c.getId(), newC);
                    }
                }
            }
        }

        // 2. Products
        Map<Long, Product> oldToNewProductMap = new HashMap<>();
        if (backup.getProducts() != null) {
            for (Product p : backup.getProducts()) {
                Long oldFid = p.getFirmId() != null ? p.getFirmId() : -1L;
                boolean shouldImport = !isFullSystem || oldToNewFirmIdMap.containsKey(oldFid);
                if (shouldImport) {
                    Long mappedFirmId = !isFullSystem ? defaultTargetFirmId : oldToNewFirmIdMap.getOrDefault(oldFid, defaultTargetFirmId);
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
                            .build();
                    newP = productRepo.save(newP);
                    if (p.getId() != null) {
                        oldToNewProductMap.put(p.getId(), newP);
                    }
                }
            }
        }

        // 3. Stock Movements
        if (backup.getStockMovements() != null) {
            for (StockMovement sm : backup.getStockMovements()) {
                Long oldFid = sm.getFirmId() != null ? sm.getFirmId() : -1L;
                boolean shouldImport = !isFullSystem
                        || oldToNewFirmIdMap.containsKey(oldFid)
                        || (sm.getProductId() != null && oldToNewProductMap.containsKey(sm.getProductId()));
                if (shouldImport) {
                    Long mappedFirmId = !isFullSystem ? defaultTargetFirmId : oldToNewFirmIdMap.getOrDefault(oldFid, defaultTargetFirmId);
                    Long newProdId = sm.getProductId();
                    if (sm.getProductId() != null && oldToNewProductMap.containsKey(sm.getProductId())) {
                        newProdId = oldToNewProductMap.get(sm.getProductId()).getId();
                    }
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
                    stockMovementRepo.save(newSm);
                }
            }
        }

        // 4. Invoices & Items
        Map<Long, Invoice> oldToNewInvoiceMap = new HashMap<>();
        if (backup.getInvoices() != null) {
            for (Invoice inv : backup.getInvoices()) {
                Long oldFid = inv.getFirmId() != null ? inv.getFirmId() : -1L;
                boolean shouldImport = !isFullSystem || oldToNewFirmIdMap.containsKey(oldFid);
                if (shouldImport) {
                    Long mappedFirmId = !isFullSystem ? defaultTargetFirmId : oldToNewFirmIdMap.getOrDefault(oldFid, defaultTargetFirmId);
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
                            newItem.setLineTotal(item.getLineTotal());

                            if (item.getProduct() != null && oldToNewProductMap.containsKey(item.getProduct().getId())) {
                                newItem.setProduct(oldToNewProductMap.get(item.getProduct().getId()));
                            }
                            newInv.getItems().add(newItem);
                        }
                    }

                    Invoice savedInv = invoiceRepo.save(newInv);
                    if (inv.getId() != null) {
                        oldToNewInvoiceMap.put(inv.getId(), savedInv);
                    }
                }
            }
        }

        // 4.1 Invoice Payments
        if (backup.getInvoicePayments() != null) {
            for (InvoicePayment ip : backup.getInvoicePayments()) {
                Long oldFid = ip.getFirmId() != null ? ip.getFirmId() : -1L;
                boolean shouldImport = !isFullSystem
                        || oldToNewFirmIdMap.containsKey(oldFid)
                        || (ip.getInvoiceId() != null && oldToNewInvoiceMap.containsKey(ip.getInvoiceId()));
                if (shouldImport) {
                    Long mappedFirmId = !isFullSystem ? defaultTargetFirmId : oldToNewFirmIdMap.getOrDefault(oldFid, defaultTargetFirmId);
                    Long newInvoiceId = ip.getInvoiceId();
                    if (ip.getInvoiceId() != null && oldToNewInvoiceMap.containsKey(ip.getInvoiceId())) {
                        newInvoiceId = oldToNewInvoiceMap.get(ip.getInvoiceId()).getId();
                    }
                    Long newCustId = ip.getCustomerId();
                    if (ip.getCustomerId() != null && oldToNewCustomerMap.containsKey(ip.getCustomerId())) {
                        newCustId = oldToNewCustomerMap.get(ip.getCustomerId()).getId();
                    }
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
                    invoicePaymentRepo.save(newIp);
                }
            }
        }

        // 5. Parties (Vendors)
        Map<Long, Party> oldToNewPartyMap = new HashMap<>();
        if (backup.getParties() != null) {
            for (Party party : backup.getParties()) {
                Long oldFid = party.getFirmId() != null ? party.getFirmId() : -1L;
                boolean shouldImport = !isFullSystem || oldToNewFirmIdMap.containsKey(oldFid);
                if (shouldImport) {
                    Long mappedFirmId = !isFullSystem ? defaultTargetFirmId : oldToNewFirmIdMap.getOrDefault(oldFid, defaultTargetFirmId);
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
                    newP = partyRepo.save(newP);
                    if (party.getId() != null) {
                        oldToNewPartyMap.put(party.getId(), newP);
                    }
                }
            }
        }

        // 6. Purchase Orders
        Map<Long, PurchaseOrder> oldToNewPoMap = new HashMap<>();
        if (backup.getPurchaseOrders() != null) {
            for (PurchaseOrder po : backup.getPurchaseOrders()) {
                Long oldFid = po.getFirmId() != null ? po.getFirmId() : -1L;
                boolean shouldImport = !isFullSystem || oldToNewFirmIdMap.containsKey(oldFid);
                if (shouldImport) {
                    Long mappedFirmId = !isFullSystem ? defaultTargetFirmId : oldToNewFirmIdMap.getOrDefault(oldFid, defaultTargetFirmId);
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

                    newPo = purchaseOrderRepo.save(newPo);
                    if (po.getId() != null) {
                        oldToNewPoMap.put(po.getId(), newPo);
                    }
                }
            }
        }

        // 7. Party Payments
        if (backup.getPartyPayments() != null) {
            for (PartyPayment pp : backup.getPartyPayments()) {
                Long oldFid = pp.getFirmId() != null ? pp.getFirmId() : -1L;
                boolean shouldImport = !isFullSystem || oldToNewFirmIdMap.containsKey(oldFid);
                if (shouldImport) {
                    Long mappedFirmId = !isFullSystem ? defaultTargetFirmId : oldToNewFirmIdMap.getOrDefault(oldFid, defaultTargetFirmId);
                    Long newPartyId = pp.getPartyId();
                    if (pp.getPartyId() != null && oldToNewPartyMap.containsKey(pp.getPartyId())) {
                        newPartyId = oldToNewPartyMap.get(pp.getPartyId()).getId();
                    }
                    Long newPoId = pp.getPurchaseOrderId();
                    if (pp.getPurchaseOrderId() != null && oldToNewPoMap.containsKey(pp.getPurchaseOrderId())) {
                        newPoId = oldToNewPoMap.get(pp.getPurchaseOrderId()).getId();
                    }

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
                    partyPaymentRepo.save(newPp);
                }
            }
        }

        // 8. Reminders & Tasks
        Map<Long, Reminder> oldToNewReminderMap = new HashMap<>();
        if (backup.getReminders() != null) {
            for (Reminder rem : backup.getReminders()) {
                Long oldFid = rem.getFirmId() != null ? rem.getFirmId() : -1L;
                boolean shouldImport = !isFullSystem || oldToNewFirmIdMap.containsKey(oldFid);
                if (shouldImport) {
                    Long mappedFirmId = !isFullSystem ? defaultTargetFirmId : oldToNewFirmIdMap.getOrDefault(oldFid, defaultTargetFirmId);
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
                            .build();
                    newRem = reminderRepo.save(newRem);
                    if (rem.getId() != null) {
                        oldToNewReminderMap.put(rem.getId(), newRem);
                    }
                }
            }
        }

        // 9. Notes
        if (backup.getNotes() != null) {
            for (Note note : backup.getNotes()) {
                Long oldFid = note.getFirmId() != null ? note.getFirmId() : -1L;
                boolean shouldImport = !isFullSystem || oldToNewFirmIdMap.containsKey(oldFid);
                if (shouldImport) {
                    Long mappedFirmId = !isFullSystem ? defaultTargetFirmId : oldToNewFirmIdMap.getOrDefault(oldFid, defaultTargetFirmId);
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
                    noteRepo.save(newNote);
                }
            }
        }

        // 10. Expenses
        if (backup.getExpenses() != null) {
            for (Expense exp : backup.getExpenses()) {
                Long oldFid = exp.getFirmId() != null ? exp.getFirmId() : -1L;
                boolean shouldImport = !isFullSystem || oldToNewFirmIdMap.containsKey(oldFid);
                if (shouldImport) {
                    Long mappedFirmId = !isFullSystem ? defaultTargetFirmId : oldToNewFirmIdMap.getOrDefault(oldFid, defaultTargetFirmId);
                    Expense newExp = Expense.builder()
                            .firmId(mappedFirmId)
                            .title(exp.getTitle())
                            .expenseDate(exp.getExpenseDate())
                            .category(exp.getCategory())
                            .amount(exp.getAmount())
                            .paymentMode(exp.getPaymentMode())
                            .notes(exp.getNotes())
                            .build();
                    expenseRepo.save(newExp);
                }
            }
        }

        // 11. Employees
        Map<Long, Employee> oldToNewEmpMap = new HashMap<>();
        if (backup.getEmployees() != null) {
            for (Employee emp : backup.getEmployees()) {
                Long oldFid = emp.getFirmId() != null ? emp.getFirmId() : -1L;
                boolean shouldImport = !isFullSystem || oldToNewFirmIdMap.containsKey(oldFid);
                if (shouldImport) {
                    Long mappedFirmId = !isFullSystem ? defaultTargetFirmId : oldToNewFirmIdMap.getOrDefault(oldFid, defaultTargetFirmId);
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

                    newEmp = employeeRepo.save(newEmp);
                    if (emp.getId() != null) {
                        oldToNewEmpMap.put(emp.getId(), newEmp);
                    }
                }
            }
        }

        // 12. Employee Sub-records
        if (backup.getAttendanceRecords() != null) {
            for (AttendanceRecord att : backup.getAttendanceRecords()) {
                if (att.getEmployee() != null && oldToNewEmpMap.containsKey(att.getEmployee().getId())) {
                    AttendanceRecord newAtt = new AttendanceRecord();
                    newAtt.setEmployee(oldToNewEmpMap.get(att.getEmployee().getId()));
                    newAtt.setDate(att.getDate());
                    newAtt.setStatus(att.getStatus());
                    newAtt.setRemarks(att.getRemarks());
                    newAtt.setLeaveType(att.getLeaveType());
                    newAtt.setApprovedBy(att.getApprovedBy());
                    attendanceRecordRepo.save(newAtt);
                }
            }
        }

        if (backup.getLeaveRecords() != null) {
            for (LeaveRecord lr : backup.getLeaveRecords()) {
                if (lr.getEmployee() != null && oldToNewEmpMap.containsKey(lr.getEmployee().getId())) {
                    LeaveRecord newLr = new LeaveRecord();
                    newLr.setEmployee(oldToNewEmpMap.get(lr.getEmployee().getId()));
                    newLr.setStartDate(lr.getStartDate());
                    newLr.setEndDate(lr.getEndDate());
                    newLr.setType(lr.getType());
                    newLr.setStatus(lr.getStatus());
                    newLr.setTotalDays(lr.getTotalDays());
                    newLr.setReason(lr.getReason());
                    newLr.setApprovedBy(lr.getApprovedBy());
                    leaveRecordRepo.save(newLr);
                }
            }
        }

        if (backup.getSalaryRecords() != null) {
            for (SalaryRecord sr : backup.getSalaryRecords()) {
                if (sr.getEmployee() != null && oldToNewEmpMap.containsKey(sr.getEmployee().getId())) {
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
                    salaryRepo.save(newSr);
                }
            }
        }

        if (backup.getAdvances() != null) {
            for (EmployeeAdvance adv : backup.getAdvances()) {
                if (adv.getEmployee() != null && oldToNewEmpMap.containsKey(adv.getEmployee().getId())) {
                    EmployeeAdvance newAdv = new EmployeeAdvance();
                    newAdv.setEmployee(oldToNewEmpMap.get(adv.getEmployee().getId()));
                    newAdv.setDate(adv.getDate());
                    newAdv.setAmount(adv.getAmount());
                    newAdv.setDescription(adv.getDescription());
                    advanceRepo.save(newAdv);
                }
            }
        }

        if (backup.getPromotions() != null) {
            for (PromotionRecord pr : backup.getPromotions()) {
                if (pr.getEmployee() != null && oldToNewEmpMap.containsKey(pr.getEmployee().getId())) {
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
                    promotionRepo.save(newPr);
                }
            }
        }

        if (backup.getEmployeeDocuments() != null) {
            for (EmployeeDocument doc : backup.getEmployeeDocuments()) {
                Long empId = doc.getEmployee() != null ? doc.getEmployee().getId() : doc.getEmployeeId();
                if (empId != null && oldToNewEmpMap.containsKey(empId)) {
                    EmployeeDocument newDoc = new EmployeeDocument();
                    newDoc.setEmployee(oldToNewEmpMap.get(empId));
                    newDoc.setType(doc.getType());
                    newDoc.setFileName(doc.getFileName());
                    newDoc.setDataBase64(doc.getDataBase64());
                    newDoc.setUploadedAt(doc.getUploadedAt());
                    employeeDocumentRepo.save(newDoc);
                }
            }
        }

        // 13. Business Letters
        if (backup.getBusinessLetters() != null) {
            for (BusinessLetter bl : backup.getBusinessLetters()) {
                Long oldFid = bl.getFirmId() != null ? bl.getFirmId() : -1L;
                boolean shouldImport = !isFullSystem || oldToNewFirmIdMap.containsKey(oldFid);
                if (shouldImport) {
                    Long mappedFirmId = !isFullSystem ? defaultTargetFirmId : oldToNewFirmIdMap.getOrDefault(oldFid, defaultTargetFirmId);
                    Long newPartyId = bl.getPartyId();
                    if (bl.getPartyId() != null && oldToNewPartyMap.containsKey(bl.getPartyId())) {
                        newPartyId = oldToNewPartyMap.get(bl.getPartyId()).getId();
                    }
                    Long newCustId = bl.getCustomerId();
                    if (bl.getCustomerId() != null && oldToNewCustomerMap.containsKey(bl.getCustomerId())) {
                        newCustId = oldToNewCustomerMap.get(bl.getCustomerId()).getId();
                    }

                    BusinessLetter newBl = BusinessLetter.builder()
                            .firmId(mappedFirmId)
                            .letterNumber(bl.getLetterNumber())
                            .letterDate(bl.getLetterDate())
                            .senderType(bl.getSenderType())
                            .senderName(bl.getSenderName())
                            .senderCompany(bl.getSenderCompany())
                            .senderAddress(bl.getSenderAddress())
                            .senderPhone(bl.getSenderPhone())
                            .senderEmail(bl.getSenderEmail())
                            .senderGstin(bl.getSenderGstin())
                            .recipientType(bl.getRecipientType())
                            .partyId(newPartyId)
                            .customerId(newCustId)
                            .recipientName(bl.getRecipientName())
                            .recipientDesignation(bl.getRecipientDesignation())
                            .recipientCompany(bl.getRecipientCompany())
                            .recipientAddress(bl.getRecipientAddress())
                            .recipientPhone(bl.getRecipientPhone())
                            .recipientEmail(bl.getRecipientEmail())
                            .subject(bl.getSubject())
                            .category(bl.getCategory())
                            .content(bl.getContent())
                            .signatoryName(bl.getSignatoryName())
                            .signatoryDesignation(bl.getSignatoryDesignation())
                            .status(bl.getStatus())
                            .includeHeader(bl.getIncludeHeader())
                            .includeFooter(bl.getIncludeFooter())
                            .build();
                    businessLetterRepo.save(newBl);
                }
            }
        }

        // 14. Inbox Messages
        if (backup.getInboxMessages() != null) {
            for (InboxMessage msg : backup.getInboxMessages()) {
                Long oldFid = msg.getFirmId() != null ? msg.getFirmId() : -1L;
                boolean shouldImport = !isFullSystem || oldToNewFirmIdMap.containsKey(oldFid);
                if (shouldImport) {
                    Long mappedFirmId = !isFullSystem ? defaultTargetFirmId : oldToNewFirmIdMap.getOrDefault(oldFid, defaultTargetFirmId);
                    Long newRemId = msg.getReminderId();
                    if (msg.getReminderId() != null && oldToNewReminderMap.containsKey(msg.getReminderId())) {
                        newRemId = oldToNewReminderMap.get(msg.getReminderId()).getId();
                    }
                    InboxMessage newMsg = InboxMessage.builder()
                            .firmId(mappedFirmId)
                            .subject(msg.getSubject())
                            .body(msg.getBody())
                            .sender(msg.getSender())
                            .isRead(msg.isRead())
                            .reminderId(newRemId)
                            .build();
                    inboxMessageRepo.save(newMsg);
                }
            }
        }

        // 15. App Configs
        if (backup.getAppConfigs() != null) {
            for (AppConfig ac : backup.getAppConfigs()) {
                if (ac.getConfigKey() != null) {
                    if ("clean_wipe".equalsIgnoreCase(mode) || "clean".equalsIgnoreCase(mode) || !appConfigRepo.existsById(ac.getConfigKey())) {
                        appConfigRepo.save(ac);
                    }
                }
            }
        }

        return restoredFirms;
    }

    @Transactional
    public void importData(BackupDTO backup, Long targetFirmId, boolean merge) {
        importSelectiveData(backup, null, merge ? "merge" : "clone", targetFirmId);
    }

    @Transactional
    public void factoryReset() {
        // Child tables referencing invoices
        invoicePaymentRepo.deleteAllInBatch();
        invoiceItemRepo.deleteAllInBatch();
        invoiceRepo.deleteAllInBatch();

        // Stock Movements
        stockMovementRepo.deleteAllInBatch();

        // Vendor & PO Records
        partyPaymentRepo.deleteAllInBatch();
        purchaseOrderItemRepo.deleteAllInBatch();
        purchaseOrderRepo.deleteAllInBatch();
        partyRepo.deleteAllInBatch();

        // Business Letters
        businessLetterRepo.deleteAllInBatch();

        // Child tables referencing employees
        attendanceRecordRepo.deleteAllInBatch();
        leaveRecordRepo.deleteAllInBatch();
        employeeDocumentRepo.deleteAllInBatch();
        salaryRepo.deleteAllInBatch();
        promotionRepo.deleteAllInBatch();
        advanceRepo.deleteAllInBatch();
        employeeRepo.deleteAllInBatch();

        // Operational business records
        expenseRepo.deleteAllInBatch();
        reminderRepo.deleteAllInBatch();
        inboxMessageRepo.deleteAllInBatch();
        noteRepo.deleteAllInBatch();

        // Core business catalogs & firms
        productRepo.deleteAllInBatch();
        customerRepo.deleteAllInBatch();
        firmDetailsRepo.deleteAllInBatch();
        appConfigRepo.deleteAllInBatch();
    }
}
