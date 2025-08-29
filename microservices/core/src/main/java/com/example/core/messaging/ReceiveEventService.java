package com.example.core.messaging;

import com.example.core.handlers.NewCointPairsEventHandler;
import com.example.shared.events.CointegrationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * Основной сервис для получения событий
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiveEventService {
    private final NewCointPairsEventHandler newCointPairsEventHandler;

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
        event.getCointPairs().forEach(v -> log.info(v.getPairName() + " z=" + v.getZScoreCurrent()));
        try {
            // Обработка различных типов событий
            switch (event.getType()) {
                case NEW_COINT_PAIRS:
                    newCointPairsEventHandler.handle(event);
                    break;
                default:
                    log.warn("⚠️ Неизвестный тип события: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при обработке события: {}", e.getMessage(), e);
        }
    }
}