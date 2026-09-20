package com.billing.simple.billsoft.repo;

import com.billing.simple.billsoft.entities.SavingRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SavingRepository extends JpaRepository<SavingRecord, Long> {

    List<SavingRecord> findByFirmIdOrderBySavingDateDescIdDesc(Long firmId);

    Page<SavingRecord> findByFirmId(Long firmId, Pageable pageable);

    Page<SavingRecord> findByFirmIdAndSavingDateBetween(Long firmId, LocalDate from, LocalDate to, Pageable pageable);

    List<SavingRecord> findByFirmIdAndSavingDateBetweenOrderBySavingDateDescIdDesc(Long firmId, LocalDate from, LocalDate to);

    List<SavingRecord> findByFirmIdAndGoalId(Long firmId, Long goalId);

    List<SavingRecord> findByGoalId(Long goalId);

    List<SavingRecord> findTop10ByFirmIdOrderBySavingDateDescIdDesc(Long firmId);

    Optional<SavingRecord> findByIdAndFirmId(Long id, Long firmId);

    boolean existsByIdAndFirmId(Long id, Long firmId);

    void deleteByIdAndFirmId(Long id, Long firmId);

    long countByFirmId(Long firmId);

    @Modifying
    @Query("UPDATE SavingRecord s SET s.goalId = null WHERE s.firmId = :firmId AND s.goalId = :goalId")
    void clearGoalIdByFirmIdAndGoalId(@Param("firmId") Long firmId, @Param("goalId") Long goalId);
}
