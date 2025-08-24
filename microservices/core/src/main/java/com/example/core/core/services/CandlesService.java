package com.example.core.core.services;

import com.example.core.client_okx.OkxClient;
import com.example.core.common.dto.Candle;
import com.example.core.common.model.PairData;
import com.example.core.common.model.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CandlesService {
    private final OkxClient okxClient;

    public Map<String, List<Candle>> getApplicableCandlesMap(PairData pairData, Settings settings) {
        Map<String, List<Candle>> candlesMap = getCandles(settings, List.of(pairData.getLongTicker(), pairData.getShortTicker()), false);
        validateCandlesLimitAndThrow(candlesMap, settings);
        return candlesMap;
    }

    //todo сделать умнее - через кэш или бд - зачем каждую минуту это делать! если объем есть то можно целый день работать, ну или чекать 1раз/час
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
        List<String> filteredTickers = swapTickers.stream()
                .filter(ticker -> !tradingTickers.contains(ticker))
                .toList();
        double minVolume = settings.isUseMinVolumeFilter() ? settings.getMinVolume() * 1_000_000 : 0.0;
        return okxClient.getValidTickersV2(filteredTickers, timeFrame, settings.getCandleLimit(), minVolume, isSorted);
    }

    private void validateCandlesLimitAndThrow(Map<String, List<Candle>> candlesMap, Settings settings) {
        if (candlesMap == null) {
            throw new IllegalArgumentException("Мапа свечей не может быть null!");
        }

        double candleLimit = settings.getCandleLimit();

        candlesMap.forEach((ticker, candles) -> {
            if (candles == null) {
                log.error("❌ Список свечей для тикера {} равен null!", ticker);
                throw new IllegalArgumentException("Список свечей не может быть null для тикера: " + ticker);
            }
            if (candles.size() != candleLimit) {
                log.error(
                        "❌ Размер списка свечей {} для тикера {} не совпадает с лимитом {}",
                        candles.size(), ticker, candleLimit
                );
                throw new IllegalArgumentException(
                        String.format(
                                "❌ Размер списка свечей для тикера %s: %d, ожидается: %d",
                                ticker, candles.size(), candleLimit
                        )
                );
            }
        });
    }
}
