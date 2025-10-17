package com.example.cointegration.repositories;

import com.example.shared.enums.TradeStatus;
import com.example.shared.models.TradeHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    @Query("SELECT COUNT(*) " +
            "FROM TradeHistory t " +
            "WHERE t.entryTime >= :startOfDay AND t.entryTime < :startOfNextDay")
    Long getTradesToday(@Param("startOfDay") LocalDateTime startOfDay, @Param("startOfNextDay") LocalDateTime startOfNextDay);

    @Query("SELECT COUNT(*) " +
            "FROM TradeHistory t")
    Long getTradesTotal();

    @Query("SELECT SUM(t.currentProfitUSDT) " +
            "FROM TradeHistory t " +
            "WHERE t.entryTime >= :startOfDay AND t.entryTime < :startOfNextDay " +
            "AND t.exitReason IS NOT NULL")
    BigDecimal getSumProfitUSDTToday(@Param("startOfDay") LocalDateTime startOfDay, @Param("startOfNextDay") LocalDateTime startOfNextDay);

    @Query("SELECT SUM(t.currentProfitUSDT) " +
            "FROM TradeHistory t " +
            "WHERE t.exitReason IS NOT NULL")
    BigDecimal getSumProfitUSDTTotal();

    @Query("SELECT SUM(t.currentProfitPercent) " +
            "FROM TradeHistory t " +
            "WHERE t.entryTime >= :startOfDay AND t.entryTime < :startOfNextDay " +
            "AND t.exitReason IS NOT NULL")
    BigDecimal getSumProfitPercentToday(@Param("startOfDay") LocalDateTime startOfDay, @Param("startOfNextDay") LocalDateTime startOfNextDay);

    @Query("SELECT SUM(t.currentProfitPercent) " +
            "FROM TradeHistory t " +
            "WHERE t.exitReason IS NOT NULL")
    BigDecimal getSumProfitPercentTotal();

    @Query("SELECT AVG(t.currentProfitUSDT) " +
            "FROM TradeHistory t " +
            "WHERE t.entryTime >= :startOfDay AND t.entryTime < :startOfNextDay " +
            "AND t.exitReason IS NOT NULL")
    BigDecimal getAvgProfitUSDTToday(@Param("startOfDay") LocalDateTime startOfDay, @Param("startOfNextDay") LocalDateTime startOfNextDay);

    @Query("SELECT AVG(t.currentProfitUSDT) " +
            "FROM TradeHistory t " +
            "WHERE t.exitReason IS NOT NULL")
    BigDecimal getAvgProfitUSDTTotal();

    @Query("SELECT AVG(t.currentProfitPercent) " +
            "FROM TradeHistory t " +
            "WHERE t.entryTime >= :startOfDay AND t.entryTime < :startOfNextDay " +
            "AND t.exitReason IS NOT NULL")
    BigDecimal getAvgProfitPercentToday(@Param("startOfDay") LocalDateTime startOfDay, @Param("startOfNextDay") LocalDateTime startOfNextDay);

    @Query("SELECT AVG(t.currentProfitPercent) " +
            "FROM TradeHistory t " +
            "WHERE t.exitReason IS NOT NULL")
    BigDecimal getAvgProfitPercentTotal();

    @Query("SELECT COUNT(*) " +
            "FROM Pair p " +
            "WHERE p.type = 'TRADING' AND p.status = :status " +
            "AND p.entryTime >= :startOfDay AND p.entryTime < :startOfNextDay")
    Long getByStatusForToday(@Param("status") TradeStatus status, @Param("startOfDay") LocalDateTime startOfDay, @Param("startOfNextDay") LocalDateTime startOfNextDay);

    @Query("SELECT COUNT(*) " +
            "FROM Pair p " +
            "WHERE p.type = 'TRADING' AND p.status = :status")
    Long getByStatusTotal(@Param("status") TradeStatus status);

    @Query("SELECT COUNT(*) " +
            "FROM Pair p " +
            "WHERE p.type = 'TRADING' AND p.exitReason = :reason " +
            "AND p.entryTime >= :startOfDay AND p.entryTime < :startOfNextDay")
    Long getByExitReasonForToday(@Param("reason") String reason, @Param("startOfDay") LocalDateTime startOfDay, @Param("startOfNextDay") LocalDateTime startOfNextDay);

    @Query("SELECT COUNT(*) " +
            "FROM Pair p " +
            "WHERE p.type = 'TRADING' AND p.exitReason = :reason")
    Long getAllByExitReason(@Param("reason") String reason);

    @Query("SELECT SUM(p.profitUSDTChanges) " +
            "FROM Pair p " +
            "WHERE p.type = 'TRADING' AND p.status = 'CLOSED' " +
            "AND p.entryTime >= :startOfDay")
    BigDecimal getSumRealizedProfitUSDTToday(@Param("startOfDay") LocalDateTime startOfDay);

    @Query("SELECT SUM(p.profitUSDTChanges) " +
            "FROM Pair p " +
            "WHERE p.type = 'TRADING' AND p.status = 'CLOSED'")
    BigDecimal getSumRealizedProfitUSDTTotal();

    @Query("SELECT SUM(p.profitPercentChanges) " +
            "FROM Pair p " +
            "WHERE p.type = 'TRADING' AND p.status = 'CLOSED' " +
            "AND p.entryTime >= :startOfDay")
    BigDecimal getSumRealizedProfitPercentToday(@Param("startOfDay") LocalDateTime startOfDay);


    @Query("SELECT SUM(p.profitPercentChanges) " +
            "FROM Pair p " +
            "WHERE p.type = 'TRADING' AND p.status = 'CLOSED'")
    BigDecimal getSumRealizedProfitPercentTotal();

    // Методы-обёртки для обратной совместимости
    default Long getTradesToday() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime startOfNextDay = startOfDay.plusDays(1);
        return getTradesToday(startOfDay, startOfNextDay);
    }

    default BigDecimal getSumProfitUSDTToday() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime startOfNextDay = startOfDay.plusDays(1);
        return getSumProfitUSDTToday(startOfDay, startOfNextDay);
    }

    default BigDecimal getSumProfitPercentToday() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime startOfNextDay = startOfDay.plusDays(1);
        return getSumProfitPercentToday(startOfDay, startOfNextDay);
    }

    default BigDecimal getAvgProfitUSDTToday() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime startOfNextDay = startOfDay.plusDays(1);
        return getAvgProfitUSDTToday(startOfDay, startOfNextDay);
    }

    default BigDecimal getAvgProfitPercentToday() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime startOfNextDay = startOfDay.plusDays(1);
        return getAvgProfitPercentToday(startOfDay, startOfNextDay);
    }

    default Long getByStatusForToday(TradeStatus status) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime startOfNextDay = startOfDay.plusDays(1);
        return getByStatusForToday(status, startOfDay, startOfNextDay);
    }

    default Long getByExitReasonForToday(String reason) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime startOfNextDay = startOfDay.plusDays(1);
        return getByExitReasonForToday(reason, startOfDay, startOfNextDay);
    }
}
