package com.billing.simple.billsoft.repo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.billing.simple.billsoft.entities.ItemMergeAudit;

@Repository
public interface ItemMergeAuditRepository extends JpaRepository<ItemMergeAudit, Long> {

    List<ItemMergeAudit> findByFirmIdOrderByMergedAtDesc(Long firmId);
}
