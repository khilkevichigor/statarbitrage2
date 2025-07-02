package com.example.statarbitrage.dto.cointegration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PairAnalysisRequest {
    private Map<String, List<ApiCandle>> pair;
    private Map<String, Object> settings;
    private boolean include_full_zscore_history = true;
}