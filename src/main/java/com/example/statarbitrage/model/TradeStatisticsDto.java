package com.example.statarbitrage.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TradeStatisticsDto {
    private long totalTrades;
    private long todayTrades;

    private BigDecimal avgProfit;
    private BigDecimal maxProfit;
    private BigDecimal minProfit;

    private BigDecimal avgProfitToday;
    private BigDecimal maxProfitToday;
    private BigDecimal minProfitToday;

    private long exitByStop;
    private long exitByTake;
    private long exitByOther;
}
