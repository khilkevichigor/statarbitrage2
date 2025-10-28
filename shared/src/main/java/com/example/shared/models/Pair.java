package com.example.shared.models;

import com.example.shared.dto.Candle;
import com.example.shared.dto.PixelSpreadHistoryItem;
import com.example.shared.dto.ProfitHistoryItem;
import com.example.shared.dto.ZScoreParam;
import com.example.shared.enums.PairType;
import com.example.shared.enums.StabilityRating;
import com.example.shared.enums.TradeStatus;
import com.example.shared.utils.BigDecimalUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Унифицированная модель торговых пар, объединяющая функциональность:
 * - StablePair (найденные пары для скрининга)
 * - CointPair (коинтегрированные пары для торговли) 
 * - TradingPair (активно торгуемые пары)
 */
@Entity
@Table(name = "pairs", 
       indexes = {
        @Index(name = "idx_pair_uuid", columnList = "uuid", unique = true),
        @Index(name = "idx_pair_type", columnList = "type"),
        @Index(name = "idx_pair_tickers", columnList = "ticker_a, ticker_b"),
        @Index(name = "idx_pair_monitoring", columnList = "is_in_monitoring"),
        @Index(name = "idx_pair_search_date", columnList = "search_date"),
        @Index(name = "idx_pair_status", columnList = "status")
       },
       uniqueConstraints = {
        // Уникальность стабильных пар по тикерам + таймфрейм + период + тип
        @UniqueConstraint(
                name = "uk_stable_pairs_unique", 
                columnNames = {"ticker_a", "ticker_b", "timeframe", "period", "type"}
        ),
        // Уникальность пар в мониторинге по тикерам + таймфрейм + период + тип
        @UniqueConstraint(
                name = "uk_monitoring_pairs_unique", 
                columnNames = {"ticker_a", "ticker_b", "timeframe", "period", "type", "is_in_monitoring"}
        )
       })
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Slf4j
public class Pair {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @CsvExportable(order = 1)
    @Column(name = "id")
    private Long id;

    @CsvExportable(order = 2)
    @Column(name = "uuid", columnDefinition = "uuid", nullable = false, updatable = false)
    @Builder.Default
    private UUID uuid = UUID.randomUUID();

    @Version
    @CsvExportable(order = 3)
    @Column(name = "version")
    private Long version;

    // ======== ОСНОВНЫЕ ПОЛЯ ========

    /**
     * Тип пары - определяет какую модель эмулирует
     */
    @Enumerated(EnumType.STRING)
    @CsvExportable(order = 4)
    @Column(name = "type", nullable = false, length = 20)
    @Builder.Default
    private PairType type = PairType.STABLE;

    /**
     * Статус торговли (для типов COINTEGRATED и TRADING)
     */
    @Enumerated(EnumType.STRING)
    @CsvExportable(order = 5)
    @Column(name = "status")
    @Builder.Default
    private TradeStatus status = TradeStatus.SELECTED;

    /**
     * Описание ошибки
     */
    @CsvExportable(order = 6)
    @Column(name = "error_description", columnDefinition = "TEXT")
    private String errorDescription;

    // ======== БАЗОВАЯ ИНФОРМАЦИЯ О ПАРЕ ========

    /**
     * Первый тикер (A или Long)
     */
    @CsvExportable(order = 7)
    @Column(name = "ticker_a", nullable = false, length = 20)
    private String tickerA;

    /**
     * Второй тикер (B или Short)
     */
    @CsvExportable(order = 8)
    @Column(name = "ticker_b", nullable = false, length = 20)
    private String tickerB;

    /**
     * Название пары (для совместимости с TradingPair/CointPair)
     */
    @CsvExportable(order = 9)
    @Column(name = "pair_name", length = 50)
    private String pairName;

    // ======== ПОЛЯ ИЗ STABLEPAIR ========

    /**
     * Общий балл стабильности (текущий/обновленный)
     */
    @CsvExportable(order = 10)
    @Column(name = "total_score")
    private Integer totalScore;

