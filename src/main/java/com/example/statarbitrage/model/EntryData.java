package com.example.statarbitrage.model;

import lombok.Data;

@Data
public class EntryData {
    //    private double zscore;
//    private double pvalue;
//    private double spread;
//    private double mean;
    private String longticker;
    private String shortticker;

    private double longTickerCurrentPrice;
    private double shortTickerCurrentPrice;
    private double longTickerEntryPrice;
    private double shortTickerEntryPrice;
    private String profit;

    private double meanEntry;
    private double spreadEntry;

}
