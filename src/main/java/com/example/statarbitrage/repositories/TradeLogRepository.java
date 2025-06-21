package com.example.statarbitrage.repositories;

import com.example.statarbitrage.model.TradeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TradeLogRepository extends JpaRepository<TradeLog, Long> {
    Optional<TradeLog> findByLongTickerAndShortTicker(String longTicker, String shortTicker);

    @Query("SELECT t FROM TradeLog t WHERE t.longTicker = :longTicker AND t.shortTicker = :shortTicker ORDER BY t.timestamp DESC")
    Optional<TradeLog> findLatestByTickers(@Param("longTicker") String longTicker, @Param("shortTicker") String shortTicker);

}
