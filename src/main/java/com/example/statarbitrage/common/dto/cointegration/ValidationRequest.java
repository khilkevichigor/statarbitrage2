package com.example.statarbitrage.common.dto.cointegration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ValidationRequest {
    private String ticker1;
    private String ticker2;
    private Map<String, List<ApiCandle>> candles_map;
    private Map<String, Object> settings;
}