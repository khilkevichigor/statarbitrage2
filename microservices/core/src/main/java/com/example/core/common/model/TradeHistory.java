package com.example.core.common.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Это сущность содеожит специфическую инфу о трейдах которой нет в PairData
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "trade_history")
public class TradeHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "long_ticker")
    private String longTicker;

    @Column(name = "short_ticker")
    private String shortTicker;

    @Column(name = "pair_uuid")
    private String pairUuid;

    @Column(name = "min_profit_minutes")
    private String minProfitMinutes;

    @Column(name = "max_profit_minutes")
    private String maxProfitMinutes;

    @Column(name = "min_profit_percent")
    private BigDecimal minProfitPercent;

    @Column(name = "max_profit_percent")
    private BigDecimal maxProfitPercent;

    @Column(name = "current_profit_usdt")
    private BigDecimal currentProfitUSDT;

    @Column(name = "current_profit_percent")
    private BigDecimal currentProfitPercent;

    @Column(name = "min_long_percent")
    private BigDecimal minLongPercent;

    @Column(name = "max_long_percent")
    private BigDecimal maxLongPercent;

    @Column(name = "current_long_percent")
    private BigDecimal currentLongPercent;

    @Column(name = "min_short_percent")
    private BigDecimal minShortPercent;

    @Column(name = "max_short_percent")
    private BigDecimal maxShortPercent;

    @Column(name = "current_short_percent")
    private BigDecimal currentShortPercent;

    @Column(name = "min_z")
    private BigDecimal minZ;

    @Column(name = "max_z")
    private BigDecimal maxZ;

    @Column(name = "current_z")
    private double currentZ;

    @Column(name = "min_corr")
    private BigDecimal minCorr;

    @Column(name = "max_corr")
    private BigDecimal maxCorr;

    @Column(name = "current_corr")
    private double currentCorr;

    @Column(name = "exit_take")
    private double exitTake;

    @Column(name = "exit_stop")
    private double exitStop;

    @Column(name = "exit_z_min")
    private double exitZMin;

    @Column(name = "exit_z_max")
    private double exitZMax;

    @Column(name = "exit_time_minutes")
    private double exitTimeMinutes;

    @Column(name = "exit_reason")
    private String exitReason;

    @Column(name = "entry_time")
    private String entryTime;

    @Column(name = "timestamp")
    private String timestamp;

}
