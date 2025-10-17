package com.example.core.handlers;

import com.example.core.messaging.SendEventService;
import com.example.core.processors.StartNewTradeProcessor;
import com.example.core.services.EventSendService;
import com.example.core.services.PriceIntersectionService;
import com.example.core.services.SettingsService;
import com.example.core.repositories.PairRepository;
import com.example.core.trading.services.OkxPortfolioManager;
import com.example.shared.dto.StartNewTradeRequest;
import com.example.shared.enums.TradeStatus;
import com.example.shared.events.UpdateUiEvent;
import com.example.shared.events.rabbit.CointegrationEvent;
import com.example.shared.events.rabbit.CoreEvent;
import com.example.shared.models.Pair;
import com.example.shared.models.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Основной сервис для получения событий
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewCointPairsEventHandler {
    private final StartNewTradeProcessor startNewTradeProcessor;
    private final EventSendService eventSendService;
    private final SettingsService settingsService;
    private final PairRepository tradingPairRepository;
    private final OkxPortfolioManager okxPortfolioManager;
    private final PriceIntersectionService priceIntersectionService;
    private final SendEventService sendEventService;

    // Мапа для хранения соответствия UUID -> Pair для получения чартов
    private final Map<String, Pair> cointPairByUuid = new HashMap<>();

    public void handle(CointegrationEvent event) {
        try {
            log.info("");
            log.info("📄 Получено событие: {}", event.getEventType());
            long schedulerStart = System.currentTimeMillis();

            List<Pair> cointPairs = event.getCointPairs();

            log.info("Получены cointPairs:");
            cointPairs.forEach(cointPair -> log.info(cointPair.getPairName()));

            List<Pair> validCointPairs = getValidCointPairs(cointPairs);
            log.info("{} пар из {} прошли валидацию", validCointPairs.size(), cointPairs.size());

            int openedPositionsCount = getOpenPositionsCount();
            log.info("На бирже {} открытых позиций", openedPositionsCount);
            int usePairs = (int) settingsService.getSettings().getUsePairs();
            int usePositions = usePairs * 2;
            if (usePositions - openedPositionsCount < 2) {
                log.info("Всего можно держать {} пар(ы) ({} позиций). Новые позиции открывать нельзя.", usePairs, usePositions);
                return;
            }

            List<Pair> filteredByMinLotCointPairs = filterByMinLotFromBlackList(validCointPairs);
            log.info("Осталось {} пар из {} после фильтрации по минимальному лоту из черного списка", filteredByMinLotCointPairs.size(), validCointPairs.size());

            List<Pair> filteredByTradingPairs = filterByExistingTradingPairs(filteredByMinLotCointPairs);
            log.info("Осталось {} пар из {} после фильтрации по trading парам", filteredByTradingPairs.size(), filteredByMinLotCointPairs.size());

            List<Pair> filteredByEnoughIntersections = filterByMinIntersections(filteredByTradingPairs);
            log.info("Осталось {} пар из {} после фильтрации по достаточному пересечению нормализованных цен", filteredByEnoughIntersections.size(), filteredByTradingPairs.size());

            Map<String, List<Pair>> missedAndRemainingPairs = splitAndGetMissedAndRemainingPairs(filteredByEnoughIntersections);
            List<Pair> missedCointPairs = missedAndRemainingPairs.get("missed");
            List<Pair> remainingCointPairs = missedAndRemainingPairs.get("remaining");
            log.info("Разделили: {} пар(ы) для начала нового трейда и {} пар(ы) для сохранения в бд", missedCointPairs.size(), remainingCointPairs.size());

            if (!remainingCointPairs.isEmpty()) {
                // Удаляем старые коинтеграционные пары
                tradingPairRepository.deleteByTypeAndStatus(com.example.shared.enums.PairType.COINTEGRATED, TradeStatus.SELECTED);
                
                // Получаем текущие настройки для заполнения minVolMln
                Settings settings = settingsService.getSettings();
                
                // Устанавливаем тип COINTEGRATED для оставшихся пар
                remainingCointPairs.forEach(pair -> {
                    pair.setType(com.example.shared.enums.PairType.COINTEGRATED);
                    pair.setStatus(TradeStatus.SELECTED);
                    // Устанавливаем минимальный объем из настроек
                    pair.setMinVolMln(BigDecimal.valueOf(settings.getMinVolume()));
                });
                tradingPairRepository.saveAll(remainingCointPairs);
                log.info("💾 Сохранили {} оставшихся коинтеграционных пар для работы через UI", remainingCointPairs.size());
            }

            // Сохраняем CointPairs в мапу для дальнейшего получения чартов
            cointPairByUuid.clear();
            missedCointPairs.forEach(cointPair -> 
                cointPairByUuid.put(cointPair.getUuid().toString(), cointPair));

            List<Pair> tradingPairs = convertToTradingPair(missedCointPairs);
            log.info("{} CointPairs сконверчены в {} TradingPairs", missedCointPairs.size(), tradingPairs.size());

            int startedNewTrades = startNewTrades(tradingPairs);
            if (startedNewTrades > 0) {
                updateUI();
            }
            logMaintainPairsCompletion(schedulerStart, startedNewTrades);
        } catch (Exception e) {
            log.error("❌ Ошибка при обработке события: {}", e.getMessage(), e);
        }
    }

    /**
     * Получает количество актуальных открытых позиций с OKX API
     *
     * @return количество открытых позиций на бирже
     */
    private int getOpenPositionsCount() {
        try {
            int count = okxPortfolioManager.getActivePositionsCount();
            log.debug("🔍 Получено {} открытых позиций с OKX API", count);
            return count;
        } catch (Exception e) {
            log.error("❌ Ошибка при получении количества открытых позиций с OKX: {}", e.getMessage());
            return 0;
        }
    }

    private Map<String, List<Pair>> splitAndGetMissedAndRemainingPairs(List<Pair> cointPairs) {
        Map<String, List<Pair>> result = new HashMap<>();

        List<Pair> activePairs = tradingPairRepository.findTradingPairsByStatus(TradeStatus.TRADING);
        Settings settings = settingsService.getSettings();
        int usePairs = (int) settings.getUsePairs();

        int necesseryNewPairs = usePairs - activePairs.size();
        necesseryNewPairs = Math.max(necesseryNewPairs, 0); // защита от отрицательных значений

        // Берем только то количество, которое нужно для новых трейдов
        List<Pair> missedPairs = cointPairs.stream()
                .limit(necesseryNewPairs)
                .toList();

        // Остальные пары — для наблюдения
        List<Pair> remainingPairs = cointPairs.stream()
                .skip(necesseryNewPairs)
                .toList();

        result.put("missed", missedPairs);
        result.put("remaining", remainingPairs);

        return result;
    }


    private List<Pair> filterByExistingTradingPairs(List<Pair> cointPairs) {
        List<String> usedTickers = getUsedTickers();
        String tickersStr = String.join(", ", usedTickers);
        List<Pair> filteredByTradingPairs = new ArrayList<>();
        cointPairs.forEach(cointPair -> {
            if (!tickersStr.contains(cointPair.getTickerA()) && !usedTickers.contains(cointPair.getTickerB())) {
                filteredByTradingPairs.add(cointPair);
            }
        });
        return filteredByTradingPairs;
    }

    private List<Pair> filterByMinIntersections(List<Pair> cointPairs) {
        Settings settings = settingsService.getSettings();

        // Если фильтр отключен, возвращаем исходный список
        if (!settings.isUseMinIntersections()) {
            log.debug("📊 Фильтр по пересечениям нормализованных цен отключен");
            return cointPairs;
        }

        int minIntersections = settings.getMinIntersections();
        log.info("📊 Применяем фильтр по пересечениям: минимум {} пересечений", minIntersections);

        List<Pair> filteredPairs = new ArrayList<>();

        for (Pair cointPair : cointPairs) {
            try {
                // Получаем пересечения вместе с нормализованными ценами
                var result = priceIntersectionService.calculateIntersectionsWithData(cointPair);
                int intersections = result.getIntersections();

                log.info("📊 Пара {}: {} пересечений нормализованных цен",
                        cointPair.getPairName(), intersections);

                if (intersections >= minIntersections) {
                    // Сохраняем нормализованные цены и количество пересечений
                    java.util.List<java.math.BigDecimal> normalizedLongList = java.util.Arrays.stream(result.getNormalizedLongPrices())
                            .mapToObj(java.math.BigDecimal::valueOf)
                            .collect(java.util.stream.Collectors.toList());
                    java.util.List<java.math.BigDecimal> normalizedShortList = java.util.Arrays.stream(result.getNormalizedShortPrices())
                            .mapToObj(java.math.BigDecimal::valueOf)
                            .collect(java.util.stream.Collectors.toList());
                    cointPair.setNormalizedLongPrices(normalizedLongList);
                    cointPair.setNormalizedShortPrices(normalizedShortList);
                    cointPair.setIntersectionsCount(intersections);

                    // Создаем чарт для отобранных пар
                    priceIntersectionService.calculateIntersectionsWithChart(cointPair, true);

                    filteredPairs.add(cointPair);
                    log.debug("✅ Пара {} прошла фильтр: {} >= {} пересечений",
                            cointPair.getPairName(), intersections, minIntersections);
                } else {
                    log.info("❌ Пара {} отфильтрована: {} < {} пересечений",
                            cointPair.getPairName(), intersections, minIntersections);
                }
            } catch (Exception e) {
                log.error("❌ Ошибка при подсчете пересечений для пары {}: {}",
                        cointPair.getPairName(), e.getMessage(), e);
                // В случае ошибки не добавляем пару в отфильтрованный список
            }
        }

        return filteredPairs;
    }

    private List<String> getUsedTickers() {
        List<Pair> activePairs = tradingPairRepository.findTradingPairsByStatus(TradeStatus.TRADING);
        List<String> tickers = new ArrayList<>();
        for (Pair pair : activePairs) {
            tickers.add(pair.getTickerA());
            tickers.add(pair.getTickerB());
        }
        return tickers;
    }

    private List<Pair> filterByMinLotFromBlackList(List<Pair> cointPairs) {
        Settings settings = settingsService.getSettings();
        String minimumLotBlacklist = settings.getMinimumLotBlacklist();
        log.info("Черный список минимальных лотов: {}", minimumLotBlacklist);
        List<Pair> filteredCointPairs = new ArrayList<>();
        cointPairs.forEach(cointPair -> {
            if (!minimumLotBlacklist.contains(cointPair.getTickerA()) && !minimumLotBlacklist.contains(cointPair.getTickerB())) {
                filteredCointPairs.add(cointPair);
            }
        });
        return filteredCointPairs;
    }

    private List<Pair> convertToTradingPair(List<Pair> cointPairs) {
        List<Pair> convertedPairs = new ArrayList<>();
        Settings settings = settingsService.getSettings();
        
        cointPairs.forEach(pair -> {
            // Создаем копию пары с типом TRADING
            Pair converted = Pair.builder()
                .uuid(pair.getUuid())
                .tickerA(pair.getTickerA())
                .tickerB(pair.getTickerB())
                .pairName(pair.getPairName())
                .type(com.example.shared.enums.PairType.TRADING)
                .status(TradeStatus.SELECTED)
                .settingsCandleLimit(pair.getSettingsCandleLimit())
                .settingsMinZ(pair.getSettingsMinZ())
                .timeframe(pair.getTimeframe())
                // Устанавливаем минимальный объем из настроек
                .minVolMln(BigDecimal.valueOf(settings.getMinVolume()))
                .build();
            if (converted != null) {
                tradingPairRepository.save(converted);
                convertedPairs.add(converted);
            }
        });
        return convertedPairs;
    }

    private List<Pair> getValidCointPairs(List<Pair> cointPairs) {
        return cointPairs.stream()
                .filter(Objects::nonNull)
                .filter(v -> v.getUuid() != null)
                .toList();
    }

    private int startNewTrades(List<Pair> newPairs) {
        AtomicInteger count = new AtomicInteger(0);

        newPairs.forEach(pair -> {
            if (startSingleNewTrade(pair)) {
                count.incrementAndGet();
            }
        });

        return count.get();
    }

    private boolean startSingleNewTrade(Pair pair) {
        try {
            Pair result = startNewTradeProcessor.startNewTrade(StartNewTradeRequest.builder()
                    .tradingPair(pair)
                    .checkAutoTrading(true)
                    .build());
            if (result != null && result.getStatus().equals(TradeStatus.TRADING)) {
                // Получаем чарт пересечений как массив байт
                byte[] intersectionChart = new byte[0];
                Pair cointPair = cointPairByUuid.get(pair.getUuid().toString());
                if (cointPair != null) {
                    intersectionChart = priceIntersectionService.getIntersectionChartAsBytes(cointPair);
                    log.info("📊 Получен чарт пересечений для пары {}: {} байт", 
                            pair.getPairName(), intersectionChart.length);
                } else {
                    log.warn("⚠️ Не найден CointPair для UUID {} (пара {})", 
                            pair.getUuid(), pair.getPairName());
                }

                String message = "Новый трейд: " + pair.getPairName();
                sendEventService.sendCoreEvent(new CoreEvent(message, intersectionChart, CoreEvent.Type.ENTRY_INTERSECTION_CHART));
            }
            return result != null;
        } catch (Exception e) {
            log.warn("⚠️ Не удалось запустить новый трейд для пары {}: {}", pair.getPairName(), e.getMessage());
            return false;
        }
    }

    private void updateUI() {
        try {
            eventSendService.updateUI(UpdateUiEvent.builder().build());
        } catch (Exception e) {
            log.error("❌ Ошибка при обновлении UI: {}", e.getMessage());
        }
    }

    private void logMaintainPairsCompletion(long startTime, int newPairsCount) {
        long duration = System.currentTimeMillis() - startTime;
        log.debug("⏱️ Запуск новых трейдов завершился за {} сек. Запущено {} новых пар", duration / 1000.0, newPairsCount);
    }
}