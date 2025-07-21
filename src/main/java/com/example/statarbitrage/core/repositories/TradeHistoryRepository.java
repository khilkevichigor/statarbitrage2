package com.example.statarbitrage.core.repositories;

import com.example.statarbitrage.common.model.TradeHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface TradeHistoryRepository extends JpaRepository<TradeHistory, Long> {
    Optional<TradeHistory> findByLongTickerAndShortTicker(String longTicker, String shortTicker);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE " +
            "FROM TradeHistory t " +
            "WHERE t.exitReason IS NULL")
    int deleteUnfinishedTrades();

    @Query("SELECT t " +
            "FROM TradeHistory t " +
            "WHERE t.longTicker = :longTicker " +
            "AND t.shortTicker = :shortTicker " +
            "ORDER BY t.timestamp DESC")
    Optional<TradeHistory> findLatestByTickers(@Param("longTicker") String longTicker, @Param("shortTicker") String shortTicker);

    @Query("SELECT t " +
            "FROM TradeHistory t " +
            "WHERE t.pairUuid = :uuid " +
            "ORDER BY t.timestamp DESC")
    Optional<TradeHistory> findLatestByUuid(@Param("uuid") String uuid);

    @Query("SELECT COUNT(*) " +
            "FROM TradeHistory t " +
            "WHERE DATE(t.entryTime) = DATE('now', 'localtime')")
    Long getTradesToday();

    @Query("SELECT COUNT(*) " +
            "FROM TradeHistory t")
    Long getTradesTotal();

    @Query("SELECT SUM(t.currentProfitPercent) " +
            "FROM TradeHistory t " +
            "WHERE DATE(t.entryTime) = DATE('now', 'localtime') " +
            "AND t.exitReason IS NOT NULL")
    BigDecimal getSumProfitToday();

    @Query("SELECT SUM(t.currentProfitPercent) " +
            "FROM TradeHistory t " +
            "WHERE t.exitReason IS NOT NULL")
    BigDecimal getSumProfitTotal();

    @Query("SELECT AVG(t.currentProfitPercent) " +
            "FROM TradeHistory t " +
            "WHERE DATE(t.entryTime) = DATE('now', 'localtime') " +
            "AND t.exitReason IS NOT NULL")
    BigDecimal getAvgProfitToday();

    @Query("SELECT AVG(t.currentProfitPercent) " +
            "FROM TradeHistory t " +
            "WHERE t.exitReason IS NOT NULL")
    BigDecimal getAvgProfitTotal();

    @Query("SELECT COUNT(*) " +
            "FROM TradeHistory t " +
            "WHERE t.exitReason = 'EXIT_REASON_BY_STOP' " +
            "AND DATE(t.entryTime) = DATE('now')")
    Long getExitByStopToday();

    @Query(value = "SELECT COUNT(*) " +
            "FROM TradeHistory " +
            "WHERE EXIT_REASON = 'EXIT_REASON_BY_STOP'", nativeQuery = true)
    Long getExitByStopTotal();

    @Query("SELECT COUNT(*) " +
            "FROM TradeHistory t " +
            "WHERE t.exitReason = 'EXIT_REASON_BY_TAKE' " +
            "AND DATE(t.entryTime) = DATE('now')")
    Long getExitByTakeToday();

    @Query("SELECT COUNT(*) " +
            "FROM TradeHistory t " +
            "WHERE t.exitReason = 'EXIT_REASON_BY_TAKE'")
    Long getExitByTakeTotal();

    @Query("SELECT COUNT(*) " +
            "FROM TradeHistory t " +
            "WHERE t.exitReason = 'EXIT_REASON_BY_Z_MIN' AND CAST(PARSEDATETIME(t.entryTime, 'yyyy-MM-dd HH:mm:ss') AS DATE) = CURRENT_DATE")
    Long getExitByZMinToday();

    @Query("SELECT COUNT(*) " +
            "FROM TradeHistory t " +
            "WHERE t.exitReason = 'EXIT_REASON_BY_Z_MIN'")
    Long getExitByZMinTotal();

    @Query("SELECT COUNT(*) " +
            "FROM TradeHistory t " +
            "WHERE t.exitReason = 'EXIT_REASON_BY_Z_MAX' AND CAST(PARSEDATETIME(t.entryTime, 'yyyy-MM-dd HH:mm:ss') AS DATE) = CURRENT_DATE")
    Long getExitByZMaxToday();

    @Query("SELECT COUNT(*) " +
            "FROM TradeHistory t " +
            "WHERE t.exitReason = 'EXIT_REASON_BY_Z_MAX'")
    Long getExitByZMaxTotal();

    @Query("SELECT COUNT(*) " +
            "FROM TradeHistory t " +
            "WHERE t.exitReason = 'EXIT_REASON_BY_TIME' AND CAST(PARSEDATETIME(t.entryTime, 'yyyy-MM-dd HH:mm:ss') AS DATE) = CURRENT_DATE")
    Long getExitByTimeToday();

    @Query("SELECT COUNT(*) " +
            "FROM TradeHistory t " +
            "WHERE t.exitReason = 'EXIT_REASON_BY_TIME'")
    Long getExitByTimeTotal();

    @Query("SELECT COUNT(*) " +
            "FROM TradeHistory t " +
            "WHERE t.exitReason = 'EXIT_REASON_BY_MANUALLY' AND CAST(PARSEDATETIME(t.entryTime, 'yyyy-MM-dd HH:mm:ss') AS DATE) = CURRENT_DATE")
    Long getExitByManuallyToday();

    @Query("SELECT COUNT(*) " +
            "FROM TradeHistory t " +
            "WHERE t.exitReason = 'EXIT_REASON_BY_MANUALLY'")
    Long getExitByManuallyTotal();

    @Query(value =
            "SELECT COUNT(*) " +
                    "FROM Pair_Data " +//todo wrong table
                    "WHERE EXIT_REASON = :reason " +
                    "AND DATE(DATETIME(ENTRY_TIME / 1000, 'unixepoch')) = DATE('now')",
            nativeQuery = true)
    Long getExitByToday(@Param("reason") String reason);

    @Query(value =
            "SELECT COUNT(*) " +
                    "FROM Pair_Data " +//todo wrong table
                    "WHERE EXIT_REASON = :reason",
            nativeQuery = true)
    Long getExitByTotal(@Param("reason") String reason);

    @Query("SELECT " +
            "COALESCE(SUM(p.profitChanges), 0) " +
            "FROM PairData p " +
            "WHERE p.status = 'CLOSED'")
    BigDecimal getSumRealizedProfit();
}
