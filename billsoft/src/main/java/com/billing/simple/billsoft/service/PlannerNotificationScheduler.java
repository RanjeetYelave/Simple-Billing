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
    private InboxMessageRepository inboxMessageRepository;

    @Autowired
    private InboxMessageService inboxMessageService;

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

            // 2. Backward compatibility for legacy inbox table
            InboxMessage msg = new InboxMessage();
            msg.setFirmId(firmId);
            msg.setSubject(subject);
            msg.setBody(body);
            msg.setSender("System (Planner)");
            msg.setRead(false);
            msg.setReminderId(item.getId());
            msg.setCreatedAt(LocalDateTime.now());
            inboxMessageRepository.save(msg);

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
            inboxMessageService.notifyAggregatedLowStock(1L, products);
        } else {
            for (FirmDetails firm : firms) {
                List<Product> products = productRepository.findByFirmId(firm.getId());
                inboxMessageService.notifyAggregatedLowStock(firm.getId(), products);
            }
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

            // Legacy backward-compat check
            inboxMessageService.sendNotificationIfAbsent(
                    firmId,
                    "⏰ Overdue Invoice: #" + inv.getInvoiceNumber(),
                    subject,
                    body,
                    "Billing System"
            );
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

            // Legacy backward-compat check
            inboxMessageService.sendNotificationIfAbsent(
                    firmId,
                    "📦 Expected Delivery: PO #" + po.getPoNumber(),
                    subject,
                    body,
                    "Purchase System"
            );
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

        // 2. Legacy check
        List<InboxMessage> existingMsgs = inboxMessageRepository.findByFirmIdOrderByCreatedAtDesc(firmId);
        boolean alreadySent = existingMsgs.stream()
                .anyMatch(m -> m.getSubject() != null && m.getSubject().startsWith(subjectPrefix));

        if (!alreadySent) {
            InboxMessage msg = new InboxMessage();
            msg.setFirmId(firmId);
            msg.setSubject(subject);
            msg.setBody(bodyBuilder.toString());
            msg.setSender("HR System");
            msg.setRead(false);
            msg.setCreatedAt(now);
            inboxMessageRepository.save(msg);
        }
    }
}