    /**
     * Общий балл стабильности при добавлении в мониторинг (изначальный)
     */
    @CsvExportable(order = 10)
    @Column(name = "total_score_entry")
    private Integer totalScoreEntry;

    /**
     * Рейтинг стабильности (EXCELLENT, GOOD, MARGINAL, POOR, REJECTED, FAILED)
     */
    @Enumerated(EnumType.STRING)
    @CsvExportable(order = 11)
    @Column(name = "stability_rating", length = 20)
    private StabilityRating stabilityRating;

    /**
     * Торгуемая ли пара
     */
    @CsvExportable(order = 12)
    @Column(name = "is_tradeable")
    @Builder.Default
    private boolean isTradeable = false;

    /**
     * Количество точек данных для анализа
     */
    @CsvExportable(order = 13)
    @Column(name = "data_points")
    private Integer dataPoints;

    /**
     * Количество свечей
     */
    @CsvExportable(order = 14)
    @Column(name = "candle_count")
    private Integer candleCount; // Количество свечей рассчитывается на основе таймфрейма и периода

    /**
     * Время анализа в секундах
     */
    @CsvExportable(order = 15)
    @Column(name = "analysis_time_seconds")
    private Double analysisTimeSeconds;

    /**
     * Таймфрейм (1m, 5m, 15m, 1H, 4H, 1D, 1W, 1M)
     */
    @CsvExportable(order = 16)
    @Column(name = "timeframe", length = 10)
    private String timeframe;

    /**
     * Период анализа (день, неделя, месяц, 1 год, 2 года, 3 года)
     */
    @CsvExportable(order = 17)
    @Column(name = "period", length = 20)
    private String period;

    /**
     * Дата поиска/анализа
     */
    @CsvExportable(order = 18)
    @Column(name = "search_date", nullable = false)
    private LocalDateTime searchDate;

    /**
     * Дата создания записи
     */
    @CsvExportable(order = 19)
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Находится ли в мониторинге
     */
    @CsvExportable(order = 20)
    @Column(name = "is_in_monitoring", nullable = false)
    @Builder.Default
    private boolean isInMonitoring = false;

    /**
     * Настройки поиска в JSON формате
     */
    @Column(name = "search_settings", columnDefinition = "TEXT")
    private String searchSettings;

    /**
     * Результаты анализа в JSON формате
     */
    @Column(name = "analysis_results", columnDefinition = "TEXT")
    private String analysisResults;

    // ======== ДОПОЛНИТЕЛЬНЫЕ ПОЛЯ ДЛЯ ТОРГОВЫХ ПАР ========
    // Эти поля будут null для типа STABLE, но заполнены для COINTEGRATED и TRADING

    // JSON поля для хранения исторических данных
    @Column(name = "long_ticker_candles_json", columnDefinition = "TEXT")
    private String longTickerCandlesJson;

    @Column(name = "short_ticker_candles_json", columnDefinition = "TEXT")
    private String shortTickerCandlesJson;

    @Column(name = "z_score_history_json", columnDefinition = "TEXT")
    private String zScoreHistoryJson;

    @Column(name = "profit_history_json", columnDefinition = "TEXT")
    private String profitHistoryJson;

    @Column(name = "pixel_spread_history_json", columnDefinition = "TEXT")
    private String pixelSpreadHistoryJson;

    // Transient поля для работы с данными в коде
    @Transient
    private List<Candle> longTickerCandles;

    @Transient
    private List<Candle> shortTickerCandles;

    @Transient
    private List<ZScoreParam> zScoreHistory;

    @Transient
    private List<ProfitHistoryItem> profitHistory;

    @Transient
    private List<PixelSpreadHistoryItem> pixelSpreadHistory;

    // Торговые цены (nullable для STABLE типа)
    @CsvExportable(order = 21)
    @Column(name = "long_ticker_entry_price", precision = 18, scale = 8)
    private BigDecimal longTickerEntryPrice;

