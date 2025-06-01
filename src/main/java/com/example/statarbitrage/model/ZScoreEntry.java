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
    private double zscore;
    private double pvalue;
    private double spread;
    private double mean;
    private String longticker;
    private String shortticker;
    private double longtickercurrentprice;
    private double shorttickercurrentprice;
    private long timestamp;
}
