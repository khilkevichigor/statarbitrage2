package com.example.shared.models;

import com.example.shared.utils.BigDecimalUtil;
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

@Entity
@Table(name = "trading_pair", indexes = {
        @Index(name = "idx_tradingpair_uuid", columnList = "uuid", unique = true)
})
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Slf4j
public class TradingPair {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @CsvExportable(order = 1)
    @Column(name = "id")
    private Long id;

    @CsvExportable(order = 2)
    @Column(columnDefinition = "uuid", nullable = false, updatable = false)
    private String uuid = UUID.randomUUID().toString();

    @Version
    @CsvExportable(order = 3)
    @Column(name = "version")
    private Long version;

    @Enumerated(EnumType.STRING)
    @CsvExportable(order = 4)
    @Column(name = "status")
    private TradeStatus status = TradeStatus.SELECTED;

    @CsvExportable(order = 5)
    @Column(name = "error_description")
    private String errorDescription;

    @Column(name = "long_ticker_candles_json", columnDefinition = "TEXT")
    private String longTickerCandlesJson;

    @Column(name = "short_ticker_candles_json", columnDefinition = "TEXT")
    private String shortTickerCandlesJson;

    @Transient
    private List<Candle> longTickerCandles;

    @Transient
    private List<Candle> shortTickerCandles;

    @Column(name = "z_score_history_json", columnDefinition = "TEXT")
    private String zScoreHistoryJson;

    @Column(name = "profit_history_json", columnDefinition = "TEXT")
    private String profitHistoryJson;

    @Column(name = "pixel_spread_history_json", columnDefinition = "TEXT")
    private String pixelSpreadHistoryJson;

    @Transient
    private List<ZScoreParam> zScoreHistory;

    @Transient
    private List<ProfitHistoryItem> profitHistory;

    @Transient
    private List<PixelSpreadHistoryItem> pixelSpreadHistory;

    @CsvExportable(order = 6)
    @Column(name = "long_ticker")
    private String longTicker;

    @CsvExportable(order = 7)
    @Column(name = "short_ticker")
    private String shortTicker;

    @CsvExportable(order = 8)
    @Column(name = "pair_name")
    private String pairName;

    @CsvExportable(order = 9)
    @Column(name = "long_ticker_entry_price")
    private double longTickerEntryPrice;

    @CsvExportable(order = 10)
    @Column(name = "long_ticker_current_price")
    private double longTickerCurrentPrice;

    @CsvExportable(order = 11)
    @Column(name = "short_ticker_entry_price")
    private double shortTickerEntryPrice;

    @CsvExportable(order = 12)
    @Column(name = "short_ticker_current_price")
    private double shortTickerCurrentPrice;

    @CsvExportable(order = 13)
    @Column(name = "mean_entry")
    private double meanEntry;

    @CsvExportable(order = 14)
    @Column(name = "mean_current")
    private double meanCurrent;

    @CsvExportable(order = 15)
    @Column(name = "spread_entry")
    private double spreadEntry;

    @CsvExportable(order = 16)
    @Column(name = "spread_current")
    private double spreadCurrent;

    @CsvExportable(order = 17)
    @Column(name = "z_score_entry")
    private double zScoreEntry;

    @CsvExportable(order = 18)
    @Column(name = "z_score_current")
    private double zScoreCurrent;

    @CsvExportable(order = 19)
    @Column(name = "p_value_entry")
    private double pValueEntry;

    @CsvExportable(order = 20)
    @Column(name = "p_value_current")
    private double pValueCurrent;

    @CsvExportable(order = 21)
    @Column(name = "adf_pvalue_entry")
    private double adfPvalueEntry;

    @CsvExportable(order = 22)
    @Column(name = "adf_pvalue_current")
    private double adfPvalueCurrent;

    @CsvExportable(order = 23)
    @Column(name = "correlation_entry")
    private double correlationEntry;

    @CsvExportable(order = 24)
    @Column(name = "correlation_current")
    private double correlationCurrent;

    @CsvExportable(order = 25)
    @Column(name = "alpha_entry")
    private double alphaEntry;

    @CsvExportable(order = 26)
    @Column(name = "alpha_current")
    private double alphaCurrent;

