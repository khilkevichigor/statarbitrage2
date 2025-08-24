package com.example.core.common.dto.cointegration;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PairAnalysisRequest {

    @JsonProperty("pair")
    private Map<String, List<ApiCandle>> pair;

    @JsonProperty("settings")
    private Map<String, Object> settings;

    @JsonProperty("include_full_zscore_history")
    private boolean include_full_zscore_history = true;
}