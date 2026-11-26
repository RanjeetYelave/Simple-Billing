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

    @org.springframework.data.jpa.repository.Query("SELECT r.productId, r.modelName FROM ForecastModelRun r " +
           "WHERE r.firmId = :firmId AND r.id IN (" +
           "  SELECT MAX(m.id) FROM ForecastModelRun m WHERE m.firmId = :firmId GROUP BY m.productId" +
           ")")
    List<Object[]> findLatestModelNamesByFirm(@org.springframework.data.repository.query.Param("firmId") Long firmId);

    void deleteAllByFirmIdAndProductId(Long firmId, Long productId);
}
