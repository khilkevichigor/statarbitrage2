package com.example.statarbitrage.processors;

import com.example.statarbitrage.model.Candle;
import com.example.statarbitrage.model.EntryData;
import com.example.statarbitrage.model.Settings;
import com.example.statarbitrage.model.ZScoreEntry;
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
    private final SettingsService settingsService;
    private final Map<String, AtomicBoolean> runningTrades = new ConcurrentHashMap<>();

    @Async
    public void sendBestChart(String chatId) {
        long startTime = System.currentTimeMillis();
        removePreviousFiles();
        Settings settings = settingsService.getSettings();
        ConcurrentHashMap<String, List<Candle>> candlesMap = candlesService.getCandles();
        List<ZScoreEntry> zScoreEntry = PythonScriptsExecuter.executeAndReturnObject(PythonScripts.CREATE_Z_SCORE_FILE.getName(), Map.of(
                        "settings", settings,
                        "candlesMap", candlesMap
                ),
                new TypeReference<>() {
                });
//        EntryData entryData = entryDataService.createEntryData(zScoreEntry, candlesMap);
//        chartService.generateCombinedChartOls(chatId, candlesMap, zScoreEntry, entryData);
        logDuration(startTime);
    }

    @Async
    public void testTrade(String chatId) {
        AtomicBoolean isRunning = runningTrades.computeIfAbsent(chatId, k -> new AtomicBoolean(false));
        if (!isRunning.compareAndSet(false, true)) {
            log.warn("testTrade уже выполняется для chatId = {}", chatId);
            return;
        }
        try {
            Settings settings = settingsService.getSettings();
            ConcurrentHashMap<String, List<Candle>> candlesMap = candlesService.updateCandlesJsonForBestAndGet(zScoreService.obtainBestPair());

            List<ZScoreEntry> zScoreEntry = PythonScriptsExecuter.executeAndReturnObject(PythonScripts.CREATE_Z_SCORE_FILE.getName(), Map.of(
                            "settings", settings,
                            "candlesMap", candlesMap
                    ),
                    new TypeReference<>() {
                    });
            EntryData entryData = entryDataService.getEntryData();
//            entryDataService.updateData(entryData, zScoreEntry, candlesMap);
//            chartService.clearChartDir();
//            chartService.generateCombinedChartOls(chatId, candlesMap, zScoreEntry, entryData);
        } finally {
            runningTrades.remove(chatId);
        }
    }

    private static void validateCurrentPricesBeforeAndAfterScriptAndThrow(ZScoreEntry firstPair, ConcurrentHashMap<String, List<Candle>> candlesMap) {
        List<Candle> longTickerCandles = candlesMap.get(firstPair.getLongticker());
        double before = longTickerCandles.get(longTickerCandles.size() - 1).getClose();
        double after = firstPair.getLongtickercurrentprice();
        if (after != before) {
            log.error("Wrong current prices before {} and after {} script", before, after);
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
        fileService.deleteSpecificFilesInProjectRoot(List.of("z_score.json", "entry_data.json", "candles.json"));
        chartService.clearChartDir();
    }
}