    @CsvExportable(order = 27)
    @Column(name = "beta_entry")
    private double betaEntry;

    @CsvExportable(order = 28)
    @Column(name = "beta_current")
    private double betaCurrent;

    @CsvExportable(order = 29)
    @Column(name = "std_entry")
    private double stdEntry;

    @CsvExportable(order = 30)
    @Column(name = "std_current")
    private double stdCurrent;

    @CsvExportable(order = 31)
    @Column(name = "z_score_changes")
    private BigDecimal zScoreChanges;

    @CsvExportable(order = 32)
    @Column(name = "long_usdt_changes")
    private BigDecimal longUSDTChanges;

    @CsvExportable(order = 33)
    @Column(name = "long_percent_changes")
    private BigDecimal longPercentChanges;

    @CsvExportable(order = 34)
    @Column(name = "short_usdt_changes")
    private BigDecimal shortUSDTChanges;

    @CsvExportable(order = 35)
    @Column(name = "short_percent_changes")
    private BigDecimal shortPercentChanges;

    @CsvExportable(order = 36)
    @Column(name = "portfolio_before_trade_usdt")
    private BigDecimal portfolioBeforeTradeUSDT;

    @CsvExportable(order = 37)
    @Column(name = "profit_usdt_changes")
    private BigDecimal profitUSDTChanges;

    @CsvExportable(order = 38)
    @Column(name = "portfolio_after_trade_usdt")
    private BigDecimal portfolioAfterTradeUSDT;

    @CsvExportable(order = 39)
    @Column(name = "profit_percent_changes")
    private BigDecimal profitPercentChanges;

    @CsvExportable(order = 40)
    @Column(name = "minutes_to_min_profit_percent")
    private long minutesToMinProfitPercent;

    @CsvExportable(order = 41)
    @Column(name = "minutes_to_max_profit_percent")
    private long minutesToMaxProfitPercent;

    @CsvExportable(order = 42)
    @Column(name = "min_profit_percent_changes")
    private BigDecimal minProfitPercentChanges;

    @CsvExportable(order = 43)
    @Column(name = "max_profit_percent_changes")
    private BigDecimal maxProfitPercentChanges;

    @CsvExportable(order = 44)
    @Column(name = "formatted_time_to_min_profit")
    private String formattedTimeToMinProfit;

    @CsvExportable(order = 45)
    @Column(name = "formatted_time_to_max_profit")
    private String formattedTimeToMaxProfit;

    @CsvExportable(order = 46)
    @Column(name = "formatted_profit_long")
    private String formattedProfitLong;

    @CsvExportable(order = 47)
    @Column(name = "formatted_profit_short")
    private String formattedProfitShort;

    @CsvExportable(order = 48)
    @Column(name = "formatted_profit_common")
    private String formattedProfitCommon;

    @CsvExportable(order = 49)
    @Column(name = "timestamp")
    private long timestamp;

    @CsvExportable(order = 50)
    @Column(name = "entry_time")
    private long entryTime;

    @CsvExportable(order = 51)
    @Column(name = "updated_time")
    private long updatedTime;

    @CsvExportable(order = 52)
    @Column(name = "max_z")
    private BigDecimal maxZ;

    @CsvExportable(order = 53)
    @Column(name = "min_z")
    private BigDecimal minZ;

    @CsvExportable(order = 54)
    @Column(name = "max_long")
    private BigDecimal maxLong;

    @CsvExportable(order = 55)
    @Column(name = "min_long")
    private BigDecimal minLong;

    @CsvExportable(order = 56)
    @Column(name = "max_short")
    private BigDecimal maxShort;

    @CsvExportable(order = 57)
    @Column(name = "min_short")
    private BigDecimal minShort;

    @CsvExportable(order = 58)
    @Column(name = "max_corr")
    private BigDecimal maxCorr;

    @CsvExportable(order = 59)
    @Column(name = "min_corr")
    private BigDecimal minCorr;

    @CsvExportable(order = 60)
    @Column(name = "exit_reason")
    private String exitReason;

    @CsvExportable(order = 61)
    @Column(name = "close_at_breakeven")
    private boolean closeAtBreakeven;

    @CsvExportable(order = 62)
    @Column(name = "settings_timeframe")
    private String settingsTimeframe;

