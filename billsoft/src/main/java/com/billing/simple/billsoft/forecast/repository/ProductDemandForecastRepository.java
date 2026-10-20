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

    void deleteAllByFirmIdAndProductId(Long firmId, Long productId);

    void deleteAllByModelRunId(Long modelRunId);
}
