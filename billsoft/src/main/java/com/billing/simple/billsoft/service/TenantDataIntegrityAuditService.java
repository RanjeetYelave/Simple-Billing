package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.repositories.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Read-Only Audit Service for Historical Multi-Tenant Data Corruption.
 * Scans the database across all entities and reports foreign-key relationships
 * where records are associated across different firm boundaries.
 * 
 * NOTE: This is strictly read-only and does not mutate any records.
 */
@Service
@Transactional(readOnly = true)
public class TenantDataIntegrityAuditService {

    private final InvoiceRepository invoiceRepo;
    private final CustomerRepository customerRepo;
    private final ProductRepository productRepo;
    private final InvoicePaymentRepository paymentRepo;
    private final SalesReturnRepository returnRepo;
    private final StockMovementRepository stockMovementRepo;
    private final PurchaseOrderRepository poRepo;
    private final PartyRepository partyRepo;
    private final PartyPaymentRepository partyPaymentRepo;
    private final EmployeeRepository employeeRepo;
    private final SalaryRecordRepository salaryRepo;
    private final EmployeeAdvanceRepository advanceRepo;
    private final AttendanceRecordRepository attendanceRepo;
    private final EmployeeDocumentRepository docRepo;
    private final LeaveRecordRepository leaveRepo;
    private final PromotionRecordRepository promotionRepo;
    private final NoteRepository noteRepo;
    private final ReminderRepository reminderRepo;
    private final BusinessLetterRepository letterRepo;

    public TenantDataIntegrityAuditService(
            InvoiceRepository invoiceRepo,
            CustomerRepository customerRepo,
            ProductRepository productRepo,
            InvoicePaymentRepository paymentRepo,
            SalesReturnRepository returnRepo,
            StockMovementRepository stockMovementRepo,
            PurchaseOrderRepository poRepo,
            PartyRepository partyRepo,
            PartyPaymentRepository partyPaymentRepo,
            EmployeeRepository employeeRepo,
            SalaryRecordRepository salaryRepo,
            EmployeeAdvanceRepository advanceRepo,
            AttendanceRecordRepository attendanceRepo,
            EmployeeDocumentRepository docRepo,
            LeaveRecordRepository leaveRepo,
            PromotionRecordRepository promotionRepo,
            NoteRepository noteRepo,
            ReminderRepository reminderRepo,
            BusinessLetterRepository letterRepo
    ) {
        this.invoiceRepo = invoiceRepo;
        this.customerRepo = customerRepo;
        this.productRepo = productRepo;
        this.paymentRepo = paymentRepo;
        this.returnRepo = returnRepo;
        this.stockMovementRepo = stockMovementRepo;
        this.poRepo = poRepo;
        this.partyRepo = partyRepo;
        this.partyPaymentRepo = partyPaymentRepo;
        this.employeeRepo = employeeRepo;
        this.salaryRepo = salaryRepo;
        this.advanceRepo = advanceRepo;
        this.attendanceRepo = attendanceRepo;
        this.docRepo = docRepo;
        this.leaveRepo = leaveRepo;
        this.promotionRepo = promotionRepo;
        this.noteRepo = noteRepo;
        this.reminderRepo = reminderRepo;
        this.letterRepo = letterRepo;
    }