    @CsvExportable(order = 22)
    @Column(name = "long_ticker_current_price", precision = 18, scale = 8)
    private BigDecimal longTickerCurrentPrice;

    @CsvExportable(order = 23)
    @Column(name = "short_ticker_entry_price", precision = 18, scale = 8)
    private BigDecimal shortTickerEntryPrice;

    @CsvExportable(order = 24)
    @Column(name = "short_ticker_current_price", precision = 18, scale = 8)
    private BigDecimal shortTickerCurrentPrice;

    // Статистические данные
    @CsvExportable(order = 25)
    @Column(name = "mean_entry", precision = 18, scale = 8)
    private BigDecimal meanEntry;

    @CsvExportable(order = 26)
    @Column(name = "mean_current", precision = 18, scale = 8)
    private BigDecimal meanCurrent;

    @CsvExportable(order = 27)
    @Column(name = "spread_entry", precision = 18, scale = 8)
    private BigDecimal spreadEntry;

    @CsvExportable(order = 28)
    @Column(name = "spread_current", precision = 18, scale = 8)
    private BigDecimal spreadCurrent;

    @CsvExportable(order = 29)
    @Column(name = "z_score_entry", precision = 10, scale = 4)
    private BigDecimal zScoreEntry;

    @CsvExportable(order = 30)
    @Column(name = "z_score_current", precision = 10, scale = 4)
    private BigDecimal zScoreCurrent;

    @CsvExportable(order = 31)
    @Column(name = "p_value_entry", precision = 10, scale = 8)
    private BigDecimal pValueEntry;

    @CsvExportable(order = 32)
    @Column(name = "p_value_current", precision = 10, scale = 8)
    private BigDecimal pValueCurrent;

    @CsvExportable(order = 33)
    @Column(name = "adf_pvalue_entry", precision = 10, scale = 8)
    private BigDecimal adfPvalueEntry;

    @CsvExportable(order = 34)
    @Column(name = "adf_pvalue_current", precision = 10, scale = 8)
    private BigDecimal adfPvalueCurrent;

    @CsvExportable(order = 35)
    @Column(name = "correlation_entry", precision = 10, scale = 8)
    private BigDecimal correlationEntry;

    @CsvExportable(order = 36)
    @Column(name = "correlation_current", precision = 10, scale = 8)
    private BigDecimal correlationCurrent;

    @CsvExportable(order = 37)
    @Column(name = "alpha_entry", precision = 18, scale = 8)
    private BigDecimal alphaEntry;

    @CsvExportable(order = 38)
    @Column(name = "alpha_current", precision = 18, scale = 8)
    private BigDecimal alphaCurrent;

    @CsvExportable(order = 39)
    @Column(name = "beta_entry", precision = 18, scale = 8)
    private BigDecimal betaEntry;

    @CsvExportable(order = 40)
    @Column(name = "beta_current", precision = 18, scale = 8)
    private BigDecimal betaCurrent;

    @CsvExportable(order = 41)
    @Column(name = "std_entry", precision = 18, scale = 8)
    private BigDecimal stdEntry;

    @CsvExportable(order = 42)
    @Column(name = "std_current", precision = 18, scale = 8)
    private BigDecimal stdCurrent;

    // Временные метки
    @CsvExportable(order = 43)
    @Column(name = "timestamp")
    private Long timestamp;

    @CsvExportable(order = 44)
    @Column(name = "entry_time")
    private LocalDateTime entryTime;

    @CsvExportable(order = 45)
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    /**
     * Время последнего пересчета Z-Score
     * Используется для оптимизации - пересчет только при появлении новых свечей
     */
    @CsvExportable(order = 46)
    @Column(name = "last_zscore_update_time")
    private LocalDateTime lastZScoreUpdateTime;

    // ======== ДОПОЛНИТЕЛЬНЫЕ ПОЛЯ ДЛЯ ТОРГОВЫХ ПАР ========
    // Эти поля будут null для типа STABLE, заполнены для COINTEGRATED и TRADING

