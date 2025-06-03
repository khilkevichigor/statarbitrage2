package com.example.statarbitrage.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ZScoreEntry {
    private String pair;
    private double zscore;
    private double pvalue;
    private double adfpvalue;
    private double correlation;
    private double alpha;
    private double beta;
    private double spread;
    private double mean;
    private double std;
    private String longticker;
    private String shortticker;
    private double longtickercurrentprice;
    private double shorttickercurrentprice;
    private long timestamp;
}
