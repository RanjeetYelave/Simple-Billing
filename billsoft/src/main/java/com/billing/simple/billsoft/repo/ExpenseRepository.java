package com.billing.simple.billsoft.repo;

import com.billing.simple.billsoft.entities.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByFirmIdOrderByExpenseDateDescIdDesc(Long firmId);

    Page<Expense> findByFirmId(Long firmId, Pageable pageable);

    Page<Expense> findByFirmIdAndExpenseDateBetween(Long firmId, LocalDate from, LocalDate to, Pageable pageable);

    List<Expense> findByFirmIdAndExpenseDateBetweenOrderByExpenseDateDescIdDesc(Long firmId, LocalDate from, LocalDate to);

    List<Expense> findTop10ByFirmIdOrderByExpenseDateDescIdDesc(Long firmId);

    Optional<Expense> findByIdAndFirmId(Long id, Long firmId);

    boolean existsByIdAndFirmId(Long id, Long firmId);

    void deleteByIdAndFirmId(Long id, Long firmId);

    long countByFirmId(Long firmId);
}

