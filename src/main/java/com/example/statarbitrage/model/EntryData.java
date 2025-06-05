package com.example.statarbitrage.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EntryData {
    private String a;
    private String b;

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

    private String profitStr;
    private BigDecimal profit;

    private BigDecimal zScoreChanges;

    private long entryTime;
    private String chartProfitMessage;
}
