package com.example.core.common.dto;

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

    private BigDecimal avgProfitUSDTToday;
    private BigDecimal avgProfitUSDTTotal;

    private BigDecimal avgProfitPercentToday;
    private BigDecimal avgProfitPercentTotal;

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

    private long exitByBreakevenToday;
    private long exitByBreakevenTotal;

    private long exitByNegativeZMinProfitToday;
    private long exitByNegativeZMinProfitTotal;

    private long exitByManuallyToday;
    private long exitByManuallyTotal;

    private BigDecimal sumProfitUnrealizedUSDTToday;
    private BigDecimal sumProfitUnrealizedUSDTTotal;

    private BigDecimal sumProfitUnrealizedPercentToday;
    private BigDecimal sumProfitUnrealizedPercentTotal;

    private BigDecimal sumProfitRealizedUSDTToday;
    private BigDecimal sumProfitRealizedUSDTTotal;

    private BigDecimal sumProfitRealizedPercentToday;
    private BigDecimal sumProfitRealizedPercentTotal;

    private BigDecimal sumProfitCombinedUSDTToday;
    private BigDecimal sumProfitCombinedUSDTTotal;

    private BigDecimal sumProfitCombinedPercentToday;
    private BigDecimal sumProfitCombinedPercentTotal;
}
