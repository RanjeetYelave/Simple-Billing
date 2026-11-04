package com.billing.simple.billsoft.bounce.repositories;

import com.billing.simple.billsoft.bounce.entities.BounceCampaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BounceCampaignRepository extends JpaRepository<BounceCampaign, Long> {
    Optional<BounceCampaign> findFirstByCampaignStatusOrderByUpdatedAtDesc(String campaignStatus);
}