    // Торговые изменения
    @CsvExportable(order = 47)
    @Column(name = "z_score_changes", precision = 18, scale = 8)
    private BigDecimal zScoreChanges;

    @CsvExportable(order = 48)
    @Column(name = "long_usdt_changes", precision = 18, scale = 8)
    private BigDecimal longUSDTChanges;

    @CsvExportable(order = 49)
    @Column(name = "long_percent_changes", precision = 18, scale = 8)
    private BigDecimal longPercentChanges;

    @CsvExportable(order = 50)
    @Column(name = "short_usdt_changes", precision = 18, scale = 8)
    private BigDecimal shortUSDTChanges;

    @CsvExportable(order = 51)
    @Column(name = "short_percent_changes", precision = 18, scale = 8)
    private BigDecimal shortPercentChanges;

    // Портфель и прибыль
    @CsvExportable(order = 52)
    @Column(name = "portfolio_before_trade_usdt", precision = 18, scale = 8)
    private BigDecimal portfolioBeforeTradeUSDT;

    @CsvExportable(order = 53)
    @Column(name = "profit_usdt_changes", precision = 18, scale = 8)
    private BigDecimal profitUSDTChanges;

    @CsvExportable(order = 54)
    @Column(name = "portfolio_after_trade_usdt", precision = 18, scale = 8)
    private BigDecimal portfolioAfterTradeUSDT;

    @CsvExportable(order = 55)
    @Column(name = "profit_percent_changes", precision = 18, scale = 8)
    private BigDecimal profitPercentChanges;

    // Время до экстремумов прибыли
    @CsvExportable(order = 56)
    @Column(name = "minutes_to_min_profit_percent")
    private Long minutesToMinProfitPercent;

    @CsvExportable(order = 57)
    @Column(name = "minutes_to_max_profit_percent")
    private Long minutesToMaxProfitPercent;

    @CsvExportable(order = 57)
    @Column(name = "min_profit_percent_changes", precision = 18, scale = 8)
    private BigDecimal minProfitPercentChanges;

    @CsvExportable(order = 58)
    @Column(name = "max_profit_percent_changes", precision = 18, scale = 8)
    private BigDecimal maxProfitPercentChanges;

    // Форматированные поля
    @CsvExportable(order = 59)
    @Column(name = "formatted_time_to_min_profit", columnDefinition = "TEXT")
    private String formattedTimeToMinProfit;

    @CsvExportable(order = 60)
    @Column(name = "formatted_time_to_max_profit", columnDefinition = "TEXT")
    private String formattedTimeToMaxProfit;

    @CsvExportable(order = 61)
    @Column(name = "formatted_profit_long", columnDefinition = "TEXT")
    private String formattedProfitLong;

    @CsvExportable(order = 62)
    @Column(name = "formatted_profit_short", columnDefinition = "TEXT")
    private String formattedProfitShort;

    @CsvExportable(order = 63)
    @Column(name = "formatted_profit_common", columnDefinition = "TEXT")
    private String formattedProfitCommon;

    // Экстремальные значения
    @CsvExportable(order = 64)
    @Column(name = "max_z", precision = 18, scale = 8)
    private BigDecimal maxZ;

    @CsvExportable(order = 65)
    @Column(name = "min_z", precision = 18, scale = 8)
    private BigDecimal minZ;

    @CsvExportable(order = 66)
    @Column(name = "max_long", precision = 18, scale = 8)
    private BigDecimal maxLong;

    @CsvExportable(order = 67)
    @Column(name = "min_long", precision = 18, scale = 8)
    private BigDecimal minLong;

    @CsvExportable(order = 68)
    @Column(name = "max_short", precision = 18, scale = 8)
    private BigDecimal maxShort;

    @CsvExportable(order = 69)
    @Column(name = "min_short", precision = 18, scale = 8)
    private BigDecimal minShort;

    @CsvExportable(order = 70)
    @Column(name = "max_corr", precision = 18, scale = 8)
    private BigDecimal maxCorr;

    @CsvExportable(order = 71)
    @Column(name = "min_corr", precision = 18, scale = 8)
    private BigDecimal minCorr;

