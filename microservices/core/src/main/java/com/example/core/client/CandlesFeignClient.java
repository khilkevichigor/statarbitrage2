package com.example.core.client;

import com.example.shared.models.Candle;
import com.example.shared.models.PairData;
import com.example.shared.models.Settings;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

@FeignClient(name = "candles-service", url = "${candles.service.url:http://localhost:8091}")
public interface CandlesFeignClient {

    @PostMapping("/api/candles/applicable-map")
    Map<String, List<Candle>> getApplicableCandlesMap(@RequestBody CandlesRequest request);

    class CandlesRequest {
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