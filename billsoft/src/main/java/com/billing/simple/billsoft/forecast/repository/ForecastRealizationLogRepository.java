package com.billing.simple.billsoft.forecast.repository;

import com.billing.simple.billsoft.forecast.entity.ForecastRealizationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ForecastRealizationLogRepository extends JpaRepository<ForecastRealizationLog, Long> {

    List<ForecastRealizationLog> findByFirmIdAndProductIdOrderByTargetDateAsc(Long firmId, Long productId);

    List<ForecastRealizationLog> findByFirmIdAndTargetDateAndEvaluationStatus(Long firmId, LocalDate targetDate, String status);

    List<ForecastRealizationLog> findByFirmIdAndProductIdAndTargetDate(Long firmId, Long productId, LocalDate targetDate);

    Optional<ForecastRealizationLog> findTopByFirmIdAndProductIdAndTargetDateAndOriginDate(
            Long firmId, Long productId, LocalDate targetDate, LocalDate originDate);

    @Query("SELECT " +
           "AVG(l.absoluteError), " +
           "SUM(l.absoluteError), " +
           "SUM(l.actualDemand), " +
           "AVG(l.predictedDemand - l.actualDemand), " +
           "COUNT(l.id) " +
           "FROM ForecastRealizationLog l " +
           "WHERE l.firmId = :firmId AND l.productId = :productId AND l.evaluationStatus = 'EVALUATED'")
    List<Object[]> computeAccuracyMetricsForProduct(
            @Param("firmId") Long firmId,
            @Param("productId") Long productId);

    @Query("SELECT " +
           "AVG(l.absoluteError), " +
           "SUM(l.absoluteError), " +
           "SUM(l.actualDemand), " +
           "AVG(l.predictedDemand - l.actualDemand), " +
           "COUNT(l.id) " +
           "FROM ForecastRealizationLog l " +
           "WHERE l.firmId = :firmId AND l.evaluationStatus = 'EVALUATED'")
    List<Object[]> computeAccuracyMetricsForFirm(@Param("firmId") Long firmId);

    @Modifying
    @Query("DELETE FROM ForecastRealizationLog l WHERE l.firmId = :firmId AND l.productId = :productId")
    void deleteAllByFirmIdAndProductId(@Param("firmId") Long firmId, @Param("productId") Long productId);
}