    // Информация о выходе
    @CsvExportable(order = 72)
    @Column(name = "exit_reason", columnDefinition = "TEXT")
    private String exitReason;

    @CsvExportable(order = 73)
    @Column(name = "close_at_breakeven")
    @Builder.Default
    private boolean closeAtBreakeven = false;

    // Торговые настройки
    @CsvExportable(order = 74)
    @Column(name = "settings_candle_limit", precision = 10, scale = 2)
    private BigDecimal settingsCandleLimit;

    @CsvExportable(order = 75)
    @Column(name = "settings_min_z", precision = 10, scale = 4)
    private BigDecimal settingsMinZ;

    // Усреднение
    @CsvExportable(order = 76)
    @Column(name = "averaging_count")
    private Integer averagingCount;

    @CsvExportable(order = 77)
    @Column(name = "last_averaging_timestamp")
    private Long lastAveragingTimestamp;

    // Дополнительные поля для усреднения
    @Column(name = "last_averaging_profit_percent", precision = 18, scale = 8)
    private BigDecimal lastAveragingProfitPercent;

    // Настройки усреднения (сохранённые для отслеживания)
    @Column(name = "settings_averaging_drawdown_multiplier")
    private Double settingsAveragingDrawdownMultiplier;

    @Column(name = "settings_max_averaging_count")
    private Integer settingsMaxAveragingCount;

    // Нормализованные цены JSON (для совместимости с более поздними версиями)
    @Column(name = "normalized_long_prices_json", columnDefinition = "TEXT")
    private String normalizedLongPricesJson;

    @Column(name = "normalized_short_prices_json", columnDefinition = "TEXT")
    private String normalizedShortPricesJson;

    // Transient поля для нормализованных цен
    @Transient
    private List<BigDecimal> normalizedLongPrices;

    @Transient
    private List<BigDecimal> normalizedShortPrices;

    @CsvExportable(order = 78)
    @Column(name = "intersections_count")
    private Integer intersectionsCount;

    /**
     * Минимальный объем торгов в млн $ при котором была отобрана данная пара
     */
    @CsvExportable(order = 79)
    @Column(name = "min_vol_mln", precision = 10, scale = 2)
    private BigDecimal minVolMln;