    @CsvExportable(order = 63)
    @Column(name = "settings_candle_limit")
    private double settingsCandleLimit;

    @CsvExportable(order = 64)
    @Column(name = "settings_min_z")
    private double settingsMinZ;

    @CsvExportable(order = 65)
    @Column(name = "settings_min_window_size")
    private double settingsMinWindowSize;

    @CsvExportable(order = 66)
    @Column(name = "settings_min_p_value")
    private double settingsMinPValue;

    @CsvExportable(order = 67)
    @Column(name = "settings_max_adf_value")
    private double settingsMaxAdfValue;

    @CsvExportable(order = 68)
    @Column(name = "settings_min_r_squared")
    private double settingsMinRSquared;

    @CsvExportable(order = 69)
    @Column(name = "settings_min_correlation")
    private double settingsMinCorrelation;

    @CsvExportable(order = 70)
    @Column(name = "settings_min_volume")
    private double settingsMinVolume;

    @CsvExportable(order = 71)
    @Column(name = "settings_check_interval")
    private double settingsCheckInterval;

    @CsvExportable(order = 72)
    @Column(name = "settings_max_long_margin_size")
    private double settingsMaxLongMarginSize;

    @CsvExportable(order = 73)
    @Column(name = "settings_max_short_margin_size")
    private double settingsMaxShortMarginSize;

    @CsvExportable(order = 74)
    @Column(name = "settings_leverage")
    private double settingsLeverage;

    @CsvExportable(order = 75)
    @Column(name = "settings_exit_take")
    private double settingsExitTake;

    @CsvExportable(order = 76)
    @Column(name = "settings_exit_stop")
    private double settingsExitStop;

    @CsvExportable(order = 77)
    @Column(name = "settings_exit_z_min")
    private double settingsExitZMin;

    @CsvExportable(order = 78)
    @Column(name = "settings_exit_z_max")
    private double settingsExitZMax;

    @CsvExportable(order = 79)
    @Column(name = "settings_exit_z_max_percent")
    private double settingsExitZMaxPercent;

    @CsvExportable(order = 80)
    @Column(name = "settings_exit_time_minutes")
    private double settingsExitTimeMinutes;

    @CsvExportable(order = 81)
    @Column(name = "settings_exit_breakeven_percent")
    private double settingsExitBreakEvenPercent;

    @CsvExportable(order = 82)
    @Column(name = "settings_use_pairs")
    private double settingsUsePairs;

    @CsvExportable(order = 83)
    @Column(name = "settings_auto_trading_enabled")
    private boolean settingsAutoTradingEnabled;

    @CsvExportable(order = 84)
    @Column(name = "settings_use_min_z_filter")
    private boolean settingsUseMinZFilter;

    @CsvExportable(order = 85)
    @Column(name = "settings_use_min_r_squared_filter")
    private boolean settingsUseMinRSquaredFilter;

    @CsvExportable(order = 86)
    @Column(name = "settings_use_min_p_value_filter")
    private boolean settingsUseMinPValueFilter;

    @CsvExportable(order = 87)
    @Column(name = "settings_use_max_adf_value_filter")
    private boolean settingsUseMaxAdfValueFilter;

    @CsvExportable(order = 88)
    @Column(name = "settings_use_min_correlation_filter")
    private boolean settingsUseMinCorrelationFilter;

    @CsvExportable(order = 89)
    @Column(name = "settings_use_min_volume_filter")
    private boolean settingsUseMinVolumeFilter;

    @CsvExportable(order = 90)
    @Column(name = "settings_use_exit_take")
    private boolean settingsUseExitTake;

    @CsvExportable(order = 91)
    @Column(name = "settings_use_exit_stop")
    private boolean settingsUseExitStop;

    @CsvExportable(order = 92)
    @Column(name = "settings_use_exit_z_min")
    private boolean settingsUseExitZMin;

    @CsvExportable(order = 93)
    @Column(name = "settings_use_exit_z_max")
    private boolean settingsUseExitZMax;

    @CsvExportable(order = 94)
    @Column(name = "settings_use_exit_z_max_percent")
    private boolean settingsUseExitZMaxPercent;

