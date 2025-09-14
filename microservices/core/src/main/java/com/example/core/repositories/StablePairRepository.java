//package com.example.core.repositories;
//
//import com.example.shared.models.StablePair;
//import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.data.jpa.repository.Query;
//import org.springframework.data.repository.query.Param;
//import org.springframework.stereotype.Repository;
//
//import java.time.LocalDateTime;
//import java.util.List;
//
//@Repository
//public interface StablePairRepository extends JpaRepository<StablePair, Long> {
//
//    // Найти все пары по тикерам
//    List<StablePair> findByTickerAAndTickerB(String tickerA, String tickerB);
//
//    // Найти все пары в мониторинге
//    List<StablePair> findByIsInMonitoringTrueOrderByCreatedAtDesc();
//
//    // Найти все найденные пары (не в мониторинге) отсортированные по дате поиска
//    List<StablePair> findByIsInMonitoringFalseOrderBySearchDateDesc();
//
//    // Найти пары по рейтингу стабильности
//    List<StablePair> findByStabilityRatingOrderByTotalScoreDesc(String stabilityRating);
//
//    // Найти торгуемые пары
//    List<StablePair> findByIsTradeableTrueOrderByTotalScoreDesc();
//
//    // Найти пары за определенный период времени
//    List<StablePair> findBySearchDateBetweenOrderBySearchDateDesc(LocalDateTime startDate, LocalDateTime endDate);
//
//    // Найти пары по таймфрейму
//    List<StablePair> findByTimeframeOrderByTotalScoreDesc(String timeframe);
//
//    // Найти лучшие пары по скору
//    @Query("SELECT sp FROM StablePair sp WHERE sp.totalScore >= :minScore ORDER BY sp.totalScore DESC")
//    List<StablePair> findByMinTotalScore(@Param("minScore") Integer minScore);
//
//    // Поиск дубликатов для предотвращения повторного добавления одинаковых пар
//    @Query("SELECT sp FROM StablePair sp WHERE sp.tickerA = :tickerA AND sp.tickerB = :tickerB " +
//           "AND sp.timeframe = :timeframe AND sp.period = :period " +
//           "AND sp.searchDate >= :searchDateFrom")
//    List<StablePair> findSimilarPairs(@Param("tickerA") String tickerA,
//                                     @Param("tickerB") String tickerB,
//                                     @Param("timeframe") String timeframe,
//                                     @Param("period") String period,
//                                     @Param("searchDateFrom") LocalDateTime searchDateFrom);
//
//    // Удалить старые найденные пары (не в мониторинге)
//    @Query("DELETE FROM StablePair sp WHERE sp.isInMonitoring = false AND sp.searchDate < :cutoffDate")
//    int deleteOldSearchResults(@Param("cutoffDate") LocalDateTime cutoffDate);
//
//    // Получить статистику по найденным парам
//    @Query("SELECT sp.stabilityRating, COUNT(sp) FROM StablePair sp " +
//           "WHERE sp.searchDate >= :fromDate GROUP BY sp.stabilityRating")
//    List<Object[]> getStabilityRatingStats(@Param("fromDate") LocalDateTime fromDate);
//
//    // Найти топ пар по рейтингу и скору
//    @Query("SELECT sp FROM StablePair sp WHERE sp.stabilityRating IN :ratings " +
//           "ORDER BY sp.totalScore DESC LIMIT :limit")
//    List<StablePair> findTopPairsByRating(@Param("ratings") List<String> ratings,
//                                         @Param("limit") int limit);
//}