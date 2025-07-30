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

//    private BigDecimal longAllocatedAmount; //todo удалить?
//    private BigDecimal shortAllocatedAmount; //todo удалить?
//    private BigDecimal totalInvestmentUSDT; //todo удалить?

    private long timeInMinutesSinceEntryToMin;
    private long timeInMinutesSinceEntryToMax;

    private BigDecimal zScoreChanges;

//    @Override
//    public String toString() {
//        return "\nChangesData {\n" +
//                "  minLong: " + minLong + "\n" +
//                "  maxLong: " + maxLong + "\n" +
//                "  longChanges: " + longChanges + "\n" +
//                "  longCurrentPrice: " + longCurrentPrice + "\n" +
//                "  minShort: " + minShort + "\n" +
//                "  maxShort: " + maxShort + "\n" +
//                "  shortChanges: " + shortChanges + "\n" +
//                "  shortCurrentPrice: " + shortCurrentPrice + "\n" +
//                "  minZ: " + minZ + "\n" +
//                "  maxZ: " + maxZ + "\n" +
//                "  minCorr: " + minCorr + "\n" +
//                "  maxCorr: " + maxCorr + "\n" +
//                "  minProfitChanges: " + minProfitChanges + "\n" +
//                "  maxProfitChanges: " + maxProfitChanges + "\n" +
//                "  profitPercentChanges: " + profitPercentChanges + "\n" +
//                "  profitUSDTChanges: " + profitUSDTChanges + "\n" +
////                "  longAllocatedAmount: " + longAllocatedAmount + "\n" + //todo удалить?
////                "  shortAllocatedAmount: " + shortAllocatedAmount + "\n" + //todo удалить?
////                "  totalInvestmentUSDT: " + totalInvestmentUSDT + "\n" + //todo удалить?
//                "  timeInMinutesSinceEntryToMin: " + timeInMinutesSinceEntryToMin + "\n" +
//                "  timeInMinutesSinceEntryToMax: " + timeInMinutesSinceEntryToMax + "\n" +
//                "  zScoreChanges: " + zScoreChanges + "\n" +
//                '}';
//    }
}