    @CsvExportable(order = 95)
    @Column(name = "settings_use_exit_time_hours")
    private boolean settingsUseExitTimeHours;

    @CsvExportable(order = 96)
    @Column(name = "settings_use_exit_break_even_percent")
    private boolean settingsUseExitBreakEvenPercent;

    @CsvExportable(order = 97)
    @Column(name = "settings_minimum_lot_blacklist")
    private String settingsMinimumLotBlacklist;

    @CsvExportable(order = 98)
    @Column(name = "use_z_score_scoring")
    private boolean useZScoreScoring;

    @CsvExportable(order = 99)
    @Column(name = "z_score_scoring_weight")
    private double zScoreScoringWeight;

    @CsvExportable(order = 100)
    @Column(name = "use_pixel_spread_scoring")
    private boolean usePixelSpreadScoring;

    @CsvExportable(order = 101)
    @Column(name = "pixel_spread_scoring_weight")
    private double pixelSpreadScoringWeight;

    @CsvExportable(order = 102)
    @Column(name = "use_cointegration_scoring")
    private boolean useCointegrationScoring;

    @CsvExportable(order = 103)
    @Column(name = "cointegration_scoring_weight")
    private double cointegrationScoringWeight;

    @CsvExportable(order = 104)
    @Column(name = "use_model_quality_scoring")
    private boolean useModelQualityScoring;

    @CsvExportable(order = 105)
    @Column(name = "model_quality_scoring_weight")
    private double modelQualityScoringWeight;

    @CsvExportable(order = 106)
    @Column(name = "use_statistics_scoring")
    private boolean useStatisticsScoring;

    @CsvExportable(order = 107)
    @Column(name = "statistics_scoring_weight")
    private double statisticsScoringWeight;

    @CsvExportable(order = 108)
    @Column(name = "use_bonus_scoring")
    private boolean useBonusScoring;

    @CsvExportable(order = 109)
    @Column(name = "bonus_scoring_weight")
    private double bonusScoringWeight;

    @CsvExportable(order = 110)
    @Column(name = "settings_use_exit_negative_z_min_profit_percent")
    private boolean settingsUseExitNegativeZMinProfitPercent;

    @CsvExportable(order = 111)
    @Column(name = "settings_exit_negative_z_min_profit_percent")
    private double settingsExitNegativeZMinProfitPercent;

    // ===== Поля усреднения =====
    @CsvExportable(order = 112)
    @Column(name = "settings_auto_averaging_enabled")
    private boolean settingsAutoAveragingEnabled;

    @CsvExportable(order = 113)
    @Column(name = "settings_averaging_drawdown_threshold")
    private double settingsAveragingDrawdownThreshold;

    @CsvExportable(order = 114)
    @Column(name = "settings_averaging_volume_multiplier")
    private double settingsAveragingVolumeMultiplier;

    @CsvExportable(order = 115)
    @Column(name = "averaging_count")
    private int averagingCount = 0;

    @CsvExportable(order = 116)
    @Column(name = "last_averaging_timestamp")
    private long lastAveragingTimestamp;

