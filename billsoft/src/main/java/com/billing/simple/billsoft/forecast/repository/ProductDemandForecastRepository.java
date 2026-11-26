package com.billing.simple.billsoft.forecast.repository;

import com.billing.simple.billsoft.forecast.entity.ProductDemandForecast;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ProductDemandForecastRepository extends JpaRepository<ProductDemandForecast, Long> {

    List<ProductDemandForecast> findAllByFirmIdAndProductIdAndModelRunIdOrderByForecastDateAsc(
            Long firmId, Long productId, Long modelRunId);

    List<ProductDemandForecast> findAllByFirmIdAndProductIdAndModelRunIdAndForecastDateBetweenOrderByForecastDateAsc(
            Long firmId, Long productId, Long modelRunId, LocalDate startDate, LocalDate endDate);

    @org.springframework.data.jpa.repository.Query("SELECT f.productId, SUM(f.predictedDemand), MAX(f.confidenceScore), MAX(f.confidenceLevel) " +
           "FROM ProductDemandForecast f " +
           "WHERE f.firmId = :firmId AND f.forecastDate BETWEEN :startDate AND :endDate " +
           "GROUP BY f.productId")
    List<Object[]> sumForecastByProductBetween(
            @org.springframework.data.repository.query.Param("firmId") Long firmId,
            @org.springframework.data.repository.query.Param("startDate") LocalDate startDate,
            @org.springframework.data.repository.query.Param("endDate") LocalDate endDate);

    void deleteAllByFirmIdAndProductId(Long firmId, Long productId);

    void deleteAllByModelRunId(Long modelRunId);
}
