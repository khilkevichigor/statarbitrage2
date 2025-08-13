package com.example.statarbitrage.common.dto.cointegration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PairAnalysisResult {

    @JsonProperty("overvaluedTicker")
    private String overvaluedTicker;

    @JsonProperty("undervaluedTicker")
    private String undervaluedTicker;

    @JsonProperty("pearson_corr")
    private Double correlation;

    @JsonProperty("pearson_corr_pvalue")
    private Double correlation_pvalue;

    @JsonProperty("johansen_is_coint")
    private Boolean is_cointegrated;

    @JsonProperty("johansen_coint_pvalue")
    private Double cointegration_pvalue;

    @JsonProperty("johansen_coint_details")
    private CointegrationDetails cointegration_details;

    @JsonProperty("latest_zscore")
    private Double latest_zscore;

    @JsonProperty("total_observations")
    private Integer total_observations;

    @JsonProperty("data_quality")
    private DataQuality data_quality;

    @JsonProperty("zscore_history")
    private List<ZScoreHistoryItem> zscore_history;
}