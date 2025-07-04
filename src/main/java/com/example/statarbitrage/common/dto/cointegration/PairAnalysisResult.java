package com.example.statarbitrage.common.dto.cointegration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PairAnalysisResult {
    private String overvaluedTicker;
    private String undervaluedTicker;
    private Double correlation;
    private Double correlation_pvalue;
    private Boolean is_cointegrated;
    private Double cointegration_pvalue;
    private CointegrationDetails cointegration_details;
    private Double latest_zscore;
    private Integer total_observations;
    private DataQuality data_quality;
    private List<ZScoreHistoryItem> zscore_history;
}