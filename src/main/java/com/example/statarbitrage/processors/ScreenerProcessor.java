package com.example.statarbitrage.processors;

import com.example.statarbitrage.api.OkxClient;
import com.example.statarbitrage.events.SendAsPhotoEvent;
import com.example.statarbitrage.events.SendAsTextEvent;
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
import java.util.Map;
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
    ConcurrentHashMap<String, List<Double>> allCloses = new ConcurrentHashMap<>();

    public void testTrade(String chatId) {
        try {
            // 1. Загружаем z_score.json
            List<ZScoreEntry> zScores = JsonUtils.readZScoreJson("z_score.json");
            if (zScores == null || zScores.isEmpty()) {
                log.warn("⚠️ z_score.json пустой или не найден");
                return;
            }

            ZScoreEntry topPair = zScores.get(0); // Берем первую (лучшую) пару

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

            allCloses.clear();
            allCloses.put(topPair.getLongticker(), longTickerCloses);
            allCloses.put(topPair.getShortticker(), shortTickerCloses);

            saveAllClosesToJson();

            enrichZScoreWithPricesFromCloses();

            zScores = JsonUtils.readZScoreJson("z_score.json");
            if (zScores == null || zScores.isEmpty()) {
                log.warn("⚠️ z_score.json пустой или не найден");
                return;
            }
            topPair = zScores.get(0);

            // 4. Устанавливаем точки входа, если они ещё не заданы
            if (topPair.getLongTickerEntryPrice() == 0.0 || topPair.getShortTickerEntryPrice() == 0.0) {
                topPair.setLongTickerEntryPrice(topPair.getLongTickerCurrentPrice());
                topPair.setShortTickerEntryPrice(topPair.getShortTickerCurrentPrice());
                topPair.setMeanEntry(topPair.getMean());
                topPair.setSpreadEntry(topPair.getSpread());
                JsonUtils.writeZScoreJson("z_score.json", zScores); //сохраняем сразу!
                String message = "🔹Установлены точки входа: LONG {{}} = {}, SHORT {{}} = {}, SPREAD = {}, MEAN = {}";
                log.info(message, topPair.getLongticker(), topPair.getLongTickerEntryPrice(), topPair.getShortticker(), topPair.getShortTickerEntryPrice(), topPair.getSpreadEntry(), topPair.getMeanEntry());
                sendText(chatId, message);
                return; //пока не надо считать прибыль
            }

            // 6. Запускаем Python-скрипты
            try {
                PythonScriptsExecuter.execute(PythonScripts.Z_SCORE.getName(), false);
                clearChartDir();
                PythonScriptsExecuter.execute(PythonScripts.CREATE_CHARTS.getName(), false);
            } catch (Exception e) {
                log.error("Ошибка при запуске Python: {}", e.getMessage(), e);
            }

            //опсле скриптов снова берем свежий файл
            zScores = JsonUtils.readZScoreJson("z_score.json");
            if (zScores == null || zScores.isEmpty()) {
                log.warn("⚠️ z_score.json пустой или не найден");
                return;
            }
            topPair = zScores.get(0);

            log.info("📊 LONG {{}}: Entry: {}, Current: {}", topPair.getLongticker(), topPair.getLongTickerEntryPrice(), topPair.getLongTickerCurrentPrice());
            log.info("📊 SHORT {{}}: Entry: {}, Current: {}", topPair.getShortticker(), topPair.getShortTickerEntryPrice(), topPair.getShortTickerCurrentPrice());

            double meanChangePercent = 100.0 * (topPair.getMean() - topPair.getMeanEntry()) / topPair.getMeanEntry();
            double spreadChangePercent = 100.0 * (topPair.getSpread() - topPair.getSpreadEntry()) / topPair.getSpreadEntry();

            double meanChangeAbs = topPair.getMean() - topPair.getMeanEntry();
            double spreadChangeAbs = topPair.getSpread() - topPair.getSpreadEntry();

            log.info("🔄 Изменение MEAN: {} (абсолютно), {}% (от начального)", String.format("%.5f", meanChangeAbs), String.format("%.2f", meanChangePercent));
            log.info("🔄 Изменение SPREAD: {} (абсолютно), {}% (от начального)", String.format("%.5f", spreadChangeAbs), String.format("%.2f", spreadChangePercent));


            // 7. Расчет прибыли
            double longReturn = (topPair.getLongTickerCurrentPrice() - topPair.getLongTickerEntryPrice()) / topPair.getLongTickerEntryPrice();
            double shortReturn = (topPair.getShortTickerEntryPrice() - topPair.getShortTickerCurrentPrice()) / topPair.getShortTickerEntryPrice();
            double profitPercent = longReturn + shortReturn;
            topPair.setProfit(String.format("%.2f%%", profitPercent * 100));
            log.info("💰Прибыль рассчитана: {}", topPair.getProfit());

            // 8. Обновляем z_score.json
            JsonUtils.writeZScoreJson("z_score.json", Collections.singletonList(topPair));

            sendText(chatId, "📊Профит " + topPair.getProfit());

            // 9. Отправляем график
            File chartDir = new File("charts");
            File[] chartFiles = chartDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));

            if (chartFiles != null && chartFiles.length > 0) {
                File chart = chartFiles[0];
                try {
                    sendChart(chatId, chart, "📊Профит " + topPair.getProfit());
                } catch (Exception e) {
                    log.error("❌ Ошибка при отправке чарта: {}", e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            log.error("❌ Ошибка в testTrade: {}", e.getMessage(), e);
        }
    }

    private void saveAllClosesToJson() {
        String jsonFilePath = "all_closes.json";
        try {
            mapper.writeValue(new File(jsonFilePath), allCloses);
        } catch (IOException e) {
            log.error("Ошибка при сохранении all_closes.json: {}", e.getMessage(), e);
        }
    }

    public void sendBestChart(String chatId) {
        long startTime = System.currentTimeMillis();
        Settings settings = settingsService.getSettings(Long.parseLong(chatId));

        Set<String> swapTickers = okxClient.getSwapTickers();
        int totalSymbols = swapTickers.size();

        ExecutorService executor = Executors.newFixedThreadPool(5);

        allCloses.clear();
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
        List.of("USDC-USDT-SWAP").forEach(symbol -> allCloses.remove(symbol));

        log.info("Собрали цены для {} монет", allCloses.size());

        //Сохраняем allCloses в JSON-файл
        saveAllClosesToJson();
        log.info("Сохранили цены в all_closes.json");

        try {
            log.info("▶️ Исполняем Python скрипт: " + PythonScripts.Z_SCORE.getName());
            PythonScriptsExecuter.execute(PythonScripts.Z_SCORE.getName(), true);

            // Обогащаем данные по парам
            enrichZScoreWithPricesFromCloses();
            log.info("Обогатили z_score.json ценами из all_closes.json");

            keepBestPairByZscoreAndPvalue();
            log.info("🔍 Сохранили лучшую пару в z_score.json");

            clearChartDir();
            log.info("Очистили папку с чартами");

            log.info("▶️ Исполняем Python скрипт: " + PythonScripts.CREATE_CHARTS.getName());
            PythonScriptsExecuter.execute(PythonScripts.CREATE_CHARTS.getName(), true);

            List<ZScoreEntry> zScores = JsonUtils.readZScoreJson("z_score.json");
            if (zScores == null || zScores.isEmpty()) {
                log.warn("⚠️ z_score.json пустой или не найден");
                return;
            }
            ZScoreEntry topPair = zScores.get(0); // Берем первую (лучшую) пару

            File chartDir = new File("charts");
            File[] chartFiles = chartDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));

            if (chartFiles != null && chartFiles.length > 0) {
                File chart = chartFiles[0];
                try {
                    sendChart(chatId, chart, "📊LONG " + topPair.getLongticker() + ", SHORT " + topPair.getShortticker());
                } catch (Exception e) {
                    log.error("❌ Ошибка при отправке чарта: {}", e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            log.error("Ошибка при запуске Python: {}", e.getMessage(), e);
        }

        log.info("✅ Python-скрипты исполнены");

        long durationMillis = System.currentTimeMillis() - startTime;
        long minutes = durationMillis / 1000 / 60;
        long seconds = (durationMillis / 1000) % 60;
        log.info("Скан завершен. Обработано {} тикеров за {} мин {} сек", totalSymbols, minutes, seconds);
    }

    private void enrichZScoreWithPricesFromCloses() {
        String zScorePath = "z_score.json";
        String allClosesPath = "all_closes.json";

        try {
            File zFile = new File(zScorePath);
            File closesFile = new File(allClosesPath);

            if (!zFile.exists() || !closesFile.exists()) {
                log.warn("Файлы z_score.json или all_closes.json не найдены.");
                return;
            }

            // Считаем пары из z_score.json
            List<ZScoreEntry> allEntries = List.of(mapper.readValue(zFile, ZScoreEntry[].class));

            // Считаем все закрытия из all_closes.json (Map<String, List<Double>>)
            Map<String, List<Double>> allCloses = mapper.readValue(closesFile,
                    mapper.getTypeFactory().constructMapType(
                            ConcurrentHashMap.class, String.class, List.class));

            for (ZScoreEntry entry : allEntries) {
                String longTicker = entry.getLongticker();
                String shortTicker = entry.getShortticker();

                List<Double> longTickerCloses = allCloses.get(longTicker);
                List<Double> shortTickerCloses = allCloses.get(shortTicker);

                if (longTickerCloses == null || longTickerCloses.isEmpty() || shortTickerCloses == null || shortTickerCloses.isEmpty()) {
                    log.warn("Нет данных по ценам для пары: {} - {}", longTicker, shortTicker);
                    continue;
                }

                double longTickerCurrentPrice = longTickerCloses.get(longTickerCloses.size() - 1);  // последняя цена
                double shortTickerCurrentPrice = shortTickerCloses.get(shortTickerCloses.size() - 1);

                entry.setLongTickerCurrentPrice(longTickerCurrentPrice);
                entry.setShortTickerCurrentPrice(shortTickerCurrentPrice);
            }

            JsonUtils.writeZScoreJson("z_score.json", allEntries);
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
}
