package com.example.statarbitrage.common.model;

import com.example.statarbitrage.common.utils.ZScoreChart;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "pair_data")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PairData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    private TradeStatus status = TradeStatus.SELECTED;

    // Временные данные свечей (не сохраняются в БД)
    @Transient
    private List<Candle> longTickerCandles;
    @Transient
    private List<Candle> shortTickerCandles;

    // Кеш для Z-score чарта (не сохраняется в БД)
    @Transient
    private BufferedImage cachedZScoreChart;
    @Transient
    private long chartGeneratedAt;

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
    private long timestamp;

    // Упрощенные геттеры для совместимости
    public double getCorrelation() {
        return correlationCurrent;
    }

    public void setCorrelation(double correlation) {
        this.correlationCurrent = correlation;
        if (this.correlationEntry == 0.0) {
            this.correlationEntry = correlation;
        }
    }

    public double getPvalue() {
        return pValueCurrent;
    }

    public void setPvalue(double pvalue) {
        this.pValueCurrent = pvalue;
        if (this.pValueEntry == 0.0) {
            this.pValueEntry = pvalue;
        }
    }

    public double getAdfpvalue() {
        return adfPvalueCurrent;
    }

    public void setAdfpvalue(double adfpvalue) {
        this.adfPvalueCurrent = adfpvalue;
        if (this.adfPvalueEntry == 0.0) {
            this.adfPvalueEntry = adfpvalue;
        }
    }

    public double getAlpha() {
        return alphaCurrent;
    }

    public void setAlpha(double alpha) {
        this.alphaCurrent = alpha;
        if (this.alphaEntry == 0.0) {
            this.alphaEntry = alpha;
        }
    }

    public double getBeta() {
        return betaCurrent;
    }

    public void setBeta(double beta) {
        this.betaCurrent = beta;
        if (this.betaEntry == 0.0) {
            this.betaEntry = beta;
        }
    }

    public double getSpread() {
        return spreadCurrent;
    }

    public void setSpread(double spread) {
        this.spreadCurrent = spread;
        if (this.spreadEntry == 0.0) {
            this.spreadEntry = spread;
        }
    }

    public double getMean() {
        return meanCurrent;
    }

    public void setMean(double mean) {
        this.meanCurrent = mean;
        if (this.meanEntry == 0.0) {
            this.meanEntry = mean;
        }
    }

    public double getStd() {
        return stdCurrent;
    }

    public void setStd(double std) {
        this.stdCurrent = std;
        if (this.stdEntry == 0.0) {
            this.stdEntry = std;
        }
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
        if (this.updatedTime == 0) {
            this.updatedTime = timestamp;
        }
    }

    /**
     * Получение Z-Score графика с кешированием
     * Чарт перегенерируется если данные обновлялись или прошло более 1 минуты
     *
     * @return BufferedImage с Z-Score графиком
     */
    public BufferedImage getZScoreChartImage() {
        long currentTime = System.currentTimeMillis();
        long cacheValidityPeriod = 60000; // 1 минута

        // Перегенерируем чарт если:
        // 1. Кеш пустой
        // 2. Данные обновлялись позже создания чарта
        // 3. Прошло более 1 минуты с момента генерации
        boolean shouldRegenerate = cachedZScoreChart == null ||
                updatedTime > chartGeneratedAt ||
                (currentTime - chartGeneratedAt) > cacheValidityPeriod;

        if (shouldRegenerate) {
            try {
                cachedZScoreChart = ZScoreChart.createBufferedImage(this);
                chartGeneratedAt = currentTime;
            } catch (Exception e) {
                // Fallback: возвращаем null если не удалось создать чарт
                cachedZScoreChart = null;
                chartGeneratedAt = 0;
                throw new RuntimeException("Failed to generate Z-Score chart for pair: " +
                        longTicker + "/" + shortTicker, e);
            }
        }

        return cachedZScoreChart;
    }

    /**
     * Очистка кеша чарта (полезно при значительных изменениях данных)
     */
    public void clearChartCache() {
        this.cachedZScoreChart = null;
        this.chartGeneratedAt = 0;
    }
}
