package com.example.statarbitrage.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Settings {
    private String timeframe;
    private int candleLimit;
    private int windowSize;
    private double significanceLevel;
    private double adfSignificanceLevel;
    private int checkInterval;
    private double capitalLong;
    private double capitalShort;
    private double leverage;
    private double feePctPerTrade;

    private double exitTake;
    private double exitStop;
    private double exitZ;
    private double exitTimeHours;

    private double minCorrelation;
    private double minVolume;
}
