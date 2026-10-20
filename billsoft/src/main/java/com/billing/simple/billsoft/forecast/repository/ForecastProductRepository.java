package com.billing.simple.billsoft.forecast.repository;

import com.billing.simple.billsoft.forecast.entity.ForecastProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ForecastProductRepository extends JpaRepository<ForecastProduct, Long> {

    Optional<ForecastProduct> findByFirmIdAndProductId(Long firmId, Long productId);

    List<ForecastProduct> findAllByFirmIdAndForecastEnabledTrue(Long firmId);

    List<ForecastProduct> findAllByFirmId(Long firmId);

    default List<ForecastProduct> findByFirmId(Long firmId) {
        return findAllByFirmId(firmId);
    }

    void deleteByFirmIdAndProductId(Long firmId, Long productId);
}
