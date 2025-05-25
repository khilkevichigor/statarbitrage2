package com.example.statarbitrage.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatArbitrageSettings {
    private String timeframe;
    private int candleLimit;
    private int windowSize;
    private double zscoreEntry;
    private double zscoreExit;
    private double significanceLevel;
    private int positionSize;
    private int depo;
    private int maxPairs;
    private int maxWorkers;
    private int checkInterval;
}
