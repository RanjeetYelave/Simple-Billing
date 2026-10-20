package com.billing.simple.billsoft.forecast.repository;

import com.billing.simple.billsoft.forecast.entity.ForecastSalesTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ForecastSalesTransactionRepository extends JpaRepository<ForecastSalesTransaction, Long> {

    @Modifying
    @Query("DELETE FROM ForecastSalesTransaction t WHERE t.firmId = :firmId AND t.sourceType = :sourceType AND t.sourceHeaderId = :sourceHeaderId")
    void deleteAllByFirmIdAndSourceTypeAndSourceHeaderId(
            @Param("firmId") Long firmId,
            @Param("sourceType") String sourceType,
            @Param("sourceHeaderId") Long sourceHeaderId);

    boolean existsByFirmIdAndSourceTypeAndSourceHeaderIdAndSourceLineOrdinal(
            Long firmId, String sourceType, Long sourceHeaderId, Integer sourceLineOrdinal);

    List<ForecastSalesTransaction> findAllByFirmIdAndProductIdAndTransactionDateBetweenOrderByTransactionDateAsc(
            Long firmId, Long productId, LocalDate startDate, LocalDate endDate);

    List<ForecastSalesTransaction> findAllByFirmIdAndTransactionDateBetween(
            Long firmId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT DISTINCT t.transactionDate FROM ForecastSalesTransaction t " +
           "WHERE t.firmId = :firmId AND t.sourceUpdatedAt >= :since")
    List<LocalDate> findDistinctTransactionDatesModifiedSince(
            @Param("firmId") Long firmId,
            @Param("since") LocalDateTime since);

    @Query("SELECT " +
           "SUM(CASE WHEN t.sourceType = 'SALE' THEN t.requestedQty ELSE 0 END), " +
           "SUM(CASE WHEN t.sourceType = 'SALE' THEN t.fulfilledQty ELSE 0 END), " +
           "SUM(CASE WHEN t.sourceType = 'SALE' THEN t.shortageQty ELSE 0 END), " +
           "SUM(CASE WHEN t.sourceType = 'RETURN' THEN t.requestedQty ELSE 0 END), " +
           "SUM(CASE WHEN t.sourceType = 'SALE' THEN t.lineTotal ELSE 0 END), " +
           "SUM(CASE WHEN t.sourceType = 'RETURN' THEN t.lineTotal ELSE 0 END), " +
           "MIN(CASE WHEN t.sourceType = 'SALE' THEN t.unitPrice ELSE NULL END), " +
           "MAX(CASE WHEN t.sourceType = 'SALE' THEN t.unitPrice ELSE NULL END), " +
           "COUNT(DISTINCT t.customerId) " +
           "FROM ForecastSalesTransaction t " +
           "WHERE t.firmId = :firmId AND t.productId = :productId AND t.transactionDate = :metricDate")
    List<Object[]> aggregateMetricsForProductAndDate(
            @Param("firmId") Long firmId,
            @Param("productId") Long productId,
            @Param("metricDate") LocalDate metricDate);

    @Query("SELECT DISTINCT t.productId FROM ForecastSalesTransaction t " +
           "WHERE t.firmId = :firmId AND t.productId IS NOT NULL")
    List<Long> findDistinctProductIdsWithTransactions(@Param("firmId") Long firmId);

    @Modifying
    @Query("DELETE FROM ForecastSalesTransaction t WHERE t.firmId = :firmId AND t.sourceType = 'SALE' " +
           "AND t.sourceHeaderId NOT IN (SELECT i.id FROM Invoice i WHERE i.firmId = :firmId)")
    int purgeOrphanedSaleTransactionsForDeletedInvoices(@Param("firmId") Long firmId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ForecastSalesTransaction t SET t.productId = :primaryId WHERE t.firmId = :firmId AND t.productId = :secondaryId")
    int migrateTransactionsToCanonical(
            @Param("firmId") Long firmId,
            @Param("primaryId") Long primaryId,
            @Param("secondaryId") Long secondaryId);
}
