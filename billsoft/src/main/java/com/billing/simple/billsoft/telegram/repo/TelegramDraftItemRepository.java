package com.billing.simple.billsoft.telegram.repo;

import com.billing.simple.billsoft.telegram.entities.TelegramDraftItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TelegramDraftItemRepository extends JpaRepository<TelegramDraftItem, Long> {

    List<TelegramDraftItem> findByDraftIdOrderByItemOrderAsc(Long draftId);

    void deleteByDraftId(Long draftId);
}
