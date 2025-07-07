package com.example.statarbitrage.trading.repositories;

import com.example.statarbitrage.trading.model.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для портфолио
 */
@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    /**
     * Найти последнее (текущее) портфолио
     */
    Optional<Portfolio> findFirstByOrderByIdDesc();

    /**
     * Найти портфолио по дате создания
     */
    List<Portfolio> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Получить историю портфолио за период
     */
    @Query("SELECT p FROM Portfolio p WHERE p.lastUpdated BETWEEN :start AND :end ORDER BY p.lastUpdated DESC")
    List<Portfolio> findPortfolioHistory(LocalDateTime start, LocalDateTime end);

    /**
     * Получить максимальный баланс за все время
     */
    @Query("SELECT MAX(p.totalBalance) FROM Portfolio p")
    Optional<Double> findMaxTotalBalance();

    /**
     * Получить минимальный баланс за все время
     */
    @Query("SELECT MIN(p.totalBalance) FROM Portfolio p")
    Optional<Double> findMinTotalBalance();
}