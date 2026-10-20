package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.dtos.CategoryPricingRuleDto;
import com.billing.simple.billsoft.dtos.EffectivePriceResponseDto;
import com.billing.simple.billsoft.dtos.PricingProfileDto;
import com.billing.simple.billsoft.service.ProductPricingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PricingRuleController {

    private final ProductPricingService pricingService;

    public PricingRuleController(ProductPricingService pricingService) {
        this.pricingService = pricingService;
    }

    // ==========================================
    // PRICING PROFILES
    // ==========================================

    @GetMapping("/pricing-profiles")
    public ResponseEntity<List<PricingProfileDto>> listProfiles(@RequestParam(required = false) Long firmId) {
        return ResponseEntity.ok(pricingService.listProfiles(firmId));
    }

    @PostMapping("/pricing-profiles")
    public ResponseEntity<PricingProfileDto> createProfile(
            @RequestBody PricingProfileDto dto,
            @RequestParam(required = false) Long firmId
    ) {
        return ResponseEntity.ok(pricingService.createProfile(dto, firmId));
    }

    @PutMapping("/pricing-profiles/{id}")
    public ResponseEntity<PricingProfileDto> updateProfile(
            @PathVariable Long id,
            @RequestBody PricingProfileDto dto,
            @RequestParam(required = false) Long firmId
    ) {
        return ResponseEntity.ok(pricingService.updateProfile(id, dto, firmId));
    }

    @DeleteMapping("/pricing-profiles/{id}")
    public ResponseEntity<Void> deleteProfile(
            @PathVariable Long id,
            @RequestParam(required = false) Long firmId
    ) {
        pricingService.deleteProfile(id, firmId);
        return ResponseEntity.noContent().build();
    }

    // ==========================================
    // CATEGORY PRICING RULES
    // ==========================================

    @GetMapping("/pricing-rules/categories")
    public ResponseEntity<List<CategoryPricingRuleDto>> getCategoryRules(@RequestParam(required = false) Long firmId) {
        return ResponseEntity.ok(pricingService.getCategoryRules(firmId));
    }

    @PutMapping("/pricing-rules/categories")
    public ResponseEntity<List<CategoryPricingRuleDto>> saveCategoryRules(
            @RequestBody List<CategoryPricingRuleDto> rules,
            @RequestParam(required = false) Long firmId
    ) {
        return ResponseEntity.ok(pricingService.saveCategoryRules(firmId, rules));
    }

    // ==========================================
    // EFFECTIVE PRICING QUERY
    // ==========================================

    @GetMapping("/products/effective-prices")
    public ResponseEntity<EffectivePriceResponseDto> getEffectivePrices(
            @RequestParam(required = false) Long profileId,
            @RequestParam(required = false) Long firmId
    ) {
        Map<Long, BigDecimal> map = pricingService.getEffectivePricesForFirm(firmId, profileId);
        return ResponseEntity.ok(EffectivePriceResponseDto.builder()
                .pricingProfileId(profileId)
                .effectivePrices(map)
                .build());
    }
}
