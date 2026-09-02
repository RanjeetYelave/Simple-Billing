package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dto.BackupDTO;
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
                         AppConfigRepository appConfigRepo) {
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

        backup.setBusinessLetters(businessLetterRepo.findByFirmIdOrderByLetterDateDescIdDesc(firmId));
        backup.setInboxMessages(inboxMessageRepo.findByFirmIdOrderByCreatedAtDesc(firmId));

        return backup;
    }

    @Transactional
    public void importData(BackupDTO backup, Long targetFirmId, boolean merge) {
        if (backup == null || backup.getMetadata() == null) {
            throw new RuntimeException("Invalid backup file: Missing metadata");
        }

        if (!merge) {
            // Mode A: Restore as New Firm (Clone)
            FirmDetails newFirm = new FirmDetails();
            if (backup.getFirmDetails() != null) {
                newFirm.setFirmName(backup.getFirmDetails().getFirmName() + " (Restored)");
                newFirm.setOwnerName(backup.getFirmDetails().getOwnerName());
                newFirm.setAddressLine1(backup.getFirmDetails().getAddressLine1());
                newFirm.setAddressLine2(backup.getFirmDetails().getAddressLine2());
                newFirm.setCity(backup.getFirmDetails().getCity());
                newFirm.setState(backup.getFirmDetails().getState());
                newFirm.setPincode(backup.getFirmDetails().getPincode());
                newFirm.setPhone(backup.getFirmDetails().getPhone());
                newFirm.setEmail(backup.getFirmDetails().getEmail());
                newFirm.setGstin(backup.getFirmDetails().getGstin());
                newFirm.setLogoBase64(backup.getFirmDetails().getLogoBase64());
                newFirm.setBankName(backup.getFirmDetails().getBankName());
                newFirm.setBankAccount(backup.getFirmDetails().getBankAccount());
                newFirm.setBankIfsc(backup.getFirmDetails().getBankIfsc());
                newFirm.setFooterNote(backup.getFirmDetails().getFooterNote());
            } else {
                newFirm.setFirmName("Restored Firm");
            }
            newFirm = firmDetailsRepo.save(newFirm);
            targetFirmId = newFirm.getId();
        }

        // 1. Import Customers
        Map<Long, Customer> oldToNewCustomerMap = new HashMap<>();
        if (backup.getCustomers() != null) {
            for (Customer c : backup.getCustomers()) {
                Customer newC = new Customer();
                newC.setFirmId(targetFirmId);
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

        // 2. Import Products (with full stock, pricing, and catalog attributes)
        Map<Long, Product> oldToNewProductMap = new HashMap<>();
        if (backup.getProducts() != null) {
            for (Product p : backup.getProducts()) {
                Product newP = Product.builder()
                        .firmId(targetFirmId)
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

        // 3. Import Stock Movements
        if (backup.getStockMovements() != null) {
            for (StockMovement sm : backup.getStockMovements()) {
                Long newProdId = sm.getProductId();
                if (sm.getProductId() != null && oldToNewProductMap.containsKey(sm.getProductId())) {
                    newProdId = oldToNewProductMap.get(sm.getProductId()).getId();
                }
                StockMovement newSm = StockMovement.builder()
                        .productId(newProdId != null ? newProdId : 0L)
                        .productName(sm.getProductName())
                        .firmId(targetFirmId)
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

        // 4. Import Invoices & Items
        if (backup.getInvoices() != null) {
            for (Invoice inv : backup.getInvoices()) {
                Invoice newInv = new Invoice();
                newInv.setFirmId(targetFirmId);
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

                // Map Customer
                if (inv.getCustomer() != null && oldToNewCustomerMap.containsKey(inv.getCustomer().getId())) {
                    newInv.setCustomer(oldToNewCustomerMap.get(inv.getCustomer().getId()));
                }

                // Add Items
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

                        // Map Product
                        if (item.getProduct() != null && oldToNewProductMap.containsKey(item.getProduct().getId())) {
                            newItem.setProduct(oldToNewProductMap.get(item.getProduct().getId()));
                        }

                        newInv.getItems().add(newItem);
                    }
                }

                invoiceRepo.save(newInv);
            }
        }

        // 5. Import Parties (Vendors)
        Map<Long, Party> oldToNewPartyMap = new HashMap<>();
        if (backup.getParties() != null) {
            for (Party party : backup.getParties()) {
                Party newP = Party.builder()
                        .firmId(targetFirmId)
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

        // 6. Import Purchase Orders
        Map<Long, PurchaseOrder> oldToNewPoMap = new HashMap<>();
        if (backup.getPurchaseOrders() != null) {
            for (PurchaseOrder po : backup.getPurchaseOrders()) {
                PurchaseOrder newPo = PurchaseOrder.builder()
                        .firmId(targetFirmId)
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

        // 7. Import Party Payments
        if (backup.getPartyPayments() != null) {
            for (PartyPayment pp : backup.getPartyPayments()) {
                Long newPartyId = pp.getPartyId();
                if (pp.getPartyId() != null && oldToNewPartyMap.containsKey(pp.getPartyId())) {
                    newPartyId = oldToNewPartyMap.get(pp.getPartyId()).getId();
                }
                Long newPoId = pp.getPurchaseOrderId();
                if (pp.getPurchaseOrderId() != null && oldToNewPoMap.containsKey(pp.getPurchaseOrderId())) {
                    newPoId = oldToNewPoMap.get(pp.getPurchaseOrderId()).getId();
                }

                PartyPayment newPp = PartyPayment.builder()
                        .firmId(targetFirmId)
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

        // 8. Import Reminders & Tasks
        if (backup.getReminders() != null) {
            for (Reminder rem : backup.getReminders()) {
                Long newCustId = rem.getCustomerId();
                if (rem.getCustomerId() != null && oldToNewCustomerMap.containsKey(rem.getCustomerId())) {
                    newCustId = oldToNewCustomerMap.get(rem.getCustomerId()).getId();
                }
                Reminder newRem = Reminder.builder()
                        .firmId(targetFirmId)
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
                reminderRepo.save(newRem);
            }
        }

        // 9. Import Notes
        if (backup.getNotes() != null) {
            for (Note note : backup.getNotes()) {
                Long newCustId = note.getCustomerId();
                if (note.getCustomerId() != null && oldToNewCustomerMap.containsKey(note.getCustomerId())) {
                    newCustId = oldToNewCustomerMap.get(note.getCustomerId()).getId();
                }
                Note newNote = Note.builder()
                        .firmId(targetFirmId)
                        .customerId(newCustId)
                        .title(note.getTitle())
                        .content(note.getContent())
                        .build();
                noteRepo.save(newNote);
            }
        }

        // 10. Import Expenses
        if (backup.getExpenses() != null) {
            for (Expense exp : backup.getExpenses()) {
                Expense newExp = Expense.builder()
                        .firmId(targetFirmId)
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

        // 11. Import Employees
        Map<Long, Employee> oldToNewEmpMap = new HashMap<>();
        if (backup.getEmployees() != null) {
            for (Employee emp : backup.getEmployees()) {
                Employee newEmp = new Employee();
                newEmp.setFirmId(targetFirmId);
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

        // 12. Import Employee Sub-records
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

        // 13. Import Business Letters
        if (backup.getBusinessLetters() != null) {
            for (BusinessLetter bl : backup.getBusinessLetters()) {
                Long newPartyId = bl.getPartyId();
                if (bl.getPartyId() != null && oldToNewPartyMap.containsKey(bl.getPartyId())) {
                    newPartyId = oldToNewPartyMap.get(bl.getPartyId()).getId();
                }
                Long newCustId = bl.getCustomerId();
                if (bl.getCustomerId() != null && oldToNewCustomerMap.containsKey(bl.getCustomerId())) {
                    newCustId = oldToNewCustomerMap.get(bl.getCustomerId()).getId();
                }

                BusinessLetter newBl = BusinessLetter.builder()
                        .firmId(targetFirmId)
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

        // 14. Import Inbox Messages
        if (backup.getInboxMessages() != null) {
            for (InboxMessage msg : backup.getInboxMessages()) {
                InboxMessage newMsg = InboxMessage.builder()
                        .firmId(targetFirmId)
                        .subject(msg.getSubject())
                        .body(msg.getBody())
                        .sender(msg.getSender())
                        .isRead(msg.isRead())
                        .reminderId(msg.getReminderId())
                        .build();
                inboxMessageRepo.save(newMsg);
            }
        }
    }

    @Transactional
    public void factoryReset() {
        // Child tables referencing invoices
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
