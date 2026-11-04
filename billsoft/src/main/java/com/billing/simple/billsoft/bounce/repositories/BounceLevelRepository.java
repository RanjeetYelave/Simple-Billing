package com.billing.simple.billsoft.bounce.repositories;

import com.billing.simple.billsoft.bounce.entities.BounceLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BounceLevelRepository extends JpaRepository<BounceLevel, Long> {
    Optional<BounceLevel> findByCampaignIdAndLevelNumber(Long campaignId, int levelNumber);
    Optional<BounceLevel> findFirstByLevelNumberOrderByCreatedAtDesc(int levelNumber);
    List<BounceLevel> findByCampaignIdOrderByLevelNumberAsc(Long campaignId);
    void deleteByCampaignId(Long campaignId);
}
