package com.example.cointegration.repositories;

import com.example.shared.enums.PairType;
import com.example.shared.enums.TradeStatus;
import com.example.shared.models.Pair;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PairRepository extends JpaRepository<Pair, Long> {

    // ======== БАЗОВЫЕ МЕТОДЫ ПОИСКА ========

    /**
     * Найти пару по UUID
     */
    Optional<Pair> findByUuid(UUID uuid);

    /**
     * Найти все пары по тикерам
     */
    List<Pair> findByTickerAAndTickerB(String tickerA, String tickerB);

    /**
     * Найти все пары по типу
     */
    List<Pair> findByTypeOrderByCreatedAtDesc(PairType type);

    /**
     * Найти все пары по статусу
     */
    List<Pair> findByStatusOrderByCreatedAtDesc(TradeStatus status);

    /**
     * Найти пары по типу и статусу
     */
    List<Pair> findByTypeAndStatusOrderByCreatedAtDesc(PairType type, TradeStatus status);

    // ======== МЕТОДЫ ДЛЯ КОИНТЕГРИРОВАННЫХ ПАР (COINTEGRATED) ========

    /**
     * Найти все коинтегрированные пары
     */
    @Query("SELECT p FROM Pair p WHERE p.type = 'COINTEGRATED' ORDER BY p.createdAt DESC")
    List<Pair> findCointegrationPairs();

    /**
     * Найти коинтегрированные пары по статусу
     */
    @Query("SELECT p FROM Pair p WHERE p.type = 'COINTEGRATED' AND p.status = :status ORDER BY p.updatedTime DESC")
    List<Pair> findCointegrationPairsByStatus(@Param("status") TradeStatus status);

    // ======== ОПЕРАЦИИ УДАЛЕНИЯ И ОЧИСТКИ ========

    /**
     * Удалить пары по типу и статусу
     */
    @Modifying
    @Query("DELETE FROM Pair p WHERE p.type = :type AND p.status = :status")
    int deleteByTypeAndStatus(@Param("type") PairType type, @Param("status") TradeStatus status);

    // ======== ОПЕРАЦИИ ОБНОВЛЕНИЯ ========

    /**
     * Обновить статус пары
     */
    @Modifying
    @Query("UPDATE Pair p SET p.status = :status, p.updatedTime = :updatedTime WHERE p.id = :id")
    int updateStatus(@Param("id") Long id, @Param("status") TradeStatus status, @Param("updatedTime") LocalDateTime updatedTime);

    /**
     * Конвертировать тип пары (например, STABLE -> COINTEGRATED)
     */
    @Modifying
    @Query("UPDATE Pair p SET p.type = :newType, p.updatedTime = :updatedTime WHERE p.id = :id")
    int convertPairType(@Param("id") Long id, @Param("newType") PairType newType, @Param("updatedTime") LocalDateTime updatedTime);

    // ======== СТАТИСТИКА И АНАЛИТИКА ========

    /**
     * Подсчет пар по типам
     */
    @Query("SELECT p.type, COUNT(p) FROM Pair p GROUP BY p.type")
    List<Object[]> countPairsByType();

    /**
     * Подсчет пар по типам и статусам
     */
    @Query("SELECT p.type, p.status, COUNT(p) FROM Pair p GROUP BY p.type, p.status")
    List<Object[]> countPairsByTypeAndStatus();
}