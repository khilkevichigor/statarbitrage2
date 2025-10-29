package com.example.core.repositories;

import com.example.shared.enums.PairType;
import com.example.shared.enums.StabilityRating;
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
     * Проверить существование пары по всем ключевым полям
     */
    boolean existsByTickerAAndTickerBAndTimeframeAndPeriodAndType(
            String tickerA, String tickerB, String timeframe, String period, PairType type);

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

    // ======== МЕТОДЫ ДЛЯ СТАБИЛЬНЫХ ПАР (STABLE) ========

    /**
     * Найти все стабильные пары в мониторинге
     */
    @Query("SELECT p FROM Pair p WHERE p.type = 'STABLE' AND p.isInMonitoring = true ORDER BY p.createdAt DESC")
    List<Pair> findStablePairsInMonitoring();

    /**
     * Найти стабильные пары в мониторинге с указанными рейтингами (enum)
     */
    @Query("SELECT p FROM Pair p WHERE p.type = 'STABLE' AND p.isInMonitoring = true AND p.stabilityRating IN :ratings ORDER BY p.createdAt DESC")
    List<Pair> findStablePairsInMonitoringByStabilityRatings(@Param("ratings") List<StabilityRating> ratings);

    /**
     * Найти стабильные пары в мониторинге с указанными рейтингами (строки - для обратной совместимости)
     *
     * @deprecated Используйте {@link #findStablePairsInMonitoringByStabilityRatings(List)}
     */
    @Deprecated
    @Query("SELECT p FROM Pair p WHERE p.type = 'STABLE' AND p.isInMonitoring = true AND p.stabilityRating IN :ratings ORDER BY p.createdAt DESC")
    List<Pair> findStablePairsInMonitoringByRatings(@Param("ratings") List<String> ratings);

    /**
     * Найти все найденные стабильные пары (не в мониторинге)
     */
    @Query("SELECT p FROM Pair p WHERE p.type = 'STABLE' AND p.isInMonitoring = false ORDER BY p.searchDate DESC")
    List<Pair> findFoundStablePairs();

    /**
     * Найти стабильные пары по рейтингу (enum)
     */
    @Query("SELECT p FROM Pair p WHERE p.type = 'STABLE' AND p.stabilityRating = :rating ORDER BY p.totalScore DESC")
    List<Pair> findStablePairsByStabilityRating(@Param("rating") StabilityRating rating);

    /**
     * Найти стабильные пары по рейтингу (строка - для обратной совместимости)
     *
     * @deprecated Используйте {@link #findStablePairsByStabilityRating(StabilityRating)}
     */
    @Deprecated
    @Query("SELECT p FROM Pair p WHERE p.type = 'STABLE' AND p.stabilityRating = :rating ORDER BY p.totalScore DESC")
    List<Pair> findStablePairsByRating(@Param("rating") String rating);

    /**
     * Найти торгуемые стабильные пары
     */
    @Query("SELECT p FROM Pair p WHERE p.type = 'STABLE' AND p.isTradeable = true ORDER BY p.totalScore DESC")
    List<Pair> findTradeableStablePairs();

    /**
     * Найти стабильные пары за определенный период
     */
    @Query("SELECT p FROM Pair p WHERE p.type = 'STABLE' AND p.searchDate BETWEEN :startDate AND :endDate ORDER BY p.searchDate DESC")
    List<Pair> findStablePairsByDateRange(@Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate);

    /**
     * Найти стабильные пары по таймфрейму
     */
    @Query("SELECT p FROM Pair p WHERE p.type = 'STABLE' AND p.timeframe = :timeframe ORDER BY p.totalScore DESC")
    List<Pair> findStablePairsByTimeframe(@Param("timeframe") String timeframe);

    /**
     * Найти стабильные пары с минимальным скором
     */
    @Query("SELECT p FROM Pair p WHERE p.type = 'STABLE' AND p.totalScore >= :minScore ORDER BY p.totalScore DESC")
    List<Pair> findStablePairsWithMinScore(@Param("minScore") Integer minScore);

    /**
     * Поиск похожих стабильных пар для предотвращения дубликатов
     */
    @Query("SELECT p FROM Pair p WHERE p.type = 'STABLE' AND p.tickerA = :tickerA AND p.tickerB = :tickerB " +
            "AND p.timeframe = :timeframe AND p.period = :period " +
            "AND p.searchDate >= :searchDateFrom")
    List<Pair> findSimilarStablePairs(@Param("tickerA") String tickerA,
                                      @Param("tickerB") String tickerB,
                                      @Param("timeframe") String timeframe,
                                      @Param("period") String period,
                                      @Param("searchDateFrom") LocalDateTime searchDateFrom);

    /**
     * Получить статистику по рейтингу стабильности
     */
    @Query("SELECT p.stabilityRating, COUNT(p) FROM Pair p " +
            "WHERE p.type = 'STABLE' AND p.searchDate >= :fromDate GROUP BY p.stabilityRating")
    List<Object[]> getStabilityRatingStats(@Param("fromDate") LocalDateTime fromDate);

    /**
     * Найти топ стабильных пар по рейтингу (enum)
     */
    @Query("SELECT p FROM Pair p WHERE p.type = 'STABLE' AND p.stabilityRating IN :ratings " +
            "ORDER BY p.totalScore DESC LIMIT :limit")
    List<Pair> findTopStablePairsByStabilityRating(@Param("ratings") List<StabilityRating> ratings,
                                                   @Param("limit") int limit);

    /**
     * Найти топ стабильных пар по рейтингу (строки - для обратной совместимости)
     *
     * @deprecated Используйте {@link #findTopStablePairsByStabilityRating(List, int)}
     */
    @Deprecated
    @Query("SELECT p FROM Pair p WHERE p.type = 'STABLE' AND p.stabilityRating IN :ratings " +
            "ORDER BY p.totalScore DESC LIMIT :limit")
    List<Pair> findTopStablePairsByRating(@Param("ratings") List<String> ratings,
                                          @Param("limit") int limit);

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

    // ======== МЕТОДЫ ДЛЯ ТОРГОВЫХ ПАР (TRADING) ========

    /**
     * Найти все торговые пары
     */
    @Query("SELECT p FROM Pair p WHERE p.type = 'TRADING' ORDER BY p.entryTime DESC")
    List<Pair> findTradingPairs();

    /**
     * Найти торговые пары по статусу
     */
    @Query("SELECT p FROM Pair p WHERE p.type = 'TRADING' AND p.status = :status ORDER BY p.updatedTime DESC")
    List<Pair> findTradingPairsByStatus(@Param("status") TradeStatus status);

    /**
     * Найти активные торговые пары
     */
    @Query("SELECT p FROM Pair p WHERE p.type = 'TRADING' AND p.status = 'TRADING' ORDER BY p.entryTime DESC")
    List<Pair> findActiveTradingPairs();

    // ======== ОПЕРАЦИИ УДАЛЕНИЯ И ОЧИСТКИ ========

    /**
     * Удалить старые найденные стабильные пары (не в мониторинге)
     */
    @Modifying
    @Query("DELETE FROM Pair p WHERE p.type = 'STABLE' AND p.isInMonitoring = false AND p.searchDate < :cutoffDate")
    int deleteOldStablePairs(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Удалить ВСЕ найденные стабильные пары (не в мониторинге)
     * Используется перед сохранением новых результатов поиска
     */
    @Modifying
    @Query("DELETE FROM Pair p WHERE p.type = 'STABLE' AND p.isInMonitoring = false")
    int deleteAllFoundStablePairs();

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
     * Обновить флаг мониторинга для стабильной пары
     */
    @Modifying
    @Query("UPDATE Pair p SET p.isInMonitoring = :inMonitoring WHERE p.id = :id AND p.type = 'STABLE'")
    int updateMonitoringStatus(@Param("id") Long id, @Param("inMonitoring") boolean inMonitoring);

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

    /**
     * Найти пары с лучшей производительностью за период
     */
    @Query("SELECT p FROM Pair p WHERE p.type = 'TRADING' AND p.entryTime >= :fromDate " +
            "ORDER BY p.profitPercentChanges DESC NULLS LAST LIMIT :limit")
    List<Pair> findTopPerformingPairs(@Param("fromDate") LocalDateTime fromDate, @Param("limit") int limit);

    // ======== ДОПОЛНИТЕЛЬНЫЕ МЕТОДЫ ДЛЯ TRADINGPAIR СОВМЕСТИМОСТИ ========

    /**
     * Найти торговые пары по статусу и времени входа после указанной даты
     */
    @Query("SELECT p FROM Pair p WHERE p.type = 'TRADING' AND p.status = :status AND p.entryTime >= :afterDate " +
            "ORDER BY p.entryTime DESC")
    List<Pair> findTradingPairsByStatusAndEntryTimeAfter(@Param("status") TradeStatus status,
                                                         @Param("afterDate") LocalDateTime afterDate);

    /**
     * Найти все торговые пары по статусу (для совместимости с TradingPairRepository)
     */
    @Query("SELECT p FROM Pair p WHERE p.type = 'TRADING' AND p.status = :status ORDER BY p.updatedTime DESC")
    List<Pair> findTradingPairsByStatusOrderByUpdatedTime(@Param("status") TradeStatus status);

    /**
     * Подсчет пар по типу и статусу
     */
    @Query("SELECT COUNT(p) FROM Pair p WHERE p.type = :type AND p.status = :status")
    int countByTypeAndStatus(@Param("type") PairType type, @Param("status") TradeStatus status);

    /**
     * Универсальный метод для поиска стабильных пар с фильтрами
     */
    @Query("SELECT p FROM Pair p WHERE p.type = 'STABLE' " +
            "AND ((:includeMonitoring = true AND p.isInMonitoring = true) " +
            "     OR (:includeFound = true AND p.isInMonitoring = false)) " +
            "AND (:ratings IS NULL OR p.stabilityRating IN :ratings) " +
            "ORDER BY p.totalScore DESC NULLS LAST, p.createdAt DESC")
    List<Pair> findStablePairsWithFilters(@Param("includeMonitoring") boolean includeMonitoring,
                                          @Param("includeFound") boolean includeFound,
                                          @Param("ratings") List<StabilityRating> ratings);
}