package com.billing.simple.billsoft.repo;

import com.billing.simple.billsoft.entities.CustomerPromiseHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomerPromiseHistoryRepository extends JpaRepository<CustomerPromiseHistory, Long> {
    List<CustomerPromiseHistory> findByFirmId(Long firmId);
    List<CustomerPromiseHistory> findByFirmIdAndPromiseIdOrderByCreatedAtDesc(Long firmId, Long promiseId);
}
