package com.example.statarbitrage.common.dto.cointegration;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DiscoveryRequest {

    @JsonProperty("candles_map")
    private Map<String, List<ApiCandle>> candles_map;

    @JsonProperty("settings")
    private Map<String, Object> settings;
}