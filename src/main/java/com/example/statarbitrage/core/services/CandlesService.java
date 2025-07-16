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

    public Map<String, List<Candle>> getApplicableCandlesMap(PairData pairData, Settings settings) {
        Map<String, List<Candle>> candlesMap = getCandles(settings, List.of(pairData.getLongTicker(), pairData.getShortTicker()), false);
        validateCandlesLimitAndThrow(candlesMap, settings);
        return candlesMap;
    }

    public Map<String, List<Candle>> getApplicableCandlesMap(Settings settings, List<String> tradingTickers) {
        List<String> applicableTickers = getApplicableTickers(settings, tradingTickers, "1D", true);
        Map<String, List<Candle>> candlesMap = getCandles(settings, applicableTickers, true);
        validateCandlesLimitAndThrow(candlesMap, settings);
        return candlesMap;
    }

    private Map<String, List<Candle>> getCandles(Settings settings, List<String> swapTickers, boolean isSorted) {
        return okxClient.getCandlesMap(swapTickers, settings, isSorted);
    }

    private List<String> getApplicableTickers(Settings settings, List<String> tradingTickers, String timeFrame, boolean isSorted) {
        List<String> swapTickers = okxClient.getAllSwapTickers(isSorted);
        List<String> filteredTickers = swapTickers.stream() //todo проверить как работает
                .filter(ticker -> !tradingTickers.contains(ticker))
                .toList();
        double minVolume = settings.isUseMinVolumeFilter() ? settings.getMinVolume() * 1_000_000 : 0.0;
        return okxClient.getValidTickers(filteredTickers, timeFrame, settings.getCandleLimit(), minVolume, isSorted);
    }

    private void validateCandlesLimitAndThrow(Map<String, List<Candle>> candlesMap, Settings settings) {
        if (candlesMap == null) {
            throw new IllegalArgumentException("Candles map cannot be null!");
        }

        double candleLimit = settings.getCandleLimit();

        candlesMap.forEach((ticker, candles) -> {
            if (candles == null) {
                log.error("Candles list for ticker {} is null!", ticker);
                throw new IllegalArgumentException("Candles list cannot be null for ticker: " + ticker);
            }
            if (candles.size() != candleLimit) {
                log.error(
                        "Candles size {} for ticker {} does not match limit {}",
                        candles.size(), ticker, candleLimit
                );
                throw new IllegalArgumentException(
                        String.format(
                                "Candles size for ticker %s is %d, but expected %d",
                                ticker, candles.size(), candleLimit
                        )
                );
            }
        });
    }
}
