package com.billing.simple.billsoft.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "backup_entity_mappings", indexes = {
    @Index(name = "idx_bem_source_lookup", columnList = "backupSourceId, sourceEntityId, entityType, targetFirmId"),
    @Index(name = "idx_bem_target_lookup", columnList = "targetFirmId, entityType, targetEntityId")
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class BackupEntityMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String backupSourceId;

    @Column(nullable = false)
    private Long sourceEntityId;

    @Column(nullable = false, length = 50)
    private String entityType;

    @Column(nullable = false)
    private Long targetFirmId;

    @Column(nullable = false)
    private Long targetEntityId;

    private LocalDateTime importedAt;

    @PrePersist
    public void prePersist() {
        if (this.importedAt == null) {
            this.importedAt = LocalDateTime.now();
        }
    }
}
