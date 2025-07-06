package com.example.statarbitrage.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ZScoreParam {
    private double zscore;
    private double pvalue;
    private double adfpvalue;
    private double correlation;
    private double alpha;
    private double beta;
    private double spread;
    private double mean;
    private double std;
    private long timestamp;
}
