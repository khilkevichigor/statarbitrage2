package com.example.statarbitrage.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PairData {

    private String a;
    private String b;

    private List<ZScoreParam> zScoreParams;
    private ConcurrentHashMap<String, List<Candle>> candles;

    private String longTicker;
    private String shortTicker;

    private String tradeType;

    private double aTickerEntryPrice;
    private double aTickerCurrentPrice;

    private double bTickerEntryPrice;
    private double bTickerCurrentPrice;

    private double meanEntry;
    private double meanCurrent;

    private double spreadEntry;
    private double spreadCurrent;

    private double zScoreEntry;
    private double zScoreCurrent;

    private double pValueEntry;
    private double pValueCurrent;

    private double adfPvalueEntry;
    private double adfPvalueCurrent;

    private double correlationEntry;
    private double correlationCurrent;

    private double alphaEntry;
    private double alphaCurrent;

    private double betaEntry;
    private double betaCurrent;

    private double stdEntry;
    private double stdCurrent;

    private BigDecimal zScoreChanges;

    private BigDecimal longChanges;
    private BigDecimal shortChanges;
    private BigDecimal profitChanges;

    private long timeInMinutesSinceEntryToMin;
    private long timeInMinutesSinceEntryToMax;

    private BigDecimal maxProfitRounded;
    private BigDecimal minProfitRounded;

    private long entryTime;
}
