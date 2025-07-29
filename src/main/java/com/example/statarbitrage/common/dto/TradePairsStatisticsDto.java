package com.example.statarbitrage.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TradePairsStatisticsDto {

    private long tradePairsWithErrorToday;
    private long tradePairsWithErrorTotal;

    private long tradePairsToday;
    private long tradePairsTotal;

    private BigDecimal avgProfitToday;
    private BigDecimal avgProfitTotal;

    private BigDecimal sumProfitUSDTToday;
    private BigDecimal sumProfitUSDTTotal;

    private BigDecimal sumProfitPercentToday;
    private BigDecimal sumProfitPercentTotal;

    private long exitByStopToday;
    private long exitByStopTotal;

    private long exitByTakeToday;
    private long exitByTakeTotal;

    private long exitByZMinToday;
    private long exitByZMinTotal;

    private long exitByZMaxToday;
    private long exitByZMaxTotal;

    private long exitByTimeToday;
    private long exitByTimeTotal;

    private long exitByManuallyToday;
    private long exitByManuallyTotal;

    private BigDecimal sumProfitUnrealizedUSDT;
    private BigDecimal sumProfitUnrealizedPercent;

    private BigDecimal sumProfitRealizedUSDT;
    private BigDecimal sumProfitRealizedPercent;

    private BigDecimal sumProfitCombinedUSDT;
    private BigDecimal sumProfitCombinedPercent;
}
