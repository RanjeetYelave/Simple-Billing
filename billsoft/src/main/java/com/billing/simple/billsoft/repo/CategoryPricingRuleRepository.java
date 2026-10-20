package com.billing.simple.billsoft.repo;

import com.billing.simple.billsoft.entities.CategoryPricingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryPricingRuleRepository extends JpaRepository<CategoryPricingRule, Long> {

    List<CategoryPricingRule> findByFirmId(Long firmId);

    List<CategoryPricingRule> findByFirmIdAndPricingProfileId(Long firmId, Long pricingProfileId);

    List<CategoryPricingRule> findByFirmIdAndCategoryNameIgnoreCase(Long firmId, String categoryName);

    Optional<CategoryPricingRule> findByFirmIdAndCategoryNameIgnoreCaseAndPricingProfileId(Long firmId, String categoryName, Long pricingProfileId);

    void deleteByFirmIdAndPricingProfileId(Long firmId, Long pricingProfileId);

    void deleteByFirmIdAndCategoryNameIgnoreCase(Long firmId, String categoryName);
}
