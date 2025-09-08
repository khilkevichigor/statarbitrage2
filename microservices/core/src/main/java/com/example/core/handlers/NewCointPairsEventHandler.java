package com.example.core.handlers;

import com.example.core.converters.CointPairToTradingPairConverter;
import com.example.core.messaging.SendEventService;
import com.example.core.processors.StartNewTradeProcessor;
import com.example.core.repositories.CointPairRepository;
import com.example.core.services.EventSendService;
import com.example.core.services.PriceIntersectionService;
import com.example.core.services.SettingsService;
import com.example.core.services.TradingPairService;
import com.example.core.trading.services.OkxPortfolioManager;
import com.example.shared.dto.StartNewTradeRequest;
import com.example.shared.enums.TradeStatus;
import com.example.shared.events.UpdateUiEvent;
import com.example.shared.events.rabbit.CointegrationEvent;
import com.example.shared.events.rabbit.CoreEvent;
import com.example.shared.models.CointPair;
import com.example.shared.models.Settings;
import com.example.shared.models.TradingPair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Основной сервис для получения событий
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewCointPairsEventHandler {
    private final CointPairToTradingPairConverter cointPairToTradingPairConverter;
    private final StartNewTradeProcessor startNewTradeProcessor;
    private final EventSendService eventSendService;
    private final SettingsService settingsService;
    private final TradingPairService tradingPairRepository;
    private final OkxPortfolioManager okxPortfolioManager;
    private final CointPairRepository cointPairRepository;
    private final PriceIntersectionService priceIntersectionService;
    private final SendEventService sendEventService;

    // Мапа для хранения соответствия UUID -> CointPair для получения чартов
    private final Map<String, CointPair> cointPairByUuid = new HashMap<>();

    public void handle(CointegrationEvent event) {
        try {
            log.info("");
            log.info("📄 Получено событие: {}", event.getEventType());
            long schedulerStart = System.currentTimeMillis();

            List<CointPair> cointPairs = event.getCointPairs();

            log.info("Получены cointPairs:");
            cointPairs.forEach(cointPair -> log.info(cointPair.getPairName()));

            List<CointPair> validCointPairs = getValidCointPairs(cointPairs);
            log.info("{} пар из {} прошли валидацию", validCointPairs.size(), cointPairs.size());

            int openedPositionsCount = getOpenPositionsCount();
            log.info("На бирже {} открытых позиций", openedPositionsCount);
            int usePairs = (int) settingsService.getSettings().getUsePairs();
            int usePositions = usePairs * 2;
            if (usePositions - openedPositionsCount < 2) {
                log.info("Всего можно держать {} пар(ы) ({} позиций). Новые позиции открывать нельзя.", usePairs, usePositions);
                return;
            }

            List<CointPair> filteredByMinLotCointPairs = filterByMinLotFromBlackList(validCointPairs);
            log.info("Осталось {} пар из {} после фильтрации по минимальному лоту из черного списка", filteredByMinLotCointPairs.size(), validCointPairs.size());

            List<CointPair> filteredByTradingPairs = filterByExistingTradingPairs(filteredByMinLotCointPairs);
            log.info("Осталось {} пар из {} после фильтрации по trading парам", filteredByTradingPairs.size(), filteredByMinLotCointPairs.size());

            List<CointPair> filteredByEnoughIntersections = filterByMinIntersections(filteredByTradingPairs);
            log.info("Осталось {} пар из {} после фильтрации по достаточному пересечению нормализованных цен", filteredByEnoughIntersections.size(), filteredByTradingPairs.size());

            Map<String, List<CointPair>> missedAndRemainingPairs = splitAndGetMissedAndRemainingPairs(filteredByEnoughIntersections);
            List<CointPair> missedCointPairs = missedAndRemainingPairs.get("missed");
            List<CointPair> remainingCointPairs = missedAndRemainingPairs.get("remaining");
            log.info("Разделили: {} пар(ы) для начала нового трейда и {} пар(ы) для сохранения в бд", missedCointPairs.size(), remainingCointPairs.size());

            if (!remainingCointPairs.isEmpty()) {
                cointPairRepository.deleteAll();
                cointPairRepository.saveAll(remainingCointPairs);
                log.info("💾 Сохранили {} оставшихся пар для работы через UI", remainingCointPairs.size());
            }

            // Сохраняем CointPairs в мапу для дальнейшего получения чартов
            cointPairByUuid.clear();
            missedCointPairs.forEach(cointPair -> 
                cointPairByUuid.put(cointPair.getUuid().toString(), cointPair));

            List<TradingPair> tradingPairs = convertToTradingPair(missedCointPairs);
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

    private Map<String, List<CointPair>> splitAndGetMissedAndRemainingPairs(List<CointPair> cointPairs) {
        Map<String, List<CointPair>> result = new HashMap<>();

        List<TradingPair> activePairs = tradingPairRepository.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);
        Settings settings = settingsService.getSettings();
        int usePairs = (int) settings.getUsePairs();

        int necesseryNewPairs = usePairs - activePairs.size();
        necesseryNewPairs = Math.max(necesseryNewPairs, 0); // защита от отрицательных значений

        // Берем только то количество, которое нужно для новых трейдов
        List<CointPair> missedPairs = cointPairs.stream()
                .limit(necesseryNewPairs)
                .toList();

        // Остальные пары — для наблюдения
        List<CointPair> remainingPairs = cointPairs.stream()
                .skip(necesseryNewPairs)
                .toList();

        result.put("missed", missedPairs);
        result.put("remaining", remainingPairs);

        return result;
    }


    private List<CointPair> filterByExistingTradingPairs(List<CointPair> cointPairs) {
        List<String> usedTickers = getUsedTickers();
        String tickersStr = String.join(", ", usedTickers);
        List<CointPair> filteredByTradingPairs = new ArrayList<>();
        cointPairs.forEach(cointPair -> {
            if (!tickersStr.contains(cointPair.getLongTicker()) && !usedTickers.contains(cointPair.getShortTicker())) {
                filteredByTradingPairs.add(cointPair);
            }
        });
        return filteredByTradingPairs;
    }

    private List<CointPair> filterByMinIntersections(List<CointPair> cointPairs) {
        Settings settings = settingsService.getSettings();

        // Если фильтр отключен, возвращаем исходный список
        if (!settings.isUseMinIntersections()) {
            log.debug("📊 Фильтр по пересечениям нормализованных цен отключен");
            return cointPairs;
        }

        int minIntersections = settings.getMinIntersections();
        log.info("📊 Применяем фильтр по пересечениям: минимум {} пересечений", minIntersections);

        List<CointPair> filteredPairs = new ArrayList<>();

        for (CointPair cointPair : cointPairs) {
            try {
                // Получаем пересечения вместе с нормализованными ценами
                var result = priceIntersectionService.calculateIntersectionsWithData(cointPair);
                int intersections = result.getIntersections();

                log.info("📊 Пара {}: {} пересечений нормализованных цен",
                        cointPair.getPairName(), intersections);

                if (intersections >= minIntersections) {
                    // Сохраняем нормализованные цены и количество пересечений
                    cointPair.setNormalizedLongPrices(result.getNormalizedLongPrices());
                    cointPair.setNormalizedShortPrices(result.getNormalizedShortPrices());
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
        List<TradingPair> activePairs = tradingPairRepository.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);
        List<String> tickers = new ArrayList<>();
        for (TradingPair pair : activePairs) {
            tickers.add(pair.getLongTicker());
            tickers.add(pair.getShortTicker());
        }
        return tickers;
    }

    private List<CointPair> filterByMinLotFromBlackList(List<CointPair> cointPairs) {
        Settings settings = settingsService.getSettings();
        String minimumLotBlacklist = settings.getMinimumLotBlacklist();
        log.info("Черный список минимальных лотов: {}", minimumLotBlacklist);
        List<CointPair> filteredCointPairs = new ArrayList<>();
        cointPairs.forEach(cointPair -> {
            if (!minimumLotBlacklist.contains(cointPair.getLongTicker()) && !minimumLotBlacklist.contains(cointPair.getShortTicker())) {
                filteredCointPairs.add(cointPair);
            }
        });
        return filteredCointPairs;
    }

    private List<TradingPair> convertToTradingPair(List<CointPair> cointPairs) {
        List<TradingPair> convertedPairs = new ArrayList<>();
        cointPairs.forEach(pair -> {
            TradingPair converted = cointPairToTradingPairConverter.convert(pair);
            if (converted != null) {
                tradingPairRepository.save(converted);
                convertedPairs.add(converted);
            }
        });
        return convertedPairs;
    }

    private List<CointPair> getValidCointPairs(List<CointPair> cointPairs) {
        return cointPairs.stream()
                .filter(Objects::nonNull)
                .filter(v -> v.getUuid() != null)
                .toList();
    }

    private int startNewTrades(List<TradingPair> newPairs) {
        AtomicInteger count = new AtomicInteger(0);

        newPairs.forEach(pair -> {
            if (startSingleNewTrade(pair)) {
                count.incrementAndGet();
            }
        });

        return count.get();
    }

    private boolean startSingleNewTrade(TradingPair pair) {
        try {
            TradingPair result = startNewTradeProcessor.startNewTrade(StartNewTradeRequest.builder()
                    .tradingPair(pair)
                    .checkAutoTrading(true)
                    .build());
            if (result != null) {
                // Получаем чарт пересечений как массив байт
                byte[] intersectionChart = new byte[0];
                CointPair cointPair = cointPairByUuid.get(pair.getUuid());
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