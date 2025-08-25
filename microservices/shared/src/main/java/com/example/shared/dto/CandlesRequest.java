package com.example.shared.dto;

import com.example.shared.models.PairData;
import com.example.shared.models.Settings;
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
    private PairData pairData;
    private boolean usePairData;

    public CandlesRequest(Settings settings, List<String> tradingTickers) {
        this.settings = settings;
        this.tradingTickers = tradingTickers;
        this.usePairData = false;
    }

    public CandlesRequest(PairData pairData, Settings settings) {
        this.pairData = pairData;
        this.settings = settings;
        this.usePairData = true;
    }
}