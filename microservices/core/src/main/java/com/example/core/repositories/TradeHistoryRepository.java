package com.example.core.repositories;

import com.example.shared.models.TradeHistory;
import com.example.shared.models.TradeStatus;
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

    //todo по хорошему здесь иметь только save, update, getById - чисто что бы получать сущность иначе каша и непонятка

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE " +
            "FROM TradeHistory t " +
            "WHERE t.exitReason IS NULL")
    int deleteUnfinishedTrades();

    @Query("SELECT t " +
            "FROM TradeHistory t " +
            "WHERE t.pairUuid = :uuid " +
            "ORDER BY t.timestamp DESC")
    Optional<TradeHistory> findLatestByUuid(@Param("uuid") String uuid);

    @Query(value = "SELECT COUNT(*) " +
            "FROM trade_history t " +
            "WHERE to_timestamp(t.entry_time / 1000)::date = current_date",
            nativeQuery = true)
    Long getTradesToday();

    @Query("SELECT COUNT(*) " +
            "FROM TradeHistory t")
    Long getTradesTotal();

    @Query(value = "SELECT SUM(t.current_profit_usdt) " +
            "FROM trade_history t " +
            "WHERE t.entry_time >= extract(epoch from current_date) * 1000 " +
            "AND t.entry_time < extract(epoch from current_date + interval '1 day') * 1000 " +
            "AND t.exit_reason IS NOT NULL",
            nativeQuery = true
    )
    BigDecimal getSumProfitUSDTToday();

    @Query("SELECT SUM(t.currentProfitUSDT) " +
            "FROM TradeHistory t " +
            "WHERE t.exitReason IS NOT NULL")
    BigDecimal getSumProfitUSDTTotal();

    @Query(value = "SELECT SUM(t.current_profit_percent) " +
            "FROM trade_history t " +
            "WHERE t.entry_time >= extract(epoch from current_date) * 1000 " +
            "AND t.entry_time < extract(epoch from current_date + interval '1 day') * 1000 " +
            "AND t.exit_reason IS NOT NULL",
            nativeQuery = true
    )
    BigDecimal getSumProfitPercentToday();

    @Query("SELECT SUM(t.currentProfitPercent) " +
            "FROM TradeHistory t " +
            "WHERE t.exitReason IS NOT NULL")
    BigDecimal getSumProfitPercentTotal();

    @Query(value = "SELECT AVG(t.current_profit_usdt) " +
            "FROM trade_history t " +
            "WHERE DATE(TO_TIMESTAMP(t.entry_time / 1000)) = CURRENT_DATE " +
            "AND t.exit_reason IS NOT NULL", nativeQuery = true)
    BigDecimal getAvgProfitUSDTToday();

    @Query("SELECT AVG(t.currentProfitUSDT) " +
            "FROM TradeHistory t " +
            "WHERE t.exitReason IS NOT NULL")
    BigDecimal getAvgProfitUSDTTotal();

    @Query(value = "SELECT AVG(t.current_profit_percent) " +
            "FROM trade_history t " +
            "WHERE to_timestamp(t.entry_time / 1000) >= CURRENT_DATE " +
            "  AND to_timestamp(t.entry_time / 1000) < CURRENT_DATE + INTERVAL '1 day' " +
            "  AND t.exit_reason IS NOT NULL", nativeQuery = true)
    BigDecimal getAvgProfitPercentToday();

    @Query("SELECT AVG(t.currentProfitPercent) " +
            "FROM TradeHistory t " +
            "WHERE t.exitReason IS NOT NULL")
    BigDecimal getAvgProfitPercentTotal();

    @Query(value = "SELECT COUNT(*) " +
            "FROM trading_pair p " +
            "WHERE p.status = :status " +
            "AND to_timestamp(p.entry_time / 1000)::date = current_date",
            nativeQuery = true)
    Long getByStatusForToday(@Param("status") String status);

    @Query("SELECT COUNT(*) " +
            "FROM TradingPair p " +
            "WHERE p.status = :status")
    Long getByStatusTotal(@Param("status") TradeStatus status);

    @Query(value = "SELECT COUNT(*) " +
            "FROM trading_pair tp " +
            "WHERE tp.exit_reason = :reason " +
            "AND tp.entry_time >= extract(epoch from current_date) * 1000 " +
            "AND tp.entry_time < extract(epoch from current_date + interval '1 day') * 1000",
            nativeQuery = true)
    Long getByExitReasonForToday(@Param("reason") String reason);

    @Query("SELECT COUNT(*) " +
            "FROM TradingPair p " +
            "WHERE p.exitReason = :reason")
    Long getAllByExitReason(@Param("reason") String reason);

    @Query("SELECT SUM(p.profitUSDTChanges) " +
            "FROM TradingPair p " +
            "WHERE p.status = 'CLOSED' " +
            "AND p.entryTime >= :startOfDay")
    BigDecimal getSumRealizedProfitUSDTToday(@Param("startOfDay") Long startOfDay);

    @Query("SELECT SUM(p.profitUSDTChanges) " +
            "FROM TradingPair p " +
            "WHERE p.status = 'CLOSED'")
    BigDecimal getSumRealizedProfitUSDTTotal();

    @Query("SELECT SUM(p.profitPercentChanges) " +
            "FROM TradingPair p " +
            "WHERE p.status = 'CLOSED' " +
            "AND p.entryTime >= :startOfDay")
    BigDecimal getSumRealizedProfitPercentToday(@Param("startOfDay") Long startOfDay);


    @Query("SELECT SUM(p.profitPercentChanges) " +
            "FROM TradingPair p " +
            "WHERE p.status = 'CLOSED'")
    BigDecimal getSumRealizedProfitPercentTotal();
}
