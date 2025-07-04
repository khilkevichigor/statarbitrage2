package com.example.statarbitrage.core.services;

import com.example.statarbitrage.client_okx.OkxClient;
import com.example.statarbitrage.common.dto.Candle;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CandlesService {
    private final ValidateService validateService;
    private final OkxClient okxClient;

    public Map<String, List<Candle>> getCandles(Settings settings, List<String> swapTickers, boolean isSorted) {
        return okxClient.getCandlesMap(swapTickers, settings, isSorted);
    }

    public Map<String, List<Candle>> getCandlesMap(PairData pairData, Settings settings) {
        Map<String, List<Candle>> candlesMap = getCandles(settings, List.of(pairData.getLongTicker(), pairData.getShortTicker()), false);
        validateService.validateCandlesLimitAndThrow(candlesMap);
        return candlesMap;
    }

    public Map<String, List<Candle>> getCandlesMap(Settings settings) {
        List<String> applicableTickers = getApplicableTickers(settings, "1D", true);
        Map<String, List<Candle>> candlesMap = getCandles(settings, applicableTickers, true);
        validateService.validateCandlesLimitAndThrow(candlesMap);
        return candlesMap;
    }

    public List<String> getApplicableTickers(Settings settings, String timeFrame, boolean isSorted) {
        List<String> swapTickers = okxClient.getAllSwapTickers(isSorted);
        return okxClient.getValidTickers(swapTickers, timeFrame, settings.getCandleLimit(), settings.getMinVolume() * 1_000_000, isSorted);
    }
}
