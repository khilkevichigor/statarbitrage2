package com.example.core.messaging;

import com.example.core.converters.CointPairMapper;
import com.example.core.converters.CointPairToTradingPairConverter;
import com.example.core.processors.StartNewTradeProcessor;
import com.example.core.services.EventSendService;
import com.example.shared.dto.StartNewTradeRequest;
import com.example.shared.events.CointegrationEvent;
import com.example.shared.events.UpdateUiEvent;
import com.example.shared.models.CointPair;
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
    private final CointPairMapper cointPairMapper;
    private final StartNewTradeProcessor startNewTradeProcessor;
    private final EventSendService eventSendService;

    /**
     * Вычитываем топик Коинтеграции
     */
    @Bean
    public Consumer<CointegrationEvent> cointegrationEventsConsumer() {
        return this::handleEvent;
    }

    private void handleEvent(CointegrationEvent event) {
        log.info("📄 Получено событие: {}", event.getEventType());
        long schedulerStart = System.currentTimeMillis();
        try {
            // Обработка различных типов событий
            switch (event.getType().name()) {
                case "NEW_COINT_PAIRS":
                    List<CointPair> cointPairs = event.getCointPairs();

                    List<CointPair> validCointPairs = getValidCointPairs(cointPairs);
                    log.info("{} пар из {} прошли валидацию", validCointPairs.size(), cointPairs.size());

                    List<TradingPair> tradingPairs = convertToTradingPair(validCointPairs);
                    log.info("{} CointPairs сконверчены в {} TradingPairs", validCointPairs.size(), cointPairs.size());

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

    private List<TradingPair> convertToTradingPair(List<CointPair> validCointPairs) {
        List<TradingPair> convertedPairs = new ArrayList<>();
//        validCointPairs.forEach(pair -> convertedPairs.add(cointPairToTradingPairConverter.convert(pair)));
        validCointPairs.forEach(pair -> convertedPairs.add(cointPairMapper.toTradingPair(pair)));
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