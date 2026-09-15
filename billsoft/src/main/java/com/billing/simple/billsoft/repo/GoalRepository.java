package com.billing.simple.billsoft.repo;

import com.billing.simple.billsoft.entities.Goal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GoalRepository extends JpaRepository<Goal, Long> {

    List<Goal> findByFirmIdOrderByCreatedAtDesc(Long firmId);

    List<Goal> findByFirmIdAndStatusOrderByCreatedAtDesc(Long firmId, String status);

    Optional<Goal> findByIdAndFirmId(Long id, Long firmId);

    boolean existsByIdAndFirmId(Long id, Long firmId);

    void deleteByIdAndFirmId(Long id, Long firmId);

    long countByFirmId(Long firmId);

    long countByFirmIdAndStatus(Long firmId, String status);
}
