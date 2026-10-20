package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dtos.CategoryPricingRuleDto;
import com.billing.simple.billsoft.dtos.PricingProfileDto;
import com.billing.simple.billsoft.entities.CategoryPricingRule;
import com.billing.simple.billsoft.entities.PricingProfile;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.repo.CategoryPricingRuleRepository;
import com.billing.simple.billsoft.repo.PricingProfileRepository;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Single Authoritative Backend Pricing Engine for RupeeCRM.
 * Resolves pricing tiers, category adjustments, and effective unit prices.
 */
@Service
public class ProductPricingService {

    private final PricingProfileRepository profileRepo;
    private final CategoryPricingRuleRepository categoryRuleRepo;
    private final ProductRepository productRepo;

    public ProductPricingService(
            PricingProfileRepository profileRepo,
            CategoryPricingRuleRepository categoryRuleRepo,
            ProductRepository productRepo
    ) {
        this.profileRepo = profileRepo;
        this.categoryRuleRepo = categoryRuleRepo;
        this.productRepo = productRepo;
    }

    private Long resolveAuthoritativeFirmId(Long clientFirmId) {
        Long tenantFirmId = TenantContext.getCurrentFirmId();
        if (tenantFirmId != null) {
            if (clientFirmId != null && !tenantFirmId.equals(clientFirmId)) {
                throw new TenantSecurityException("Cross-firm access violation: client (" + clientFirmId + ") != context (" + tenantFirmId + ")");
            }
            return tenantFirmId;
        }
        if (clientFirmId != null && clientFirmId > 0) {
            return clientFirmId;
        }
        throw new TenantSecurityException("Active firm context is required for pricing operations");
    }

    @Transactional(readOnly = true)
    public Long getDefaultProfileId(Long firmId) {
        if (firmId == null || firmId <= 0) return null;
        return profileRepo.findFirstByFirmIdAndIsDefaultTrue(firmId)
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .map(PricingProfile::getId)
                .orElse(null);
    }

    /**
     * Resolves the single authoritative selling price for a product under an optional pricing profile.
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateEffectivePrice(Product product, Long pricingProfileId, Long firmId) {
        if (product == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal basePrice = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
        if (basePrice.compareTo(BigDecimal.ZERO) < 0) {
            basePrice = BigDecimal.ZERO;
        }

        Long targetFirmId = firmId != null ? firmId : product.getFirmId();
        if (targetFirmId == null) {
            targetFirmId = TenantContext.getCurrentFirmId();
        }

        Long effectiveProfileId = pricingProfileId;
        if ((effectiveProfileId == null || effectiveProfileId <= 0) && targetFirmId != null) {
            effectiveProfileId = getDefaultProfileId(targetFirmId);
        }

        if (effectiveProfileId == null || effectiveProfileId <= 0) {
            return basePrice.setScale(2, RoundingMode.HALF_UP);
        }

        Optional<PricingProfile> profileOpt = profileRepo.findByIdAndFirmId(effectiveProfileId, targetFirmId);
        if (profileOpt.isEmpty() || !Boolean.TRUE.equals(profileOpt.get().getIsActive())) {
            return basePrice.setScale(2, RoundingMode.HALF_UP);
        }

        String category = product.getCategory();
        if (category == null || category.trim().isEmpty()) {
            return basePrice.setScale(2, RoundingMode.HALF_UP);
        }

        Optional<CategoryPricingRule> ruleOpt = categoryRuleRepo.findByFirmIdAndCategoryNameIgnoreCaseAndPricingProfileId(
                targetFirmId, category.trim(), effectiveProfileId
        );

        if (ruleOpt.isEmpty()) {
            return basePrice.setScale(2, RoundingMode.HALF_UP);
        }

        CategoryPricingRule rule = ruleOpt.get();
        BigDecimal adjVal = rule.getAdjustmentValue() != null ? rule.getAdjustmentValue() : BigDecimal.ZERO;
        BigDecimal effectivePrice;

        if ("FIXED".equalsIgnoreCase(rule.getAdjustmentType())) {
            effectivePrice = basePrice.add(adjVal);
        } else {
            // PERCENT: effective = base + (base * adjVal / 100)
            BigDecimal pctAdjustment = basePrice.multiply(adjVal)
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            effectivePrice = basePrice.add(pctAdjustment);
        }

        if (effectivePrice.compareTo(BigDecimal.ZERO) < 0) {
            effectivePrice = BigDecimal.ZERO;
        }

        return effectivePrice.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Pre-computes effective prices for all active products of a firm under a pricing profile.
     */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> getEffectivePricesForFirm(Long clientFirmId, Long pricingProfileId) {
        Long firmId = resolveAuthoritativeFirmId(clientFirmId);
        List<Product> products = productRepo.findByFirmId(firmId);
        Map<Long, BigDecimal> result = new LinkedHashMap<>();

        if (products == null || products.isEmpty()) {
            return result;
        }

        for (Product p : products) {
            if (p.getId() != null && (p.getIsArchived() == null || !p.getIsArchived())) {
                BigDecimal effective = calculateEffectivePrice(p, pricingProfileId, firmId);
                result.put(p.getId(), effective);
            }
        }
        return result;
    }

