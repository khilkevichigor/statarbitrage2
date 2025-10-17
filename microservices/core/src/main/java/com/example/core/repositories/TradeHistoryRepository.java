package com.example.core.repositories;

import com.example.shared.enums.TradeStatus;
import com.example.shared.models.TradeHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
            "WHERE to_timestamp(t.entry_time / 1000.0)::date = current_date",
            nativeQuery = true)
    Long getTradesToday();

    @Query("SELECT COUNT(*) " +
            "FROM TradeHistory t")
    Long getTradesTotal();

    @Query(value = "SELECT SUM(t.current_profit_usdt) " +
            "FROM trade_history t " +
            "WHERE to_timestamp(t.entry_time / 1000.0)::date = current_date " +
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
            "WHERE to_timestamp(t.entry_time / 1000.0)::date = current_date " +
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
            "WHERE DATE(TO_TIMESTAMP(t.entry_time / 1000.0)) = CURRENT_DATE " +
            "AND t.exit_reason IS NOT NULL", nativeQuery = true)
    BigDecimal getAvgProfitUSDTToday();

    @Query("SELECT AVG(t.currentProfitUSDT) " +
            "FROM TradeHistory t " +
            "WHERE t.exitReason IS NOT NULL")
    BigDecimal getAvgProfitUSDTTotal();

    @Query(value = "SELECT AVG(t.current_profit_percent) " +
            "FROM trade_history t " +
            "WHERE to_timestamp(t.entry_time / 1000.0) >= CURRENT_DATE " +
            "  AND to_timestamp(t.entry_time / 1000.0) < CURRENT_DATE + INTERVAL '1 day' " +
            "  AND t.exit_reason IS NOT NULL", nativeQuery = true)
    BigDecimal getAvgProfitPercentToday();

    @Query("SELECT AVG(t.currentProfitPercent) " +
            "FROM TradeHistory t " +
            "WHERE t.exitReason IS NOT NULL")
    BigDecimal getAvgProfitPercentTotal();

    @Query(value = "SELECT COUNT(*) " +
            "FROM pairs p " +
            "WHERE p.status = :status " +
            "AND p.entry_time::date = current_date",
            nativeQuery = true)
    Long getByStatusForToday(@Param("status") String status);

    @Query("SELECT COUNT(*) " +
            "FROM Pair p " +
            "WHERE p.status = :status")
    Long getByStatusTotal(@Param("status") TradeStatus status);

    @Query(value = "SELECT COUNT(*) " +
            "FROM pairs tp " +
            "WHERE tp.exit_reason = :reason " +
            "AND tp.entry_time::date = current_date",
            nativeQuery = true)
    Long getByExitReasonForToday(@Param("reason") String reason);

    @Query("SELECT COUNT(*) " +
            "FROM Pair p " +
            "WHERE p.exitReason = :reason")
    Long getAllByExitReason(@Param("reason") String reason);

    @Query("SELECT SUM(p.profitUSDTChanges) " +
            "FROM Pair p " +
            "WHERE p.status = 'CLOSED' " +
            "AND p.entryTime >= :startOfDay")
    BigDecimal getSumRealizedProfitUSDTToday(@Param("startOfDay") LocalDateTime startOfDay);

    @Query("SELECT SUM(p.profitUSDTChanges) " +
            "FROM Pair p " +
            "WHERE p.status = 'CLOSED'")
    BigDecimal getSumRealizedProfitUSDTTotal();

    @Query("SELECT SUM(p.profitPercentChanges) " +
            "FROM Pair p " +
            "WHERE p.status = 'CLOSED' " +
            "AND p.entryTime >= :startOfDay")
    BigDecimal getSumRealizedProfitPercentToday(@Param("startOfDay") LocalDateTime startOfDay);


    @Query("SELECT SUM(p.profitPercentChanges) " +
            "FROM Pair p " +
            "WHERE p.status = 'CLOSED'")
    BigDecimal getSumRealizedProfitPercentTotal();
}
