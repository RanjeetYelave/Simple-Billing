package com.billing.simple.billsoft.forecast.repository;

import com.billing.simple.billsoft.forecast.entity.ProductRestockRecommendation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRestockRecommendationRepository extends JpaRepository<ProductRestockRecommendation, Long> {

    Optional<ProductRestockRecommendation> findTopByFirmIdAndProductIdAndRecommendationStatusOrderByGeneratedAtDesc(
            Long firmId, Long productId, String recommendationStatus);

    Optional<ProductRestockRecommendation> findTopByFirmIdAndProductIdOrderByGeneratedAtDesc(
            Long firmId, Long productId);

    Page<ProductRestockRecommendation> findAllByFirmIdAndRecommendationStatus(
            Long firmId, String recommendationStatus, Pageable pageable);

    Page<ProductRestockRecommendation> findAllByFirmIdAndRecommendationStatusAndUrgencyStatus(
            Long firmId, String recommendationStatus, String urgencyStatus, Pageable pageable);

    List<ProductRestockRecommendation> findAllByFirmIdAndRecommendationStatus(
            Long firmId, String recommendationStatus);

    void deleteAllByFirmIdAndProductId(Long firmId, Long productId);
}
