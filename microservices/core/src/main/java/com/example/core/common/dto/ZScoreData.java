package com.example.core.common.dto;

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
    @JsonProperty("overvaluedTicker")
    private String overValuedTicker;

    @JsonProperty("undervaluedTicker")
    private String underValuedTicker;

    // Core statistical data
    @JsonProperty("pearson_corr")
    private Double pearsonCorr;
    @JsonProperty("pearson_corr_pvalue")
    private Double pearsonCorrPValue;
    @JsonProperty("johansen_is_coint")
    private boolean johansenIsCoint;
    @JsonProperty("johansen_coint_pvalue")
    private Double johansenCointPValue;
    @JsonProperty("latest_zscore")
    private Double latestZScore;
    @JsonProperty("total_observations")
    private Integer totalObservations;

    // Flattened fields from CointegrationDetails
    @JsonProperty("trace_statistic")
    private Double johansenTraceStatistic;
    @JsonProperty("critical_value_95")
    private Double johansenCriticalValue95;
    @JsonProperty("eigenvalues")
    private List<Double> johansenEigenValues;
    @JsonProperty("cointegrating_vector")
    private List<Double> johansenCointegratingVector;
    @JsonProperty("error")
    private String johansenError;

    // Flattened fields from DataQuality
    @JsonProperty("avg_r_squared")
    private Double avgRSquared;
    @JsonProperty("avg_adf_pvalue")
    private Double avgAdfPvalue;
    @JsonProperty("stable_periods")
    private Integer stablePeriods;

    // Full Z-score history
    @JsonProperty("zscore_history")
    private List<ZScoreParam> zScoreHistory;
}
