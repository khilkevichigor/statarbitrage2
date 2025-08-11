package com.example.statarbitrage.common.model;

import com.example.statarbitrage.common.dto.Candle;
import com.example.statarbitrage.common.dto.ProfitHistoryItem;
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

import static com.example.statarbitrage.common.utils.BigDecimalUtil.safeScale;

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

    @Column(columnDefinition = "TEXT")
    private String profitHistoryJson;

    // Временный список для работы с историей Z-Score (не сохраняется в БД)
    @Transient
    private List<ZScoreParam> zScoreHistory;

    @Transient
    private List<ProfitHistoryItem> profitHistory;

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

    private BigDecimal portfolioBeforeTradeUSDT; //баланс до трейда пары
    private BigDecimal profitUSDTChanges; //профит
    private BigDecimal portfolioAfterTradeUSDT;//баланс после трейда пары
    private BigDecimal profitPercentChanges;

    private long minutesToMinProfitPercent;
    private long minutesToMaxProfitPercent;

    private BigDecimal minProfitPercentChanges;
    private BigDecimal maxProfitPercentChanges;

    private String timeToMinProfit;
    private String timeToMaxProfit;
    private String profitLong;
    private String profitShort;
    private String profitCommon;

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

    private boolean closeAtBreakeven;

    //todo может сюда еще сетить либо целый Settings либо поля что бы PairData была полностью информативная
    private String settingsTimeframe;
    private double settingsCandleLimit;
    private double settingsMinZ;
    private double settingsMinWindowSize;
    private double settingsMinPValue;
    private double settingsMaxAdfValue;
    private double settingsMinRSquared;
    private double settingsMinCorrelation;
    private double settingsMinVolume;
    private double settingsCheckInterval;
    private double settingsMaxLongMarginSize;
    private double settingsMaxShortMarginSize;
    private double settingsLeverage;
    private double settingsExitTake;
    private double settingsExitStop;
    private double settingsExitZMin;
    private double settingsExitZMax;
    private double settingsExitZMaxPercent;
    private double settingsExitTimeHours;
    private double settingsExitBreakEvenPercent;
    private double settingsUsePairs;
    private boolean settingsAutoTradingEnabled;
    private boolean settingsUseMinZFilter;
    private boolean settingsUseMinRSquaredFilter;
    private boolean settingsUseMinPValueFilter;
    private boolean settingsUseMaxAdfValueFilter;
    private boolean settingsUseMinCorrelationFilter;
    private boolean settingsUseMinVolumeFilter;
    private boolean settingsUseExitTake;
    private boolean settingsUseExitStop;
    private boolean settingsUseExitZMin;
    private boolean settingsUseExitZMax;
    private boolean settingsUseExitZMaxPercent;
    private boolean settingsUseExitTimeHours;
    private boolean settingsUseExitBreakEvenPercent;
    private String settingsMinimumLotBlacklist;

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
     * Добавить новую точку в историю профита
     *
     * @param profitHistoryItem новая точка данных
     */
    public void addProfitHistoryPoint(ProfitHistoryItem profitHistoryItem) {
        // Гарантируем, что история загружена из JSON
        getProfitHistory();

        if (profitHistory == null) {
            profitHistory = new ArrayList<>();
        }

        // Проверяем, нет ли уже точки с таким же timestamp (избегаем дубликатов)
        boolean exists = profitHistory.stream()
                .anyMatch(existing -> existing.getTimestamp() == profitHistoryItem.getTimestamp());

        if (!exists) {
            profitHistory.add(profitHistoryItem);
            saveProfitHistoryToJson();
        }
    }

    /**
     * Получить историю профита
     *
     * @return список ProfitHistoryItem
     */
    public List<ProfitHistoryItem> getProfitHistory() {
        if (profitHistory == null && profitHistoryJson != null && !profitHistoryJson.isEmpty()) {
            loadProfitHistoryFromJson();
        }
        return profitHistory != null ? profitHistory : new ArrayList<>();
    }

    /**
     * Сериализация истории профита в JSON для сохранения в БД
     */
    private void saveProfitHistoryToJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            this.profitHistoryJson = mapper.writeValueAsString(profitHistory);
        } catch (Exception e) {
            log.error("Ошибка сериализации истории профита для пары {}", pairName, e);
        }
    }

    /**
     * Десериализация истории профита из JSON при загрузке из БД
     */
    private void loadProfitHistoryFromJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            TypeReference<List<com.example.statarbitrage.common.dto.ProfitHistoryItem>> typeRef = new TypeReference<>() {
            };
            this.profitHistory = mapper.readValue(profitHistoryJson, typeRef);
        } catch (Exception e) {
            log.error("Ошибка десериализации истории профита для пары {}", pairName, e);
            this.profitHistory = new ArrayList<>();
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

    /**
     * Обновляет поля перед экспортом в csv чтобы они небыли пустыми
     */
    public void updateFormattedFieldsBeforeExportToCsv() {
        getFormattedTimeToMinProfit();
        getFormattedTimeToMaxProfit();
        getFormattedProfitCommon();
        getFormattedProfitLong();
        getFormattedProfitShort();
    }

    public String getFormattedTimeToMinProfit() {
        BigDecimal minProfitChanges = safeScale(this.getMinProfitPercentChanges(), 2);
        long minutes = this.getMinutesToMinProfitPercent();
        return String.format("%s%%/%smin",
                minProfitChanges != null ? minProfitChanges.toPlainString() : "N/A",
                minutes);
    }

    public String getFormattedTimeToMaxProfit() {
        BigDecimal maxProfitChanges = safeScale(this.getMaxProfitPercentChanges(), 2);
        long minutes = this.getMinutesToMaxProfitPercent();
        return String.format("%s%%/%smin",
                maxProfitChanges != null ? maxProfitChanges.toPlainString() : "N/A",
                minutes);
    }

    public String getFormattedProfitCommon() {
        BigDecimal profitUSDT = safeScale(this.getProfitUSDTChanges(), 2);
        BigDecimal profitPercent = safeScale(this.getProfitPercentChanges(), 2);
        return String.format("%s$/%s%%",
                profitUSDT != null ? profitUSDT.toPlainString() : "N/A",
                profitPercent != null ? profitPercent.toPlainString() : "N/A");
    }

    public String getFormattedProfitLong() {
        BigDecimal profitUSDT = safeScale(this.getLongUSDTChanges(), 2);
        BigDecimal profitPercent = safeScale(this.getLongPercentChanges(), 2);
        return String.format("%s$/%s%%",
                profitUSDT != null ? profitUSDT.toPlainString() : "N/A",
                profitPercent != null ? profitPercent.toPlainString() : "N/A");
    }

    public String getFormattedProfitShort() {
        BigDecimal profitUSDT = safeScale(this.getShortUSDTChanges(), 2);
        BigDecimal profitPercent = safeScale(this.getShortPercentChanges(), 2);
        return String.format("%s$/%s%%",
                profitUSDT != null ? profitUSDT.toPlainString() : "N/A",
                profitPercent != null ? profitPercent.toPlainString() : "N/A");
    }
}
