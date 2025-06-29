package com.example.statarbitrage.vaadin.processors;

import com.example.statarbitrage.model.*;
import com.example.statarbitrage.services.*;
import com.example.statarbitrage.vaadin.python.PythonRestClient;
import com.example.statarbitrage.vaadin.services.TradeStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TestTradeProcessor {
    private final PairDataService pairDataService;
    private final CandlesService candlesService;
    private final ZScoreService zScoreService;
    private final SettingsService settingsService;
    private final ValidateService validateService;
    private final TradeLogService tradeLogService;

    public void testTrade(PairData pairData) {
        log.info("Trading pair...");
        Settings settings = settingsService.getSettingsFromDb();
        Map<String, List<Candle>> candlesMap = candlesService.getCandles(List.of(pairData.getLongTicker(), pairData.getShortTicker()), false);
        validateService.validateCandlesLimitAndThrow(candlesMap);

        List<ZScoreData> zScoreDataList = PythonRestClient.fetchZScoreData(
                settings,
                candlesMap
        );
        validateService.validateSizeOfPairsAndThrow(zScoreDataList, 1);

        ZScoreData zScoreData = zScoreDataList.get(0);

        if (pairData.getStatus() == TradeStatus.SELECTED) {
            //готовим перед трейдами
//            zScoreService.handleNegativeZ(Collections.singletonList(zScoreData));
//            validateService.validatePositiveZAndThrow(Collections.singletonList(zScoreData));
            if (validateService.isLastZLessThenMinZ(Collections.singletonList(zScoreData), settings)) {
                return;
            }
        }

        logData(zScoreData);
        pairDataService.update(pairData, zScoreData, candlesMap);
        tradeLogService.saveFromPairData(pairData);
    }

    private static void logData(ZScoreData zScoreData) {
        ZScoreParam latest = zScoreData.getZscoreParams().get(zScoreData.getZscoreParams().size() - 1); // последние params
        log.info(String.format("Наша пара: %s/%s | p=%.5f | adf=%.5f | z=%.2f | corr=%.2f",
                zScoreData.getLongTicker(), zScoreData.getShortTicker(),
                latest.getPvalue(), latest.getAdfpvalue(), latest.getZscore(), latest.getCorrelation()
        ));
    }
}
