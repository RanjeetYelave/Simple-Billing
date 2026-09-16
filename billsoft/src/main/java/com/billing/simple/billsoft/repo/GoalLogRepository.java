package com.billing.simple.billsoft.repo;

import com.billing.simple.billsoft.entities.GoalLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GoalLogRepository extends JpaRepository<GoalLog, Long> {

    List<GoalLog> findByGoalIdAndFirmIdOrderByLogDateAscCreatedAtAsc(Long goalId, Long firmId);

    List<GoalLog> findByGoalIdOrderByLogDateAscCreatedAtAsc(Long goalId);

    List<GoalLog> findByFirmIdOrderByLogDateAscCreatedAtAsc(Long firmId);

    List<GoalLog> findAllByOrderByLogDateAscCreatedAtAsc();

    void deleteByGoalIdAndFirmId(Long goalId, Long firmId);

    void deleteByGoalId(Long goalId);

    void deleteByFirmId(Long firmId);
}
