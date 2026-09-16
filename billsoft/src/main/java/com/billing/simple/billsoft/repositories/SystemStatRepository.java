package com.billing.simple.billsoft.repositories;

import com.billing.simple.billsoft.entities.SystemStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemStatRepository extends JpaRepository<SystemStat, Long> {
}
