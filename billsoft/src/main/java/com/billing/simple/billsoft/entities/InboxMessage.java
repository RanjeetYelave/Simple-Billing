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
@Table(name = "inbox_messages")
public class InboxMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long firmId;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(length = 2000)
    private String body;

    @Column(length = 100)
    private String sender;

    private boolean isRead = false;

    private Long reminderId;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getFirmId() { return firmId; }
    public void setFirmId(Long firmId) { this.firmId = firmId; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean isRead) { this.isRead = isRead; }

    public Long getReminderId() { return reminderId; }
    public void setReminderId(Long reminderId) { this.reminderId = reminderId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public static InboxMessageBuilder builder() {
        return new InboxMessageBuilder();
    }

    public static class InboxMessageBuilder {
        private Long id;
        private Long firmId;
        private String subject;
        private String body;
        private String sender;
        private boolean isRead;
        private Long reminderId;
        private LocalDateTime createdAt;

        public InboxMessageBuilder id(Long id) { this.id = id; return this; }
        public InboxMessageBuilder firmId(Long firmId) { this.firmId = firmId; return this; }
        public InboxMessageBuilder subject(String subject) { this.subject = subject; return this; }
        public InboxMessageBuilder body(String body) { this.body = body; return this; }
        public InboxMessageBuilder sender(String sender) { this.sender = sender; return this; }
        public InboxMessageBuilder isRead(boolean isRead) { this.isRead = isRead; return this; }
        public InboxMessageBuilder reminderId(Long reminderId) { this.reminderId = reminderId; return this; }
        public InboxMessageBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

        public InboxMessage build() {
            InboxMessage msg = new InboxMessage();
            msg.id = this.id;
            msg.firmId = this.firmId;
            msg.subject = this.subject;
            msg.body = this.body;
            msg.sender = this.sender;
            msg.isRead = this.isRead;
            msg.reminderId = this.reminderId;
            msg.createdAt = this.createdAt;
            return msg;
        }
    }
}


