package com.example.statarbitrage.core.repositories;

import com.example.statarbitrage.common.model.TradeHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface TradeHistoryRepository extends JpaRepository<TradeHistory, Long> {
    Optional<TradeHistory> findByLongTickerAndShortTicker(String longTicker, String shortTicker);

    @Modifying
    @Query(value =
            "DELETE " +
                    "FROM trade_history " +
                    "WHERE exit_reason IS NULL", nativeQuery = true)
    int deleteUnfinishedTrades();

    @Query(value =
            "SELECT t " +
                    "FROM TradeHistory t " +
                    "WHERE t.longTicker = :longTicker " +
                    "AND t.shortTicker = :shortTicker " +
                    "ORDER BY t.timestamp DESC")
    Optional<TradeHistory> findLatestByTickers(@Param("longTicker") String longTicker, @Param("shortTicker") String shortTicker);

    @Query(value =
            "SELECT t " +
                    "FROM TradeHistory t " +
                    "WHERE t.pairUuid = :uuid " +
                    "ORDER BY t.timestamp DESC")
    Optional<TradeHistory> findLatestByUuid(@Param("uuid") String uuid);

    @Query(value =
            "SELECT COUNT(*) " +
                    "FROM TradeHistory " +
                    "WHERE SUBSTR(ENTRY_TIME, 1, 10) = DATE('now', 'localtime')", nativeQuery = true)
    Long getTradesToday();

    @Query(value =
            "SELECT COUNT(*) " +
                    "FROM TradeHistory", nativeQuery = true)
    Long getTradesTotal();

    @Query(value =
            "SELECT SUM(CURRENT_PROFIT_PERCENT) " +
                    "FROM TradeHistory " +
                    "WHERE SUBSTR(ENTRY_TIME, 1, 10) = DATE('now', 'localtime') " +
                    "AND EXIT_REASON IS NOT NULL",
            nativeQuery = true)
    BigDecimal getSumProfitToday();

    @Query(value =
            "SELECT SUM(CURRENT_PROFIT_PERCENT) " +
                    "FROM TradeHistory " +
                    "WHERE EXIT_REASON IS NOT NULL",
            nativeQuery = true)
    BigDecimal getSumProfitTotal();

    @Query(value =
            "SELECT AVG(CURRENT_PROFIT_PERCENT) " +
                    "FROM TradeHistory " +
                    "WHERE SUBSTR(ENTRY_TIME, 1, 10) = DATE('now', 'localtime') " +
                    "AND EXIT_REASON IS NOT NULL", nativeQuery = true)
    BigDecimal getAvgProfitToday();

    @Query(value =
            "SELECT AVG(CURRENT_PROFIT_PERCENT) " +
                    "FROM TradeHistory " +
                    "WHERE EXIT_REASON IS NOT NULL", nativeQuery = true)
    BigDecimal getAvgProfitTotal();

    @Query(value =
            "SELECT COUNT(*) " +
                    "FROM TradeHistory " +
                    "WHERE EXIT_REASON = 'EXIT_REASON_BY_STOP' " +
                    "AND DATE(ENTRY_TIME) = DATE('now')", nativeQuery = true)
    Long getExitByStopToday();

    @Query(value = "SELECT COUNT(*) " +
            "FROM TradeHistory " +
            "WHERE EXIT_REASON = 'EXIT_REASON_BY_STOP'", nativeQuery = true)
    Long getExitByStopTotal();

    @Query(value =
            "SELECT COUNT(*) " +
                    "FROM TradeHistory " +
                    "WHERE EXIT_REASON = 'EXIT_REASON_BY_TAKE' " +
                    "AND DATE(ENTRY_TIME) = DATE('now')", nativeQuery = true)
    Long getExitByTakeToday();

    @Query(value =
            "SELECT COUNT(*) " +
                    "FROM TradeHistory " +
                    "WHERE EXIT_REASON = 'EXIT_REASON_BY_TAKE'", nativeQuery = true)
    Long getExitByTakeTotal();

    @Query(value =
            "SELECT COUNT(*) " +
                    "FROM TradeHistory " +
                    "WHERE EXIT_REASON = 'EXIT_REASON_BY_Z_MIN' AND CAST(PARSEDATETIME(ENTRY_TIME, 'yyyy-MM-dd HH:mm:ss') AS DATE) = CURRENT_DATE", nativeQuery = true)
    Long getExitByZMinToday();

    @Query(value =
            "SELECT COUNT(*) " +
                    "FROM TradeHistory " +
                    "WHERE EXIT_REASON = 'EXIT_REASON_BY_Z_MIN'", nativeQuery = true)
    Long getExitByZMinTotal();

    @Query(value =
            "SELECT COUNT(*) " +
                    "FROM TradeHistory " +
                    "WHERE EXIT_REASON = 'EXIT_REASON_BY_Z_MAX' AND CAST(PARSEDATETIME(ENTRY_TIME, 'yyyy-MM-dd HH:mm:ss') AS DATE) = CURRENT_DATE", nativeQuery = true)
    Long getExitByZMaxToday();

    @Query(value =
            "SELECT COUNT(*) " +
                    "FROM TradeHistory " +
                    "WHERE EXIT_REASON = 'EXIT_REASON_BY_Z_MAX'", nativeQuery = true)
    Long getExitByZMaxTotal();

    @Query(value =
            "SELECT COUNT(*) " +
                    "FROM TradeHistory " +
                    "WHERE EXIT_REASON = 'EXIT_REASON_BY_TIME' AND CAST(PARSEDATETIME(ENTRY_TIME, 'yyyy-MM-dd HH:mm:ss') AS DATE) = CURRENT_DATE", nativeQuery = true)
    Long getExitByTimeToday();

    @Query(value =
            "SELECT COUNT(*) " +
                    "FROM TradeHistory " +
                    "WHERE EXIT_REASON = 'EXIT_REASON_BY_TIME'", nativeQuery = true)
    Long getExitByTimeTotal();

    @Query(value =
            "SELECT COUNT(*) " +
                    "FROM TradeHistory " +
                    "WHERE EXIT_REASON = 'EXIT_REASON_BY_MANUALLY' AND CAST(PARSEDATETIME(ENTRY_TIME, 'yyyy-MM-dd HH:mm:ss') AS DATE) = CURRENT_DATE", nativeQuery = true)
    Long getExitByManuallyToday();

    @Query(value =
            "SELECT COUNT(*) " +
                    "FROM TradeHistory " +
                    "WHERE EXIT_REASON = 'EXIT_REASON_BY_MANUALLY'", nativeQuery = true)
    Long getExitByManuallyTotal();

    @Query(value =
            "SELECT COUNT(*) " +
                    "FROM PairData " +//todo wrong table
                    "WHERE EXIT_REASON = :reason " +
                    "AND DATE(DATETIME(ENTRY_TIME / 1000, 'unixepoch')) = DATE('now')",
            nativeQuery = true)
    Long getExitByToday(@Param("reason") String reason);

    @Query(value =
            "SELECT COUNT(*) " +
                    "FROM PairData " +//todo wrong table
                    "WHERE EXIT_REASON = :reason",
            nativeQuery = true)
    Long getExitByTotal(@Param("reason") String reason);

    @Query(value =
            "SELECT COALESCE(SUM(profit_changes), 0) " +
                    "FROM PairData " + //todo wrong table
                    "WHERE STATUS = 'CLOSED'",
            nativeQuery = true)
    BigDecimal getSumRealizedProfit();
}
