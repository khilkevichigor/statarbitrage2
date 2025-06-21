package com.example.statarbitrage.processors;

import com.example.statarbitrage.bot.TradeType;
import com.example.statarbitrage.events.StartNewTradeEvent;
import com.example.statarbitrage.model.*;
import com.example.statarbitrage.python.PythonScripts;
import com.example.statarbitrage.python.PythonScriptsExecuter;
import com.example.statarbitrage.services.*;
import com.example.statarbitrage.threecommas.ThreeCommasFlowService;
import com.example.statarbitrage.threecommas.ThreeCommasService;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.example.statarbitrage.constant.Constants.*;

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
    private final CsvLogService csvLogService;
    private final ValidateService validateService;
    private final ThreeCommasService threeCommasService;
    private final ThreeCommasFlowService threeCommasFlowService;
    private final EventSendService eventSendService;
    private final TradeLogService tradeLogService;
    private final ExportService exportService;
    private final Map<String, AtomicBoolean> runningTrades = new ConcurrentHashMap<>();

    @Async
    public void findBestAsync(String chatId) {
        findBest(chatId);
    }

    public void findBest(String chatId) {
        long startTime = System.currentTimeMillis();
        removePreviousFiles();
        List<String> applicableTickers = candlesService.getApplicableTickers("1D", true);
        Map<String, List<Candle>> candlesMap = candlesService.getCandles(applicableTickers, true);
        validateCandlesLimitAndThrow(candlesMap);
        List<ZScoreData> zScoreDataList = PythonScriptsExecuter.executeAndReturnObject(PythonScripts.CALC_ZSCORES.getName(), Map.of(
                        "settings", settingsService.getSettings(),
                        "candles_map", candlesMap,
                        "mode", "send_best_chart" //чтобы отфильтровать плохие пары
                ),
                new TypeReference<>() {
                });
        zScoreService.reduceDuplicates(zScoreDataList);
        zScoreService.sortByLongTicker(zScoreDataList);
        zScoreService.sortParamsByTimestamp(zScoreDataList);
        ZScoreData best = zScoreService.obtainBest(zScoreDataList);
        PairData pairData = pairDataService.createPairData(best, candlesMap);
        chartService.createAndSend(chatId, pairData);
        logDuration(startTime);
    }

    @Async
    public void testTradeAsync(String chatId) {
        testTrade(chatId);
    }

    public void testTrade(String chatId) {
        AtomicBoolean isRunning = runningTrades.computeIfAbsent(chatId, k -> new AtomicBoolean(false));
        if (!isRunning.compareAndSet(false, true)) {
            log.warn("testTrade уже выполняется для chatId = {}", chatId);
            return;
        }
        try {
            PairData pairData = pairDataService.getPairData();
            Map<String, List<Candle>> candlesMap = candlesService.getCandles(List.of(pairData.getLongTicker(), pairData.getShortTicker()), false);
            validateCandlesLimitAndThrow(candlesMap);
            List<ZScoreData> zScoreDataList = PythonScriptsExecuter.executeAndReturnObject(PythonScripts.CALC_ZSCORES.getName(), Map.of(
                            "settings", settingsService.getSettings(),
                            "candles_map", candlesMap,
                            "mode", "test_trade",
                            "long_ticker", pairData.getLongTicker(),
                            "short_ticker", pairData.getShortTicker()
                    ),
                    new TypeReference<>() {
                    });
            zScoreService.sortParamsByTimestamp(zScoreDataList);
            validateSizeOfPairsAndThrow(zScoreDataList, 1);
            ZScoreData first = zScoreDataList.get(0);
            logData(first);
            pairDataService.update(pairData, first, candlesMap);
            TradeLog tradeLog = tradeLogService.saveFromPairData(pairData);
            exportService.exportToCsvV2();
            csvLogService.logOrUpdatePair(tradeLog);
            chartService.createAndSend(chatId, pairData);
            if (pairData.getExitReason() != null) {
                sendEventToStartNewTrade(chatId, true);
            }
        } finally {
            runningTrades.remove(chatId);
        }
    }

    private void sendEventToStartNewTrade(String chatId, boolean withLogging) {
        try {
            eventSendService.sendStartNewTradeEvent(StartNewTradeEvent.builder()
                    .chatId(chatId)
                    .build());
            if (withLogging) {
                log.info("📤 Отправлен эвент на новый трейд");
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при отправке эвента на новый трейд: {}", e.getMessage(), e);
        }
    }

    public void test3commasApi(String chatIdStr) {
        try {
            threeCommasService.test();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void validateCandlesLimitAndThrow(Map<String, List<Candle>> candlesMap) {
        validateService.validateCandlesLimitAndThrow(candlesMap);
    }

    public void simulation(String chatId, TradeType tradeType) {
        //кидать эвент на find
        //запускать find с флагом isSimulation
        //в конце find если isSimulation слать эвент на testTrade
        //если Z пересекла 0 - слать новый эвент на find
    }

    private static void logData(ZScoreData first) {
        ZScoreParam latest = first.getZscoreParams().get(first.getZscoreParams().size() - 1); // последние params
        log.info(String.format("Наша пара: %s/%s | p=%.5f | adf=%.5f | z=%.2f | corr=%.2f",
                first.getLongTicker(), first.getShortTicker(),
                latest.getPvalue(), latest.getAdfpvalue(), latest.getZscore(), latest.getCorrelation()
        ));
    }

    private void validateSizeOfPairsAndThrow(List<ZScoreData> zScoreDataList, int size) {
        validateService.validateSizeOfPairsAndThrow(zScoreDataList, size);
    }

    private static void logDuration(long startTime) {
        long durationMillis = System.currentTimeMillis() - startTime;
        long minutes = durationMillis / 1000 / 60;
        long seconds = (durationMillis / 1000) % 60;
        log.info("Скан завершен. Обработано за {} мин {} сек", minutes, seconds);
    }

    private void removePreviousFiles() {
        fileService.deleteSpecificFilesInProjectRoot(List.of(Z_SCORE_FILE_NAME, PAIR_DATA_FILE_NAME, CANDLES_FILE_NAME));
        chartService.clearChartDir();
    }

    public void startRealTrade(String chatIdStr) {
        threeCommasFlowService.startRealTradeViaDcaBots(chatIdStr);
    }

    public void stopRealTrade(String chatIdStr) {
        threeCommasFlowService.stopRealTradeViaDcaBots(chatIdStr);
    }
}
