package com.example.statarbitrage.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZScoreEntry {
    private String a;
    private String b;
    private double zscore;
    private double pvalue;
    private String direction;
    private double aCurrentPrice;
    private double bCurrentPrice;
    private double aEntryPrice;
    private double bEntryPrice;
    private String profit;
    private double spread;
    private double mean;
    private String longTicker;
    private String shortTicker;
}
