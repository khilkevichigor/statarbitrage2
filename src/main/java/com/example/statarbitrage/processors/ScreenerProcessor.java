package com.example.statarbitrage.processors;

import com.example.statarbitrage.api.OkxClient;
import com.example.statarbitrage.events.SendAsPhotoEvent;
import com.example.statarbitrage.events.SendAsTextEvent;
import com.example.statarbitrage.model.EntryData;
import com.example.statarbitrage.model.ProfitData;
import com.example.statarbitrage.model.Settings;
import com.example.statarbitrage.model.ZScoreEntry;
import com.example.statarbitrage.python.PythonScripts;
import com.example.statarbitrage.python.PythonScriptsExecuter;
import com.example.statarbitrage.services.EventSendService;
import com.example.statarbitrage.services.FileService;
import com.example.statarbitrage.services.ProfitService;
import com.example.statarbitrage.services.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
            if (topPair == null) {
                log.warn("⚠️ topPair не найден");
                return;
            }
            EntryData entryData = fileService.getEntryData();
            if (entryData == null) {
                log.warn("⚠️ entryData не найден");
                return;
            }

            // 2. Получаем настройки пользователя
            Settings settings = settingsService.getSettings(Long.parseLong(chatId));
            if (settings == null) {
                log.warn("⚠️ Настройки пользователя не найдены для chatId {}", chatId);
                return;
            }

            // 3. Получаем текущие цены закрытия
            List<Double> longTickerCloses = okxClient.getCloses(topPair.getLongticker(), settings.getTimeframe(), settings.getCandleLimit());
            List<Double> shortTickerCloses = okxClient.getCloses(topPair.getShortticker(), settings.getTimeframe(), settings.getCandleLimit());
            if (longTickerCloses.isEmpty() || shortTickerCloses.isEmpty()) {
                log.warn("⚠️ Не удалось получить цены для пары: {} и {}", topPair.getLongticker(), topPair.getShortticker());
                return;
            }

            ConcurrentHashMap<String, List<Double>> topPairCloses = new ConcurrentHashMap<>();
            topPairCloses.put(topPair.getLongticker(), longTickerCloses);
            topPairCloses.put(topPair.getShortticker(), shortTickerCloses);

            fileService.writeAllClosesToJson(topPairCloses);

            updateCurrentPrices(entryData, topPairCloses);

            entryData = fileService.getEntryData(); //todo возможно можно не делать тк по ссылке изменили

            // 4. Устанавливаем точки входа, если они ещё не заданы
            if (entryData.getLongTickerEntryPrice() == 0.0 || entryData.getShortTickerEntryPrice() == 0.0) {
                entryData.setLongticker(topPair.getLongticker());
                entryData.setShortticker(topPair.getShortticker());
                entryData.setLongTickerEntryPrice(longTickerCloses.get(longTickerCloses.size() - 1));
                entryData.setShortTickerEntryPrice(shortTickerCloses.get(shortTickerCloses.size() - 1));
                entryData.setMeanEntry(topPair.getMean());
                entryData.setSpreadEntry(topPair.getSpread());

                fileService.writeEntryDataToJson(Collections.singletonList(entryData)); //сохраняем сразу!

                log.info("🔹Установлены точки входа: LONG {{}} = {}, SHORT {{}} = {}, SPREAD = {}, MEAN = {}", entryData.getLongticker(), entryData.getLongTickerEntryPrice(), entryData.getShortticker(), entryData.getShortTickerEntryPrice(), entryData.getSpreadEntry(), entryData.getMeanEntry());
                return; //пока не надо считать прибыль
            }

            log.info("🐍Запускаем скрипты...");

            // 6. Запускаем Python-скрипты
            PythonScriptsExecuter.execute(PythonScripts.Z_SCORE.getName(), false);
            log.info("Исполнили " + PythonScripts.Z_SCORE.getName());

            fileService.clearChartDir();
            log.info("Очистили папку с чартами");

            PythonScriptsExecuter.execute(PythonScripts.CREATE_CHARTS.getName(), false);
            log.info("Исполнили " + PythonScripts.CREATE_CHARTS.getName());

            log.info("🐍скрипты отработали");

            //после скриптов снова берем свежий файл
            topPair = fileService.getTopPairEntry();

            double meanChangeAbs = topPair.getMean() - entryData.getMeanEntry();
            double spreadChangeAbs = topPair.getSpread() - entryData.getSpreadEntry();

            double meanChangePercent = 100.0 * Math.abs(meanChangeAbs) / Math.abs(entryData.getMeanEntry());
            if (meanChangeAbs < 0) meanChangePercent = -meanChangePercent;

            double spreadChangePercent = 100.0 * Math.abs(spreadChangeAbs) / Math.abs(entryData.getSpreadEntry());
            if (spreadChangeAbs < 0) spreadChangePercent = -spreadChangePercent;

            log.info("🔄 Изменение MEAN: {} (абсолютно), {}% (от начального)",
                    String.format("%+.5f", meanChangeAbs),
                    String.format("%+.2f", meanChangePercent));

            log.info("🔄 Изменение SPREAD: {} (абсолютно), {}% (от начального)",
                    String.format("%+.5f", spreadChangeAbs),
                    String.format("%+.2f", spreadChangePercent));

            // 7. Расчет прибыли
            ProfitData profitData = profitService.calculateAndSetProfit(entryData, settings.getCapitalLong(), settings.getCapitalShort(), settings.getLeverage(), settings.getFeePctPerTrade());

            // 9. Отправляем график
            try {
                sendChart(chatId, fileService.getChart(), profitData.getLogMessage());
            } catch (Exception e) {
                log.error("❌ Ошибка при отправке чарта: {}", e.getMessage(), e);
            }
        } catch (Exception e) {
            log.error("❌ Ошибка в testTrade: {}", e.getMessage(), e);
        } finally {
            isRunning.set(false);
        }
    }

    @Async
    public void sendBestChart(String chatId) {
        long startTime = System.currentTimeMillis();

        fileService.deleteSpecificFilesInProjectRoot(List.of("z_score.json", "entry_data.json", "all_closes.json"));
        log.info("Удалили z_score.json, entry_data.json, all_closes.json");

        Settings settings = settingsService.getSettings(Long.parseLong(chatId));

        Set<String> swapTickers = okxClient.getSwapTickers();
        int totalSymbols = swapTickers.size();

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
        log.info("Собрали цены для {} монет", allCloses.size());

        List.of("USDC-USDT-SWAP").forEach(allCloses::remove);
        log.info("Удалили цены тикеров из черного списка");

        //Сохраняем allCloses в JSON-файл
        fileService.writeAllClosesToJson(allCloses);
        log.info("Сохранили цены в all_closes.json");

        log.info("🐍Запускаем скрипты...");

        PythonScriptsExecuter.execute(PythonScripts.Z_SCORE.getName(), true);
        log.info("Исполнили " + PythonScripts.Z_SCORE.getName());

        fileService.keepBestPairByZscoreAndPvalue();
        log.info("🔍 Сохранили лучшую пару в z_score.json");

        fileService.clearChartDir();
        log.info("Очистили папку с чартами");

        PythonScriptsExecuter.execute(PythonScripts.CREATE_CHARTS.getName(), true);
        log.info("Исполнили " + PythonScripts.CREATE_CHARTS.getName());

        log.info("🐍скрипты отработали");

        ZScoreEntry topPair = fileService.getTopPairEntry(); // Берем первую (лучшую) пару

        EntryData entryData = fileService.createEntryData(topPair);//создаем на этапе поиска
        log.info("Создали entry_data.json");

        updateCurrentPrices(entryData, allCloses);
        log.info("Обогатили entry_data.json ценами из all_closes.json");

        File chartDir = new File("charts");
        File[] chartFiles = chartDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));

        if (chartFiles != null && chartFiles.length > 0) {
            File chart = chartFiles[0];
            try {
                sendChart(chatId, chart, "📊LONG " + topPair.getLongticker() + ", SHORT " + topPair.getShortticker());
                log.info("Отправили чарт в телеграм");
            } catch (Exception e) {
                log.error("❌ Ошибка при отправке чарта: {}", e.getMessage(), e);
            }
        }

        long durationMillis = System.currentTimeMillis() - startTime;
        long minutes = durationMillis / 1000 / 60;
        long seconds = (durationMillis / 1000) % 60;
        log.info("Скан завершен. Обработано {} тикеров за {} мин {} сек", totalSymbols, minutes, seconds);
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

    private void sendChart(String chatId, File chartFile, String caption) {
        try {
            eventSendService.sendAsPhoto(SendAsPhotoEvent.builder()
                    .chatId(chatId)
                    .photo(chartFile)
                    .caption(caption)
                    .build());
            log.info("📤 Чарт отправлен в Telegram: {}", chartFile.getName());
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
