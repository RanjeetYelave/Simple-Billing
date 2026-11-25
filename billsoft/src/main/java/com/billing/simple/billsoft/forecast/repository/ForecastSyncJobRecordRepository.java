package com.billing.simple.billsoft.forecast.repository;

import com.billing.simple.billsoft.forecast.entity.ForecastSyncJobRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ForecastSyncJobRecordRepository extends JpaRepository<ForecastSyncJobRecord, Long> {

    Optional<ForecastSyncJobRecord> findTopByFirmIdOrderByStartedAtDesc(Long firmId);

    Optional<ForecastSyncJobRecord> findTopByFirmIdAndStatusOrderByCompletedAtDesc(Long firmId, String status);
}
