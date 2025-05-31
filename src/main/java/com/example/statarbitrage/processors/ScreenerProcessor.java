package com.example.statarbitrage.processors;

import com.example.statarbitrage.api.OkxClient;
import com.example.statarbitrage.events.SendAsTextEvent;
import com.example.statarbitrage.model.*;
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

    private final OkxClient okxClient;
    private final EventSendService eventSendService;
    private final SettingsService settingsService;
    private final ProfitService profitService;
    private final FileService fileService;
    private final EntryDataService entryDataService;
    private final ChartService chartService;
    private final ZScoreService zScoreService;
    private final CandlesService candlesService;

    private final Map<String, AtomicBoolean> runningTrades = new ConcurrentHashMap<>();

    @Async
    public void testTrade(String chatId) {
        AtomicBoolean isRunning = runningTrades.computeIfAbsent(chatId, k -> new AtomicBoolean(false));
        if (!isRunning.compareAndSet(false, true)) {
            log.warn("testTrade уже выполняется для chatId = {}", chatId);
            return;
        }

        try {
            ZScoreEntry topPair = zScoreService.getTopPairEntry();
            EntryData entryData = entryDataService.getEntryData();
            Settings settings = settingsService.getSettings();

            ConcurrentHashMap<String, List<Candle>> candlesMap = okxClient.getCandlesMap(Set.of(entryData.getLongticker(), entryData.getShortticker()), settings);
            candlesService.save(candlesMap);
            entryDataService.updateCurrentPricesFromCandles(entryData, candlesMap);
            entryDataService.setupEntryPointsIfNeededFromCandles(entryData, topPair, candlesMap);
            ProfitData profitData = profitService.calculateAndSetProfit(entryData, settings.getCapitalLong(), settings.getCapitalShort(), settings.getLeverage(), settings.getFeePctPerTrade());

            PythonScriptsExecuter.execute(PythonScripts.CREATE_Z_SCORE_FILE.getName(), true);
            chartService.clearChartDir();
            PythonScriptsExecuter.execute(PythonScripts.CREATE_CHART.getName(), true);

            chartService.sendChart(chatId, chartService.getChart(), profitData.getLogMessage(), false);
        } catch (Exception e) {
            log.error("❌ Ошибка в testTrade: {}", e.getMessage(), e);
        } finally {
            isRunning.set(false);
        }
    }

    @Async
    public void sendBestChart(String chatId) {
        long startTime = System.currentTimeMillis();

        fileService.deleteSpecificFilesInProjectRoot(List.of("z_score.json", "entry_data.json", "candles.json"));

        Settings settings = settingsService.getSettings();

        Set<String> swapTickers = okxClient.getSwapTickers();
        int totalSymbols = swapTickers.size();

        ConcurrentHashMap<String, List<Candle>> allCandles = okxClient.getCandlesMap(swapTickers, settings);
        log.info("Собрали цены для {} монет", allCandles.size());

        List.of("USDC-USDT-SWAP").forEach(allCandles::remove);
        log.info("Удалили цены тикеров из черного списка");

        candlesService.save(allCandles);
        log.info("Сохранили цены в candles.json");

        log.info("🐍Запускаем скрипты...");

        PythonScriptsExecuter.execute(PythonScripts.CREATE_Z_SCORE_FILE.getName(), true);

        zScoreService.keepPairWithMaxZScore();
        log.info("🔍 Оставили лучшую пару в z_score.json");

        chartService.clearChartDir();
        log.info("Очистили папку с чартами");

        ZScoreEntry topPair = zScoreService.getTopPairEntry();

        EntryData entryData = entryDataService.createEntryData(topPair);
        log.info("Создали entry_data.json");

        PythonScriptsExecuter.execute(PythonScripts.CREATE_CHART.getName(), true);

        log.info("🐍скрипты отработали");

        entryDataService.updateCurrentPricesFromCandles(entryData, allCandles);
        log.info("Обогатили entry_data.json ценами из all_candles.json");

        try {
            chartService.sendChart(chatId, chartService.getChart(), "📊LONG " + topPair.getLongticker() + ", SHORT " + topPair.getShortticker(), true);
        } catch (Exception e) {
            log.error("❌ Ошибка при отправке чарта: {}", e.getMessage(), e);
        }

        long durationMillis = System.currentTimeMillis() - startTime;
        long minutes = durationMillis / 1000 / 60;
        long seconds = (durationMillis / 1000) % 60;
        log.info("Скан завершен. Обработано {} тикеров за {} мин {} сек", totalSymbols, minutes, seconds);
    }

    private void sendSignal(String chatId, String text) {
        System.out.println(text);
        sendText(chatId, text);
    }

    private void sendText(String chatId, String text) {
        eventSendService.sendAsText(SendAsTextEvent.builder()
                .chatId(chatId)
                .text(text)
                .enableMarkdown(true)
                .build());
    }
}
