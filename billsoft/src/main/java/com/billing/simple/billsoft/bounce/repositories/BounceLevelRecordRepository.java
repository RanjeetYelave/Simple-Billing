package com.billing.simple.billsoft.bounce.repositories;

import com.billing.simple.billsoft.bounce.entities.BounceLevelRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BounceLevelRecordRepository extends JpaRepository<BounceLevelRecord, Long> {
    Optional<BounceLevelRecord> findByLevelNumber(int levelNumber);
    List<BounceLevelRecord> findAllByOrderByLevelNumberAsc();
}
