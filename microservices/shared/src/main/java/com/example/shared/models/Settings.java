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

    // ===== Логика =====
    public double getExpectedZParamsCount() {
        return this.getCandleLimit() - this.getMinWindowSize();
    }
}
