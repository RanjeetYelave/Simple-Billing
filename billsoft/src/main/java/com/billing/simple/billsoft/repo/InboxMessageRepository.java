package com.billing.simple.billsoft.repo;

import com.billing.simple.billsoft.entities.InboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InboxMessageRepository extends JpaRepository<InboxMessage, Long> {

    List<InboxMessage> findByFirmIdOrderByCreatedAtDesc(Long firmId);

    List<InboxMessage> findByFirmIdAndIsReadFalse(Long firmId);

    Optional<InboxMessage> findByIdAndFirmId(Long id, Long firmId);

    boolean existsByIdAndFirmId(Long id, Long firmId);

    void deleteByIdAndFirmId(Long id, Long firmId);

    long countByFirmId(Long firmId);

    boolean existsByFirmIdAndSubjectStartingWithAndCreatedAtAfter(Long firmId, String subjectPrefix, LocalDateTime afterDate);
}

