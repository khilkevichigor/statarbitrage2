package com.example.statarbitrage.common.model;

import com.example.statarbitrage.common.dto.Candle;
import com.example.statarbitrage.common.dto.ZScoreParam;
import com.example.statarbitrage.common.utils.ZScoreChart;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "pair_data", indexes = {
        @Index(name = "idx_pairdata_uuid", columnList = "uuid", unique = true)
})
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Slf4j
public class PairData {

    //todo подумать что бы сделать поля Settings, Changes, ZScoreData а то каша

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "uuid", nullable = false, updatable = false)
    private String uuid = UUID.randomUUID().toString();

    @Version
    private Long version;

    @Enumerated(EnumType.STRING)
    private TradeStatus status = TradeStatus.SELECTED;
    private String errorDescription;

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

    // История Z-Score данных (сохраняется в БД как JSON)
    @Column(columnDefinition = "TEXT")
    private String zScoreHistoryJson;

    // Временный список для работы с историей Z-Score (не сохраняется в БД)
    @Transient
    private List<ZScoreParam> zScoreHistory;

    private String longTicker;
    private String shortTicker;
    private String pairName;

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

    private BigDecimal longUSDTChanges;
    private BigDecimal longPercentChanges;

    private BigDecimal shortUSDTChanges;
    private BigDecimal shortPercentChanges;

    private BigDecimal profitUSDTChanges;
    private BigDecimal profitPercentChanges;

    private long timeInMinutesSinceEntryToMinProfit;
    private long timeInMinutesSinceEntryToMaxProfit;

    private BigDecimal minProfitChanges;
    private BigDecimal maxProfitChanges;

    private long timestamp; //время создания PairData (fetch)
    private long entryTime; //время начала трейда
    private long updatedTime; //время обновления пары

    private BigDecimal maxZ;
    private BigDecimal minZ;
    private BigDecimal maxLong;
    private BigDecimal minLong;
    private BigDecimal maxShort;
    private BigDecimal minShort;
    private BigDecimal maxCorr;
    private BigDecimal minCorr;

    private String exitReason;

    private boolean isVirtual;

    //todo может сюда еще сетить либо целый Settings либо поля что бы PairData была полностью информативная

    public PairData(String longTicker, String shortTicker) {
        this.longTicker = longTicker;
        this.shortTicker = shortTicker;
        this.pairName = getPairName();
    }

    public String getPairName() {
        if (pairName == null || pairName.isEmpty()) {
            return longTicker + "/" + shortTicker;
        }
        return pairName;
    }

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
     * Добавить новую точку в историю Z-Score
     *
     * @param zScoreParam новая точка данных
     */
    public void addZScorePoint(ZScoreParam zScoreParam) {
        if (zScoreHistory == null) {
            zScoreHistory = new ArrayList<>();
        }

        // Проверяем, нет ли уже точки с таким же timestamp (избегаем дубликатов)
        boolean exists = zScoreHistory.stream()
                .anyMatch(existing -> existing.getTimestamp() == zScoreParam.getTimestamp());

        if (!exists) {
            zScoreHistory.add(zScoreParam);

            saveZScoreHistoryToJson();
            clearChartCache(); // Очищаем кеш чарта при добавлении новых данных
        }
    }

    /**
     * Получить историю Z-Score данных
     *
     * @return список ZScoreParam
     */
    public List<ZScoreParam> getZScoreHistory() {
        if (zScoreHistory == null && zScoreHistoryJson != null && !zScoreHistoryJson.isEmpty()) {
            loadZScoreHistoryFromJson();
        }
        return zScoreHistory != null ? zScoreHistory : new ArrayList<>();
    }

    /**
     * Сериализация истории Z-Score в JSON для сохранения в БД
     */
    private void saveZScoreHistoryToJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            this.zScoreHistoryJson = mapper.writeValueAsString(zScoreHistory);
        } catch (Exception e) {
            log.error("Ошибка сериализации истории Z-Score для пары {}/{}", longTicker, shortTicker, e);
        }
    }

    /**
     * Десериализация истории Z-Score из JSON при загрузке из БД
     */
    private void loadZScoreHistoryFromJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            TypeReference<List<ZScoreParam>> typeRef = new TypeReference<>() {
            };
            this.zScoreHistory = mapper.readValue(zScoreHistoryJson, typeRef);
        } catch (Exception e) {
            log.error("Ошибка десериализации истории Z-Score для пары {}/{}", longTicker, shortTicker, e);
            this.zScoreHistory = new ArrayList<>();
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

    // Методы для версионности (нужны для работы с @Version)
    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
