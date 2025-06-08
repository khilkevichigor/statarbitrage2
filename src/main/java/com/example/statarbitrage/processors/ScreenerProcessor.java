package com.example.statarbitrage.processors;

import com.example.statarbitrage.bot.TradeType;
import com.example.statarbitrage.model.Candle;
import com.example.statarbitrage.model.PairData;
import com.example.statarbitrage.model.ZScoreEntry;
import com.example.statarbitrage.model.ZScoreTimeSeries;
import com.example.statarbitrage.python.PythonScripts;
import com.example.statarbitrage.python.PythonScriptsExecuter;
import com.example.statarbitrage.services.*;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScreenerProcessor {
    private final PairDataService pairDataService;
    private final ChartService chartService;
    private final ZScoreService zScoreService;
    private final CandlesService candlesService;
    private final FileService fileService;
    private final SettingsService settingsService;
    private final Map<String, AtomicBoolean> runningTrades = new ConcurrentHashMap<>();

    @Async
    public void sendBestChart(String chatId) {
        long startTime = System.currentTimeMillis();
        removePreviousFiles();
        Set<String> applicableTickers = candlesService.getApplicableTickers("1D");
        log.info("Всего отобрано {} тикеров", applicableTickers.size());

        ConcurrentHashMap<String, List<Candle>> candlesMap = candlesService.getCandles(applicableTickers);
        List<ZScoreTimeSeries> zScoreTimeSeries = PythonScriptsExecuter.executeAndReturnObject(PythonScripts.CALC_ZSCORES.getName(), Map.of(
                        "settings", settingsService.getSettings(),
                        "candlesMap", candlesMap,
                        "mode", "sendBestChart" //что бы фильтровать плохие пары
                ),
                new TypeReference<>() {
                });
        ZScoreTimeSeries bestZScoreTimeSeries = zScoreService.obtainBest(zScoreTimeSeries);
        PairData pairData = pairDataService.createPairData(bestZScoreTimeSeries.getEntries().get(0), candlesMap);
        chartService.createAndSend(chatId, bestZScoreTimeSeries.getEntries(), pairData);
        logDuration(startTime);
    }

    @Async
    public void testTrade(String chatId, TradeType tradeType) {
        AtomicBoolean isRunning = runningTrades.computeIfAbsent(chatId, k -> new AtomicBoolean(false));
        if (!isRunning.compareAndSet(false, true)) {
            log.warn("testTrade уже выполняется для chatId = {}", chatId);
            return;
        }
        try {
            PairData pairData = pairDataService.getPairData();
            ConcurrentHashMap<String, List<Candle>> candlesMap = candlesService.getCandles(Set.of(pairData.getLongTicker(), pairData.getShortTicker()));
            List<ZScoreTimeSeries> zScoreTimeSeries = PythonScriptsExecuter.executeAndReturnObject(PythonScripts.CALC_ZSCORES.getName(), Map.of(
                            "settings", settingsService.getSettings(),
                            "candlesMap", candlesMap,
                            "mode", "testTrade" //чтобы не отфильтровать нашу уже отобранную лучшую пару
                    ),
                    new TypeReference<>() {
                    });
            validateSizeOfPairsAndThrow(zScoreTimeSeries);
            ZScoreTimeSeries firstPair = zScoreTimeSeries.get(0);
            validateCurrentPricesBeforeAndAfterScriptAndThrow(firstPair.getEntries().get(firstPair.getEntries().size() - 1), candlesMap);
            pairDataService.updatePairDataAndSave(pairData, firstPair.getEntries().get(0), candlesMap, tradeType);
            clearChartsDirectory();
            chartService.createAndSend(chatId, firstPair.getEntries(), pairData);
        } finally {
            runningTrades.remove(chatId);
        }
    }

    private static void validateSizeOfPairsAndThrow(List<ZScoreTimeSeries> zScoreTimeSeries) {
        if (zScoreTimeSeries.size() != 1) {
            throw new IllegalArgumentException("Size not equal 1!");
        }
    }

    private void clearChartsDirectory() {
        chartService.clearChartDir();
    }

    private static void validateCurrentPricesBeforeAndAfterScriptAndThrow(ZScoreEntry entry, ConcurrentHashMap<String, List<Candle>> candlesMap) {
        List<Candle> aTickerCandles = candlesMap.get(entry.getA());
        double before = aTickerCandles.get(aTickerCandles.size() - 1).getClose();
        double after = entry.getAtickercurrentprice();
        if (after != before) {
            log.error("Wrong current prices before {{}} and after {{}} script", before, after);
            throw new IllegalArgumentException("Wrong current prices before and after script");
        }
    }

    private static void logDuration(long startTime) {
        long durationMillis = System.currentTimeMillis() - startTime;
        long minutes = durationMillis / 1000 / 60;
        long seconds = (durationMillis / 1000) % 60;
        log.info("Скан завершен. Обработано за {} мин {} сек", minutes, seconds);
    }

    private void removePreviousFiles() {
        fileService.deleteSpecificFilesInProjectRoot(List.of("z_score.json", "pair_data.json", "candles.json"));
        chartService.clearChartDir();
    }
}
