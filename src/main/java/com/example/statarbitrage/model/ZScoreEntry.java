package com.example.statarbitrage.model;

import lombok.Data;

@Data
public class ZScoreEntry {
    private double zscore;
    private double pvalue;
    private double spread;
    private double mean;
    private String longticker;
    private String shortticker;
    private double longtickercurrentprice; //todo remove
    private double shorttickercurrentprice; //todo remove
    private long timestamp; //todo remove?

}
