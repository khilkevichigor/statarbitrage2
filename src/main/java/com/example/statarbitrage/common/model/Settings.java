package com.example.statarbitrage.common.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Settings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String timeframe;
    private double candleLimit;
    private double minZ;
    private double minWindowSize;
    private double minPValue;
    private double minAdfValue;
    private double minRSquared;
    private double minCorrelation;
    private double minVolume;

    private double checkInterval;

    private double capitalLong;
    private double capitalShort;
    private double leverage;
    private double feePctPerTrade;

    private double exitTake;
    private double exitStop;
    private double exitZMin;
    private double exitZMax;
    private double exitZMaxPercent;
    private double exitTimeHours;

    private double usePairs;

    private boolean autoTradingEnabled = false;

    public double getExpectedZParamsCount() {
        return this.getCandleLimit() - this.getMinWindowSize();
    }
}
