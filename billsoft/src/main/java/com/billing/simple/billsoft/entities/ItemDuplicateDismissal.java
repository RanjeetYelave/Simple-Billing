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
@Table(name = "item_duplicate_dismissals", indexes = {
    @Index(name = "idx_dismissal_firm_pair", columnList = "firmId, sourceTypeA, itemIdA, sourceTypeB, itemIdB")
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ItemDuplicateDismissal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long firmId;

    @Column(nullable = false, length = 20)
    private String sourceTypeA; // 'PRODUCT' or 'SAVED_ITEM'

    @Column(nullable = false)
    private Long itemIdA;

    @Column(nullable = false, length = 20)
    private String sourceTypeB; // 'PRODUCT' or 'SAVED_ITEM'

    @Column(nullable = false)
    private Long itemIdB;

    private LocalDateTime dismissedAt;

    @PrePersist
    public void prePersist() {
        if (this.dismissedAt == null) {
            this.dismissedAt = LocalDateTime.now();
        }
        canonicalizePairOrder();
    }

    /**
     * Canonicalizes (A, B) vs (B, A) so order does not matter.
     */
    public void canonicalizePairOrder() {
        if (sourceTypeA == null || sourceTypeB == null || itemIdA == null || itemIdB == null) {
            return;
        }
        String keyA = sourceTypeA + ":" + itemIdA;
        String keyB = sourceTypeB + ":" + itemIdB;
        if (keyA.compareTo(keyB) > 0) {
            String tempType = sourceTypeA;
            Long tempId = itemIdA;
            this.sourceTypeA = sourceTypeB;
            this.itemIdA = itemIdB;
            this.sourceTypeB = tempType;
            this.itemIdB = tempId;
        }
    }
}
