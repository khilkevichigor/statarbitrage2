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
            List<Double> aCloses = okxClient.getCloses(topPair.getA(), settings.getTimeframe(), settings.getCandleLimit());
            List<Double> bCloses = okxClient.getCloses(topPair.getB(), settings.getTimeframe(), settings.getCandleLimit());
            if (aCloses.isEmpty() || bCloses.isEmpty()) {
                log.warn("⚠️ Не удалось получить цены для пары: {} и {}", topPair.getA(), topPair.getB());
                return;
            }

            allCloses.clear();
            allCloses.put(topPair.getA(), aCloses);
            allCloses.put(topPair.getB(), bCloses);

            double currentAPrice = aCloses.get(aCloses.size() - 1);
            double currentBPrice = bCloses.get(bCloses.size() - 1);

            // 4. Устанавливаем точки входа, если они ещё не заданы
            if (topPair.getAEntryPrice() == 0.0 || topPair.getBEntryPrice() == 0.0) {
                topPair.setAEntryPrice(currentAPrice);
                topPair.setBEntryPrice(currentBPrice);
                log.info("🔹 Установлены точки входа: A = {}, B = {}", currentAPrice, currentBPrice);
                JsonUtils.writeZScoreJson("z_score.json", zScores); // 💾 сохраняем сразу!
                return; // 👈 пока не надо считать прибыль
            }

            // 5. Сохраняем цены в all_closes.json
            saveAllClosesToJson("Ошибка при сохранении all_closes.json: {}");

            // 6. Запускаем Python-скрипты
            try {
                PythonScriptsExecuter.execute(PythonScripts.Z_SCORE.getName());
                clearChartDir();
                PythonScriptsExecuter.execute(PythonScripts.CREATE_CHARTS.getName());
            } catch (Exception e) {
                log.error("Ошибка при запуске Python: {}", e.getMessage(), e);
            }

            // 7. Расчет прибыли
            double aEntryPrice = topPair.getAEntryPrice();
            double bEntryPrice = topPair.getBEntryPrice();

            double profit;
            if (topPair.getLongTicker().equals(topPair.getA())) {
                double aReturn = (currentAPrice - aEntryPrice) / aEntryPrice;
                double bReturn = (bEntryPrice - currentBPrice) / bEntryPrice;
                profit = (aReturn + bReturn) * settings.getPositionSize();
            } else {
                double aReturn = (aEntryPrice - currentAPrice) / aEntryPrice;
                double bReturn = (currentBPrice - bEntryPrice) / bEntryPrice;
                profit = (aReturn + bReturn) * settings.getPositionSize();
            }

            topPair.setProfit(String.format("%.2f%%", profit * 100)); // "0.25%"
            log.info("💰 Прибыль рассчитана: {}", topPair.getProfit());

            // 8. Обновляем z_score.json
            JsonUtils.writeZScoreJson("z_score.json", zScores);

            // 9. Отправляем график
            File chartDir = new File("charts");
            File[] chartFiles = chartDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));

            if (chartFiles != null && chartFiles.length > 0) {
                File chart = chartFiles[0];
                sendChart(chatId, chart, topPair.getProfit());
            }

        } catch (Exception e) {
            log.error("❌ Ошибка в testTrade: {}", e.getMessage(), e);
        }
    }

    private void saveAllClosesToJson(String s) {
        //Сохраняем allCloses в JSON-файл
        String jsonFilePath = "all_closes.json";
        try {
            mapper.writeValue(new File(jsonFilePath), allCloses);
            log.info("Сохранили цены в all_closes.json");
        } catch (IOException e) {
            log.error(s, e.getMessage(), e);
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
        saveAllClosesToJson("Ошибка при сохранении closes.json: {}");

        try {
            PythonScriptsExecuter.execute(PythonScripts.Z_SCORE.getName());

            // Обогащаем данные по парам
            enrichZScoreWithPricesFromCloses();

//            keepBestByProfit();

            keepBestPairByZscoreAndPvalue();

            clearChartDir();

            PythonScriptsExecuter.execute(PythonScripts.CREATE_CHARTS.getName());

            File chartDir = new File("charts");
            File[] chartFiles = chartDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));

            if (chartFiles != null && chartFiles.length > 0) {
                File chart = chartFiles[0];
                try {
                    sendChart(chatId, chart, "📊 Лучшая пара по z-score/p-value");
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
                String a = entry.getA();
                String b = entry.getB();

                List<Double> closes1 = allCloses.get(a);
                List<Double> closes2 = allCloses.get(b);

                if (closes1 == null || closes1.isEmpty() || closes2 == null || closes2.isEmpty()) {
                    log.warn("Нет данных по ценам для пары: {} - {}", a, b);
                    continue;
                }

                double aPrice = closes1.get(closes1.size() - 1);  // последняя цена
                double bPrice = closes2.get(closes2.size() - 1);

                entry.setACurrentPrice(aPrice);
                entry.setBCurrentPrice(bPrice);
            }

            mapper.writeValue(zFile, allEntries);
            log.info("Обогатили z_score.json ценами из all_closes.json");

        } catch (Exception e) {
            log.error("Ошибка при обогащении z_score.json из all_closes.json: {}", e.getMessage(), e);
        }
    }

    // Метод для расчёта доходности возврата к среднему (mean reversion)
    private String calculateProfitForMeanReversion(double priceA, double priceB, ZScoreEntry zScoreEntry) {
        double spreadNow = priceB / priceA;  // отношение цен текущего спреда
        double v = (zScoreEntry.getMean() - zScoreEntry.getSpread()) / spreadNow;
        return String.format("%.2f%%", v * 100); // "0.25%"
    }

    private void clearChartDir() {
        // --- очищаем папку charts перед созданием новых графиков ---
        String chartsDir = "charts";
        clearDirectory(chartsDir);
        log.info("Очистили папку с чартами: {}", chartsDir);
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

    private void keepBestByProfit() {
        String zScorePath = "z_score.json";
        try {
            File zFile = new File(zScorePath);
            if (!zFile.exists()) {
                log.warn("Файл z_score.json не найден.");
                return;
            }

            List<ZScoreEntry> allEntries = List.of(mapper.readValue(zFile, ZScoreEntry[].class));

            ZScoreEntry best = allEntries.stream()
                    .filter(e -> {
                        String p = e.getProfit();
                        return p != null && p.endsWith("%");
                    })
                    .max((e1, e2) -> {
                        double profit1 = parseProfitPercent(e1.getProfit());
                        double profit2 = parseProfitPercent(e2.getProfit());
                        return Double.compare(profit1, profit2);
                    })
                    .orElse(null);

            if (best != null) {
                mapper.writeValue(zFile, List.of(best));
                log.info("🔝 Сохранили лучшую по доходности пару в z_score.json: {}", best);
            }

        } catch (Exception e) {
            log.error("❌ Ошибка при фильтрации по доходности: {}", e.getMessage(), e);
        }
    }

    // 🔧 Вспомогательный метод для преобразования "0.35%" → 0.35
    private double parseProfitPercent(String profitStr) {
        try {
            return Double.parseDouble(profitStr.replace("%", ""));
        } catch (NumberFormatException e) {
            return Double.NEGATIVE_INFINITY;
        }
    }


    private void keepBestPairByZscoreAndPvalue() {
        // 📌 Оставляем только одну лучшую пару по zscore/pvalue
        String zScorePath = "z_score.json";
        try {
            File zFile = new File(zScorePath);
            if (zFile.exists()) {
                List<ZScoreEntry> allEntries = List.of(mapper.readValue(zFile, ZScoreEntry[].class));

                // Фильтрация: минимальный pvalue и при равенстве — максимальный zscore
                ZScoreEntry best = allEntries.stream()
                        .min((e1, e2) -> {
                            int cmp = Double.compare(e1.getPValue(), e2.getPValue());
                            if (cmp == 0) {
                                // При равных pvalue берём с большим zscore
                                return -Double.compare(e1.getZScore(), e2.getZScore());
                            }
                            return cmp;
                        })
                        .orElse(null);

                if (best != null) {
                    mapper.writeValue(zFile, List.of(best));
                    log.info("🔍 Сохранили лучшую пару в z_score.json: {}", best);
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
