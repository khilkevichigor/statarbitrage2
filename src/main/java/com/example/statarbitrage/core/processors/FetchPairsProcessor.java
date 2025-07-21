package com.example.statarbitrage.core.processors;

import com.example.statarbitrage.common.dto.Candle;
import com.example.statarbitrage.common.dto.ZScoreData;
import com.example.statarbitrage.common.dto.ZScoreParam;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.common.model.TradeStatus;
import com.example.statarbitrage.core.services.CandlesService;
import com.example.statarbitrage.core.services.PairDataService;
import com.example.statarbitrage.core.services.SettingsService;
import com.example.statarbitrage.core.services.ZScoreService;
import com.example.statarbitrage.ui.dto.FetchPairsRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FetchPairsProcessor {
    private final PairDataService pairDataService;
    private final ZScoreService zScoreService;
    private final CandlesService candlesService;
    private final SettingsService settingsService;

    public List<PairData> fetchPairs(FetchPairsRequest request) {
        validateRequest(request);

        long startTime = System.currentTimeMillis();
        log.info("🚀 Начинаем поиск пар для торговли...");

        Settings settings = settingsService.getSettings();
        List<String> tradingTickers = collectTradingTickers();

        Map<String, List<Candle>> candlesMap = fetchCandlesData(settings, tradingTickers);
        if (candlesMap.isEmpty()) {
            log.warn("⚠️ Не удалось получить данные свечей");
            return Collections.emptyList();
        }

        int count = determinePairCount(request, settings);
        List<ZScoreData> zScoreDataList = calculateTopPairs(settings, candlesMap, count);

        if (zScoreDataList.isEmpty()) {
            log.warn("Пропуск хода - подходящие пары не найдены");
            return Collections.emptyList();
        }

        logFoundPairs(zScoreDataList);

        List<PairData> topPairs = createPairDataList(zScoreDataList, candlesMap);

        logCompletionStats(topPairs, startTime);

        return topPairs;
    }

    private void validateRequest(FetchPairsRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Неверный запрос на поиск пар");
        }
    }

    private List<String> collectTradingTickers() {
        List<PairData> tradingPairs = pairDataService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);

        List<String> tradingTickers = new ArrayList<>();
        tradingPairs.forEach(p -> {
            tradingTickers.add(p.getLongTicker());
            tradingTickers.add(p.getShortTicker());
        });

        return tradingTickers;
    }

    private Map<String, List<Candle>> fetchCandlesData(Settings settings, List<String> tradingTickers) {
        long candlesStartTime = System.currentTimeMillis();

        Map<String, List<Candle>> candlesMap = candlesService.getApplicableCandlesMap(settings, tradingTickers);

        long candlesEndTime = System.currentTimeMillis();
        log.info("✅ Собрали карту свечей за {}с",
                String.format("%.2f", (candlesEndTime - candlesStartTime) / 1000.0));

        return candlesMap;
    }

    private int determinePairCount(FetchPairsRequest request, Settings settings) {
        return request.getCountOfPairs() != null ? request.getCountOfPairs() : (int) settings.getUsePairs();
    }

    private List<ZScoreData> calculateTopPairs(Settings settings, Map<String, List<Candle>> candlesMap, int count) {
        try {
            return zScoreService.getTopNPairs(settings, candlesMap, count);
        } catch (Exception e) {
            log.error("❌ Ошибка при расчете Z-Score: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private void logFoundPairs(List<ZScoreData> zScoreDataList) {
        for (int i = 0; i < zScoreDataList.size(); i++) {
            ZScoreData zScoreData = zScoreDataList.get(i);
            ZScoreParam latest = zScoreData.getLastZScoreParam();
            log.info(String.format("%d. Пара: underValuedTicker=%s overValuedTicker=%s | p=%.5f | adf=%.5f | z=%.2f | corr=%.2f",
                    i + 1, zScoreData.getUndervaluedTicker(), zScoreData.getOvervaluedTicker(),
                    latest.getPvalue(), latest.getAdfpvalue(), latest.getZscore(), latest.getCorrelation()));
        }
    }

    private List<PairData> createPairDataList(List<ZScoreData> zScoreDataList, Map<String, List<Candle>> candlesMap) {
        try {
            return pairDataService.createPairDataList(zScoreDataList, candlesMap);
        } catch (Exception e) {
            log.error("❌ Ошибка при создании PairData: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private void logCompletionStats(List<PairData> topPairs, long startTime) {
        long endTime = System.currentTimeMillis();
        log.info("Создали новых PairData: {{}}", topPairs.size());
        log.info("✅ Поиск пар завершен за {}с",
                String.format("%.2f", (endTime - startTime) / 1000.0));
    }
}
