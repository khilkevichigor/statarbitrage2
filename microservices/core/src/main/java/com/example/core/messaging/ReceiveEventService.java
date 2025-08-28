package com.example.core.messaging;

import com.example.core.converters.CointPairToTradingPairConverter;
import com.example.core.processors.StartNewTradeProcessor;
import com.example.core.services.EventSendService;
import com.example.core.services.SettingsService;
import com.example.core.services.TradingPairService;
import com.example.core.trading.services.OkxPortfolioManager;
import com.example.shared.dto.StartNewTradeRequest;
import com.example.shared.events.CointegrationEvent;
import com.example.shared.events.UpdateUiEvent;
import com.example.shared.models.CointPair;
import com.example.shared.models.Settings;
import com.example.shared.models.TradeStatus;
import com.example.shared.models.TradingPair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Основной сервис для получения событий
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiveEventService {
    private final CointPairToTradingPairConverter cointPairToTradingPairConverter;
    private final StartNewTradeProcessor startNewTradeProcessor;
    private final EventSendService eventSendService;
    private final SettingsService settingsService;
    private final TradingPairService tradingPairRepository;
    private final OkxPortfolioManager okxPortfolioManager;

    /**
     * Вычитываем топик Коинтеграции
     */
    @Bean
    public Consumer<CointegrationEvent> cointegrationEventsConsumer() {
        return this::handleEvent;
    }

    private void handleEvent(CointegrationEvent event) {
        log.info("");
        log.info("📄 Получено событие: {}", event.getEventType());
        long schedulerStart = System.currentTimeMillis();
        try {
            // Обработка различных типов событий
            switch (event.getType().name()) {
                case "NEW_COINT_PAIRS":
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
                        log.info("Всего можно держать {} пар ({} позиций). Новые позиции открывать нельзя.", usePairs, usePositions);
                        return;
                    }

                    List<CointPair> filteredByMinLotCointPairs = filterByMinLotFromBlackList(validCointPairs);
                    log.info("Осталось {} пар из {} после фильтрации по минимальному лоту из черного списка", filteredByMinLotCointPairs.size(), validCointPairs.size());

                    List<CointPair> filteredByTradingPairs = filterByExistingTradingPairs(filteredByMinLotCointPairs);
                    log.info("Осталось {} пар из {} после фильтрации по trading парам", filteredByTradingPairs.size(), filteredByMinLotCointPairs.size());

                    List<CointPair> missedCountCointPairs = getMissedPairs(filteredByTradingPairs);
                    log.info("Взяли необходимые {} пар для нового трейда", missedCountCointPairs.size());

                    List<TradingPair> tradingPairs = convertToTradingPair(missedCountCointPairs);
                    log.info("{} CointPairs сконверчены в {} TradingPairs", missedCountCointPairs.size(), tradingPairs.size());

                    int startedNewTrades = startNewTrades(tradingPairs);
                    if (startedNewTrades > 0) {
                        updateUI();
                    }
                    logMaintainPairsCompletion(schedulerStart, startedNewTrades);
                    break;
                default:
                    log.warn("⚠️ Неизвестный тип события: {}", event.getEventType());
            }
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

    private List<CointPair> getMissedPairs(List<CointPair> filteredByTradingPairs) {
        List<TradingPair> activePairs = tradingPairRepository.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);
        Settings settings = settingsService.getSettings();
        int usePairs = (int) settings.getUsePairs();

        int necesseryNewPairs = usePairs - activePairs.size();

        return filteredByTradingPairs.stream()
                .limit(Math.max(necesseryNewPairs, 0)) // берем только нужное количество, не меньше 0
                .toList(); // в Java 16+ можно использовать toList(), иначе collect(Collectors.toList())

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

    private List<TradingPair> convertToTradingPair(List<CointPair> validCointPairs) {
        List<TradingPair> convertedPairs = new ArrayList<>();
        validCointPairs.forEach(pair -> convertedPairs.add(cointPairToTradingPairConverter.convert(pair)));
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