package com.billing.simple.billsoft.forecast.repository;

import com.billing.simple.billsoft.forecast.entity.ForecastModelRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ForecastModelRunRepository extends JpaRepository<ForecastModelRun, Long> {

    Optional<ForecastModelRun> findTopByFirmIdAndProductIdOrderByRunTimestampDesc(Long firmId, Long productId);

    List<ForecastModelRun> findAllByFirmIdAndProductIdOrderByRunTimestampDesc(Long firmId, Long productId);

    void deleteAllByFirmIdAndProductId(Long firmId, Long productId);
}
