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

    @Query("SELECT COUNT(*) " +
            "FROM TradeHistory t " +
            "WHERE DATE(t.entryTime) = DATE('now', 'localtime')")
    Long getTradesToday();

    @Query("SELECT COUNT(*) " +
            "FROM TradeHistory t")
    Long getTradesTotal();

    @Query("SELECT SUM(t.currentProfitUSDT) " +
            "FROM TradeHistory t " +
            "WHERE DATE(t.entryTime) = DATE('now', 'localtime') " +
            "AND t.exitReason IS NOT NULL")
    BigDecimal getSumProfitUSDTToday();

    @Query("SELECT SUM(t.currentProfitUSDT) " +
            "FROM TradeHistory t " +
            "WHERE t.exitReason IS NOT NULL")
    BigDecimal getSumProfitUSDTTotal();

    @Query("SELECT SUM(t.currentProfitPercent) " +
            "FROM TradeHistory t " +
            "WHERE DATE(t.entryTime) = DATE('now', 'localtime') " +
            "AND t.exitReason IS NOT NULL")
    BigDecimal getSumProfitPercentToday();

    @Query("SELECT SUM(t.currentProfitPercent) " +
            "FROM TradeHistory t " +
            "WHERE t.exitReason IS NOT NULL")
    BigDecimal getSumProfitPercentTotal();

    @Query("SELECT AVG(t.currentProfitUSDT) " +
            "FROM TradeHistory t " +
            "WHERE DATE(t.entryTime) = DATE('now', 'localtime') " +
            "AND t.exitReason IS NOT NULL")
    BigDecimal getAvgProfitUSDTToday();

    @Query("SELECT AVG(t.currentProfitUSDT) " +
            "FROM TradeHistory t " +
            "WHERE t.exitReason IS NOT NULL")
    BigDecimal getAvgProfitUSDTTotal();

    @Query("SELECT AVG(t.currentProfitPercent) " +
            "FROM TradeHistory t " +
            "WHERE DATE(t.entryTime) = DATE('now', 'localtime') " +
            "AND t.exitReason IS NOT NULL")
    BigDecimal getAvgProfitPercentToday();

    @Query("SELECT AVG(t.currentProfitPercent) " +
            "FROM TradeHistory t " +
            "WHERE t.exitReason IS NOT NULL")
    BigDecimal getAvgProfitPercentTotal();

    @Query("SELECT COUNT(*) " +
            "FROM TradingPair p " +
            "WHERE p.status = :status " +
            "AND DATE(DATETIME(p.entryTime / 1000, 'unixepoch')) = DATE('now')")
    Long getByStatusForToday(@Param("status") TradeStatus status);

    @Query("SELECT COUNT(*) " +
            "FROM TradingPair p " +
            "WHERE p.status = :status")
    Long getByStatusTotal(@Param("status") TradeStatus status);

    @Query("SELECT COUNT(*) " +
            "FROM TradingPair p " +
            "WHERE p.exitReason = :reason " +
            "AND DATE(DATETIME(p.entryTime / 1000, 'unixepoch')) = DATE('now')")
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
