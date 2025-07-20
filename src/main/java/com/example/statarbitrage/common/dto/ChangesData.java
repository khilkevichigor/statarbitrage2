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
public class ChangesData {
    private BigDecimal minLong;
    private BigDecimal maxLong;
    private BigDecimal longChanges;
    private BigDecimal longCurrentPrice;

    private BigDecimal minShort;
    private BigDecimal maxShort;
    private BigDecimal shortChanges;
    private BigDecimal shortCurrentPrice;

    private BigDecimal minZ;
    private BigDecimal maxZ;

    private BigDecimal minCorr;
    private BigDecimal maxCorr;

    private BigDecimal minProfitChanges;
    private BigDecimal maxProfitChanges;
    private BigDecimal profitChanges;


    private long timeInMinutesSinceEntryToMin;
    private long timeInMinutesSinceEntryToMax;

    private BigDecimal zScoreChanges;
}
