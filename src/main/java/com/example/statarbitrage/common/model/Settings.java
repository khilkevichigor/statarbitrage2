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
    private double maxPValue;
    private double maxAdfValue;
    private double minRSquared;
    private double minCorrelation;
    private double minVolume;

    private double checkInterval;

    private double maxLongMarginSize;
    private double maxShortMarginSize;
    private double leverage;

    private double exitTake;
    private double exitStop;
    private double exitZMin;
    private double exitZMax;
    private double exitZMaxPercent;
    private double exitTimeHours;
    private double exitBreakEvenPercent; //% профита для закрытия по БУ

    private double usePairs;

    @Builder.Default
    private boolean autoTradingEnabled = false;

    // Флаги включения/выключения фильтров
    @Builder.Default
    private boolean useMinZFilter = true;
    @Builder.Default
    private boolean useMinRSquaredFilter = true;
    @Builder.Default
    private boolean useMaxPValueFilter = true;
    @Builder.Default
    private boolean useMaxAdfValueFilter = true;
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
    @Builder.Default
    private boolean useExitBreakEvenPercent = true;
    @Builder.Default
    private boolean useCointegrationStabilityFilter = true; //todo добавить на UI

    // Блэклист тикеров с высокими требованиями к минимальному лоту
    @Column(length = 1000)
    @Builder.Default
    private String minimumLotBlacklist = "ETH-USDT-SWAP,BTC-USDT-SWAP";

    public double getExpectedZParamsCount() {
        return this.getCandleLimit() - this.getMinWindowSize();
    }
}
