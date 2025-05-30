package com.example.statarbitrage.processors;

import com.example.statarbitrage.api.OkxClient;
import com.example.statarbitrage.events.SendAsPhotoEvent;
import com.example.statarbitrage.events.SendAsTextEvent;
import com.example.statarbitrage.model.*;
import com.example.statarbitrage.python.PythonScripts;
import com.example.statarbitrage.python.PythonScriptsExecuter;
import com.example.statarbitrage.services.EventSendService;
import com.example.statarbitrage.services.FileService;
import com.example.statarbitrage.services.ProfitService;
import com.example.statarbitrage.services.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    private final Map<String, AtomicBoolean> runningTrades = new ConcurrentHashMap<>();

    @Async
    public void testTrade(String chatId) {
        AtomicBoolean isRunning = runningTrades.computeIfAbsent(chatId, k -> new AtomicBoolean(false));
        if (!isRunning.compareAndSet(false, true)) {
            log.warn("testTrade уже выполняется для chatId = {}", chatId);
            return;
        }

        try {
            ZScoreEntry topPair = fileService.getTopPairEntry();
            EntryData entryData = fileService.getEntryData();
            Settings settings = settingsService.getSettings(Long.parseLong(chatId));

            ConcurrentHashMap<String, List<Candle>> topPairCandles = saveCandlesToJson(topPair, settings);
            updateCurrentPricesFromCandles(entryData, topPairCandles);
            setupEntryPointsIfNeededFromCandles(entryData, topPair, topPairCandles);
            ProfitData profitData = profitService.calculateAndSetProfit(entryData, settings.getCapitalLong(), settings.getCapitalShort(), settings.getLeverage(), settings.getFeePctPerTrade());

            PythonScriptsExecuter.execute(PythonScripts.Z_SCORE_CANDLES.getName(), true);
            fileService.clearChartDir();
            PythonScriptsExecuter.execute(PythonScripts.CREATE_CHARTS_CANDLES.getName(), true);

            sendChart(chatId, fileService.getChart(), profitData.getLogMessage(), false);
        } catch (Exception e) {
            log.error("❌ Ошибка в testTrade: {}", e.getMessage(), e);
        } finally {
            isRunning.set(false);
        }
    }

    @Async
    public void sendBestChart(String chatId) {
        long startTime = System.currentTimeMillis();

        fileService.deleteSpecificFilesInProjectRoot(List.of("z_score.json", "entry_data.json", "all_candles.json"));
        log.info("Удалили z_score.json, entry_data.json, all_candles.json");

        Settings settings = settingsService.getSettings(Long.parseLong(chatId));

        Set<String> swapTickers = okxClient.getSwapTickers();
        int totalSymbols = swapTickers.size();

        ConcurrentHashMap<String, List<Candle>> allCandles = getCandlesMap(swapTickers, settings);
        log.info("Собрали цены для {} монет", allCandles.size());

        List.of("USDC-USDT-SWAP").forEach(allCandles::remove);
        log.info("Удалили цены тикеров из черного списка");

        fileService.writeAllCandlesToJson(allCandles);
        log.info("Сохранили цены в all_candles.json");

        log.info("🐍Запускаем скрипты...");

        PythonScriptsExecuter.execute(PythonScripts.Z_SCORE_CANDLES.getName(), true);
        log.info("Исполнили скрипт " + PythonScripts.Z_SCORE_CANDLES.getName());

        fileService.keepPairWithMaxZScore();
        log.info("🔍 Сохранили лучшую пару в z_score.json");

        fileService.clearChartDir();
        log.info("Очистили папку с чартами");

        ZScoreEntry topPair = fileService.getTopPairEntry();

        EntryData entryData = createEntryData(topPair);
        log.info("Создали entry_data.json");

        PythonScriptsExecuter.execute(PythonScripts.CREATE_CHARTS_CANDLES.getName(), true);
        log.info("Исполнили скрипт " + PythonScripts.CREATE_CHARTS.getName());

        log.info("🐍скрипты отработали");

        updateCurrentPricesFromCandles(entryData, allCandles);
        log.info("Обогатили entry_data.json ценами из all_candles.json");

        //Отправляем график
        try {
            sendChart(chatId, fileService.getChart(), "📊LONG " + topPair.getLongticker() + ", SHORT " + topPair.getShortticker(), true);
        } catch (Exception e) {
            log.error("❌ Ошибка при отправке чарта: {}", e.getMessage(), e);
        }

        long durationMillis = System.currentTimeMillis() - startTime;
        long minutes = durationMillis / 1000 / 60;
        long seconds = (durationMillis / 1000) % 60;
        log.info("Скан завершен. Обработано {} тикеров за {} мин {} сек", totalSymbols, minutes, seconds);
    }

    private void getCloses(Set<String> swapTickers, Settings settings) {
        ExecutorService executor = Executors.newFixedThreadPool(5);
        ConcurrentHashMap<String, List<Double>> allCloses = new ConcurrentHashMap<>();
        try {
            List<CompletableFuture<Void>> futures = swapTickers.stream()
                    .map(symbol -> CompletableFuture.runAsync(() -> {
                        try {
                            List<Double> closes = okxClient.getCloses(symbol, settings.getTimeframe(), settings.getCandleLimit());
                            allCloses.put(symbol, closes);
                        } catch (Exception e) {
                            log.error("Ошибка при обработке {}: {}", symbol, e.getMessage(), e);
                        }
                    }, executor))
                    .toList();

            // Ожидаем завершения всех задач
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            executor.shutdown();
        }
    }

    @NotNull
    private ConcurrentHashMap<String, List<Candle>> getCandlesMap(Set<String> swapTickers, Settings settings) {
        ExecutorService executor = Executors.newFixedThreadPool(5);
        ConcurrentHashMap<String, List<Candle>> allCandles = new ConcurrentHashMap<>();
        try {
            List<CompletableFuture<Void>> futures = swapTickers.stream()
                    .map(symbol -> CompletableFuture.runAsync(() -> {
                        try {
                            List<Candle> candles = okxClient.getCandleList(symbol, settings.getTimeframe(), settings.getCandleLimit());
                            allCandles.put(symbol, candles);
                        } catch (Exception e) {
                            log.error("Ошибка при обработке {}: {}", symbol, e.getMessage(), e);
                        }
                    }, executor))
                    .toList();

            // Ожидаем завершения всех задач
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            executor.shutdown();
        }
        return allCandles;
    }

    private void setupEntryPointsIfNeeded(EntryData entryData, ZScoreEntry topPair, ConcurrentHashMap<String, List<Double>> topPairCloses) {
        if (entryData.getLongTickerEntryPrice() == 0.0 || entryData.getShortTickerEntryPrice() == 0.0) {
            entryData.setLongticker(topPair.getLongticker());
            entryData.setShortticker(topPair.getShortticker());

            List<Double> longTickerCloses = topPairCloses.get(topPair.getLongticker());
            List<Double> shortTickerCloses = topPairCloses.get(topPair.getShortticker());

            entryData.setLongTickerEntryPrice(longTickerCloses.get(longTickerCloses.size() - 1));
            entryData.setShortTickerEntryPrice(shortTickerCloses.get(shortTickerCloses.size() - 1));
            entryData.setMeanEntry(topPair.getMean());
            entryData.setSpreadEntry(topPair.getSpread());
            fileService.writeEntryDataToJson(Collections.singletonList(entryData));
            log.info("🔹Установлены точки входа: LONG {{}} = {}, SHORT {{}} = {}, SPREAD = {}, MEAN = {}", entryData.getLongticker(), entryData.getLongTickerEntryPrice(), entryData.getShortticker(), entryData.getShortTickerEntryPrice(), entryData.getSpreadEntry(), entryData.getMeanEntry());
        }
    }

    private void setupEntryPointsIfNeededFromCandles(EntryData entryData, ZScoreEntry topPair, ConcurrentHashMap<String, List<Candle>> topPairCandles) {
        if (entryData.getLongTickerEntryPrice() == 0.0 || entryData.getShortTickerEntryPrice() == 0.0) {
            entryData.setLongticker(topPair.getLongticker());
            entryData.setShortticker(topPair.getShortticker());

            List<Candle> longTickerCandles = topPairCandles.get(topPair.getLongticker());
            List<Candle> shortTickerCandles = topPairCandles.get(topPair.getShortticker());

            if (longTickerCandles == null || longTickerCandles.isEmpty() ||
                    shortTickerCandles == null || shortTickerCandles.isEmpty()) {
                log.warn("Нет данных по свечам для установки точек входа: {} - {}", topPair.getLongticker(), topPair.getShortticker());
                return;
            }

            Candle longCandle = longTickerCandles.get(longTickerCandles.size() - 1);
            Candle shortCandle = shortTickerCandles.get(shortTickerCandles.size() - 1);

            double longEntryPrice = longCandle.getClose();
            double shortEntryPrice = shortCandle.getClose();

            entryData.setLongTickerEntryPrice(longEntryPrice);
            entryData.setShortTickerEntryPrice(shortEntryPrice);
            entryData.setMeanEntry(topPair.getMean());
            entryData.setSpreadEntry(topPair.getSpread());

            // Ставим время открытия по long-свечке (можно и усреднить, если нужно)
            entryData.setEntryTime(longCandle.getTimestamp());

            fileService.writeEntryDataToJson(Collections.singletonList(entryData));

            log.info("🔹Установлены точки входа: LONG {{}} = {}, SHORT {{}} = {}, SPREAD = {}, MEAN = {}, ВРЕМЯ = {}",
                    entryData.getLongticker(), longEntryPrice,
                    entryData.getShortticker(), shortEntryPrice,
                    entryData.getSpreadEntry(), entryData.getMeanEntry(),
                    entryData.getEntryTime());
        }
    }


    private ConcurrentHashMap<String, List<Double>> getTopPairCloses(ZScoreEntry topPair, Settings settings) {
        List<Double> longTickerCloses = okxClient.getCloses(topPair.getLongticker(), settings.getTimeframe(), settings.getCandleLimit());
        List<Double> shortTickerCloses = okxClient.getCloses(topPair.getShortticker(), settings.getTimeframe(), settings.getCandleLimit());
        if (longTickerCloses.isEmpty() || shortTickerCloses.isEmpty()) {
            log.warn("⚠️ Не удалось получить цены для пары: {} и {}", topPair.getLongticker(), topPair.getShortticker());
            return null;
        }
        ConcurrentHashMap<String, List<Double>> topPairCloses = new ConcurrentHashMap<>();
        topPairCloses.put(topPair.getLongticker(), longTickerCloses);
        topPairCloses.put(topPair.getShortticker(), shortTickerCloses);
        fileService.writeAllClosesToJson(topPairCloses);
        return topPairCloses;
    }

    private ConcurrentHashMap<String, List<Candle>> saveCandlesToJson(ZScoreEntry topPair, Settings settings) {
        List<Candle> longTickerCandles = okxClient.getCandleList(
                topPair.getLongticker(),
                settings.getTimeframe(),
                settings.getCandleLimit()
        )
//                .stream()
//                .skip(Math.max(0, settings.getCandleLimit() - 2))
//                .collect(Collectors.toList())
                ;

        List<Candle> shortTickerCandles = okxClient.getCandleList(
                topPair.getShortticker(),
                settings.getTimeframe(),
                settings.getCandleLimit()
        )
//                .stream()
//                .skip(Math.max(0, settings.getCandleLimit() - 2))
//                .collect(Collectors.toList())
                ;

        ConcurrentHashMap<String, List<Candle>> allCandles = new ConcurrentHashMap<>();
        allCandles.put(topPair.getLongticker(), longTickerCandles);
        allCandles.put(topPair.getShortticker(), shortTickerCandles);

        fileService.writeAllCandlesToJson(allCandles); // этот метод должен принимать Map<String, List<Candle>>
        return allCandles;
    }


    public EntryData createEntryData(ZScoreEntry topPair) {
        EntryData entryData = new EntryData();
        entryData.setLongticker(topPair.getLongticker());
        entryData.setShortticker(topPair.getShortticker());
        fileService.writeEntryDataToJson(Collections.singletonList(entryData));
        return entryData;
    }

    private void updateCurrentPrices(EntryData entryData, ConcurrentHashMap<String, List<Double>> allCloses) {
        try {
            String longTicker = entryData.getLongticker();
            String shortTicker = entryData.getShortticker();

            List<Double> longTickerCloses = allCloses.get(longTicker);
            List<Double> shortTickerCloses = allCloses.get(shortTicker);

            if (longTickerCloses == null || longTickerCloses.isEmpty() || shortTickerCloses == null || shortTickerCloses.isEmpty()) {
                log.warn("Нет данных по ценам для пары: {} - {}", longTicker, shortTicker);
            }

            entryData.setLongTickerCurrentPrice(longTickerCloses.get(longTickerCloses.size() - 1));
            entryData.setShortTickerCurrentPrice(shortTickerCloses.get(shortTickerCloses.size() - 1));

            fileService.writeEntryDataToJson(Collections.singletonList(entryData));
        } catch (Exception e) {
            log.error("Ошибка при обогащении z_score.json из all_closes.json: {}", e.getMessage(), e);
        }
    }

    private void updateCurrentPricesFromCandles(EntryData entryData, ConcurrentHashMap<String, List<Candle>> allCandles) {
        try {
            String longTicker = entryData.getLongticker();
            String shortTicker = entryData.getShortticker();

            List<Candle> longTickerCandles = allCandles.get(longTicker);
            List<Candle> shortTickerCandles = allCandles.get(shortTicker);

            if (longTickerCandles == null || longTickerCandles.isEmpty() ||
                    shortTickerCandles == null || shortTickerCandles.isEmpty()) {
                log.warn("Нет данных по свечам для пары: {} - {}", longTicker, shortTicker);
                return;
            }

            double longPrice = longTickerCandles.get(longTickerCandles.size() - 1).getClose();
            double shortPrice = shortTickerCandles.get(shortTickerCandles.size() - 1).getClose();

            entryData.setLongTickerCurrentPrice(longPrice);
            entryData.setShortTickerCurrentPrice(shortPrice);

            fileService.writeEntryDataToJson(Collections.singletonList(entryData));
        } catch (Exception e) {
            log.error("Ошибка при обогащении z_score.json из свечей: {}", e.getMessage(), e);
        }
    }

    private void sendChart(String chatId, File chartFile, String caption, boolean withLogging) {
        try {
            eventSendService.sendAsPhoto(SendAsPhotoEvent.builder()
                    .chatId(chatId)
                    .photo(chartFile)
                    .caption(caption)
                    .build());
            if (withLogging) {
                log.info("📤 Чарт отправлен в Telegram: {}", chartFile.getName());
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при отправке чарта: {}", e.getMessage(), e);
        }
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
