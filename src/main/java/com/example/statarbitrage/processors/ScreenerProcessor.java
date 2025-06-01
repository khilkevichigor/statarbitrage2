package com.example.statarbitrage.processors;

import com.example.statarbitrage.model.Candle;
import com.example.statarbitrage.model.EntryData;
import com.example.statarbitrage.model.ProfitData;
import com.example.statarbitrage.model.ZScoreEntry;
import com.example.statarbitrage.python.PythonScripts;
import com.example.statarbitrage.python.PythonScriptsExecuter;
import com.example.statarbitrage.services.*;
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
    private final EntryDataService entryDataService;
    private final ChartService chartService;
    private final ZScoreService zScoreService;
    private final CandlesService candlesService;
    private final FileService fileService;
    private final Map<String, AtomicBoolean> runningTrades = new ConcurrentHashMap<>();

    @Async
    public void sendBestChart(String chatId) {
        long startTime = System.currentTimeMillis();
        removePreviousFiles();
        ConcurrentHashMap<String, List<Candle>> candlesMap = candlesService.getCandles();
        PythonScriptsExecuter.execute(PythonScripts.CREATE_Z_SCORE_FILE.getName(), true);
        ZScoreEntry bestPair = zScoreService.getBestPair();
        entryDataService.createEntryData(bestPair, candlesMap);
        PythonScriptsExecuter.execute(PythonScripts.CREATE_CHART.getName(), true);
        chartService.sendChart(chatId, chartService.getChart(), "📊LONG " + bestPair.getLongticker() + ", SHORT " + bestPair.getShortticker(), true);
        logDuration(startTime);
    }

    @Async
    public void testTrade(String chatId) {
        AtomicBoolean isRunning = runningTrades.computeIfAbsent(chatId, k -> new AtomicBoolean(false));
        if (!isRunning.compareAndSet(false, true)) {
            log.warn("testTrade уже выполняется для chatId = {}", chatId);
            return;
        }

        ZScoreEntry bestPair = zScoreService.getBestPair();
        EntryData entryData = entryDataService.getEntryData();

        ConcurrentHashMap<String, List<Candle>> candlesMap = candlesService.getCandles(Set.of(entryData.getLongticker(), entryData.getShortticker()));
        entryDataService.updateCurrentPrices(entryData, candlesMap);
        entryDataService.setupEntryPointsIfNeededFromCandles(entryData, bestPair, candlesMap);
        ProfitData profitData = entryDataService.calculateAndSetProfit(entryData);

        PythonScriptsExecuter.execute(PythonScripts.CREATE_Z_SCORE_FILE.getName(), true);

        chartService.clearChartDir();

        PythonScriptsExecuter.execute(PythonScripts.CREATE_CHART.getName(), true);

        chartService.sendChart(chatId, chartService.getChart(), profitData.getLogMessage(), false);
    }

    private static void logDuration(long startTime) {
        long durationMillis = System.currentTimeMillis() - startTime;
        long minutes = durationMillis / 1000 / 60;
        long seconds = (durationMillis / 1000) % 60;
        log.info("Скан завершен. Обработано за {} мин {} сек", minutes, seconds);
    }

    private void removePreviousFiles() {
        fileService.deleteSpecificFilesInProjectRoot(List.of("z_score.json", "entry_data.json", "candles.json"));
        chartService.clearChartDir();
    }
}
