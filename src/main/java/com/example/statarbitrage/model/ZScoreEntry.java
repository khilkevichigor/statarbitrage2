package com.example.statarbitrage.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZScoreEntry {
    private double zscore;
    private double pvalue;
    private double spread;
    private double mean;
    private String longticker;
    private String shortticker;

    private double longTickerCurrentPrice;
    private double shortTickerCurrentPrice;
    private double longTickerEntryPrice;
    private double shortTickerEntryPrice;
    private String profit;
}
