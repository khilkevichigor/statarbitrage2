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

    public void process(String chatId) {
        long startTime = System.currentTimeMillis();
        Settings settings = settingsService.getSettings(Long.parseLong(chatId));

        Set<String> swapTickers = okxClient.getSwapTickers();
        int totalSymbols = swapTickers.size();

        ExecutorService executor = Executors.newFixedThreadPool(5);
        ConcurrentHashMap<String, List<Double>> allCloses = new ConcurrentHashMap<>();

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
        executor.shutdown();

        log.info("Собрали цены для {} монет", allCloses.size());

        // ✅ Сохраняем allCloses в JSON-файл
        ObjectMapper mapper = new ObjectMapper();
        String jsonFilePath = "all_closes.json";
        try {
            mapper.writeValue(new File(jsonFilePath), allCloses);
        } catch (IOException e) {
            log.error("Ошибка при сохранении closes.json: {}", e.getMessage(), e);
        }

        log.info("Сохранили цены в all_closes.json");

        try {
            PythonScriptsExecuter.execute(PythonScripts.Z_SCORE_FIND_ALL_AND_SAVE.getName());

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

            // --- очищаем папку charts перед созданием новых графиков ---
            String chartsDir = "charts";
            clearDirectory(chartsDir);
            log.info("Очистили папку с чартами: {}", chartsDir);

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

    private void clearDirectory(String dirPath) {
        File dir = new File(dirPath);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!file.delete()) {
                        log.warn("Не удалось удалить файл: {}", file.getAbsolutePath());
                    }
                }
            }
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
