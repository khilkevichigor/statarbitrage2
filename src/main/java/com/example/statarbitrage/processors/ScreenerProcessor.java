package com.example.statarbitrage.processors;

import com.example.statarbitrage.api.OkxClient;
import com.example.statarbitrage.events.SendAsPhotoEvent;
import com.example.statarbitrage.events.SendAsTextEvent;
import com.example.statarbitrage.model.EntryData;
import com.example.statarbitrage.model.Settings;
import com.example.statarbitrage.model.ZScoreEntry;
import com.example.statarbitrage.python.PythonScripts;
import com.example.statarbitrage.python.PythonScriptsExecuter;
import com.example.statarbitrage.services.EventSendService;
import com.example.statarbitrage.services.SettingsService;
import com.example.statarbitrage.utils.JsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScreenerProcessor {

    private final OkxClient okxClient;
    private final EventSendService eventSendService;
    private final SettingsService settingsService;

    // ObjectMapper создаём один раз, он потокобезопасен
    private final ObjectMapper mapper = new ObjectMapper();

    public void testTrade(String chatId) {
        try {
            ZScoreEntry topPair = getzScoreEntry();
            EntryData entryData = getEntryData();

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

            saveAllClosesToJson(topPairCloses);

            updateCurrentPrices(entryData, topPairCloses);

            entryData = getEntryData();

            // 4. Устанавливаем точки входа, если они ещё не заданы
            if (entryData.getLongTickerEntryPrice() == 0.0 || entryData.getShortTickerEntryPrice() == 0.0) {
                entryData.setLongticker(topPair.getLongticker());
                entryData.setShortticker(topPair.getShortticker());
                entryData.setLongTickerEntryPrice(longTickerCloses.get(longTickerCloses.size() - 1));
                entryData.setShortTickerEntryPrice(shortTickerCloses.get(shortTickerCloses.size() - 1));
                entryData.setMeanEntry(topPair.getMean());
                entryData.setSpreadEntry(topPair.getSpread());

                JsonUtils.writeEntryDataJson("entry_data.json", Collections.singletonList(entryData)); //сохраняем сразу!

                log.info("🔹Установлены точки входа: LONG {{}} = {}, SHORT {{}} = {}, SPREAD = {}, MEAN = {}", entryData.getLongticker(), entryData.getLongTickerEntryPrice(), entryData.getShortticker(), entryData.getShortTickerEntryPrice(), entryData.getSpreadEntry(), entryData.getMeanEntry());
                return; //пока не надо считать прибыль
            }

            log.info("🐍Запускаем скрипты...");

            // 6. Запускаем Python-скрипты
            PythonScriptsExecuter.execute(PythonScripts.Z_SCORE.getName(), false);
            log.info("Исполнили " + PythonScripts.Z_SCORE.getName());

            clearChartDir();
            log.info("Очистили папку с чартами");

            PythonScriptsExecuter.execute(PythonScripts.CREATE_CHARTS.getName(), false);
            log.info("Исполнили " + PythonScripts.CREATE_CHARTS.getName());

            log.info("🐍скрипты отработали");

            //после скриптов снова берем свежий файл
            topPair = getzScoreEntry();

            log.info("📊 LONG {{}}: Entry: {}, Current: {}", entryData.getLongticker(), entryData.getLongTickerEntryPrice(), entryData.getLongTickerCurrentPrice());
            log.info("📊 SHORT {{}}: Entry: {}, Current: {}", entryData.getShortticker(), entryData.getShortTickerEntryPrice(), entryData.getShortTickerCurrentPrice());

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
            double longReturn = (entryData.getLongTickerCurrentPrice() - entryData.getLongTickerEntryPrice()) / entryData.getLongTickerEntryPrice();
            double shortReturn = (entryData.getShortTickerEntryPrice() - entryData.getShortTickerCurrentPrice()) / entryData.getShortTickerEntryPrice();
            double profitPercent = longReturn + shortReturn;
            entryData.setProfit(String.format("%.2f%%", profitPercent * 100));
            log.info("💰Прибыль рассчитана: {}", entryData.getProfit());

            // 8. Обновляем entry_data.json
            JsonUtils.writeEntryDataJson("entry_data.json", Collections.singletonList(entryData));

            sendText(chatId, "📊Профит " + entryData.getProfit());

            // 9. Отправляем график
            File chartDir = new File("charts");
            File[] chartFiles = chartDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));

            if (chartFiles != null && chartFiles.length > 0) {
                File chart = chartFiles[0];
                try {
                    sendChart(chatId, chart, "📊Профит " + entryData.getProfit());
                } catch (Exception e) {
                    log.error("❌ Ошибка при отправке чарта: {}", e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            log.error("❌ Ошибка в testTrade: {}", e.getMessage(), e);
        }
    }

    public void sendBestChart(String chatId) {
        long startTime = System.currentTimeMillis();

        deleteSpecificFilesInProjectRoot(List.of("z_score.json", "entry_data.json", "all_closes.json"));
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
        saveAllClosesToJson(allCloses);
        log.info("Сохранили цены в all_closes.json");

        log.info("🐍Запускаем скрипты...");

        PythonScriptsExecuter.execute(PythonScripts.Z_SCORE.getName(), true);
        log.info("Исполнили " + PythonScripts.Z_SCORE.getName());

        keepBestPairByZscoreAndPvalue();
        log.info("🔍 Сохранили лучшую пару в z_score.json");

        clearChartDir();
        log.info("Очистили папку с чартами");

        PythonScriptsExecuter.execute(PythonScripts.CREATE_CHARTS.getName(), true);
        log.info("Исполнили " + PythonScripts.CREATE_CHARTS.getName());

        log.info("🐍скрипты отработали");

        ZScoreEntry topPair = getzScoreEntry(); // Берем первую (лучшую) пару

        EntryData entryData = createEntryData(topPair);//создаем на этапе поиска
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

    public void deleteSpecificFilesInProjectRoot(List<String> fileNames) {
        File projectRoot = new File(".");
        for (String fileName : fileNames) {
            File file = new File(projectRoot, fileName);
            if (file.exists()) {
                boolean deleted = file.delete();
                if (deleted) {
                    log.info("✅ Удалён файл: {}", file.getAbsolutePath());
                } else {
                    log.warn("⚠️ Не удалось удалить файл: {}", file.getAbsolutePath());
                }
            } else {
                log.info("ℹ️ Файл не найден: {}", file.getAbsolutePath());
            }
        }
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

            JsonUtils.writeEntryDataJson("entry_data.json", Collections.singletonList(entryData));
        } catch (Exception e) {
            log.error("Ошибка при обогащении z_score.json из all_closes.json: {}", e.getMessage(), e);
        }
    }

    private void clearChartDir() {
        // --- очищаем папку charts перед созданием новых графиков ---
        String chartsDir = "charts";
        clearDirectory(chartsDir);
    }

    private void clearDirectory(String dirPath) {
        File dir = new File(dirPath);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        clearDirectory(file.getAbsolutePath());
                    }
                    if (!file.delete()) {
                        log.warn("Не удалось удалить файл: {}", file.getAbsolutePath());
                    }
                }
            }
        }
    }

    private void keepBestPairByZscoreAndPvalue() {
        //Оставляем только одну лучшую пару по zscore/pvalue
        String zScorePath = "z_score.json";
        try {
            File zFile = new File(zScorePath);
            if (zFile.exists()) {
                List<ZScoreEntry> allEntries = List.of(mapper.readValue(zFile, ZScoreEntry[].class));

                //Фильтрация: минимальный pvalue и при равенстве — максимальный zscore
                ZScoreEntry best = allEntries.stream()
                        .min((e1, e2) -> {
                            int cmp = Double.compare(e1.getPvalue(), e2.getPvalue());
                            if (cmp == 0) {
                                //При равных pvalue берём с большим zscore
                                return -Double.compare(e1.getZscore(), e2.getZscore());
                            }
                            return cmp;
                        })
                        .orElse(null);

                if (best != null) {
                    mapper.writeValue(zFile, List.of(best));
                }
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при фильтрации z_score.json: {}", e.getMessage(), e);
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

    private static ZScoreEntry getzScoreEntry() {
        List<ZScoreEntry> zScores = JsonUtils.readZScoreJson("z_score.json");
        if (zScores == null || zScores.isEmpty()) {
            log.warn("⚠️ z_score.json пустой или не найден");
            throw new RuntimeException("⚠️ z_score.json пустой или не найден");
        }
        return zScores.get(0);
    }

    private static EntryData getEntryData() {
        List<EntryData> entryData = JsonUtils.readEntryDataJson("entry_data.json");
        if (entryData == null || entryData.isEmpty()) {
            String message = "⚠️entry_data.json пустой или не найден";
            log.warn(message);
            throw new RuntimeException(message);
        }
        return entryData.get(0);
    }

    private EntryData getOrCreateEntryData() {
        String jsonFilePath = "entry_data.json";
        List<EntryData> entryDataList = JsonUtils.readEntryDataJson(jsonFilePath);
        if (entryDataList == null || entryDataList.isEmpty()) {
            try {
                EntryData entryData = new EntryData();
                mapper.writeValue(new File(jsonFilePath), Collections.singletonList(entryData));
                return JsonUtils.readEntryDataJson(jsonFilePath).get(0);
            } catch (IOException e) {
                log.error("Ошибка при сохранении entry_data.json: {}", e.getMessage(), e);
            }
        }
        return entryDataList.get(0);
    }

    private EntryData createEntryData(ZScoreEntry topPair) {
        String jsonFilePath = "entry_data.json";
        try {
            EntryData entryData = new EntryData();
            entryData.setPvalue(topPair.getPvalue());
            entryData.setZscore(topPair.getZscore());
            entryData.setLongticker(topPair.getLongticker());
            entryData.setShortticker(topPair.getShortticker());
            entryData.setSpread(topPair.getSpread());
            entryData.setMean(topPair.getMean());
            mapper.writeValue(new File(jsonFilePath), Collections.singletonList(entryData));
            return JsonUtils.readEntryDataJson(jsonFilePath).get(0);
        } catch (IOException e) {
            String message = "Ошибка при сохранении entry_data.json: {}";
            log.error(message, e.getMessage(), e);
            throw new RuntimeException(message);
        }
    }

    private void saveAllClosesToJson(ConcurrentHashMap<String, List<Double>> topPairCloses) {
        String jsonFilePath = "all_closes.json";
        try {
            mapper.writeValue(new File(jsonFilePath), topPairCloses);
        } catch (IOException e) {
            log.error("Ошибка при сохранении all_closes.json: {}", e.getMessage(), e);
        }
    }
}
