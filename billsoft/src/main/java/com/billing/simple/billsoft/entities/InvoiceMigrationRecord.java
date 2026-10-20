package com.billing.simple.billsoft.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Immutable migration history recording every cross-firm invoice transition
 * (both forward migrations and exact-snapshot reverts).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "invoice_migration_records", indexes = {
    @Index(name = "idx_mig_invoice_id", columnList = "invoiceId, createdAt DESC"),
    @Index(name = "idx_mig_source_firm", columnList = "sourceFirmId, createdAt DESC"),
    @Index(name = "idx_mig_target_firm", columnList = "targetFirmId, createdAt DESC")
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class InvoiceMigrationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long invoiceId;

    @Column(nullable = false)
    private Long sourceFirmId;

    @Column(nullable = false)
    private Long targetFirmId;

    @Column(nullable = false, length = 100)
    private String sourceInvoiceNumber;

    @Column(nullable = false, length = 100)
    private String targetInvoiceNumber;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isRevert = false;

    // Links to parent migration record if this is a revert or chained transition
    private Long parentMigrationId;

    // Status: ACTIVE, REVERTED, SUPERSEDED
    @Builder.Default
    @Column(nullable = false, length = 50)
    private String status = "ACTIVE";

    // Complete JSON serialization of pre-migration aggregate business state
    @Lob
    @Column(nullable = false, columnDefinition = "CLOB")
    private String preMigrationSnapshot;

    @Column(length = 100)
    private String migratedBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.isRevert == null) {
            this.isRevert = false;
        }
        if (this.status == null) {
            this.status = "ACTIVE";
        }
        if (this.migratedBy == null || this.migratedBy.isBlank()) {
            this.migratedBy = "system";
        }
    }
}
