package com.billing.simple.billsoft.forecast.service;

import com.billing.simple.billsoft.forecast.config.ForecastModuleProperties;
import com.billing.simple.billsoft.forecast.dto.ForecastConfigDto;
import com.billing.simple.billsoft.forecast.entity.ForecastProduct;
import com.billing.simple.billsoft.forecast.repository.ForecastProductRepository;
import com.billing.simple.billsoft.repo.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Manages product-level replenishment and forecasting parameters with fallback hierarchy:
 * Product Override -> Firm Default -> Technical Fallback
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForecastProductConfigService {

    private final ForecastProductRepository configRepo;
    private final ProductRepository productRepo;
    private final ForecastModuleProperties properties;

    @Transactional(readOnly = true)
    public ForecastProduct getOrCreateConfig(Long firmId, Long productId) {
        return configRepo.findByFirmIdAndProductId(firmId, productId)
                .orElseGet(() -> ForecastProduct.builder()
                        .firmId(firmId)
                        .productId(productId)
                        .forecastEnabled(true)
                        .leadTimeDays(properties.getDefaultLeadTimeDays())
                        .reviewPeriodDays(properties.getDefaultReviewPeriodDays())
                        .minimumOrderQty(properties.getDefaultMoq())
                        .packSize(properties.getDefaultPackSize())
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build());
    }

    @Transactional(readOnly = true)
    public ForecastConfigDto getConfigDto(Long firmId, Long productId) {
        productRepo.findByIdAndFirmId(productId, firmId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
        ForecastProduct p = getOrCreateConfig(firmId, productId);
        return ForecastConfigDto.builder()
                .productId(productId)
                .forecastEnabled(p.getForecastEnabled())
                .leadTimeDays(p.getLeadTimeDays() != null ? p.getLeadTimeDays() : properties.getDefaultLeadTimeDays())
                .reviewPeriodDays(p.getReviewPeriodDays() != null ? p.getReviewPeriodDays() : properties.getDefaultReviewPeriodDays())
                .minimumOrderQty(p.getMinimumOrderQty() != null ? p.getMinimumOrderQty() : properties.getDefaultMoq())
                .packSize(p.getPackSize() != null ? p.getPackSize() : properties.getDefaultPackSize())
                .safetyStockOverride(p.getSafetyStockOverride())
                .modelOverride(p.getModelOverride())
                .build();
    }

    @Transactional
    public ForecastConfigDto updateConfig(Long firmId, Long productId, ForecastConfigDto dto) {
        productRepo.findByIdAndFirmId(productId, firmId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
        ForecastProduct p = configRepo.findByFirmIdAndProductId(firmId, productId)
                .orElseGet(() -> ForecastProduct.builder()
                        .firmId(firmId)
                        .productId(productId)
                        .build());

        if (dto.getForecastEnabled() != null) p.setForecastEnabled(dto.getForecastEnabled());
        if (dto.getLeadTimeDays() != null) p.setLeadTimeDays(Math.max(1, dto.getLeadTimeDays()));
        if (dto.getReviewPeriodDays() != null) p.setReviewPeriodDays(Math.max(1, dto.getReviewPeriodDays()));
        if (dto.getMinimumOrderQty() != null) p.setMinimumOrderQty(dto.getMinimumOrderQty().max(BigDecimal.ZERO));
        if (dto.getPackSize() != null) p.setPackSize(dto.getPackSize().max(BigDecimal.ONE));
        p.setSafetyStockOverride(dto.getSafetyStockOverride());
        p.setModelOverride(dto.getModelOverride());
        p.setUpdatedAt(LocalDateTime.now());

        ForecastProduct saved = configRepo.save(p);
        log.info("Updated forecast configuration for product {} in firm {}", productId, firmId);

        return ForecastConfigDto.builder()
                .productId(productId)
                .forecastEnabled(saved.getForecastEnabled())
                .leadTimeDays(saved.getLeadTimeDays())
                .reviewPeriodDays(saved.getReviewPeriodDays())
                .minimumOrderQty(saved.getMinimumOrderQty())
                .packSize(saved.getPackSize())
                .safetyStockOverride(saved.getSafetyStockOverride())
                .modelOverride(saved.getModelOverride())
                .build();
    }
}
