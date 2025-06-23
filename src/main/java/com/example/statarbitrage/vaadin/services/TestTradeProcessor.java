package com.example.statarbitrage.vaadin.services;

import com.example.statarbitrage.model.Candle;
import com.example.statarbitrage.model.PairData;
import com.example.statarbitrage.model.ZScoreData;
import com.example.statarbitrage.model.ZScoreParam;
import com.example.statarbitrage.services.*;
import com.example.statarbitrage.threecommas.ThreeCommasFlowService;
import com.example.statarbitrage.threecommas.ThreeCommasService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TestTradeProcessor {
    private final PairDataService pairDataService;
    private final ChartService chartService;
    private final ZScoreService zScoreService;
    private final CandlesService candlesService;
    private final FileService fileService;
    private final SettingsService settingsService;
    private final CsvLogService csvLogService;
    private final ValidateService validateService;
    private final ThreeCommasService threeCommasService;
    private final ThreeCommasFlowService threeCommasFlowService;
    private final EventSendService eventSendService;
    private final TradeLogService tradeLogService;
    private final ExportService exportService;
    private final CointegrationCalculator cointegrationCalculator;

    public void testTrade(PairData pairData) {
        Map<String, List<Candle>> candlesMap = candlesService.getCandles(List.of(pairData.getLongTicker(), pairData.getShortTicker()), false);
        validateCandlesLimitAndThrow(candlesMap);

        double[] longTickerPrices = getClosePrices(candlesMap.get(pairData.getLongTicker()));
        double[] shortTickerPrices = getClosePrices(candlesMap.get(pairData.getShortTicker()));

        ZScoreData zScoreData = cointegrationCalculator.calculatePairZScores(
                settingsService.getSettingsFromDb(),
                pairData.getLongTicker(), pairData.getShortTicker(),
                longTickerPrices, shortTickerPrices
        );

        logData(zScoreData);
        pairDataService.update(pairData, zScoreData, candlesMap);
    }

    private void validateCandlesLimitAndThrow(Map<String, List<Candle>> candlesMap) {
        validateService.validateCandlesLimitAndThrow(candlesMap);
    }

    private static void logData(ZScoreData first) {
        ZScoreParam latest = first.getZscoreParams().get(first.getZscoreParams().size() - 1); // последние params
        log.info(String.format("Наша пара: %s/%s | p=%.5f | adf=%.5f | z=%.2f | corr=%.2f",
                first.getLongTicker(), first.getShortTicker(),
                latest.getPvalue(), latest.getAdfpvalue(), latest.getZscore(), latest.getCorrelation()
        ));
    }

    public double[] getClosePrices(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            return new double[0];
        }
        return candles.stream()
                .mapToDouble(Candle::getClose)
                .toArray();
    }

}
