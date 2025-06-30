package com.example.statarbitrage.vaadin.processors;

import com.example.statarbitrage.model.*;
import com.example.statarbitrage.services.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartNewTradeProcessor {
    private final PairDataService pairDataService;
    private final CandlesService candlesService;
    private final SettingsService settingsService;
    private final TradeLogService tradeLogService;
    private final ZScoreService zScoreService;
    private final ValidateService validateService;

    //todo сделать юнит тесты что бы понять как меняется Z а то ощущение что он пляшет ппц
    public PairData startNewTrade(PairData pairData) {
        Settings settings = settingsService.getSettingsFromDb();

        if (validateService.isLastZLessThenMinZ(pairData, settings)) {
            //если впервые прогоняем и Z<ZMin
            pairDataService.delete(pairData);
            log.warn("ZCurrent < ZMin, deleted the pair");
            return null;
        }

        Map<String, List<Candle>> candlesMap = candlesService.getCandlesMap(pairData, settings);
        Optional<ZScoreData> maybeZScoreData = zScoreService.calculateZScoreDataForNewTrade(settings, candlesMap);

        if (maybeZScoreData.isEmpty()) {
            log.warn("ZScore data is empty");
            return null;
        }

        ZScoreData zScoreData = maybeZScoreData.get();

        ZScoreParam latest = zScoreData.getLastZScoreParam(); // последние params
        log.info(String.format("Наш новый трейд: long=%s short=%s | p=%.5f | adf=%.5f | z=%.2f | corr=%.2f", zScoreData.getLongTicker(), zScoreData.getShortTicker(), latest.getPvalue(), latest.getAdfpvalue(), latest.getZscore(), latest.getCorrelation()));

        List<Candle> longTickerCandles = candlesMap.get(pairData.getLongTicker());
        List<Candle> shortTickerCandles = candlesMap.get(pairData.getShortTicker());
        pairDataService.update(pairData, zScoreData, longTickerCandles, shortTickerCandles);

        tradeLogService.saveFromPairData(pairData);
        return pairData;
    }
}
