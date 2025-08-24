package com.example.core.core.processors;

import com.example.core.common.dto.Candle;
import com.example.core.common.dto.ZScoreData;
import com.example.core.common.model.PairData;
import com.example.core.common.model.Settings;
import com.example.core.common.model.TradeStatus;
import com.example.core.core.services.CandlesService;
import com.example.core.core.services.PairDataService;
import com.example.core.core.services.SettingsService;
import com.example.core.core.services.ZScoreService;
import com.example.core.ui.dto.FetchPairsRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class FetchPairsProcessor {
    private final PairDataService pairDataService;
    private final ZScoreService zScoreService;
    private final CandlesService candlesService;
    private final SettingsService settingsService;

    public List<PairData> fetchPairs(FetchPairsRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("❌ FetchPairsRequest не может быть null");
        }

        long start = System.currentTimeMillis();
        log.info("");
        log.info("🔎 Начало поиска пар...");

        Settings settings = settingsService.getSettings();
        List<String> usedTickers = getUsedTickers();
        Map<String, List<Candle>> candlesMap = getCandles(settings, usedTickers);

        if (candlesMap.isEmpty()) {
            log.warn("⚠️ Данные свечей не получены — пропуск поиска.");
            return Collections.emptyList();
        }

        int count = Optional.ofNullable(request.getCountOfPairs())
                .orElse((int) settings.getUsePairs());

        List<ZScoreData> zScoreDataList = computeZScoreData(settings, candlesMap, count);
        if (zScoreDataList.isEmpty()) {
            return Collections.emptyList();
        }

        logZScoreResults(zScoreDataList);

        List<PairData> pairs = createPairs(zScoreDataList, candlesMap);

        log.debug("✅ Создано {} пар", pairs.size());
        pairs.forEach(p -> log.debug("📈 {}", p.getPairName()));
        log.debug("🕒 Время выполнения: {} сек", String.format("%.2f", (System.currentTimeMillis() - start) / 1000.0));

        return pairs;
    }

    private List<String> getUsedTickers() {
        List<PairData> activePairs = pairDataService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);
        List<String> tickers = new ArrayList<>();
        for (PairData pair : activePairs) {
            tickers.add(pair.getLongTicker());
            tickers.add(pair.getShortTicker());
        }
        return tickers;
    }

    private Map<String, List<Candle>> getCandles(Settings settings, List<String> tradingTickers) {
        long start = System.currentTimeMillis();
        Map<String, List<Candle>> map = candlesService.getApplicableCandlesMap(settings, tradingTickers);
        log.debug("✅ Свечи загружены за {} сек", String.format("%.2f", (System.currentTimeMillis() - start) / 1000.0));
        return map;
    }

    private List<ZScoreData> computeZScoreData(Settings settings, Map<String, List<Candle>> candlesMap, int count) {
        try {
            return zScoreService.getTopNZScoreData(settings, candlesMap, count);
        } catch (Exception e) {
            log.error("❌ Ошибка при расчете Z-Score: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private void logZScoreResults(List<ZScoreData> dataList) {
        int index = 1;
        for (ZScoreData data : dataList) {
            // Use NumberFormatter.format which handles nulls and returns "N/A"
            String cointegrationPValue = com.example.core.common.utils.NumberFormatter.format(data.getJohansenCointPValue(), 5);
            String avgAdfPValue = com.example.core.common.utils.NumberFormatter.format(data.getAvgAdfPvalue(), 5);
            String latestZscore = com.example.core.common.utils.NumberFormatter.format(data.getLatestZScore(), 2);
            String correlation = com.example.core.common.utils.NumberFormatter.format(data.getPearsonCorr(), 2);

            log.debug(String.format("%d. Пара: underValuedTicker=%s overValuedTicker=%s | p=%s | adf=%s | z=%s | corr=%s",
                    index++, data.getUnderValuedTicker(), data.getOverValuedTicker(),
                    cointegrationPValue, avgAdfPValue, latestZscore, correlation));
        }
    }

    private List<PairData> createPairs(List<ZScoreData> zScoreDataList, Map<String, List<Candle>> candlesMap) {
        try {
            return pairDataService.createPairDataList(zScoreDataList, candlesMap);
        } catch (Exception e) {
            log.error("❌ Ошибка при создании PairData: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
