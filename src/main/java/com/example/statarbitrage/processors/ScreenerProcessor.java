package com.example.statarbitrage.processors;

import com.example.statarbitrage.api.OkxClient;
import com.example.statarbitrage.events.SendAsTextEvent;
import com.example.statarbitrage.model.Settings;
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

    public String process(String chatId) {
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

        // ✅ Сохраняем allCloses в JSON-файл
        ObjectMapper mapper = new ObjectMapper();
        String jsonFilePath = "closes.json";
        try {
            mapper.writeValue(new File(jsonFilePath), allCloses);
        } catch (IOException e) {
            log.error("Ошибка при сохранении closes.json: {}", e.getMessage(), e);
            return "Ошибка при сохранении данных";
        }

        // ✅ Вызываем Python-скрипт, передаём путь и настройки
        try {
            System.out.println("-->>1");
            PythonScriptsExecuter.execute(PythonScripts.FIND_ALL_AND_SAVE.getName());
            System.out.println("-->>2");
        } catch (Exception e) {
            log.error("Ошибка при запуске Python: {}", e.getMessage(), e);
            return "Ошибка при выполнении скрипта";
        }

        long durationMillis = System.currentTimeMillis() - startTime;
        long minutes = durationMillis / 1000 / 60;
        long seconds = (durationMillis / 1000) % 60;
        log.info("Скан завершен. Обработано {} тикеров за {} мин {} сек", totalSymbols, minutes, seconds);

        return "Скан завершен";
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