    public TradingPair(String longTicker, String shortTicker) {
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

    /**
     * Получить список длинных свечей
     */
    public List<Candle> getLongTickerCandles() {
        if (longTickerCandles == null && longTickerCandlesJson != null && !longTickerCandlesJson.isEmpty()) {
            loadLongTickerCandlesFromJson();
        }
        return longTickerCandles != null ? longTickerCandles : new ArrayList<>();
    }

    /**
     * Установить список длинных свечей
     */
    public void setLongTickerCandles(List<Candle> longTickerCandles) {
        this.longTickerCandles = longTickerCandles;
        saveLongTickerCandlesToJson();
    }

    /**
     * Получить список коротких свечей
     */
    public List<Candle> getShortTickerCandles() {
        if (shortTickerCandles == null && shortTickerCandlesJson != null && !shortTickerCandlesJson.isEmpty()) {
            loadShortTickerCandlesFromJson();
        }
        return shortTickerCandles != null ? shortTickerCandles : new ArrayList<>();
    }

    /**
     * Установить список коротких свечей
     */
    public void setShortTickerCandles(List<Candle> shortTickerCandles) {
        this.shortTickerCandles = shortTickerCandles;
        saveShortTickerCandlesToJson();
    }

    /**
     * Сериализация длинных свечей в JSON для сохранения в БД
     */
    private void saveLongTickerCandlesToJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            this.longTickerCandlesJson = mapper.writeValueAsString(longTickerCandles);
        } catch (Exception e) {
            log.error("Ошибка сериализации длинных свечей для пары {}", pairName, e);
        }
    }

    /**
     * Десериализация длинных свечей из JSON при загрузке из БД
     */
    private void loadLongTickerCandlesFromJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            TypeReference<List<Candle>> typeRef = new TypeReference<>() {
            };
            this.longTickerCandles = mapper.readValue(longTickerCandlesJson, typeRef);
        } catch (Exception e) {
            log.error("Ошибка десериализации длинных свечей для пары {}", pairName, e);
            this.longTickerCandles = new ArrayList<>();
        }
    }

    /**
     * Сериализация коротких свечей в JSON для сохранения в БД
     */
    private void saveShortTickerCandlesToJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            this.shortTickerCandlesJson = mapper.writeValueAsString(shortTickerCandles);
        } catch (Exception e) {
            log.error("Ошибка сериализации коротких свечей для пары {}", pairName, e);
        }
    }

    /**
     * Десериализация коротких свечей из JSON при загрузке из БД
     */
    private void loadShortTickerCandlesFromJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            TypeReference<List<Candle>> typeRef = new TypeReference<>() {
            };
            this.shortTickerCandles = mapper.readValue(shortTickerCandlesJson, typeRef);
        } catch (Exception e) {
            log.error("Ошибка десериализации коротких свечей для пары {}", pairName, e);
            this.shortTickerCandles = new ArrayList<>();
        }
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
        BigDecimal minProfitChanges = BigDecimalUtil.safeScale(this.getMinProfitPercentChanges(), 2);
        long minutes = this.getMinutesToMinProfitPercent();
        this.formattedTimeToMinProfit = String.format("%s%%/%smin",
                minProfitChanges != null ? minProfitChanges.toPlainString() : "N/A",
                minutes);
        return this.formattedTimeToMinProfit;
    }

    public String getFormattedTimeToMaxProfit() {
        BigDecimal maxProfitChanges = BigDecimalUtil.safeScale(this.getMaxProfitPercentChanges(), 2);
        long minutes = this.getMinutesToMaxProfitPercent();
        this.formattedTimeToMaxProfit = String.format("%s%%/%smin",
                maxProfitChanges != null ? maxProfitChanges.toPlainString() : "N/A",
                minutes);
        return this.formattedTimeToMaxProfit;
    }

    public String getFormattedProfitCommon() {
        BigDecimal profitUSDT = BigDecimalUtil.safeScale(this.getProfitUSDTChanges(), 2);
        BigDecimal profitPercent = BigDecimalUtil.safeScale(this.getProfitPercentChanges(), 2);
        this.formattedProfitCommon = String.format("%s$/%s%%",
                profitUSDT != null ? profitUSDT.toPlainString() : "N/A",
                profitPercent != null ? profitPercent.toPlainString() : "N/A");
        return this.formattedProfitCommon;
    }

    public String getFormattedProfitLong() {
        BigDecimal profitUSDT = BigDecimalUtil.safeScale(this.getLongUSDTChanges(), 2);
        BigDecimal profitPercent = BigDecimalUtil.safeScale(this.getLongPercentChanges(), 2);
        this.formattedProfitLong = String.format("%s$/%s%%",
                profitUSDT != null ? profitUSDT.toPlainString() : "N/A",
                profitPercent != null ? profitPercent.toPlainString() : "N/A");
        return this.formattedProfitLong;
    }

    public String getFormattedProfitShort() {
        BigDecimal profitUSDT = BigDecimalUtil.safeScale(this.getShortUSDTChanges(), 2);
        BigDecimal profitPercent = BigDecimalUtil.safeScale(this.getShortPercentChanges(), 2);
        this.formattedProfitShort = String.format("%s$/%s%%",
                profitUSDT != null ? profitUSDT.toPlainString() : "N/A",
                profitPercent != null ? profitPercent.toPlainString() : "N/A");
        return this.formattedProfitShort;
    }
}
