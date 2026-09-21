package com.billing.simple.billsoft.repo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.billing.simple.billsoft.entities.ItemDuplicateDismissal;

@Repository
public interface ItemDuplicateDismissalRepository extends JpaRepository<ItemDuplicateDismissal, Long> {

    List<ItemDuplicateDismissal> findByFirmId(Long firmId);

    @Query("SELECT COUNT(d) > 0 FROM ItemDuplicateDismissal d WHERE d.firmId = :firmId AND " +
           "((d.sourceTypeA = :typeA AND d.itemIdA = :idA AND d.sourceTypeB = :typeB AND d.itemIdB = :idB) OR " +
           " (d.sourceTypeA = :typeB AND d.itemIdA = :idB AND d.sourceTypeB = :typeA AND d.itemIdB = :idA))")
    boolean isDismissed(@Param("firmId") Long firmId,
                        @Param("typeA") String typeA,
                        @Param("idA") Long idA,
                        @Param("typeB") String typeB,
                        @Param("idB") Long idB);
}