    // ======== МЕТОДЫ ЖИЗНЕННОГО ЦИКЛА ========

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (searchDate == null) {
            searchDate = LocalDateTime.now();
        }
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        if (pairName == null && tickerA != null && tickerB != null) {
            pairName = tickerA + "/" + tickerB;
        }
    }

    // ======== УТИЛИТЫ ДЛЯ РАБОТЫ С JSON ПОЛЯМИ ========

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Методы для работы с настройками поиска
    public void setSearchSettingsMap(Map<String, Object> settings) {
        try {
            this.searchSettings = objectMapper.writeValueAsString(settings);
        } catch (JsonProcessingException e) {
            log.error("Ошибка при сериализации настроек поиска", e);
            this.searchSettings = "{}";
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getSearchSettingsMap() {
        if (searchSettings == null || searchSettings.isEmpty()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(searchSettings, Map.class);
        } catch (JsonProcessingException e) {
            log.error("Ошибка при десериализации настроек поиска", e);
            return Map.of();
        }
    }

    // Методы для работы с результатами анализа
    public void setAnalysisResultsMap(Map<String, Object> results) {
        try {
            this.analysisResults = objectMapper.writeValueAsString(results);
        } catch (JsonProcessingException e) {
            log.error("Ошибка при сериализации результатов анализа", e);
            this.analysisResults = "{}";
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getAnalysisResultsMap() {
        if (analysisResults == null || analysisResults.isEmpty()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(analysisResults, Map.class);
        } catch (JsonProcessingException e) {
            log.error("Ошибка при десериализации результатов анализа", e);
            return Map.of();
        }
    }

    // Методы для работы с candles JSON
    public void setLongTickerCandles(List<Candle> candles) {
        this.longTickerCandles = candles;
        if (candles != null) {
            try {
                this.longTickerCandlesJson = objectMapper.writeValueAsString(candles);
            } catch (JsonProcessingException e) {
                log.error("Ошибка при сериализации свечей long тикера", e);
                this.longTickerCandlesJson = "[]";
            }
        } else {
            this.longTickerCandlesJson = null;
        }
    }

    public List<Candle> getLongTickerCandles() {
        if (longTickerCandles != null) {
            return longTickerCandles;
        }
        if (longTickerCandlesJson != null && !longTickerCandlesJson.isEmpty()) {
            try {
                longTickerCandles = objectMapper.readValue(longTickerCandlesJson, 
                        new TypeReference<List<Candle>>() {});
                return longTickerCandles;
            } catch (JsonProcessingException e) {
                log.error("Ошибка при десериализации свечей long тикера", e);
            }
        }
        return new ArrayList<>();
    }

    public void setShortTickerCandles(List<Candle> candles) {
        this.shortTickerCandles = candles;
        if (candles != null) {
            try {
                this.shortTickerCandlesJson = objectMapper.writeValueAsString(candles);
            } catch (JsonProcessingException e) {
                log.error("Ошибка при сериализации свечей short тикера", e);
                this.shortTickerCandlesJson = "[]";
            }
        } else {
            this.shortTickerCandlesJson = null;
        }
    }

    public List<Candle> getShortTickerCandles() {
        if (shortTickerCandles != null) {
            return shortTickerCandles;
        }
        if (shortTickerCandlesJson != null && !shortTickerCandlesJson.isEmpty()) {
            try {
                shortTickerCandles = objectMapper.readValue(shortTickerCandlesJson, 
                        new TypeReference<List<Candle>>() {});
                return shortTickerCandles;
            } catch (JsonProcessingException e) {
                log.error("Ошибка при десериализации свечей short тикера", e);
            }
        }
        return new ArrayList<>();
    }

    // ======== УТИЛИТИ МЕТОДЫ ========

    /**
     * Получить название пары
     */
    public String getPairName() {
        if (pairName != null) {
            return pairName;
        }
        return (tickerA != null && tickerB != null) ? tickerA + "/" + tickerB : "";
    }

    /**
     * Получить long тикер для совместимости с TradingPair
     */
    public String getLongTicker() {
        return tickerA;
    }

    /**
     * Установить long тикер для совместимости с TradingPair
     */
    public void setLongTicker(String longTicker) {
        this.tickerA = longTicker;
    }

    /**
     * Получить short тикер для совместимости с TradingPair
     */
    public String getShortTicker() {
        return tickerB;
    }

    /**
     * Установить short тикер для совместимости с TradingPair
     */
    public void setShortTicker(String shortTicker) {
        this.tickerB = shortTicker;
    }

    // ======== СТАТИЧЕСКИЕ МЕТОДЫ ДЛЯ СОЗДАНИЯ ИЗ СТАБИЛЬНЫХ ПАР ========

    //todo что это за монстр с рефлексией?!!!
    /**
     * Создание Pair из результата анализа стабильности
     */
    public static Pair fromStabilityResult(Object stabilityResult,
                                          String timeframe, String period, 
                                          Map<String, Object> searchSettings) {
        Pair pair = new Pair();
        pair.setType(PairType.STABLE);
        pair.setTimeframe(timeframe);
        pair.setPeriod(period);
        pair.setSearchSettingsMap(searchSettings);
        
        // Извлекаем данные из StabilityResultDto через рефлексию
        String tickerA = null;
        String tickerB = null;
        try {
            Class<?> dtoClass = stabilityResult.getClass();

            // Извлекаем ticker_a и ticker_b
            Field tickerAField = dtoClass.getDeclaredField("tickerA");
            tickerAField.setAccessible(true);
            tickerA = (String) tickerAField.get(stabilityResult);
            pair.setTickerA(tickerA);

            Field tickerBField = dtoClass.getDeclaredField("tickerB");
            tickerBField.setAccessible(true);
            tickerB = (String) tickerBField.get(stabilityResult);
            pair.setTickerB(tickerB);

            // Извлекаем остальные поля
            Field totalScoreField = dtoClass.getDeclaredField("totalScore");
            totalScoreField.setAccessible(true);
            Integer totalScore = (Integer) totalScoreField.get(stabilityResult);
            pair.setTotalScore(totalScore);

            Field stabilityRatingField = dtoClass.getDeclaredField("stabilityRating");
            stabilityRatingField.setAccessible(true);
            Object stabilityRatingObj = stabilityRatingField.get(stabilityResult);
            if (stabilityRatingObj instanceof StabilityRating) {
                // Новый формат - уже enum
                pair.setStabilityRating((StabilityRating) stabilityRatingObj);
            } else if (stabilityRatingObj instanceof String) {
                // Старый формат - строка, конвертируем в enum
                pair.setStabilityRating(StabilityRating.fromString((String) stabilityRatingObj));
            }

            java.lang.reflect.Field isTradeableField = dtoClass.getDeclaredField("isTradeable");
            isTradeableField.setAccessible(true);
            Boolean isTradeable = (Boolean) isTradeableField.get(stabilityResult);
            pair.setTradeable(isTradeable != null ? isTradeable : false);

            java.lang.reflect.Field dataPointsField = dtoClass.getDeclaredField("dataPoints");
            dataPointsField.setAccessible(true);
            Integer dataPoints = (Integer) dataPointsField.get(stabilityResult);
            pair.setDataPoints(dataPoints);

            java.lang.reflect.Field analysisTimeField = dtoClass.getDeclaredField("analysisTimeSeconds");
            analysisTimeField.setAccessible(true);
            Double analysisTimeSeconds = (Double) analysisTimeField.get(stabilityResult);
            pair.setAnalysisTimeSeconds(analysisTimeSeconds);

            // Устанавливаем candleCount равным dataPoints
            pair.setCandleCount(dataPoints);
            
            // Сохраняем весь объект в JSON формате для analysisResults
            Map<String, Object> analysisResultsMap = new HashMap<>();
            analysisResultsMap.put("tickerA", tickerA);
            analysisResultsMap.put("tickerB", tickerB);
            analysisResultsMap.put("totalScore", totalScore);
            analysisResultsMap.put("stabilityRating", stabilityRatingObj);
            analysisResultsMap.put("isTradeable", isTradeable);
            analysisResultsMap.put("dataPoints", dataPoints);
            analysisResultsMap.put("candleCount", dataPoints);
            analysisResultsMap.put("analysisTimeSeconds", analysisTimeSeconds);

            // Дополнительные поля
            tryAddField(stabilityResult, dtoClass, "blockScores", analysisResultsMap);
            tryAddField(stabilityResult, dtoClass, "qualityMetrics", analysisResultsMap);
            tryAddField(stabilityResult, dtoClass, "redFlags", analysisResultsMap);
            tryAddField(stabilityResult, dtoClass, "summary", analysisResultsMap);

            pair.setAnalysisResultsMap(analysisResultsMap);

        } catch (Exception e) {
            log.error("❌ Ошибка при извлечении данных из StabilityResultDto: {}", e.getMessage(), e);
            throw new RuntimeException("Не удалось создать Pair из результата анализа", e);
        }

        // Извлекаем и сохраняем значение minVolume из настроек поиска (ПОСЛЕ извлечения тикеров)
        log.debug("🔍 SearchSettings: {}", searchSettings);
        if (searchSettings != null && searchSettings.containsKey("minVolume")) {
            Object minVolumeObj = searchSettings.get("minVolume");
            log.debug("📊 Найден minVolume в searchSettings: {} (тип: {})", minVolumeObj, minVolumeObj != null ? minVolumeObj.getClass().getSimpleName() : "null");
            if (minVolumeObj instanceof Number) {
                BigDecimal minVolValue = BigDecimal.valueOf(((Number) minVolumeObj).doubleValue());
                pair.setMinVolMln(minVolValue);
                log.debug("✅ Установлен minVolMln = {} для пары {}/{}", minVolValue, tickerA, tickerB);
            } else {
                log.warn("⚠️ minVolume не является Number: {}", minVolumeObj);
            }
        } else {
            log.warn("⚠️ minVolume отсутствует в searchSettings для пары {}/{}. SearchSettings keys: {}", 
                    tickerA, tickerB, searchSettings != null ? searchSettings.keySet() : "null");
        }

        return pair;
    }

    private static void tryAddField(Object source, Class<?> clazz, String fieldName, 
                                   Map<String, Object> targetMap) {
        try {
            java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(source);
            targetMap.put(fieldName, value);
        } catch (Exception e) {
            // Поле может отсутствовать - игнорируем
        }
    }

    // ======== МЕТОДЫ ДЛЯ РАБОТЫ С HISTORY ========

    public void addPixelSpreadPoint(PixelSpreadHistoryItem item) {
        if (pixelSpreadHistory == null) {
            pixelSpreadHistory = new ArrayList<>();
        }
        pixelSpreadHistory.add(item);
    }

    public List<PixelSpreadHistoryItem> getPixelSpreadHistory() {
        return pixelSpreadHistory != null ? pixelSpreadHistory : new ArrayList<>();
    }

    public void addZScorePoint(ZScoreParam item) {
        if (zScoreHistory == null) {
            zScoreHistory = new ArrayList<>();
        }
        zScoreHistory.add(item);
        // Автоматически сохраняем в JSON поле
        setZScoreHistory(zScoreHistory);
    }

    public List<ZScoreParam> getZScoreHistory() {
        if (zScoreHistory != null) {
            return zScoreHistory;
        }
        if (zScoreHistoryJson != null && !zScoreHistoryJson.isEmpty()) {
            try {
                zScoreHistory = objectMapper.readValue(zScoreHistoryJson,
                        new TypeReference<List<ZScoreParam>>() {});
                return zScoreHistory;
            } catch (JsonProcessingException e) {
                log.error("Ошибка при десериализации истории Z-Score", e);
            }
        }
        return new ArrayList<>();
    }

    public void setZScoreHistory(List<ZScoreParam> history) {
        this.zScoreHistory = history;
        if (history != null) {
            try {
                this.zScoreHistoryJson = objectMapper.writeValueAsString(history);
            } catch (JsonProcessingException e) {
                log.error("Ошибка при сериализации истории Z-Score", e);
                this.zScoreHistoryJson = "[]";
            }
        } else {
            this.zScoreHistoryJson = null;
        }
    }

    public void addProfitHistoryPoint(ProfitHistoryItem item) {
        if (profitHistory == null) {
            profitHistory = new ArrayList<>();
        }
        profitHistory.add(item);
        // Автоматически сохраняем в JSON поле
        setProfitHistory(profitHistory);
    }

    public List<ProfitHistoryItem> getProfitHistory() {
        if (profitHistory != null) {
            return profitHistory;
        }
        if (profitHistoryJson != null && !profitHistoryJson.isEmpty()) {
            try {
                profitHistory = objectMapper.readValue(profitHistoryJson,
                        new TypeReference<List<ProfitHistoryItem>>() {});
                return profitHistory;
            } catch (JsonProcessingException e) {
                log.error("Ошибка при десериализации истории профита", e);
            }
        }
        return new ArrayList<>();
    }

    public void setProfitHistory(List<ProfitHistoryItem> history) {
        this.profitHistory = history;
        if (history != null) {
            try {
                this.profitHistoryJson = objectMapper.writeValueAsString(history);
            } catch (JsonProcessingException e) {
                log.error("Ошибка при сериализации истории профита", e);
                this.profitHistoryJson = "[]";
            }
        } else {
            this.profitHistoryJson = null;
        }
    }

    // Метод для совместимости с TradingPair
    public String getSettingsTimeframe() {
        return getTimeframe();
    }
}