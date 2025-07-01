package com.example.statarbitrage.repositories;

import com.example.statarbitrage.model.TradeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface TradeLogRepository extends JpaRepository<TradeLog, Long> {
    Optional<TradeLog> findByLongTickerAndShortTicker(String longTicker, String shortTicker);

    @Modifying
    @Query(value =
            "DELETE " +
                    "FROM TRADE_LOG " +
                    "WHERE EXIT_REASON IS NULL", nativeQuery = true)
    int deleteUnfinishedTrades();

    @Query(value =
            "SELECT t " +
                    "FROM TradeLog t " +
                    "WHERE t.longTicker = :longTicker " +
                    "AND t.shortTicker = :shortTicker " +
                    "ORDER BY t.timestamp DESC")
    Optional<TradeLog> findLatestByTickers(@Param("longTicker") String longTicker, @Param("shortTicker") String shortTicker);

    @Query(value =
            "SELECT COUNT(*) " +
                    "FROM TRADE_LOG " +
                    "WHERE CAST(PARSEDATETIME(ENTRY_TIME, 'yyyy-MM-dd HH:mm:ss') AS DATE) = CURRENT_DATE", nativeQuery = true)
    Long getTradesToday();

    @Query(value =
            "SELECT COUNT(*) " +
                    "FROM TRADE_LOG", nativeQuery = true)
    Long getTradesTotal();

    @Query(value =
            "SELECT SUM(CURRENT_PROFIT_PERCENT) " +
                    "FROM TRADE_LOG " +
                    "WHERE LEFT(ENTRY_TIME, 10) = FORMATDATETIME(CURRENT_DATE(), 'yyyy-MM-dd') " +
                    "AND EXIT_REASON IS NOT NULL",
            nativeQuery = true)
    BigDecimal getSumProfitToday();

    @Query(value =
            "SELECT SUM(CURRENT_PROFIT_PERCENT) " +
                    "FROM TRADE_LOG " +
                    "WHERE EXIT_REASON IS NOT NULL",
            nativeQuery = true)
    BigDecimal getSumProfitTotal();

    @Query(value =
            "SELECT AVG(CURRENT_PROFIT_PERCENT) " +
                    "FROM TRADE_LOG " +
                    "WHERE LEFT(ENTRY_TIME, 10) = FORMATDATETIME(CURRENT_DATE(), 'yyyy-MM-dd') " +
                    "AND EXIT_REASON IS NOT NULL", nativeQuery = true)
    BigDecimal getAvgProfitToday();

    @Query(value =
            "SELECT AVG(CURRENT_PROFIT_PERCENT) " +
                    "FROM TRADE_LOG " +
                    "WHERE EXIT_REASON IS NOT NULL", nativeQuery = true)
    BigDecimal getAvgProfitTotal();

    @Query(value =
            "SELECT COUNT(*) " +
                    "FROM TRADE_LOG " +
                    "WHERE EXIT_REASON = 'EXIT_REASON_BY_STOP' " +
                    "AND CAST(PARSEDATETIME(ENTRY_TIME, 'yyyy-MM-dd HH:mm:ss') AS DATE) = CURRENT_DATE", nativeQuery = true)
    Long getExitByStopToday();

    @Query(value = "SELECT COUNT(*) " +
            "FROM TRADE_LOG " +
            "WHERE EXIT_REASON = 'EXIT_REASON_BY_STOP'", nativeQuery = true)
    Long getExitByStopTotal();

    @Query(value =
            "SELECT COUNT(*) " +
                    "FROM TRADE_LOG " +
                    "WHERE EXIT_REASON = 'EXIT_REASON_BY_TAKE' " +
                    "AND CAST(PARSEDATETIME(ENTRY_TIME, 'yyyy-MM-dd HH:mm:ss') AS DATE) = CURRENT_DATE", nativeQuery = true)
    Long getExitByTakeToday();

    @Query(value =
            "SELECT COUNT(*) " +
                    "FROM TRADE_LOG " +
                    "WHERE EXIT_REASON = 'EXIT_REASON_BY_TAKE'", nativeQuery = true)
    Long getExitByTakeTotal();

    @Query(value =
            "SELECT COUNT(*) " +
                    "FROM TRADE_LOG " +
                    "WHERE EXIT_REASON = 'EXIT_REASON_BY_Z_MIN' AND CAST(PARSEDATETIME(ENTRY_TIME, 'yyyy-MM-dd HH:mm:ss') AS DATE) = CURRENT_DATE", nativeQuery = true)
    Long getExitByZMinToday();

    @Query(value =
            "SELECT COUNT(*) " +
                    "FROM TRADE_LOG " +
                    "WHERE EXIT_REASON = 'EXIT_REASON_BY_Z_MIN'", nativeQuery = true)
    Long getExitByZMinTotal();

    @Query(value =
            "SELECT COUNT(*) " +
                    "FROM TRADE_LOG " +
                    "WHERE EXIT_REASON = 'EXIT_REASON_BY_Z_MAX' AND CAST(PARSEDATETIME(ENTRY_TIME, 'yyyy-MM-dd HH:mm:ss') AS DATE) = CURRENT_DATE", nativeQuery = true)
    Long getExitByZMaxToday();

    @Query(value =
            "SELECT COUNT(*) " +
                    "FROM TRADE_LOG " +
                    "WHERE EXIT_REASON = 'EXIT_REASON_BY_Z_MAX'", nativeQuery = true)
    Long getExitByZMaxTotal();

    @Query(value =
            "SELECT COUNT(*) " +
                    "FROM TRADE_LOG " +
                    "WHERE EXIT_REASON = 'EXIT_REASON_BY_TIME' AND CAST(PARSEDATETIME(ENTRY_TIME, 'yyyy-MM-dd HH:mm:ss') AS DATE) = CURRENT_DATE", nativeQuery = true)
    Long getExitByTimeToday();

    @Query(value =
            "SELECT COUNT(*) " +
                    "FROM TRADE_LOG " +
                    "WHERE EXIT_REASON = 'EXIT_REASON_BY_TIME'", nativeQuery = true)
    Long getExitByTimeTotal();

    @Query(value =
            "SELECT COUNT(*) " +
                    "FROM TRADE_LOG " +
                    "WHERE EXIT_REASON = 'EXIT_REASON_BY_MANUALLY' AND CAST(PARSEDATETIME(ENTRY_TIME, 'yyyy-MM-dd HH:mm:ss') AS DATE) = CURRENT_DATE", nativeQuery = true)
    Long getExitByManuallyToday();

    @Query(value =
            "SELECT COUNT(*) " +
                    "FROM TRADE_LOG " +
                    "WHERE EXIT_REASON = 'EXIT_REASON_BY_MANUALLY'", nativeQuery = true)
    Long getExitByManuallyTotal();

    @Query(value =
            "SELECT COUNT(*) " +
                    "FROM PAIR_DATA " +
                    "WHERE EXIT_REASON = :reason " +
                    "AND CAST(DATEADD('MILLISECOND', ENTRY_TIME, DATE '1970-01-01') AS DATE) = CURRENT_DATE",
            nativeQuery = true)
    Long getExitByToday(@Param("reason") String reason);

    @Query(value =
            "SELECT COUNT(*) " +
                    "FROM PAIR_DATA " +
                    "WHERE EXIT_REASON = :reason",
            nativeQuery = true)
    Long getExitByTotal(@Param("reason") String reason);

    @Query("SELECT SUM(currentProfitPercent) FROM TradeLog WHERE exitReason IS NOT NULL")
    BigDecimal getSumRealizedProfit();
}
