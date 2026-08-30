package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.entities.Employee;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.InboxMessage;
import com.billing.simple.billsoft.entities.Reminder;
import com.billing.simple.billsoft.entities.SalaryRecord;
import com.billing.simple.billsoft.repo.EmployeeRepository;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;
import com.billing.simple.billsoft.repo.InboxMessageRepository;
import com.billing.simple.billsoft.repo.ReminderRepository;
import com.billing.simple.billsoft.repo.SalaryRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
    private EmployeeRepository employeeRepository;

    @Autowired
    private SalaryRecordRepository salaryRecordRepository;

    @Autowired
    private FirmDetailsRepository firmDetailsRepository;

    @Scheduled(fixedDelay = 10000) // Runs every 10 seconds
    @Transactional
    public void checkDuePlannerItems() {
        LocalDateTime now = LocalDateTime.now();
        List<Reminder> allReminders = reminderRepository.findAll();
        for (Reminder item : allReminders) {
            // Only check items that are not completed, not already notified, and have a due date
            if (!item.isCompleted() && !item.isInboxNotified() && item.getDueDate() != null) {
                if (item.getDueDate().isBefore(now) || item.getDueDate().isEqual(now)) {
                    // Send inbox message!
                    String typeStr = "task".equalsIgnoreCase(item.getType()) ? "Task" : "Reminder";
                    String subject = typeStr + " Due: " + item.getTitle();
                    String body = "The following " + typeStr.toLowerCase() + " is now due:\n\n" +
                            "Title: " + item.getTitle() + "\n" +
                            "Due Date: " + item.getDueDate().toString().replace("T", " ") + "\n\n" +
                            "Notes:\n" + (item.getNote() != null ? item.getNote() : "No notes provided.");

                    InboxMessage msg = new InboxMessage();
                    msg.setFirmId(item.getFirmId() != null ? item.getFirmId() : 1L);
                    msg.setSubject(subject);
                    msg.setBody(body);
                    msg.setSender("System (Planner)");
                    msg.setRead(false);
                    msg.setReminderId(item.getId());
                    msg.setCreatedAt(LocalDateTime.now());

                    inboxMessageRepository.save(msg);

                    // Mark as notified so we don't send it again
                    item.setInboxNotified(true);
                    reminderRepository.save(item);
                }
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

        // Only send if there are pending salaries
        if (pendingEmployees.isEmpty()) {
            return;
        }

        String subjectPrefix = "💰 Monthly Payroll Reminder — " + monthNameFormatted + " " + now.getYear();
        List<InboxMessage> existingMsgs = inboxMessageRepository.findByFirmIdOrderByCreatedAtDesc(firmId);
        boolean alreadySent = existingMsgs.stream()
                .anyMatch(m -> m.getSubject() != null && m.getSubject().startsWith(subjectPrefix));

        if (alreadySent) {
            return;
        }

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
