package com.example.candles.controller;

import com.example.candles.service.CandlesService;
import com.example.shared.models.Candle;
import com.example.shared.models.PairData;
import com.example.shared.models.Settings;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/candles")
@RequiredArgsConstructor
public class CandlesController {

    private final CandlesService candlesService;

    @PostMapping("/applicable-map")
    public Map<String, List<Candle>> getApplicableCandlesMap(@RequestBody CandlesRequest request) {
        if (request.isPairData()) {
            return candlesService.getApplicableCandlesMap(request.getPairData(), request.getSettings());
        } else {
            return candlesService.getApplicableCandlesMap(request.getSettings(), request.getTradingTickers());
        }
    }

    public static class CandlesRequest {
        private Settings settings;
        private List<String> tradingTickers;
        private PairData pairData;
        private boolean isPairData;

        public CandlesRequest() {
        }

        public CandlesRequest(Settings settings, List<String> tradingTickers) {
            this.settings = settings;
            this.tradingTickers = tradingTickers;
            this.isPairData = false;
        }

        public CandlesRequest(PairData pairData, Settings settings) {
            this.pairData = pairData;
            this.settings = settings;
            this.isPairData = true;
        }

        public Settings getSettings() {
            return settings;
        }

        public void setSettings(Settings settings) {
            this.settings = settings;
        }

        public List<String> getTradingTickers() {
            return tradingTickers;
        }

        public void setTradingTickers(List<String> tradingTickers) {
            this.tradingTickers = tradingTickers;
        }

        public PairData getPairData() {
            return pairData;
        }

        public void setPairData(PairData pairData) {
            this.pairData = pairData;
        }

        public boolean isPairData() {
            return isPairData;
        }

        public void setPairData(boolean pairData) {
            isPairData = pairData;
        }
    }
}