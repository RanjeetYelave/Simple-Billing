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
@Table(name = "notes")
public class Note {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Firm this note is associated with
    private Long firmId;

    // Optional link to customer
    private Long customerId;

    @Column(nullable = false, length = 200)
    private String title;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String content;

    @Column(length = 1000)
    private String tags; // Comma-separated values

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getFirmId() { return firmId; }
    public void setFirmId(Long firmId) { this.firmId = firmId; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public static NoteBuilder builder() {
        return new NoteBuilder();
    }

    public static class NoteBuilder {
        private Long id;
        private Long firmId;
        private Long customerId;
        private String title;
        private String content;
        private String tags;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public NoteBuilder id(Long id) { this.id = id; return this; }
        public NoteBuilder firmId(Long firmId) { this.firmId = firmId; return this; }
        public NoteBuilder customerId(Long customerId) { this.customerId = customerId; return this; }
        public NoteBuilder title(String title) { this.title = title; return this; }
        public NoteBuilder content(String content) { this.content = content; return this; }
        public NoteBuilder tags(String tags) { this.tags = tags; return this; }
        public NoteBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public NoteBuilder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

        public Note build() {
            Note n = new Note();
            n.id = this.id;
            n.firmId = this.firmId;
            n.customerId = this.customerId;
            n.title = this.title;
            n.content = this.content;
            n.tags = this.tags;
            n.createdAt = this.createdAt;
            n.updatedAt = this.updatedAt;
            return n;
        }
    }
}


