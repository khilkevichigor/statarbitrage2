package com.example.shared.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Settings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "timeframe")
    private String timeframe;

    @Column(name = "candle_limit")
    private double candleLimit;

    @Column(name = "min_z")
    private double minZ;

    @Column(name = "min_window_size")
    private double minWindowSize;

    @Column(name = "max_p_value")
    private double maxPValue;

    @Column(name = "max_adf_value")
    private double maxAdfValue;

    @Column(name = "min_r_squared")
    private double minRSquared;

    @Column(name = "min_correlation")
    private double minCorrelation;

    @Column(name = "min_volume")
    private double minVolume;

    @Column(name = "check_interval")
    private double checkInterval;

    @Column(name = "max_long_margin_size")
    private double maxLongMarginSize;

    @Column(name = "max_short_margin_size")
    private double maxShortMarginSize;

    @Column(name = "leverage")
    private double leverage;

    @Column(name = "exit_take")
    private double exitTake;

    @Column(name = "exit_stop")
    private double exitStop;

    @Column(name = "exit_z_min")
    private double exitZMin;

    @Column(name = "exit_z_max")
    private double exitZMax;

    @Column(name = "exit_z_max_percent")
    private double exitZMaxPercent;

    @Column(name = "exit_time_minutes")
    private double exitTimeMinutes;

    @Column(name = "exit_break_even_percent")
    private double exitBreakEvenPercent;

    @Column(name = "exit_negative_z_min_profit_percent")
    private double exitNegativeZMinProfitPercent;

    @Column(name = "use_pairs")
    private double usePairs;

    @Builder.Default
    @Column(name = "auto_trading_enabled")
    private boolean autoTradingEnabled = false;

    // ===== Флаги фильтров =====
    @Builder.Default
    @Column(name = "use_min_z_filter")
    private boolean useMinZFilter = true;

    @Builder.Default
    @Column(name = "use_min_r_squared_filter")
    private boolean useMinRSquaredFilter = true;

    @Builder.Default
    @Column(name = "use_max_p_value_filter")
    private boolean useMaxPValueFilter = true;

    @Builder.Default
    @Column(name = "use_max_adf_value_filter")
    private boolean useMaxAdfValueFilter = true;

    @Builder.Default
    @Column(name = "use_min_correlation_filter")
    private boolean useMinCorrelationFilter = true;

    @Builder.Default
    @Column(name = "use_min_volume_filter")
    private boolean useMinVolumeFilter = true;

    // ===== Флаги стратегий выхода =====
    @Builder.Default
    @Column(name = "use_exit_take")
    private boolean useExitTake = true;

    @Builder.Default
    @Column(name = "use_exit_stop")
    private boolean useExitStop = true;

    @Builder.Default
    @Column(name = "use_exit_z_min")
    private boolean useExitZMin = true;

    @Builder.Default
    @Column(name = "use_exit_z_max")
    private boolean useExitZMax = true;

    @Builder.Default
    @Column(name = "use_exit_z_max_percent")
    private boolean useExitZMaxPercent = true;

    @Builder.Default
    @Column(name = "use_exit_time_minutes")
    private boolean useExitTimeMinutes = true;

    @Builder.Default
    @Column(name = "use_exit_break_even_percent")
    private boolean useExitBreakEvenPercent = true;

    @Builder.Default
    @Column(name = "use_exit_negative_z_min_profit_percent")
    private boolean useExitNegativeZMinProfitPercent = true;

    @Builder.Default
    @Column(name = "use_cointegration_stability_filter")
    private boolean useCointegrationStabilityFilter = true;

    // ===== Списки =====
    @Column(name = "minimum_lot_blacklist", length = 1000)
    @Builder.Default
    private String minimumLotBlacklist = "";

    @Column(name = "observed_pairs", length = 1000)
    @Builder.Default
    private String observedPairs = "";

    // ===== Флаги скоринга =====
    @Builder.Default
    @Column(name = "use_z_score_scoring")
    private boolean useZScoreScoring = true;

    @Builder.Default
    @Column(name = "use_pixel_spread_scoring")
    private boolean usePixelSpreadScoring = true;

    @Builder.Default
    @Column(name = "use_cointegration_scoring")
    private boolean useCointegrationScoring = true;

    @Builder.Default
    @Column(name = "use_model_quality_scoring")
    private boolean useModelQualityScoring = true;

    @Builder.Default
    @Column(name = "use_statistics_scoring")
    private boolean useStatisticsScoring = true;

    @Builder.Default
    @Column(name = "use_bonus_scoring")
    private boolean useBonusScoring = true;

    // ===== Веса скоринга =====
    @Builder.Default
    @Column(name = "z_score_scoring_weight")
    private double zScoreScoringWeight = 40.0;

    @Builder.Default
    @Column(name = "pixel_spread_scoring_weight")
    private double pixelSpreadScoringWeight = 25.0;

    @Builder.Default
    @Column(name = "cointegration_scoring_weight")
    private double cointegrationScoringWeight = 25.0;

    @Builder.Default
    @Column(name = "model_quality_scoring_weight")
    private double modelQualityScoringWeight = 20.0;

    @Builder.Default
    @Column(name = "statistics_scoring_weight")
    private double statisticsScoringWeight = 10.0;

    @Builder.Default
    @Column(name = "bonus_scoring_weight")
    private double bonusScoringWeight = 5.0;

    // ===== Усреднение =====
    @Builder.Default
    @Column(name = "auto_averaging_enabled")
    private boolean autoAveragingEnabled = false;

    @Builder.Default
    @Column(name = "averaging_drawdown_threshold")
    private double averagingDrawdownThreshold = 5.0;

    @Builder.Default
    @Column(name = "averaging_volume_multiplier")
    private double averagingVolumeMultiplier = 1.5;

    @Builder.Default
    @Column(name = "averaging_drawdown_multiplier")
    private double averagingDrawdownMultiplier = 1.5;

    @Builder.Default
    @Column(name = "max_averaging_count")
    private int maxAveragingCount = 3;

    // ===== Автообъем =====
    @Builder.Default
    @Column(name = "auto_volume_enabled")
    private boolean autoVolumeEnabled = false;

    // ===== Фильтр по пересечениям нормализованных цен =====
    @Builder.Default
    @Column(name = "min_intersections")
    private int minIntersections = 10;

    @Builder.Default
    @Column(name = "use_min_intersections")
    private boolean useMinIntersections = false;

    // ===== Настройки кэша свечей =====
    @Builder.Default
    @Column(name = "candle_cache_enabled")
    private boolean candleCacheEnabled = true;

    @Builder.Default
    @Column(name = "candle_cache_default_exchange")
    private String candleCacheDefaultExchange = "OKX";

    @Builder.Default
    @Column(name = "candle_cache_thread_count")
    private int candleCacheThreadCount = 5;

    @Builder.Default
    @Column(name = "candle_cache_active_timeframes")
    private String candleCacheActiveTimeframes = "1m,5m,15m,1H,4H,1D";

    @Builder.Default
    @Column(name = "candle_cache_preload_schedule")
    private String candleCachePreloadSchedule = "0 0 2 * * SUN";

    @Builder.Default
    @Column(name = "candle_cache_daily_update_schedule")
    private String candleCacheDailyUpdateSchedule = "0 */30 * * * *";

    @Builder.Default
    @Column(name = "candle_cache_force_load_period_days")
    private int candleCacheForceLoadPeriodDays = 365;

    // ===== Логика =====
    public double getExpectedZParamsCount() {
        return this.getCandleLimit() - this.getMinWindowSize();
    }

    /**
     * Копирует все настройки из другого объекта Settings
     */
    public void copyFrom(Settings other) {
        if (other == null) return;
        
        this.timeframe = other.timeframe;
        this.candleLimit = other.candleLimit;
        this.minZ = other.minZ;
        this.minWindowSize = other.minWindowSize;
        this.maxPValue = other.maxPValue;
        this.maxAdfValue = other.maxAdfValue;
        this.minRSquared = other.minRSquared;
        this.minCorrelation = other.minCorrelation;
        this.minVolume = other.minVolume;
        this.checkInterval = other.checkInterval;
        this.maxLongMarginSize = other.maxLongMarginSize;
        this.maxShortMarginSize = other.maxShortMarginSize;
        this.leverage = other.leverage;
        this.exitTake = other.exitTake;
        this.exitStop = other.exitStop;
        this.exitZMin = other.exitZMin;
        this.exitZMax = other.exitZMax;
        this.exitZMaxPercent = other.exitZMaxPercent;
        this.exitTimeMinutes = other.exitTimeMinutes;
        this.exitBreakEvenPercent = other.exitBreakEvenPercent;
        this.exitNegativeZMinProfitPercent = other.exitNegativeZMinProfitPercent;
        this.usePairs = other.usePairs;
        this.autoTradingEnabled = other.autoTradingEnabled;
        
        // Флаги фильтров
        this.useMinZFilter = other.useMinZFilter;
        this.useMinRSquaredFilter = other.useMinRSquaredFilter;
        this.useMaxPValueFilter = other.useMaxPValueFilter;
        this.useMaxAdfValueFilter = other.useMaxAdfValueFilter;
        this.useMinCorrelationFilter = other.useMinCorrelationFilter;
        this.useMinVolumeFilter = other.useMinVolumeFilter;
        
        // Флаги стратегий выхода
        this.useExitTake = other.useExitTake;
        this.useExitStop = other.useExitStop;
        this.useExitZMin = other.useExitZMin;
        this.useExitZMax = other.useExitZMax;
        this.useExitZMaxPercent = other.useExitZMaxPercent;
        this.useExitTimeMinutes = other.useExitTimeMinutes;
        this.useExitBreakEvenPercent = other.useExitBreakEvenPercent;
        this.useExitNegativeZMinProfitPercent = other.useExitNegativeZMinProfitPercent;
        this.useCointegrationStabilityFilter = other.useCointegrationStabilityFilter;
        
        // Списки
        this.minimumLotBlacklist = other.minimumLotBlacklist;
        this.observedPairs = other.observedPairs;
        
        // Флаги скоринга
        this.useZScoreScoring = other.useZScoreScoring;
        this.usePixelSpreadScoring = other.usePixelSpreadScoring;
        this.useCointegrationScoring = other.useCointegrationScoring;
        this.useModelQualityScoring = other.useModelQualityScoring;
        this.useStatisticsScoring = other.useStatisticsScoring;
        this.useBonusScoring = other.useBonusScoring;
        
        // Веса скоринга
        this.zScoreScoringWeight = other.zScoreScoringWeight;
        this.pixelSpreadScoringWeight = other.pixelSpreadScoringWeight;
        this.cointegrationScoringWeight = other.cointegrationScoringWeight;
        this.modelQualityScoringWeight = other.modelQualityScoringWeight;
        this.statisticsScoringWeight = other.statisticsScoringWeight;
        this.bonusScoringWeight = other.bonusScoringWeight;
        
        // Усреднение
        this.autoAveragingEnabled = other.autoAveragingEnabled;
        this.averagingDrawdownThreshold = other.averagingDrawdownThreshold;
        this.averagingVolumeMultiplier = other.averagingVolumeMultiplier;
        this.averagingDrawdownMultiplier = other.averagingDrawdownMultiplier;
        this.maxAveragingCount = other.maxAveragingCount;
        
        // Автообъем
        this.autoVolumeEnabled = other.autoVolumeEnabled;
        
        // Фильтр по пересечениям
        this.minIntersections = other.minIntersections;
        this.useMinIntersections = other.useMinIntersections;
        
        // Настройки кэша свечей
        this.candleCacheEnabled = other.candleCacheEnabled;
        this.candleCacheDefaultExchange = other.candleCacheDefaultExchange;
        this.candleCacheThreadCount = other.candleCacheThreadCount;
        this.candleCacheActiveTimeframes = other.candleCacheActiveTimeframes;
        this.candleCachePreloadSchedule = other.candleCachePreloadSchedule;
        this.candleCacheDailyUpdateSchedule = other.candleCacheDailyUpdateSchedule;
        this.candleCacheForceLoadPeriodDays = other.candleCacheForceLoadPeriodDays;
    }
}
