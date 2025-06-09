package com.example.statarbitrage.processors;

import com.example.statarbitrage.bot.TradeType;
import com.example.statarbitrage.model.Candle;
import com.example.statarbitrage.model.PairData;
import com.example.statarbitrage.model.ZScoreData;
import com.example.statarbitrage.model.ZScoreParam;
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
import java.util.stream.Stream;

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
    private final PairLogService pairLogService;
    private final Map<String, AtomicBoolean> runningTrades = new ConcurrentHashMap<>();

    @Async
    public void sendBestChart(String chatId) {
        long startTime = System.currentTimeMillis();
        removePreviousFiles();
        List<String> applicableTickers = candlesService.getApplicableTickers("1D");
        log.info("Всего отобрано {} тикеров", applicableTickers.size());

        ConcurrentHashMap<String, List<Candle>> candlesMap = candlesService.getCandles(applicableTickers);
        List<ZScoreData> zScoreDataList = PythonScriptsExecuter.executeAndReturnObject(PythonScripts.CALC_ZSCORES.getName(), Map.of(
                        "settings", settingsService.getSettings(),
                        "candlesMap", candlesMap,
                        "mode", "sendBestChart" //чтобы отфильтровать плохие пары
                ),
                new TypeReference<>() {
                });
        zScoreService.reduceDuplicates(zScoreDataList);
        zScoreService.sortParamsByTimestamp(zScoreDataList);
        ZScoreData best = zScoreService.obtainBest(zScoreDataList);
        PairData pairData = pairDataService.createPairData(best, candlesMap);
        chartService.createAndSend(chatId, pairData);
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
            PairData pairData = pairDataService.getPairData();
            ConcurrentHashMap<String, List<Candle>> candlesMap = candlesService.getCandles(Stream.of(pairData.getLongTicker(), pairData.getShortTicker()).sorted().toList());
            List<ZScoreData> zScoreDataList = PythonScriptsExecuter.executeAndReturnObject(PythonScripts.CALC_ZSCORES.getName(), Map.of(
                            "settings", settingsService.getSettings(),
                            "candlesMap", candlesMap,
                            "mode", "testTrade" //чтобы не отфильтровать нашу уже отобранную на FIND лучшую пару
                    ),
                    new TypeReference<>() {
                    });
            zScoreService.reduceDuplicates(zScoreDataList);
            zScoreService.sortParamsByTimestamp(zScoreDataList);
            validateSizeOfPairsAndThrow(zScoreDataList);
            ZScoreData first = zScoreDataList.get(0);
            logData(first);
            validateCurrentPricesBeforeAndAfterScriptAndThrow(first, candlesMap);
            pairDataService.update(pairData, first, candlesMap);
            //обновлять строку в csv
            pairLogService.logOrUpdatePair(pairData);
            chartService.createAndSend(chatId, pairData);
        } finally {
            runningTrades.remove(chatId);
        }
    }

    public void simulation(String chatId, TradeType tradeType) {

    }

    private static void logData(ZScoreData first) {
        ZScoreParam latest = first.getZscoreParams().get(first.getZscoreParams().size() - 1); // последние params
        log.info(String.format("Наша пара: %s/%s | p=%.5f | adf=%.5f | z=%.2f | corr=%.2f",
                latest.getLongticker(), latest.getShortticker(),
                latest.getPvalue(), latest.getAdfpvalue(), latest.getZscore(), latest.getCorrelation()
        ));
    }

    private static void validateSizeOfPairsAndThrow(List<ZScoreData> zScoreDataList) {
        if (zScoreDataList.size() != 1) {
            throw new IllegalArgumentException("Size not equal 1!");
        }
    }

    private static void validateCurrentPricesBeforeAndAfterScriptAndThrow(ZScoreData zScoreData, ConcurrentHashMap<String, List<Candle>> candlesMap) {
        ZScoreParam latestParam = zScoreData.getZscoreParams().get(zScoreData.getZscoreParams().size() - 1);
        List<Candle> longTickerCandles = candlesMap.get(latestParam.getLongticker());
        double before = longTickerCandles.get(longTickerCandles.size() - 1).getClose(); //последний из свечей
        double after = latestParam.getLongtickercurrentprice(); //последний от скрипта
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
