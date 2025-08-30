package com.example.core.messaging;

import com.example.core.handlers.NewCointPairsEventHandler;
import com.example.shared.events.rabbit.CointegrationEvent;
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

    //todo сделать кнопку "Торговать" для наблюдаемых пар - например перевести BTC-ETH в торговлю! Или же сделать техтАреа для Отобранные пары по
    // аналогии с Наблюдаемыми! Или же сделать техтАреа в настройках что бы указывать пары которые нужно всегда мониторить на торговлю.

    //todo сделать таблицу открытых позиций для управления

    private void handleEvent(CointegrationEvent event) {
        log.info("");
        log.info("📄 Получено событие: {}", event.getEventType());
        event.getCointPairs().forEach(v -> log.info("{} z={}", v.getPairName(), v.getZScoreCurrent()));
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