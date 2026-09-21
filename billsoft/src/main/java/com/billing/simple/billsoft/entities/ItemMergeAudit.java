package com.billing.simple.billsoft.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "item_merge_audits", indexes = {
    @Index(name = "idx_merge_audit_firm_time", columnList = "firmId, mergedAt")
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ItemMergeAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long firmId;

    @Column(nullable = false, length = 20)
    private String primaryType; // 'PRODUCT' or 'SAVED_ITEM'

    @Column(nullable = false)
    private Long primaryId;

    private String primaryName;

    @Column(nullable = false, length = 20)
    private String secondaryType; // 'PRODUCT' or 'SAVED_ITEM'

    @Column(nullable = false)
    private Long secondaryId;

    private String secondaryName;

    @Column(precision = 12, scale = 3)
    private BigDecimal stockTransferred;

    @Column(length = 2000)
    private String detailsJson;

    private LocalDateTime mergedAt;

    @PrePersist
    public void prePersist() {
        if (this.mergedAt == null) {
            this.mergedAt = LocalDateTime.now();
        }
    }
}
