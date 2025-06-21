package com.example.statarbitrage.repositories;

import com.example.statarbitrage.model.TradeLog;
import com.example.statarbitrage.model.TradeStatisticsDto;
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


    @Query(value = """
                SELECT 
                    COUNT(*) AS total_trades,
                    SUM(CASE WHEN ENTRY_TIME LIKE FORMATDATETIME(NOW(), 'yyyy-MM-dd') || '%' THEN 1 ELSE 0 END) AS today_trades,
            
                    AVG(CURRENT_PROFIT_PERCENT) AS avg_profit,
                    MAX(CURRENT_PROFIT_PERCENT) AS max_profit,
                    MIN(CURRENT_PROFIT_PERCENT) AS min_profit,
            
                    AVG(CASE WHEN ENTRY_TIME LIKE FORMATDATETIME(NOW(), 'yyyy-MM-dd') || '%' THEN CURRENT_PROFIT_PERCENT ELSE NULL END) AS avg_profit_today,
                    MAX(CASE WHEN ENTRY_TIME LIKE FORMATDATETIME(NOW(), 'yyyy-MM-dd') || '%' THEN CURRENT_PROFIT_PERCENT ELSE NULL END) AS max_profit_today,
                    MIN(CASE WHEN ENTRY_TIME LIKE FORMATDATETIME(NOW(), 'yyyy-MM-dd') || '%' THEN CURRENT_PROFIT_PERCENT ELSE NULL END) AS min_profit_today,
            
                    SUM(CASE WHEN EXIT_REASON = 'STOP' THEN 1 ELSE 0 END) AS exit_by_stop,
                    SUM(CASE WHEN EXIT_REASON = 'TAKE' THEN 1 ELSE 0 END) AS exit_by_take,
                    SUM(CASE WHEN EXIT_REASON NOT IN ('STOP', 'TAKE') OR EXIT_REASON IS NULL THEN 1 ELSE 0 END) AS exit_by_other
                FROM TRADE_LOG
            """, nativeQuery = true)
    TradeStatisticsDto getTradeStatistics();
}
