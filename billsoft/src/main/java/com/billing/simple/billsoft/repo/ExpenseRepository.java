package com.billing.simple.billsoft.repo;

import com.billing.simple.billsoft.entities.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByFirmIdOrderByExpenseDateDescIdDesc(Long firmId);
}
