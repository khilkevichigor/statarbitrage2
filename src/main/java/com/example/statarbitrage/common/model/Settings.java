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

    private double maxPositionPercentPerPair;
    private double maxShortMarginSize;
    private double maxLongMarginSize;
    private double leverage;
    private double feePctPerTrade;

    private double exitTake;
    private double exitStop;
    private double exitZMin;
    private double exitZMax;
    private double exitZMaxPercent;
    private double exitTimeHours;

    private double usePairs;

    @Builder.Default
    private boolean autoTradingEnabled = false;
    @Builder.Default
    private boolean virtualTradingEnabled = true;

    // Флаги включения/выключения фильтров
    @Builder.Default
    private boolean useMinZFilter = true;
    @Builder.Default
    private boolean useMinRSquaredFilter = true;
    @Builder.Default
    private boolean useMinPValueFilter = true;
    @Builder.Default
    private boolean useMinAdfValueFilter = true;
    @Builder.Default
    private boolean useMinCorrelationFilter = true;
    @Builder.Default
    private boolean useMinVolumeFilter = true;

    // Флаги включения/выключения стратегий выхода
    @Builder.Default
    private boolean useExitTake = true;
    @Builder.Default
    private boolean useExitStop = true;
    @Builder.Default
    private boolean useExitZMin = true;
    @Builder.Default
    private boolean useExitZMax = true;
    @Builder.Default
    private boolean useExitZMaxPercent = true;
    @Builder.Default
    private boolean useExitTimeHours = true;

    public double getExpectedZParamsCount() {
        return this.getCandleLimit() - this.getMinWindowSize();
    }
}
