package com.example.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PairAggregatedStatisticsDto {

    private String pairName;
    private long totalTrades;
    private BigDecimal totalProfitUSDT;
    private BigDecimal averageProfitPercent;
    private long averageTradeDuration;
    private long totalAveragingCount;
    private long averageTimeToMinProfit;
    private long averageTimeToMaxProfit;
    private BigDecimal averageZScoreEntry;
    private BigDecimal averageZScoreCurrent;
    private BigDecimal averageZScoreMax;
    private BigDecimal averageZScoreMin;
    private BigDecimal averageCorrelationEntry;
    private BigDecimal averageCorrelationCurrent;
    
    private String mostUsedTimeframe; // Наиболее часто используемый ТФ для этой пары
    private Integer mostUsedCandleCount; // Наиболее часто используемое количество свечей
}