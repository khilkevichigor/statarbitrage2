package com.example.statarbitrage.dto.cointegration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DiscoveryRequest {
    private Map<String, List<ApiCandle>> candles_map;
    private Map<String, Object> settings;
}