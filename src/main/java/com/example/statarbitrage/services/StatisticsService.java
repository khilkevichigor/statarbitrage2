package com.example.statarbitrage.services;

import com.example.statarbitrage.model.TradeStatisticsDto;
import com.example.statarbitrage.repositories.TradeLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final TradeLogRepository tradeLogRepository;

    public void printTradeStatistics() {
        TradeStatisticsDto stats = tradeLogRepository.getTradeStatistics();
        log.info("""
                        📊 TRADE STATS:
                        - Total trades: {}
                        - Today trades: {}
                        
                        📈 Profit:
                        - Avg: {}%
                        - Max: {}%
                        - Min: {}%
                        
                        📈 Today Profit:
                        - Avg: {}%
                        - Max: {}%
                        - Min: {}%
                        
                        ❌ Exit Reasons:
                        - STOP: {}
                        - TAKE: {}
                        - OTHER: {}
                        """,
                stats.getTotalTrades(),
                stats.getTodayTrades(),

                format(stats.getAvgProfit()),
                format(stats.getMaxProfit()),
                format(stats.getMinProfit()),

                format(stats.getAvgProfitToday()),
                format(stats.getMaxProfitToday()),
                format(stats.getMinProfitToday()),

                stats.getExitByStop(),
                stats.getExitByTake(),
                stats.getExitByOther()
        );
    }

    // Вспомогательный метод для округления BigDecimal до 2 знаков после запятой
    private String format(BigDecimal value) {
        return value == null ? "n/a" : value.setScale(2, RoundingMode.HALF_UP).toString();
    }
}
