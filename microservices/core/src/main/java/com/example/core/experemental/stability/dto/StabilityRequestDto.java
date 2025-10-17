package com.example.core.experemental.stability.dto;

import com.example.shared.dto.Candle;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StabilityRequestDto {

    @JsonProperty("candles_map")
    private Map<String, List<Candle>> candlesMap;

    @JsonProperty("settings")
    private Map<String, Object> settings;
}