package com.example.statarbitrage.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EntryData {
    private String longticker;
    private String shortticker;
    private double longTickerCurrentPrice;
    private double shortTickerCurrentPrice;
    private double longTickerEntryPrice;
    private double shortTickerEntryPrice;
    private long entryTime;
    private String profit;
    private double meanEntry;
    private double spreadEntry;
    private double zScoreEntry;
    private String chartProfitMessage;
}
