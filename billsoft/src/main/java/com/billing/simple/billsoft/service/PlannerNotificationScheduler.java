package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.entities.Reminder;
import com.billing.simple.billsoft.entities.InboxMessage;
import com.billing.simple.billsoft.repo.ReminderRepository;
import com.billing.simple.billsoft.repo.InboxMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class PlannerNotificationScheduler {

    @Autowired
    private ReminderRepository reminderRepository;

    @Autowired
    private InboxMessageRepository inboxMessageRepository;

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
            String subject = "💰 Monthly Payroll Reminder — " + monthName.substring(0, 1) + monthName.substring(1).toLowerCase() + " " + now.getYear();
            
            // Check if reminder message for this month already exists
            List<InboxMessage> existingMsgs = inboxMessageRepository.findAll();
            boolean exists = existingMsgs.stream()
                    .anyMatch(m -> m.getSubject() != null && m.getSubject().equals(subject));
            
            if (!exists) {
                InboxMessage msg = new InboxMessage();
                msg.setFirmId(1L);
                msg.setSubject(subject);
                msg.setBody("Monthly payroll processing is unlocked starting today (27th of " + monthName.substring(0, 1) + monthName.substring(1).toLowerCase() + "). Please visit the HR module -> Monthly Payroll section to calculate final salary disbursals, review leaves, and generate payslip PDFs.");
                msg.setSender("HR System");
                msg.setRead(false);
                msg.setCreatedAt(now);
                inboxMessageRepository.save(msg);
            }
        }
    }
}
