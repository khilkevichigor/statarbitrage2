package com.example.statarbitrage.vaadin.processors;

import com.example.statarbitrage.model.*;
import com.example.statarbitrage.services.*;
import com.example.statarbitrage.vaadin.services.TradeStatus;
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
    private final CandlesService candlesService;
    private final SettingsService settingsService;
    private final TradeLogService tradeLogService;
    private final ZScoreService zScoreService;
    private final ValidateService validateService;

    public void testTrade(PairData pairData) {
        long start = System.currentTimeMillis();
        log.info("Trading pair...");

        Settings settings = settingsService.getSettingsFromDb();
        if (pairData.getStatus() == TradeStatus.SELECTED) {
            if (validateService.isLastZLessThenMinZ(pairData, settings)) {
                //если впервые прогоняем и Z<ZMin
                pairDataService.delete(pairData);
                log.warn("ZCurrent < ZMin, deleted pair");
            }
        }

        Map<String, List<Candle>> candlesMap = candlesService.getCandlesMap(pairData, settings);
        ZScoreData zScoreData = zScoreService.calculateZScoreDataOnUpdate(settings, candlesMap); //todo -ZEntry !!!
        logData(zScoreData); //todo ???
        pairDataService.update(pairData, zScoreData, candlesMap);
        tradeLogService.saveFromPairData(pairData);

        long end = System.currentTimeMillis();
        log.info("⏱️ testTrade() finished in {} сек", (end - start) / 1000.0);
    }

    private static void logData(ZScoreData zScoreData) {
        ZScoreParam latest = zScoreData.getZscoreParams().get(zScoreData.getZscoreParams().size() - 1); // последние params
        log.info(String.format("Наша пара: %s/%s | p=%.5f | adf=%.5f | z=%.2f | corr=%.2f",
                zScoreData.getLongTicker(), zScoreData.getShortTicker(),
                latest.getPvalue(), latest.getAdfpvalue(), latest.getZscore(), latest.getCorrelation()
        ));
    }
}
