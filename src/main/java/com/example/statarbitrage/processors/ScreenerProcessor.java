package com.example.statarbitrage.processors;

import com.example.statarbitrage.api.OkxClient;
import com.example.statarbitrage.events.SendAsTextEvent;
import com.example.statarbitrage.messages.SignalMessageBuilder;
import com.example.statarbitrage.model.CoinParameters;
import com.example.statarbitrage.model.UserSettings;
import com.example.statarbitrage.services.CoinParametersService;
import com.example.statarbitrage.services.ConditionService;
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
    private final CoinParametersService coinParametersService;
    private final ConditionService conditionService;
    private Set<String> previouslyFoundCoins = new HashSet<>();

    public void process(String chatId, String symol) {
        UserSettings userSettings = settingsService.getSettings(Long.parseLong(chatId));
        List<Double> btcCloses = okxClient.getCloses("BTC-USDT-SWAP", userSettings.getHtf().getTfName(), 300); // для корреляции
        if (Objects.nonNull(symol)) {
            scanCoin(chatId, symol, userSettings, btcCloses);
        } else {
            scanAll(chatId, userSettings, btcCloses);
        }
    }

    private void scanCoin(String chatId, String symbol, UserSettings userSettings, List<Double> btcCloses) {
        CoinParameters coinParameters = coinParametersService.getParameters(symbol, userSettings, btcCloses);
        if (!conditionService.checkAndSetEmoji(userSettings, coinParameters)) {
            //ignore
        }
        String signalMessage = SignalMessageBuilder.buildSignalText(userSettings, coinParameters);
        sendSignal(chatId, signalMessage);
    }

    private void scanAll(String chatId, UserSettings userSettings, List<Double> btcCloses) {
        sendText(chatId, "🔎 Ищу монеты...");
        long startTime = System.currentTimeMillis();

        Set<String> swapTickers = okxClient.getSwapTickers();
        int totalSymbols = swapTickers.size();

        ExecutorService executor = Executors.newFixedThreadPool(5); // можно увеличить до 20, если нужно
        List<CompletableFuture<Boolean>> futures = swapTickers.stream()
                .map(symbol -> CompletableFuture.supplyAsync(() -> {
                    try {
                        CoinParameters coinParameters = coinParametersService.getParameters(symbol, userSettings, btcCloses);
                        if (!conditionService.checkAndSetEmoji(userSettings, coinParameters)) {
                            return false;
                        }
                        String signalMessage = SignalMessageBuilder.buildSignalText(userSettings, coinParameters);
                        sendSignal(chatId, signalMessage);
                        return true;
                    } catch (Exception e) {
                        log.error("Ошибка при обработке {}: {}", symbol, e.getMessage(), e);
                        return false;
                    }
                }, executor))
                .toList();

        // Дождаться завершения всех задач и подсчитать количество сигналов
        long foundCount = futures.stream()
                .map(CompletableFuture::join)
                .filter(Boolean::booleanValue)
                .count();

        executor.shutdown();

        long endTime = System.currentTimeMillis();
        long durationMillis = endTime - startTime;
        long minutes = durationMillis / 1000 / 60;
        long seconds = (durationMillis / 1000) % 60;

        sendText(chatId, String.format("На этом все! Найдено: %d (из %d монет за %d мин %d сек)", foundCount, totalSymbols, minutes, seconds));
    }

    public String scanAllAuto(String chatId) {
        long startTime = System.currentTimeMillis();
        UserSettings userSettings = settingsService.getSettings(Long.parseLong(chatId));
        List<Double> btcCloses = okxClient.getCloses("BTC-USDT-SWAP", userSettings.getHtf().getTfName(), 300); // для корреляции

        Set<String> swapTickers = okxClient.getSwapTickers();
        if (userSettings.isUseTopGainersLosers()) {
            swapTickers = coinParametersService.getTopGainersLosers(swapTickers, userSettings.getTopGainersLosersQnty());
        }
        int totalSymbols = swapTickers.size();

        ExecutorService executor = Executors.newFixedThreadPool(5); // можно увеличить при необходимости
        Set<String> currentFoundCoins = ConcurrentHashMap.newKeySet(); // потоко-безопасный Set
        List<CompletableFuture<String>> futures = swapTickers.stream()
                .map(symbol -> CompletableFuture.supplyAsync(() -> {
                    try {
                        CoinParameters coinParameters = coinParametersService.getParameters(symbol, userSettings, btcCloses);

                        if (!conditionService.checkAndSetEmoji(userSettings, coinParameters)) {
                            return null;
                        }
                        String shortSymbol = symbol.replace("-USDT-SWAP", "") + " " + coinParameters.getEmoji();

                        String info = SignalMessageBuilder.getInfo(coinParameters, userSettings);

                        currentFoundCoins.add(shortSymbol);

                        boolean isNew = !previouslyFoundCoins.contains(shortSymbol);

                        return isNew ? shortSymbol + "*" + info : shortSymbol + info;
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
