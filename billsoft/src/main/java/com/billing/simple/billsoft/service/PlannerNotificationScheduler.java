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
}
