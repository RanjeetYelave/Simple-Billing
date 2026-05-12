package com.billing.simple.billsoft.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import com.billing.simple.billsoft.entities.AppConfig;

public interface AppConfigRepository extends JpaRepository<AppConfig, String> {}
