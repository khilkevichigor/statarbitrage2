package com.example.statarbitrage.processors;

import com.example.statarbitrage.api.OkxClient;
import com.example.statarbitrage.events.SendAsTextEvent;
import com.example.statarbitrage.model.Settings;
import com.example.statarbitrage.model.ZScoreEntry;
import com.example.statarbitrage.python.PythonScripts;
import com.example.statarbitrage.python.PythonScriptsExecuter;
import com.example.statarbitrage.services.EventSendService;
import com.example.statarbitrage.services.SettingsService;
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

    public void process(String chatId) {
        long startTime = System.currentTimeMillis();
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

        // ✅ Сохраняем allCloses в JSON-файл
        String jsonFilePath = "all_closes.json";
        try {
            mapper.writeValue(new File(jsonFilePath), allCloses);
        } catch (IOException e) {
            log.error("Ошибка при сохранении closes.json: {}", e.getMessage(), e);
        }

        log.info("Сохранили цены в all_closes.json");

        try {
            PythonScriptsExecuter.execute(PythonScripts.Z_SCORE_FIND_ALL_AND_SAVE.getName());

            // Обогащаем данные по парам
            enrichZScoreWithPricesAndProfitFromCloses();

            keepBestPairByZscoreAndPvalue();

            clearChartDir();

            PythonScriptsExecuter.execute(PythonScripts.CREATE_CHARTS.getName());

        } catch (Exception e) {
            log.error("Ошибка при запуске Python: {}", e.getMessage(), e);
        }

        log.info("✅ Python-скрипты исполнены");

        long durationMillis = System.currentTimeMillis() - startTime;
        long minutes = durationMillis / 1000 / 60;
        long seconds = (durationMillis / 1000) % 60;
        log.info("Скан завершен. Обработано {} тикеров за {} мин {} сек", totalSymbols, minutes, seconds);
    }

    private void enrichZScoreWithPricesAndProfitFromCloses() {
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

                entry.setAPrice(aPrice);
                entry.setBPrice(bPrice);

                String profit = calculateProfitForMeanReversion(aPrice, bPrice, entry);
                entry.setProfit(profit);
            }

            mapper.writeValue(zFile, allEntries);
            log.info("Обогатили z_score.json ценами из all_closes.json и доходностью");

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
                            int cmp = Double.compare(e1.getPvalue(), e2.getPvalue());
                            if (cmp == 0) {
                                // При равных pvalue берём с большим zscore
                                return -Double.compare(e1.getZscore(), e2.getZscore());
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
