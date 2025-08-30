package com.example.cointegration.repositories;

import com.example.shared.enums.TradeStatus;
import com.example.shared.models.TradingPair;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface TradingPairRepository extends JpaRepository<TradingPair, Long> {
    @Query("SELECT p " +
            "FROM TradingPair p " +
            "WHERE p.status = :status " +
            "AND p.entryTime >= :startOfDay " +
            "ORDER BY p.entryTime DESC")
    List<TradingPair> findAllByStatusOrderByEntryTimeTodayDesc(
            @Param("status") TradeStatus status,
            @Param("startOfDay") Long startOfDay);

    // 1. Сортировка по entryTime (от новых к старым)
    @Query("SELECT p " +
            "FROM TradingPair p " +
            "WHERE p.status = :status " +
            "ORDER BY p.entryTime DESC")
    List<TradingPair> findAllByStatusOrderByEntryTimeDesc(@Param("status") TradeStatus status);

    @Query("SELECT p " +
            "FROM TradingPair p " +
            "WHERE p.status = :status " +
            "ORDER BY p.updatedTime DESC")
    List<TradingPair> findAllByStatusOrderByUpdatedTimeDesc(@Param("status") TradeStatus status);

    List<TradingPair> findAllByStatusIn(List<TradeStatus> statuses);

    List<TradingPair> findByLongTickerAndShortTicker(String longTicker, String shortTicker);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE " +
            "FROM TradingPair p " +
            "WHERE p.status = :status")
    int deleteAllByStatus(@Param("status") TradeStatus status);

    @Query("SELECT COUNT(p) " +
            "FROM TradingPair p " +
            "WHERE p.status = :status")
    int countByStatus(@Param("status") TradeStatus status);
}
