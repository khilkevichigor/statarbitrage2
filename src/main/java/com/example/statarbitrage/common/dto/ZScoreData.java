package com.example.statarbitrage.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZScoreData {

    // Ticker information
    private String overvaluedTicker;
    private String undervaluedTicker;

    // Core statistical data
    private Double correlation;
    @JsonProperty("correlation_pvalue")
    private Double correlationPvalue;
    @JsonProperty("is_cointegrated")
    private boolean isCointegrated;
    @JsonProperty("cointegration_pvalue")
    private Double cointegrationPvalue;
    @JsonProperty("latest_zscore")
    private Double latestZscore;
    @JsonProperty("total_observations")
    private Integer totalObservations;

    // Flattened fields from CointegrationDetails
    @JsonProperty("trace_statistic")
    private Double traceStatistic;
    @JsonProperty("critical_value_95")
    private Double criticalValue95;
    private List<Double> eigenvalues;
    @JsonProperty("cointegrating_vector")
    private List<Double> cointegratingVector;
    private String error;

    // Flattened fields from DataQuality
    @JsonProperty("avg_r_squared")
    private Double avgRSquared;
    @JsonProperty("avg_adf_pvalue")
    private Double avgAdfPvalue;
    @JsonProperty("stable_periods")
    private Integer stablePeriods;

    // Full Z-score history
    @JsonProperty("zscore_history")
    private List<ZScoreParam> zscoreHistory;
}
