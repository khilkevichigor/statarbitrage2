package com.example.shared.dto;

import com.example.shared.models.Settings;
import com.example.shared.models.TradingPair;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CandlesRequest {
    private Settings settings;
    private List<String> tradingTickers;
    private TradingPair tradingPair;
    private boolean usePairData;

    public CandlesRequest(Settings settings, List<String> tradingTickers) {
        this.settings = settings;
        this.tradingTickers = tradingTickers;
        this.usePairData = false;
    }

    public CandlesRequest(TradingPair tradingPair, Settings settings) {
        this.tradingPair = tradingPair;
        this.settings = settings;
        this.usePairData = true;
    }
}