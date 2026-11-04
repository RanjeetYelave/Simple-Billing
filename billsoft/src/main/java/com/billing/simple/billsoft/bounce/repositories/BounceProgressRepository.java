package com.billing.simple.billsoft.bounce.repositories;

import com.billing.simple.billsoft.bounce.entities.BounceProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BounceProgressRepository extends JpaRepository<BounceProgress, Long> {
    Optional<BounceProgress> findByCampaignId(Long campaignId);
    void deleteByCampaignId(Long campaignId);
}
