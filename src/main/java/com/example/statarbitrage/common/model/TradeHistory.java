package com.example.statarbitrage.common.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TradeHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String longTicker;
    private String shortTicker;

    private BigDecimal currentProfitPercent;
    private BigDecimal minProfitPercent;
    private String minProfitMinutes;
    private BigDecimal maxProfitPercent;
    private String maxProfitMinutes;

    private BigDecimal currentLongPercent;
    private BigDecimal minLongPercent;
    private BigDecimal maxLongPercent;

    private BigDecimal currentShortPercent;
    private BigDecimal minShortPercent;
    private BigDecimal maxShortPercent;

    private double currentZ;
    private BigDecimal minZ;
    private BigDecimal maxZ;

    private double currentCorr;
    private BigDecimal minCorr;
    private BigDecimal maxCorr;

    private double exitTake;
    private double exitStop;
    private double exitZMin;
    private double exitZMax;
    private double exitTimeHours;

    private String exitReason;
    private String entryTime;
    private String timestamp;
}
