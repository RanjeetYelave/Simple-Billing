package com.billing.simple.billsoft.bounce.repositories;

import com.billing.simple.billsoft.bounce.entities.BounceSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BounceSettingsRepository extends JpaRepository<BounceSettings, Long> {
}