    // ==========================================
    // PRICING PROFILE MANAGEMENT
    // ==========================================

    @Transactional(readOnly = true)
    public List<PricingProfileDto> listProfiles(Long clientFirmId) {
        Long firmId = resolveAuthoritativeFirmId(clientFirmId);
        ensureDefaultProfilesExist(firmId);
        return profileRepo.findByFirmIdOrderByIdAsc(firmId).stream()
                .map(this::toProfileDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void ensureDefaultProfilesExist(Long firmId) {
        if (firmId == null || firmId <= 0) return;
        List<PricingProfile> existing = profileRepo.findByFirmIdOrderByIdAsc(firmId);
        if (existing.isEmpty()) {
            PricingProfile retail = PricingProfile.builder()
                    .firmId(firmId)
                    .name("Retail (Standard)")
                    .code("RETAIL")
                    .description("Standard retail selling price")
                    .isDefault(true)
                    .isActive(true)
                    .build();

            PricingProfile wholesale = PricingProfile.builder()
                    .firmId(firmId)
                    .name("Wholesale")
                    .code("WHOLESALE")
                    .description("Wholesale volume pricing tier")
                    .isDefault(false)
                    .isActive(true)
                    .build();

            PricingProfile custom1 = PricingProfile.builder()
                    .firmId(firmId)
                    .name("Custom Tier 1")
                    .code("CUSTOM_1")
                    .description("Special corporate / partner pricing")
                    .isDefault(false)
                    .isActive(true)
                    .build();

            PricingProfile custom2 = PricingProfile.builder()
                    .firmId(firmId)
                    .name("Custom Tier 2")
                    .code("CUSTOM_2")
                    .description("Promotional / seasonal tier")
                    .isDefault(false)
                    .isActive(true)
                    .build();

            profileRepo.saveAll(List.of(retail, wholesale, custom1, custom2));
        }
    }

    @Transactional
    public PricingProfileDto createProfile(PricingProfileDto dto, Long clientFirmId) {
        Long firmId = resolveAuthoritativeFirmId(clientFirmId);
        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Pricing profile name is required");
        }
        String code = dto.getCode() != null && !dto.getCode().trim().isEmpty()
                ? dto.getCode().trim().toUpperCase()
                : dto.getName().trim().toUpperCase().replaceAll("[^A-Z0-9_]", "_");

        if (profileRepo.existsByFirmIdAndCode(firmId, code)) {
            throw new IllegalArgumentException("A pricing profile with code '" + code + "' already exists for this firm");
        }

        if (Boolean.TRUE.equals(dto.getIsDefault())) {
            profileRepo.findFirstByFirmIdAndIsDefaultTrue(firmId).ifPresent(currDef -> {
                currDef.setIsDefault(false);
                profileRepo.save(currDef);
            });
        }

        PricingProfile profile = PricingProfile.builder()
                .firmId(firmId)
                .name(dto.getName().trim())
                .code(code)
                .description(dto.getDescription())
                .isDefault(Boolean.TRUE.equals(dto.getIsDefault()))
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();

        PricingProfile saved = profileRepo.save(profile);
        return toProfileDto(saved);
    }

    @Transactional
    public PricingProfileDto updateProfile(Long id, PricingProfileDto dto, Long clientFirmId) {
        Long firmId = resolveAuthoritativeFirmId(clientFirmId);
        PricingProfile existing = profileRepo.findByIdAndFirmId(id, firmId)
                .orElseThrow(() -> new IllegalArgumentException("Pricing profile #" + id + " not found for this firm"));

        if (dto.getVersion() != null) {
            if (!dto.getVersion().equals(existing.getVersion())) {
                throw new org.springframework.orm.ObjectOptimisticLockingFailureException(PricingProfile.class, id);
            }
            existing.setVersion(dto.getVersion());
        }

        if (dto.getName() != null && !dto.getName().trim().isEmpty()) {
            existing.setName(dto.getName().trim());
        }
        if (dto.getCode() != null && !dto.getCode().trim().isEmpty()) {
            String newCode = dto.getCode().trim().toUpperCase();
            if (profileRepo.existsByFirmIdAndCodeAndIdNot(firmId, newCode, id)) {
                throw new IllegalArgumentException("A pricing profile with code '" + newCode + "' already exists");
            }
            existing.setCode(newCode);
        }
        if (dto.getDescription() != null) {
            existing.setDescription(dto.getDescription());
        }
        if (dto.getIsActive() != null) {
            existing.setIsActive(dto.getIsActive());
        }
        if (Boolean.TRUE.equals(dto.getIsDefault()) && !Boolean.TRUE.equals(existing.getIsDefault())) {
            profileRepo.findFirstByFirmIdAndIsDefaultTrue(firmId).ifPresent(currDef -> {
                if (!currDef.getId().equals(existing.getId())) {
                    currDef.setIsDefault(false);
                    profileRepo.save(currDef);
                }
            });
            existing.setIsDefault(true);
        } else if (Boolean.FALSE.equals(dto.getIsDefault())) {
            existing.setIsDefault(false);
        }

        PricingProfile saved = profileRepo.save(existing);
        return toProfileDto(saved);
    }

    @Transactional
    public void deleteProfile(Long id, Long clientFirmId) {
        Long firmId = resolveAuthoritativeFirmId(clientFirmId);
        PricingProfile existing = profileRepo.findByIdAndFirmId(id, firmId)
                .orElseThrow(() -> new IllegalArgumentException("Pricing profile #" + id + " not found for this firm"));

        categoryRuleRepo.deleteByFirmIdAndPricingProfileId(firmId, id);
        profileRepo.delete(existing);
    }

    // ==========================================
    // CATEGORY PRICING RULES MATRIX
    // ==========================================

    @Transactional(readOnly = true)
    public List<CategoryPricingRuleDto> getCategoryRules(Long clientFirmId) {
        Long firmId = resolveAuthoritativeFirmId(clientFirmId);
        List<CategoryPricingRule> rules = categoryRuleRepo.findByFirmId(firmId);
        Map<Long, PricingProfile> profileMap = profileRepo.findByFirmIdOrderByIdAsc(firmId).stream()
                .collect(Collectors.toMap(PricingProfile::getId, p -> p, (a, b) -> a));

        return rules.stream().map(r -> {
            PricingProfile p = profileMap.get(r.getPricingProfileId());
            return CategoryPricingRuleDto.builder()
                    .id(r.getId())
                    .firmId(r.getFirmId())
                    .categoryName(r.getCategoryName())
                    .pricingProfileId(r.getPricingProfileId())
                    .pricingProfileCode(p != null ? p.getCode() : null)
                    .pricingProfileName(p != null ? p.getName() : null)
                    .adjustmentType(r.getAdjustmentType())
                    .adjustmentValue(r.getAdjustmentValue())
                    .build();
        }).collect(Collectors.toList());
    }

    @Transactional
    public List<CategoryPricingRuleDto> saveCategoryRules(Long clientFirmId, List<CategoryPricingRuleDto> rulesDto) {
        Long firmId = resolveAuthoritativeFirmId(clientFirmId);
        if (rulesDto == null) {
            return Collections.emptyList();
        }

        List<CategoryPricingRule> toSave = new ArrayList<>();
        for (CategoryPricingRuleDto dto : rulesDto) {
            if (dto.getCategoryName() == null || dto.getCategoryName().trim().isEmpty() || dto.getPricingProfileId() == null) {
                continue;
            }
            String cat = dto.getCategoryName().trim();
            Long profileId = dto.getPricingProfileId();

            // Verify profile belongs to this firm
            PricingProfile profile = profileRepo.findByIdAndFirmId(profileId, firmId)
                    .orElseThrow(() -> new TenantSecurityException("Pricing profile #" + profileId + " does not belong to firm #" + firmId));

            Optional<CategoryPricingRule> existing = categoryRuleRepo.findByFirmIdAndCategoryNameIgnoreCaseAndPricingProfileId(
                    firmId, cat, profileId
            );

            BigDecimal adjVal = dto.getAdjustmentValue() != null ? dto.getAdjustmentValue() : BigDecimal.ZERO;
            String adjType = dto.getAdjustmentType() != null ? dto.getAdjustmentType().trim().toUpperCase() : "PERCENT";

            CategoryPricingRule rule = existing.orElseGet(() -> CategoryPricingRule.builder()
                    .firmId(firmId)
                    .categoryName(cat)
                    .pricingProfileId(profileId)
                    .build());

            rule.setAdjustmentType(adjType);
            rule.setAdjustmentValue(adjVal);
            toSave.add(rule);
        }

        categoryRuleRepo.saveAll(toSave);
        return getCategoryRules(firmId);
    }

    private PricingProfileDto toProfileDto(PricingProfile p) {
        return PricingProfileDto.builder()
                .id(p.getId())
                .firmId(p.getFirmId())
                .name(p.getName())
                .code(p.getCode())
                .description(p.getDescription())
                .isDefault(p.getIsDefault())
                .isActive(p.getIsActive())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .version(p.getVersion())
                .build();
    }
}
