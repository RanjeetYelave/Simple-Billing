package com.billing.simple.billsoft.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.billing.simple.billsoft.entities.AppConfig;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface AppConfigRepository extends JpaRepository<AppConfig, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM AppConfig c WHERE c.configKey = :configKey")
    Optional<AppConfig> findByConfigKeyForUpdate(@Param("configKey") String configKey);
}
