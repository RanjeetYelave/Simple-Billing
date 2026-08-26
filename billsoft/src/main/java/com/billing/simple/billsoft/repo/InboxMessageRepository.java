package com.billing.simple.billsoft.repo;

import com.billing.simple.billsoft.entities.InboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InboxMessageRepository extends JpaRepository<InboxMessage, Long> {
    List<InboxMessage> findByFirmIdOrderByCreatedAtDesc(Long firmId);
    List<InboxMessage> findByFirmIdAndIsReadFalse(Long firmId);
}
