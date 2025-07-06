package com.example.statarbitrage.core.processors;

import com.example.statarbitrage.common.dto.Candle;
import com.example.statarbitrage.common.dto.ZScoreData;
import com.example.statarbitrage.common.dto.ZScoreParam;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.core.services.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateTradeProcessor {
    private final PairDataService pairDataService;
    private final CandlesService candlesService;
    private final SettingsService settingsService;
    private final TradeLogService tradeLogService;
    private final ZScoreService zScoreService;

    public void updateTrade(PairData pairData) {
        Settings settings = settingsService.getSettings();
        log.info("🚀 Начинаем обновление трейда...");

        Map<String, List<Candle>> candlesMap = candlesService.getApplicableCandlesMap(pairData, settings);
        ZScoreData zScoreData = zScoreService.calculateZScoreData(settings, candlesMap);
        logData(zScoreData);

        List<Candle> longTickerCandles = candlesMap.get(pairData.getLongTicker());
        List<Candle> shortTickerCandles = candlesMap.get(pairData.getShortTicker());
        pairDataService.update(pairData, zScoreData, longTickerCandles, shortTickerCandles);

        tradeLogService.saveFromPairData(pairData);
    }

    private static void logData(ZScoreData zScoreData) {
        ZScoreParam latest = zScoreData.getLastZScoreParam(); // последние params
        log.info(String.format("Наша пара: underValued=%s overValued=%s | p=%.5f | adf=%.5f | z=%.2f | corr=%.2f",
                zScoreData.getUndervaluedTicker(), zScoreData.getOvervaluedTicker(),
                latest.getPvalue(), latest.getAdfpvalue(), latest.getZscore(), latest.getCorrelation()
        ));
    }
}
