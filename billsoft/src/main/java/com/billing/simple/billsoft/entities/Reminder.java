package com.billing.simple.billsoft.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "reminders")
public class Reminder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Firm the reminder belongs to
    @Column(nullable = false)
    private Long firmId;

    // Optional link to customer
    private Long customerId;

    // Optional link to invoice (or estimate)
    private Long invoiceId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String note;

    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.billing.simple.billsoft.config.FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime dueDate;

    private boolean completed = false;
    private Boolean inboxNotified = false;

    private String type; // "reminder" or "task"
    private String tags; // comma-separated values
    private String status; // "TODO", "IN_PROGRESS", "DONE"
    private Integer progress; // 0-100

    private LocalDateTime completedAt;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public boolean isInboxNotified() {
        return inboxNotified != null && inboxNotified;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getFirmId() { return firmId; }
    public void setFirmId(Long firmId) { this.firmId = firmId; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public Long getInvoiceId() { return invoiceId; }
    public void setInvoiceId(Long invoiceId) { this.invoiceId = invoiceId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getDueDate() { return dueDate; }
    public void setDueDate(LocalDateTime dueDate) { this.dueDate = dueDate; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public Boolean getInboxNotified() { return inboxNotified; }
    public void setInboxNotified(Boolean inboxNotified) { this.inboxNotified = inboxNotified; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public static ReminderBuilder builder() {
        return new ReminderBuilder();
    }

    public static class ReminderBuilder {
        private Long id;
        private Long firmId;
        private Long customerId;
        private Long invoiceId;
        private String title;
        private String note;
        private LocalDateTime dueDate;
        private boolean completed = false;
        private Boolean inboxNotified = false;
        private String type;
        private String tags;
        private String status;
        private Integer progress;
        private LocalDateTime completedAt;
        private LocalDateTime createdAt;

        public ReminderBuilder id(Long id) { this.id = id; return this; }
        public ReminderBuilder firmId(Long firmId) { this.firmId = firmId; return this; }
        public ReminderBuilder customerId(Long customerId) { this.customerId = customerId; return this; }
        public ReminderBuilder invoiceId(Long invoiceId) { this.invoiceId = invoiceId; return this; }
        public ReminderBuilder title(String title) { this.title = title; return this; }
        public ReminderBuilder note(String note) { this.note = note; return this; }
        public ReminderBuilder dueDate(LocalDateTime dueDate) { this.dueDate = dueDate; return this; }
        public ReminderBuilder completed(boolean completed) { this.completed = completed; return this; }
        public ReminderBuilder inboxNotified(Boolean inboxNotified) { this.inboxNotified = inboxNotified; return this; }
        public ReminderBuilder type(String type) { this.type = type; return this; }
        public ReminderBuilder tags(String tags) { this.tags = tags; return this; }
        public ReminderBuilder status(String status) { this.status = status; return this; }
        public ReminderBuilder progress(Integer progress) { this.progress = progress; return this; }
        public ReminderBuilder completedAt(LocalDateTime completedAt) { this.completedAt = completedAt; return this; }
        public ReminderBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

        public Reminder build() {
            Reminder r = new Reminder();
            r.id = this.id;
            r.firmId = this.firmId;
            r.customerId = this.customerId;
            r.invoiceId = this.invoiceId;
            r.title = this.title;
            r.note = this.note;
            r.dueDate = this.dueDate;
            r.completed = this.completed;
            r.inboxNotified = this.inboxNotified != null ? this.inboxNotified : false;
            r.type = this.type;
            r.tags = this.tags;
            r.status = this.status;
            r.progress = this.progress;
            r.completedAt = this.completedAt;
            r.createdAt = this.createdAt;
            return r;
        }
    }
}