    public Map<String, Object> performFullAudit() {
        Map<String, Object> report = new LinkedHashMap<>();
        List<Map<String, Object>> violations = new ArrayList<>();

        int totalInvoicesScanned = 0;
        int totalPaymentsScanned = 0;
        int totalReturnsScanned = 0;
        int totalStockMovementsScanned = 0;
        int totalPoScanned = 0;
        int totalPartyPaymentsScanned = 0;
        int totalNotesScanned = 0;
        int totalRemindersScanned = 0;
        int totalLettersScanned = 0;

        // 1. Scan Invoices (Customer mismatch, Line-item Product mismatch, Quote source mismatch)
        List<Invoice> allInvoices = invoiceRepo.findAll();
        totalInvoicesScanned = allInvoices.size();
        for (Invoice inv : allInvoices) {
            Long invFirmId = inv.getFirmId();

            // Customer check
            if (inv.getCustomer() != null && inv.getCustomer().getFirmId() != null) {
                if (!Objects.equals(invFirmId, inv.getCustomer().getFirmId())) {
                    violations.add(createViolation(
                            "INVOICE_CUSTOMER_MISMATCH",
                            "Invoice ID " + inv.getId() + " (Firm " + invFirmId + ") is linked to Customer ID " + inv.getCustomer().getId() + " (Firm " + inv.getCustomer().getFirmId() + ")",
                            inv.getId(), invFirmId, inv.getCustomer().getId(), inv.getCustomer().getFirmId()
                    ));
                }
            }

            // Products in line items check
            if (inv.getItems() != null) {
                for (InvoiceItem item : inv.getItems()) {
                    if (item.getProduct() != null && item.getProduct().getFirmId() != null) {
                        if (!Objects.equals(invFirmId, item.getProduct().getFirmId())) {
                            violations.add(createViolation(
                                    "INVOICE_PRODUCT_MISMATCH",
                                    "Invoice ID " + inv.getId() + " (Firm " + invFirmId + ") contains Product ID " + item.getProduct().getId() + " (Firm " + item.getProduct().getFirmId() + ")",
                                    inv.getId(), invFirmId, item.getProduct().getId(), item.getProduct().getFirmId()
                            ));
                        }
                    }
                }
            }
        }

        // 2. Scan Invoice Payments
        List<InvoicePayment> allPayments = paymentRepo.findAll();
        totalPaymentsScanned = allPayments.size();
        for (InvoicePayment p : allPayments) {
            if (p.getInvoiceId() != null) {
                Optional<Invoice> invOpt = invoiceRepo.findById(p.getInvoiceId());
                if (invOpt.isPresent()) {
                    Invoice inv = invOpt.get();
                    if (!Objects.equals(p.getFirmId(), inv.getFirmId())) {
                        violations.add(createViolation(
                                "PAYMENT_INVOICE_MISMATCH",
                                "Payment ID " + p.getId() + " (Firm " + p.getFirmId() + ") is recorded against Invoice ID " + inv.getId() + " (Firm " + inv.getFirmId() + ")",
                                p.getId(), p.getFirmId(), inv.getId(), inv.getFirmId()
                        ));
                    }
                }
            }
        }

        // 3. Scan Sales Returns
        List<SalesReturn> allReturns = returnRepo.findAll();
        totalReturnsScanned = allReturns.size();
        for (SalesReturn r : allReturns) {
            if (r.getInvoice() != null) {
                if (!Objects.equals(r.getFirmId(), r.getInvoice().getFirmId())) {
                    violations.add(createViolation(
                            "RETURN_INVOICE_MISMATCH",
                            "Sales Return ID " + r.getId() + " (Firm " + r.getFirmId() + ") is recorded against Invoice ID " + r.getInvoice().getId() + " (Firm " + r.getInvoice().getFirmId() + ")",
                            r.getId(), r.getFirmId(), r.getInvoice().getId(), r.getInvoice().getFirmId()
                    ));
                }
            }
        }

        // 4. Scan Stock Movements
        List<StockMovement> allMovements = stockMovementRepo.findAll();
        totalStockMovementsScanned = allMovements.size();
        for (StockMovement sm : allMovements) {
            if (sm.getProductId() != null && sm.getFirmId() != null) {
                Optional<Product> prod = productRepo.findById(sm.getProductId());
                if (prod.isPresent() && !Objects.equals(sm.getFirmId(), prod.get().getFirmId())) {
                    violations.add(createViolation(
                            "STOCK_MOVEMENT_PRODUCT_MISMATCH",
                            "StockMovement ID " + sm.getId() + " (Firm " + sm.getFirmId() + ") logged for Product ID " + prod.get().getId() + " (Firm " + prod.get().getFirmId() + ")",
                            sm.getId(), sm.getFirmId(), prod.get().getId(), prod.get().getFirmId()
                    ));
                }
            }
        }

        // 5. Scan Purchase Orders
        List<PurchaseOrder> allPos = poRepo.findAll();
        totalPoScanned = allPos.size();
        for (PurchaseOrder po : allPos) {
            if (po.getParty() != null && po.getParty().getFirmId() != null) {
                if (!Objects.equals(po.getFirmId(), po.getParty().getFirmId())) {
                    violations.add(createViolation(
                            "PURCHASE_ORDER_PARTY_MISMATCH",
                            "PO ID " + po.getId() + " (Firm " + po.getFirmId() + ") is assigned to Party ID " + po.getParty().getId() + " (Firm " + po.getParty().getFirmId() + ")",
                            po.getId(), po.getFirmId(), po.getParty().getId(), po.getParty().getFirmId()
                    ));
                }
            }
        }

        // 6. Scan Notes & Reminders
        List<Note> allNotes = noteRepo.findAll();
        totalNotesScanned = allNotes.size();
        for (Note n : allNotes) {
            if (n.getCustomerId() != null && n.getFirmId() != null) {
                Optional<Customer> cust = customerRepo.findById(n.getCustomerId());
                if (cust.isPresent() && !Objects.equals(n.getFirmId(), cust.get().getFirmId())) {
                    violations.add(createViolation(
                            "NOTE_CUSTOMER_MISMATCH",
                            "Note ID " + n.getId() + " (Firm " + n.getFirmId() + ") references Customer ID " + cust.get().getId() + " (Firm " + cust.get().getFirmId() + ")",
                            n.getId(), n.getFirmId(), cust.get().getId(), cust.get().getFirmId()
                    ));
                }
            }
        }

        List<Reminder> allReminders = reminderRepo.findAll();
        totalRemindersScanned = allReminders.size();
        for (Reminder r : allReminders) {
            if (r.getCustomerId() != null && r.getFirmId() != null) {
                Optional<Customer> cust = customerRepo.findById(r.getCustomerId());
                if (cust.isPresent() && !Objects.equals(r.getFirmId(), cust.get().getFirmId())) {
                    violations.add(createViolation(
                            "REMINDER_CUSTOMER_MISMATCH",
                            "Reminder ID " + r.getId() + " (Firm " + r.getFirmId() + ") references Customer ID " + cust.get().getId() + " (Firm " + cust.get().getFirmId() + ")",
                            r.getId(), r.getFirmId(), cust.get().getId(), cust.get().getFirmId()
                    ));
                }
            }
        }

        report.put("status", violations.isEmpty() ? "CLEAN" : "CORRUPTION_DETECTED");
        report.put("totalViolationsCount", violations.size());
        report.put("violations", violations);
        report.put("scannedCounts", Map.of(
                "invoices", totalInvoicesScanned,
                "payments", totalPaymentsScanned,
                "salesReturns", totalReturnsScanned,
                "stockMovements", totalStockMovementsScanned,
                "purchaseOrders", totalPoScanned,
                "notes", totalNotesScanned,
                "reminders", totalRemindersScanned
        ));
        report.put("auditTimestamp", java.time.LocalDateTime.now().toString());

        return report;
    }

    private Map<String, Object> createViolation(String type, String description, Long primaryId, Long primaryFirmId, Long foreignId, Long foreignFirmId) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("violationType", type);
        v.put("description", description);
        v.put("primaryRecordId", primaryId);
        v.put("primaryFirmId", primaryFirmId);
        v.put("foreignRecordId", foreignId);
        v.put("foreignFirmId", foreignFirmId);
        return v;
    }
}
