package com.billing.simple.billsoft.repo;

import com.billing.simple.billsoft.entities.PromotionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PromotionRecordRepository extends JpaRepository<PromotionRecord, Long> {
    List<PromotionRecord> findByEmployeeIdOrderByEffectiveDateDesc(Long employeeId);
    List<PromotionRecord> findByIsAppliedFalse();
    void deleteByEmployeeId(Long employeeId);
}
