package com.example.statarbitrage.common.model;

import com.example.statarbitrage.common.annotation.CsvExportable;
import com.example.statarbitrage.common.dto.Candle;
import com.example.statarbitrage.common.dto.PixelSpreadHistoryItem;
import com.example.statarbitrage.common.dto.ProfitHistoryItem;
import com.example.statarbitrage.common.dto.ZScoreParam;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @CsvExportable(order = 1)
    private Long id;

    @CsvExportable(order = 2)
    @Column(columnDefinition = "uuid", nullable = false, updatable = false)
    private String uuid = UUID.randomUUID().toString();

    @Version
    @CsvExportable(order = 3)
    private Long version;

    @Enumerated(EnumType.STRING)
    @CsvExportable(order = 4)
    private TradeStatus status = TradeStatus.SELECTED;

    @CsvExportable(order = 5)
    private String errorDescription;

    //    @Transient
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "pair_data_long_candles",
            joinColumns = @JoinColumn(name = "pair_data_id")
    )
    private List<Candle> longTickerCandles;

    //    @Transient
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "pair_data_short_candles",
            joinColumns = @JoinColumn(name = "pair_data_id")
    )
    private List<Candle> shortTickerCandles;

    @Column(columnDefinition = "TEXT")
    private String zScoreHistoryJson;

    @Column(columnDefinition = "TEXT")
    private String profitHistoryJson;

    @Column(columnDefinition = "TEXT")
    private String pixelSpreadHistoryJson;

    @Transient
    private List<ZScoreParam> zScoreHistory;

    @Transient
    private List<ProfitHistoryItem> profitHistory;

    @Transient
    private List<PixelSpreadHistoryItem> pixelSpreadHistory;

    @CsvExportable(order = 6)
    private String longTicker;
    @CsvExportable(order = 7)
    private String shortTicker;
    @CsvExportable(order = 8)
    private String pairName;

    @CsvExportable(order = 9)
    private double longTickerEntryPrice;
    @CsvExportable(order = 10)
    private double longTickerCurrentPrice;

    @CsvExportable(order = 11)
    private double shortTickerEntryPrice;
    @CsvExportable(order = 12)
    private double shortTickerCurrentPrice;

    @CsvExportable(order = 13)
    private double meanEntry;
    @CsvExportable(order = 14)
    private double meanCurrent;

    @CsvExportable(order = 15)
    private double spreadEntry;
    @CsvExportable(order = 16)
    private double spreadCurrent;

    @CsvExportable(order = 17)
    private double zScoreEntry;
    @CsvExportable(order = 18)
    private double zScoreCurrent;

    @CsvExportable(order = 19)
    private double pValueEntry;
    @CsvExportable(order = 20)
    private double pValueCurrent;

    @CsvExportable(order = 21)
    private double adfPvalueEntry;
    @CsvExportable(order = 22)
    private double adfPvalueCurrent;

    @CsvExportable(order = 23)
    private double correlationEntry;
    @CsvExportable(order = 24)
    private double correlationCurrent;

    @CsvExportable(order = 25)
    private double alphaEntry;
    @CsvExportable(order = 26)
    private double alphaCurrent;

    @CsvExportable(order = 27)
    private double betaEntry;
    @CsvExportable(order = 28)
    private double betaCurrent;

    @CsvExportable(order = 29)
    private double stdEntry;
    @CsvExportable(order = 30)
    private double stdCurrent;

    @CsvExportable(order = 31)
    private BigDecimal zScoreChanges;

    @CsvExportable(order = 32)
    private BigDecimal longUSDTChanges;
    @CsvExportable(order = 33)
    private BigDecimal longPercentChanges;

    @CsvExportable(order = 34)
    private BigDecimal shortUSDTChanges;
    @CsvExportable(order = 35)
    private BigDecimal shortPercentChanges;

    @CsvExportable(order = 36)
    private BigDecimal portfolioBeforeTradeUSDT;
    @CsvExportable(order = 37)
    private BigDecimal profitUSDTChanges;
    @CsvExportable(order = 38)
    private BigDecimal portfolioAfterTradeUSDT;
    @CsvExportable(order = 39)
    private BigDecimal profitPercentChanges;

    @CsvExportable(order = 40)
    private long minutesToMinProfitPercent;
    @CsvExportable(order = 41)
    private long minutesToMaxProfitPercent;

    @CsvExportable(order = 42)
    private BigDecimal minProfitPercentChanges;
    @CsvExportable(order = 43)
    private BigDecimal maxProfitPercentChanges;

    @CsvExportable(order = 44)
    private String formattedTimeToMinProfit;
    @CsvExportable(order = 45)
    private String formattedTimeToMaxProfit;
    @CsvExportable(order = 46)
    private String formattedProfitLong;
    @CsvExportable(order = 47)
    private String formattedProfitShort;
    @CsvExportable(order = 48)
    private String formattedProfitCommon;

    @CsvExportable(order = 49)
    private long timestamp;
    @CsvExportable(order = 50)
    private long entryTime;
    @CsvExportable(order = 51)
    private long updatedTime;

    @CsvExportable(order = 52)
    private BigDecimal maxZ;
    @CsvExportable(order = 53)
    private BigDecimal minZ;
    @CsvExportable(order = 54)
    private BigDecimal maxLong;
    @CsvExportable(order = 55)
    private BigDecimal minLong;
    @CsvExportable(order = 56)
    private BigDecimal maxShort;
    @CsvExportable(order = 57)
    private BigDecimal minShort;
    @CsvExportable(order = 58)
    private BigDecimal maxCorr;
    @CsvExportable(order = 59)
    private BigDecimal minCorr;

    @CsvExportable(order = 60)
    private String exitReason;

    @CsvExportable(order = 61)
    private boolean closeAtBreakeven;

    @CsvExportable(order = 62)
    private String settingsTimeframe;
    @CsvExportable(order = 63)
    private double settingsCandleLimit;
    @CsvExportable(order = 64)
    private double settingsMinZ;
    @CsvExportable(order = 65)
    private double settingsMinWindowSize;
    @CsvExportable(order = 66)
    private double settingsMinPValue;
    @CsvExportable(order = 67)
    private double settingsMaxAdfValue;
    @CsvExportable(order = 68)
    private double settingsMinRSquared;
    @CsvExportable(order = 69)
    private double settingsMinCorrelation;
    @CsvExportable(order = 70)
    private double settingsMinVolume;
    @CsvExportable(order = 71)
    private double settingsCheckInterval;
    @CsvExportable(order = 72)
    private double settingsMaxLongMarginSize;
    @CsvExportable(order = 73)
    private double settingsMaxShortMarginSize;
    @CsvExportable(order = 74)
    private double settingsLeverage;
    @CsvExportable(order = 75)
    private double settingsExitTake;
    @CsvExportable(order = 76)
    private double settingsExitStop;
    @CsvExportable(order = 77)
    private double settingsExitZMin;
    @CsvExportable(order = 78)
    private double settingsExitZMax;
    @CsvExportable(order = 79)
    private double settingsExitZMaxPercent;
    @CsvExportable(order = 80)
    private double settingsExitTimeMinutes;
    @CsvExportable(order = 81)
    private double settingsExitBreakEvenPercent;
    @CsvExportable(order = 82)
    private double settingsUsePairs;
    @CsvExportable(order = 83)
    private boolean settingsAutoTradingEnabled;
    @CsvExportable(order = 84)
    private boolean settingsUseMinZFilter;
    @CsvExportable(order = 85)
    private boolean settingsUseMinRSquaredFilter;
    @CsvExportable(order = 86)
    private boolean settingsUseMinPValueFilter;
    @CsvExportable(order = 87)
    private boolean settingsUseMaxAdfValueFilter;
    @CsvExportable(order = 88)
    private boolean settingsUseMinCorrelationFilter;
    @CsvExportable(order = 89)
    private boolean settingsUseMinVolumeFilter;
    @CsvExportable(order = 90)
    private boolean settingsUseExitTake;
    @CsvExportable(order = 91)
    private boolean settingsUseExitStop;
    @CsvExportable(order = 92)
    private boolean settingsUseExitZMin;
    @CsvExportable(order = 93)
    private boolean settingsUseExitZMax;
    @CsvExportable(order = 94)
    private boolean settingsUseExitZMaxPercent;
    @CsvExportable(order = 95)
    private boolean settingsUseExitTimeHours;
    @CsvExportable(order = 96)
    private boolean settingsUseExitBreakEvenPercent;
    @CsvExportable(order = 97)
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
            TypeReference<List<ProfitHistoryItem>> typeRef = new TypeReference<>() {
            };
            this.profitHistory = mapper.readValue(profitHistoryJson, typeRef);
        } catch (Exception e) {
            log.error("Ошибка десериализации истории профита для пары {}", pairName, e);
            this.profitHistory = new ArrayList<>();
        }
    }

    /**
     * Добавить новую точку в историю пиксельного спреда
     *
     * @param pixelSpreadHistoryItem новая точка данных
     */
    public void addPixelSpreadPoint(PixelSpreadHistoryItem pixelSpreadHistoryItem) {
        getPixelSpreadHistory();

        if (pixelSpreadHistory == null) {
            pixelSpreadHistory = new ArrayList<>();
        }

        boolean exists = pixelSpreadHistory.stream()
                .anyMatch(existing -> existing.getTimestamp() == pixelSpreadHistoryItem.getTimestamp());

        if (!exists) {
            pixelSpreadHistory.add(pixelSpreadHistoryItem);
            savePixelSpreadHistoryToJson();
        }
    }

    /**
     * Получить историю пиксельного спреда
     *
     * @return список PixelSpreadHistoryItem
     */
    public List<PixelSpreadHistoryItem> getPixelSpreadHistory() {
        if (pixelSpreadHistory == null && pixelSpreadHistoryJson != null && !pixelSpreadHistoryJson.isEmpty()) {
            loadPixelSpreadHistoryFromJson();
        }
        return pixelSpreadHistory != null ? pixelSpreadHistory : new ArrayList<>();
    }

    /**
     * Сериализация истории пиксельного спреда в JSON для сохранения в БД
     */
    private void savePixelSpreadHistoryToJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            this.pixelSpreadHistoryJson = mapper.writeValueAsString(pixelSpreadHistory);
        } catch (Exception e) {
            log.error("Ошибка сериализации истории пиксельного спреда для пары {}", pairName, e);
        }
    }

    /**
     * Десериализация истории пиксельного спреда из JSON при загрузке из БД
     */
    private void loadPixelSpreadHistoryFromJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            TypeReference<List<PixelSpreadHistoryItem>> typeRef = new TypeReference<>() {
            };
            this.pixelSpreadHistory = mapper.readValue(pixelSpreadHistoryJson, typeRef);
        } catch (Exception e) {
            log.error("Ошибка десериализации истории пиксельного спреда для пары {}", pairName, e);
            this.pixelSpreadHistory = new ArrayList<>();
        }
    }

    /**
     * Очищает историю пиксельного спреда
     */
    public void clearPixelSpreadHistory() {
        if (pixelSpreadHistory != null) {
            pixelSpreadHistory.clear();
        }
        pixelSpreadHistoryJson = null;
        log.debug("🔢 История пиксельного спреда очищена для пары {}", pairName);
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
        this.formattedTimeToMinProfit = String.format("%s%%/%smin",
                minProfitChanges != null ? minProfitChanges.toPlainString() : "N/A",
                minutes);
        return this.formattedTimeToMinProfit;
    }

    public String getFormattedTimeToMaxProfit() {
        BigDecimal maxProfitChanges = safeScale(this.getMaxProfitPercentChanges(), 2);
        long minutes = this.getMinutesToMaxProfitPercent();
        this.formattedTimeToMaxProfit = String.format("%s%%/%smin",
                maxProfitChanges != null ? maxProfitChanges.toPlainString() : "N/A",
                minutes);
        return this.formattedTimeToMaxProfit;
    }

    public String getFormattedProfitCommon() {
        BigDecimal profitUSDT = safeScale(this.getProfitUSDTChanges(), 2);
        BigDecimal profitPercent = safeScale(this.getProfitPercentChanges(), 2);
        this.formattedProfitCommon = String.format("%s$/%s%%",
                profitUSDT != null ? profitUSDT.toPlainString() : "N/A",
                profitPercent != null ? profitPercent.toPlainString() : "N/A");
        return this.formattedProfitCommon;
    }

    public String getFormattedProfitLong() {
        BigDecimal profitUSDT = safeScale(this.getLongUSDTChanges(), 2);
        BigDecimal profitPercent = safeScale(this.getLongPercentChanges(), 2);
        this.formattedProfitLong = String.format("%s$/%s%%",
                profitUSDT != null ? profitUSDT.toPlainString() : "N/A",
                profitPercent != null ? profitPercent.toPlainString() : "N/A");
        return this.formattedProfitLong;
    }

    public String getFormattedProfitShort() {
        BigDecimal profitUSDT = safeScale(this.getShortUSDTChanges(), 2);
        BigDecimal profitPercent = safeScale(this.getShortPercentChanges(), 2);
        this.formattedProfitShort = String.format("%s$/%s%%",
                profitUSDT != null ? profitUSDT.toPlainString() : "N/A",
                profitPercent != null ? profitPercent.toPlainString() : "N/A");
        return this.formattedProfitShort;
    }
}
