package com.example.statarbitrage.common.model;

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
    private Long id;

    private String longTicker;
    private String shortTicker;
    private String pairUuid;

    private String minProfitMinutes;
    private String maxProfitMinutes;
    private BigDecimal minProfitPercent;
    private BigDecimal maxProfitPercent;
    private BigDecimal currentProfitUSDT;
    private BigDecimal currentProfitPercent;

    private BigDecimal minLongPercent;
    private BigDecimal maxLongPercent;
    private BigDecimal currentLongPercent;

    private BigDecimal minShortPercent;
    private BigDecimal maxShortPercent;
    private BigDecimal currentShortPercent;

    private BigDecimal minZ;
    private BigDecimal maxZ;
    private double currentZ;

    private BigDecimal minCorr;
    private BigDecimal maxCorr;
    private double currentCorr;

    private double exitTake;
    private double exitStop;
    private double exitZMin;
    private double exitZMax;
    private double exitTimeHours;

    private String exitReason;
    private String entryTime;
    private String timestamp;

}
