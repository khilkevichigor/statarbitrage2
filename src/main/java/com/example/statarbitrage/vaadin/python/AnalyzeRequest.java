package com.example.statarbitrage.vaadin.python;

import com.example.statarbitrage.model.Candle;
import com.example.statarbitrage.model.Settings;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalyzeRequest {
    private Map<String, List<Candle>> candles_map;
    private Settings settings;
}
