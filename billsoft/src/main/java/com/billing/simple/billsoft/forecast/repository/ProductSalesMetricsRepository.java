package com.billing.simple.billsoft.forecast.repository;

import com.billing.simple.billsoft.forecast.entity.ProductSalesMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductSalesMetricsRepository extends JpaRepository<ProductSalesMetrics, Long> {

    Optional<ProductSalesMetrics> findByFirmIdAndProductIdAndMetricDate(Long firmId, Long productId, LocalDate metricDate);

    List<ProductSalesMetrics> findAllByFirmIdAndProductIdAndMetricDateBetweenOrderByMetricDateAsc(
            Long firmId, Long productId, LocalDate startDate, LocalDate endDate);

    List<ProductSalesMetrics> findAllByFirmIdAndProductIdOrderByMetricDateAsc(Long firmId, Long productId);

    @Query("SELECT " +
           "SUM(m.grossRequestedQty), " +
           "SUM(m.grossFulfilledQty), " +
           "SUM(m.shortageQty), " +
           "SUM(m.returnedQty), " +
           "SUM(m.netRequestedQty), " +
           "SUM(m.grossSales), " +
           "SUM(m.refundAmount), " +
           "SUM(m.netSales), " +
           "MIN(m.minRealizedPrice), " +
           "MAX(m.maxRealizedPrice), " +
           "MAX(m.metricDate) " +
           "FROM ProductSalesMetrics m " +
           "WHERE m.firmId = :firmId AND m.productId = :productId")
    List<Object[]> getHistoricalInsightSummaryForProduct(
            @Param("firmId") Long firmId,
            @Param("productId") Long productId);

    @Query("SELECT COUNT(DISTINCT t.customerId) FROM ForecastSalesTransaction t " +
           "WHERE t.firmId = :firmId AND t.productId = :productId AND t.customerId IS NOT NULL")
    Integer countDistinctLifetimeCustomersForProduct(
            @Param("firmId") Long firmId,
            @Param("productId") Long productId);

    void deleteAllByFirmIdAndProductId(Long firmId, Long productId);
}
