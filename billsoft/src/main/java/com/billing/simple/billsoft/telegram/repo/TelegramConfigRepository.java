package com.billing.simple.billsoft.telegram.repo;

import com.billing.simple.billsoft.telegram.entities.TelegramConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TelegramConfigRepository extends JpaRepository<TelegramConfig, Long> {
}
