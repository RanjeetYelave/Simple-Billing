package com.billing.simple.billsoft.repo;

import com.billing.simple.billsoft.entities.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    Optional<NotificationPreference> findByFirmId(Long firmId);

    boolean existsByFirmId(Long firmId);
}
