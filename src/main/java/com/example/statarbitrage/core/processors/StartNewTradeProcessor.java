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
import java.util.Objects;
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

    public PairData startNewTrade(PairData pairData) {
        Settings settings = settingsService.getSettings();
        log.info("🚀 Начинаем новый трейд...");

        //Проверка на дурака
        if (validateService.isLastZLessThenMinZ(pairData, settings)) {
            //если впервые прогоняем и Z<ZMin
            pairDataService.delete(pairData);
            log.warn("Удалили пару {} - {} т.к. ZCurrent < ZMin", pairData.getLongTicker(), pairData.getShortTicker());
            return null;
        }

        Map<String, List<Candle>> candlesMap = candlesService.getApplicableCandlesMap(pairData, settings);
        Optional<ZScoreData> maybeZScoreData = zScoreService.calculateZScoreDataForNewTrade(pairData, settings, candlesMap);

        if (maybeZScoreData.isEmpty()) {
            log.warn("ZScore data is empty");
            return null;
        }

        ZScoreData zScoreData = maybeZScoreData.get();

        ZScoreParam latest = zScoreData.getLastZScoreParam(); // последние params

        if (!Objects.equals(pairData.getLongTicker(), zScoreData.getUndervaluedTicker()) || !Objects.equals(pairData.getShortTicker(), zScoreData.getOvervaluedTicker())) {
            String message = String.format("Начало нового терейда для пары лонг=%s шорт=%s. Тикеры поменялись местами!!! Торговать нельзя!!!", pairData.getLongTicker(), pairData.getShortTicker());
            log.error(message);
            throw new IllegalArgumentException(message);
        }

        log.info(String.format("Наш новый трейд: underValued=%s overValued=%s | p=%.5f | adf=%.5f | z=%.2f | corr=%.2f", zScoreData.getUndervaluedTicker(), zScoreData.getOvervaluedTicker(), latest.getPvalue(), latest.getAdfpvalue(), latest.getZscore(), latest.getCorrelation()));

        List<Candle> longTickerCandles = candlesMap.get(pairData.getLongTicker());
        List<Candle> shortTickerCandles = candlesMap.get(pairData.getShortTicker());
        pairDataService.update(pairData, zScoreData, longTickerCandles, shortTickerCandles);

        tradeLogService.saveFromPairData(pairData);
        return pairData;
    }
}
