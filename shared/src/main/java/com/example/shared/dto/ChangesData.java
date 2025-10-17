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
public class ChangesData {
    private BigDecimal minLong;
    private BigDecimal maxLong;
    private BigDecimal longUSDTChanges;
    private BigDecimal longPercentChanges;
    private BigDecimal longCurrentPrice;

    private BigDecimal minShort;
    private BigDecimal maxShort;
    private BigDecimal shortUSDTChanges;
    private BigDecimal shortPercentChanges;
    private BigDecimal shortCurrentPrice;

    private BigDecimal minZ;
    private BigDecimal maxZ;

    private BigDecimal minCorr;
    private BigDecimal maxCorr;

    private BigDecimal minProfitChanges;
    private BigDecimal maxProfitChanges;
    private BigDecimal profitPercentChanges;
    private BigDecimal profitUSDTChanges;

    private long timeInMinutesSinceEntryToMinProfit;
    private long timeInMinutesSinceEntryToMaxProfit;

    private BigDecimal zScoreChanges;
}
