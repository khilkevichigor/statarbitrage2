package com.example.statarbitrage.processors;

import com.example.statarbitrage.api.OkxClient;
import com.example.statarbitrage.events.SendAsTextEvent;
import com.example.statarbitrage.model.StatArbitrageSettings;
import com.example.statarbitrage.services.EventSendService;
import com.example.statarbitrage.services.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScreenerProcessor {
    private final OkxClient okxClient;
    private final EventSendService eventSendService;
    private final SettingsService settingsService;
    private Set<String> previouslyFoundCoins = new HashSet<>();

    public String scanAllAuto(String chatId) {
        long startTime = System.currentTimeMillis();
        StatArbitrageSettings statArbitrageSettings = settingsService.getSettings(Long.parseLong(chatId));

        Set<String> swapTickers = okxClient.getSwapTickers();
        int totalSymbols = swapTickers.size();

        ExecutorService executor = Executors.newFixedThreadPool(5); // можно увеличить при необходимости
        Set<String> currentFoundCoins = ConcurrentHashMap.newKeySet(); // потоко-безопасный Set
        List<CompletableFuture<String>> futures = swapTickers.stream()
                .map(symbol -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return "";
                    } catch (Exception e) {
                        log.error("Ошибка при обработке {}: {}", symbol, e.getMessage(), e);
                        return null;
                    }
                }, executor))
                .toList();

        List<String> foundSymbols = futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList();

        executor.shutdown();

        // Обновить список монет, которые были найдены — оставить только текущие
        previouslyFoundCoins.retainAll(currentFoundCoins); // удалить те, которые больше не появляются
        previouslyFoundCoins.addAll(currentFoundCoins); // добавить все текущие

        // Сортируем и формируем вывод в столбик
        String output = foundSymbols.stream()
                .sorted(String::compareToIgnoreCase)
                .collect(Collectors.joining("\n"));

        long durationMillis = System.currentTimeMillis() - startTime;
        long minutes = durationMillis / 1000 / 60;
        long seconds = (durationMillis / 1000) % 60;
        log.info("Скан завершен. Найдено {} из {} за {} мин {} сек", foundSymbols.size(), totalSymbols, minutes, seconds);

        return String.join(", ", output);
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
