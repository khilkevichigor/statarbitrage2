package com.example.statarbitrage.model;

import com.example.statarbitrage.vaadin.services.TradeStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PairData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Transient
    private List<ZScoreParam> zScoreParams;

    @Transient
    private List<Candle> longTickerCandles;
    @Transient
    private List<Candle> shortTickerCandles;

    @Enumerated(EnumType.STRING)
    private TradeStatus status = TradeStatus.SELECTED; // Значение по умолчанию

    private String longTicker;
    private String shortTicker;

    private double longTickerEntryPrice;
    private double longTickerCurrentPrice;

    private double shortTickerEntryPrice;
    private double shortTickerCurrentPrice;

    private double meanEntry;
    private double meanCurrent;

    private double spreadEntry;
    private double spreadCurrent;

    private double zScoreEntry;
    private double zScoreCurrent;

    private double pValueEntry;
    private double pValueCurrent;

    private double adfPvalueEntry;
    private double adfPvalueCurrent;

    private double correlationEntry;
    private double correlationCurrent;

    private double alphaEntry;
    private double alphaCurrent;

    private double betaEntry;
    private double betaCurrent;

    private double stdEntry;
    private double stdCurrent;

    private BigDecimal zScoreChanges;

    private BigDecimal longChanges;
    private BigDecimal shortChanges;
    private BigDecimal profitChanges;

    private long timeInMinutesSinceEntryToMin;
    private long timeInMinutesSinceEntryToMax;

    private BigDecimal maxProfitRounded;
    private BigDecimal minProfitRounded;

    private long entryTime;
    private long updatedTime;

    private BigDecimal maxZ;
    private BigDecimal minZ;
    private BigDecimal maxLong;
    private BigDecimal minLong;
    private BigDecimal maxShort;
    private BigDecimal minShort;
    private BigDecimal maxCorr;
    private BigDecimal minCorr;

    private String exitReason;

    public ZScoreParam getLastZScoreParam() {
        return zScoreParams.get(zScoreParams.size() - 1);
    }
}
