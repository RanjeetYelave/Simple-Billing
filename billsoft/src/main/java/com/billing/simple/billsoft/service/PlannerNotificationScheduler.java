package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dto.NotificationRequest;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.repositories.PurchaseOrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class PlannerNotificationScheduler {

    @Autowired
    private ReminderRepository reminderRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private SalaryRecordRepository salaryRecordRepository;

    @Autowired
    private FirmDetailsRepository firmDetailsRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Scheduled(fixedDelay = 10000) // Runs every 10 seconds
    @Transactional
    public void checkDuePlannerItems() {
        LocalDateTime now = LocalDateTime.now();
        List<Reminder> dueReminders = reminderRepository.findDueReminders(now);
        for (Reminder item : dueReminders) {
            String typeStr = "task".equalsIgnoreCase(item.getType()) ? "Task" : "Reminder";
            String subject = typeStr + " Due: " + item.getTitle();
            String body = "The following " + typeStr.toLowerCase() + " is now due:\n\n" +
                    "Title: " + item.getTitle() + "\n" +
                    "Due Date: " + (item.getDueDate() != null ? item.getDueDate().toString().replace("T", " ") : "Now") + "\n\n" +
                    "Notes:\n" + (item.getNote() != null ? item.getNote() : "No notes provided.");

            Long firmId = item.getFirmId() != null ? item.getFirmId() : 1L;

            // 1. Emit to canonical NotificationService
            if (notificationService != null) {
                String eventKey = "planner:" + ("task".equalsIgnoreCase(item.getType()) ? "task" : "reminder") + ":" + item.getId();
                notificationService.createOrUpdate(NotificationRequest.builder()
                        .firmId(firmId)
                        .eventKey(eventKey)
                        .category(NotificationCategory.PLANNER)
                        .priority(NotificationPriority.HIGH)
                        .title(subject)
                        .body(body)
                        .sender("System (Planner)")
                        .primaryActionLabel("Open in Planner")
                        .primaryActionType(NotificationActionType.NAVIGATE)
                        .primaryActionTarget("{\"page\":\"planner\",\"plannerTab\":\"board\"}")
                        .secondaryActionLabel("Mark Done")
                        .secondaryActionType(NotificationActionType.API_ACTION)
                        .secondaryActionTarget("MARK_TASK_DONE")
                        .build());
            }

            item.setInboxNotified(true);
            reminderRepository.save(item);
        }
    }

    @Scheduled(fixedDelay = 15000) // Runs every 15 seconds
    @Transactional
    public void checkLowStockAlerts() {
        List<FirmDetails> firms = firmDetailsRepository.findAll();
        if (firms.isEmpty()) {
            List<Product> products = productRepository.findByFirmId(1L);
            notifyAggregatedLowStock(1L, products);
        } else {
            for (FirmDetails firm : firms) {
                List<Product> products = productRepository.findByFirmId(firm.getId());
                notifyAggregatedLowStock(firm.getId(), products);
            }
        }
    }

    /**
     * Aggregates all low stock and out-of-stock items for a firm into a single consolidated notification.
     */
    @Transactional
    public void notifyAggregatedLowStock(Long firmId, List<Product> products) {
        if (firmId == null || products == null || products.isEmpty()) {
            return;
        }

        List<Product> outOfStock = new ArrayList<>();
        List<Product> lowStock = new ArrayList<>();

        for (Product p : products) {
            if ("SERVICE".equalsIgnoreCase(p.getItemType())) {
                continue;
            }
            java.math.BigDecimal stock = p.getStockQuantity() != null ? p.getStockQuantity() : java.math.BigDecimal.ZERO;
            java.math.BigDecimal min = p.getMinStockLevel() != null ? p.getMinStockLevel() : new java.math.BigDecimal("5.000");

            if (stock.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                outOfStock.add(p);
            } else if (stock.compareTo(min) <= 0) {
                lowStock.add(p);
            }
        }

        int totalAlertItems = outOfStock.size() + lowStock.size();
        if (totalAlertItems == 0) {
            // Inventory condition resolved; mark aggregate low stock notification as ACTIONED
            if (notificationService != null) {
                notificationService.resolveByEventKey(firmId, "inventory:low-stock:aggregate:" + firmId);
            }
            return;
        }

        String subject;
        if (!outOfStock.isEmpty() && !lowStock.isEmpty()) {
            subject = String.format("⚠️ Inventory Alert: %d Items Require Attention (%d Out of Stock, %d Low)",
                    totalAlertItems, outOfStock.size(), lowStock.size());
        } else if (!outOfStock.isEmpty()) {
            subject = String.format("🔴 Inventory Alert: %d %s Out of Stock",
                    outOfStock.size(), outOfStock.size() == 1 ? "Item" : "Items");
        } else {
            subject = String.format("⚠️ Inventory Alert: %d %s Low on Stock",
                    lowStock.size(), lowStock.size() == 1 ? "Item" : "Items");
        }

        StringBuilder body = new StringBuilder();
        body.append("Consolidated Inventory Alert: Several items have reached or breached their safety reorder thresholds.\n\n");

        if (!outOfStock.isEmpty()) {
            body.append("🔴 OUT OF STOCK (").append(outOfStock.size()).append("):\n");
            int limit = Math.min(outOfStock.size(), 10);
            for (int i = 0; i < limit; i++) {
                Product p = outOfStock.get(i);
                String skuStr = (p.getSku() != null && !p.getSku().isBlank()) ? " [SKU: " + p.getSku() + "]" : "";
                String unitStr = p.getUnit() != null ? p.getUnit() : "pcs";
                body.append("• ").append(p.getName()).append(skuStr)
                        .append(" — 0 ").append(unitStr).append(" (Min Threshold: ")
                        .append(p.getMinStockLevel() != null ? p.getMinStockLevel().stripTrailingZeros().toPlainString() : "0")
                        .append(" ").append(unitStr).append(")\n");
            }
            if (outOfStock.size() > 10) {
                body.append("... and ").append(outOfStock.size() - 10).append(" more out-of-stock items.\n");
            }
            body.append("\n");
        }

        if (!lowStock.isEmpty()) {
            body.append("🟡 LOW STOCK (").append(lowStock.size()).append("):\n");
            int limit = Math.min(lowStock.size(), 10);
            for (int i = 0; i < limit; i++) {
                Product p = lowStock.get(i);
                String skuStr = (p.getSku() != null && !p.getSku().isBlank()) ? " [SKU: " + p.getSku() + "]" : "";
                String unitStr = p.getUnit() != null ? p.getUnit() : "pcs";
                String currentStock = p.getStockQuantity() != null ? p.getStockQuantity().stripTrailingZeros().toPlainString() : "0";
                String minStock = p.getMinStockLevel() != null ? p.getMinStockLevel().stripTrailingZeros().toPlainString() : "0";
                body.append("• ").append(p.getName()).append(skuStr)
                        .append(" — ").append(currentStock).append(" ").append(unitStr)
                        .append(" remaining (Min Threshold: ").append(minStock).append(" ").append(unitStr).append(")\n");
            }
            if (lowStock.size() > 10) {
                body.append("... and ").append(lowStock.size() - 10).append(" more low-stock items.\n");
            }
            body.append("\n");
        }

        body.append("Action Required: Please visit the Inventory Manager to adjust counts or generate Purchase Orders to replenish supply.");

        // Canonical Notification with deterministic eventKey
        if (notificationService != null) {
            notificationService.createOrUpdate(NotificationRequest.builder()
                    .firmId(firmId)
                    .eventKey("inventory:low-stock:aggregate:" + firmId)
                    .category(NotificationCategory.INVENTORY)
                    .priority(NotificationPriority.HIGH)
                    .title(subject)
                    .body(body.toString())
                    .sender("Inventory System")
                    .primaryActionLabel("Open Inventory Catalog")
                    .primaryActionType(NotificationActionType.NAVIGATE)
                    .primaryActionTarget("{\"page\":\"firm\",\"tab\":\"inventory\"}")
                    .secondaryActionLabel("Create Purchase Order")
                    .secondaryActionType(NotificationActionType.NAVIGATE)
                    .secondaryActionTarget("{\"page\":\"firm\",\"tab\":\"paperwork\",\"subTab\":\"orders\"}")
                    .build());
        }
    }

    @Scheduled(fixedDelay = 30000) // Runs every 30 seconds
    @Transactional
    public void checkOverdueInvoices() {
        LocalDate today = LocalDate.now();
        List<Invoice> invoices = invoiceRepository.findOverdueInvoices(today);
        for (Invoice inv : invoices) {
            String custName = inv.getCustomer() != null ? inv.getCustomer().getName() : "Customer";
            String subject = "⏰ Overdue Invoice: #" + inv.getInvoiceNumber() + " (" + custName + ")";
            String invDateStr = inv.getInvoiceDate() != null ? inv.getInvoiceDate().toLocalDate().toString() : "N/A";
            String body = "An issued invoice has passed its payment due date and remains unpaid!\n\n" +
                    "• Invoice Number: " + inv.getInvoiceNumber() + "\n" +
                    "• Customer: " + custName + "\n" +
                    "• Invoice Date: " + invDateStr + "\n" +
                    "• Due Date: " + inv.getDueDate() + "\n" +
                    "• Total Outstanding: ₹" + (inv.getTotalAmount() != null ? inv.getTotalAmount() : "0.00") + "\n\n" +
                    "Action Required: Please follow up with the customer or issue a payment reminder notice.";

            Long firmId = inv.getFirmId() != null ? inv.getFirmId() : 1L;

            if (notificationService != null) {
                notificationService.createOrUpdate(NotificationRequest.builder()
                        .firmId(firmId)
                        .eventKey("billing:invoice:overdue:" + inv.getId())
                        .category(NotificationCategory.BILLING)
                        .priority(NotificationPriority.HIGH)
                        .title(subject)
                        .body(body)
                        .sender("Billing System")
                        .primaryActionLabel("View Invoice")
                        .primaryActionType(NotificationActionType.NAVIGATE)
                        .primaryActionTarget("{\"page\":\"invoices\",\"invoiceId\":" + inv.getId() + "}")
                        .secondaryActionLabel("Record Payment")
                        .secondaryActionType(NotificationActionType.MODAL)
                        .secondaryActionTarget("{\"modal\":\"payment\",\"invoiceId\":" + inv.getId() + "}")
                        .build());
            }
        }
    }

    @Scheduled(fixedDelay = 30000) // Runs every 30 seconds
    @Transactional
    public void checkPendingPurchaseOrderDeliveries() {
        LocalDate today = LocalDate.now();
        List<PurchaseOrder> pos = purchaseOrderRepository.findPendingDeliveries(today);
        for (PurchaseOrder po : pos) {
            String partyName = po.getPartyName() != null ? po.getPartyName() : (po.getParty() != null ? po.getParty().getName() : "Vendor");
            String subject = "📦 Expected Delivery: PO #" + po.getPoNumber() + " (" + partyName + ")";
            String body = "A vendor purchase order is due for delivery today or overdue!\n\n" +
                    "• PO Number: " + po.getPoNumber() + "\n" +
                    "• Supplier / Vendor: " + partyName + "\n" +
                    "• PO Date: " + po.getPoDate() + "\n" +
                    "• Expected Delivery: " + po.getExpectedDeliveryDate() + "\n" +
                    "• Total Amount: ₹" + (po.getTotalAmount() != null ? po.getTotalAmount() : "0.00") + "\n\n" +
                    "Action Required: Check with vendor and mark PO as 'RECEIVED' upon goods arrival to update stock counts.";

            Long firmId = po.getFirmId() != null ? po.getFirmId() : 1L;

            if (notificationService != null) {
                notificationService.createOrUpdate(NotificationRequest.builder()
                        .firmId(firmId)
                        .eventKey("purchase:po:delivery:" + po.getId())
                        .category(NotificationCategory.PURCHASE)
                        .priority(NotificationPriority.NORMAL)
                        .title(subject)
                        .body(body)
                        .sender("Purchase System")
                        .primaryActionLabel("View Purchase Order")
                        .primaryActionType(NotificationActionType.NAVIGATE)
                        .primaryActionTarget("{\"page\":\"firm\",\"tab\":\"paperwork\",\"subTab\":\"orders\",\"poId\":" + po.getId() + "}")
                        .build());
            }
        }
    }

    @Scheduled(fixedDelay = 30000) // Runs every 30 seconds
    @Transactional
    public void checkPayrollMonthlyReminders() {
        LocalDateTime now = LocalDateTime.now();
        if (now.getDayOfMonth() >= 27) {
            String monthName = now.getMonth().name();
            String monthNameFormatted = monthName.substring(0, 1) + monthName.substring(1).toLowerCase();
            String monthYear = String.format("%02d-%d", now.getMonthValue(), now.getYear());

            List<FirmDetails> firms = firmDetailsRepository.findAll();
            if (firms.isEmpty()) {
                checkAndSendPayrollReminderForFirm(1L, monthNameFormatted, monthYear, now);
            } else {
                for (FirmDetails firm : firms) {
                    checkAndSendPayrollReminderForFirm(firm.getId(), monthNameFormatted, monthYear, now);
                }
            }
        }
    }

    @Scheduled(fixedDelay = 30000) // Runs every 30 seconds to reconcile expired snoozes
    @Transactional
    public void reconcileSnoozedNotifications() {
        if (notificationService != null) {
            notificationService.reconcileExpiredSnoozes();
        }
    }

    private void checkAndSendPayrollReminderForFirm(Long firmId, String monthNameFormatted, String monthYear, LocalDateTime now) {
        List<Employee> allEmployees = employeeRepository.findByFirmId(firmId);
        if (allEmployees == null || allEmployees.isEmpty()) {
            return;
        }

        List<Employee> activeEmployees = allEmployees.stream()
                .filter(e -> Boolean.TRUE.equals(e.getIsActive()))
                .toList();

        if (activeEmployees.isEmpty()) {
            return;
        }

        List<Employee> pendingEmployees = new ArrayList<>();
        for (Employee emp : activeEmployees) {
            Optional<SalaryRecord> salOpt = salaryRecordRepository.findByEmployeeIdAndMonthYear(emp.getId(), monthYear);
            if (salOpt.isEmpty()) {
                pendingEmployees.add(emp);
            }
        }

        if (pendingEmployees.isEmpty()) {
            // All active employee salaries disbursed for this month; resolve active notification
            if (notificationService != null) {
                notificationService.resolveByEventKey(firmId, "hr:payroll:" + firmId + ":" + monthYear);
            }
            return;
        }

        String subjectPrefix = "💰 Monthly Payroll Reminder — " + monthNameFormatted + " " + now.getYear();
        String subject = subjectPrefix + " (" + pendingEmployees.size() + " Pending)";

        StringBuilder bodyBuilder = new StringBuilder();
        bodyBuilder.append("Monthly payroll processing is unlocked starting today (27th of ")
                .append(monthNameFormatted).append(" ").append(now.getYear())
                .append(").\n\n")
                .append("Pending Employee Salaries (").append(pendingEmployees.size()).append("):\n");

        for (Employee emp : pendingEmployees) {
            String roleStr = (emp.getRole() != null && !emp.getRole().isBlank()) ? " (" + emp.getRole() + ")" : "";
            bodyBuilder.append("• ").append(emp.getName()).append(roleStr).append("\n");
        }

        bodyBuilder.append("\nPlease visit the HR module -> Monthly Payroll section to calculate final salary disbursals, review leaves, and generate payslip PDFs.");

        // 1. Emit to canonical NotificationService with deterministic monthly key
        if (notificationService != null) {
            notificationService.createOrUpdate(NotificationRequest.builder()
                    .firmId(firmId)
                    .eventKey("hr:payroll:" + firmId + ":" + monthYear)
                    .category(NotificationCategory.HR)
                    .priority(NotificationPriority.HIGH)
                    .title(subject)
                    .body(bodyBuilder.toString())
                    .sender("HR System")
                    .primaryActionLabel("Process Payroll")
                    .primaryActionType(NotificationActionType.NAVIGATE)
                    .primaryActionTarget("{\"page\":\"hr\",\"tab\":\"payroll\"}")
                    .build());
        }
    }
}
