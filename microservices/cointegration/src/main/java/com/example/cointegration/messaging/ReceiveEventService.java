package com.example.cointegration.messaging;

import com.example.cointegration.repositories.CointPairRepository;
import com.example.shared.events.CointegrationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * Основной сервис для получений событий
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiveEventService {
    private final CointPairRepository cointPairRepository;

    /**
     * Вычитываем нужный топик
     */
    @Bean
    public Consumer<CointegrationEvent> cointegrationEventsConsumer() { //todo должны быть другие топики
        return this::handleEvent;
    }

    private void handleEvent(CointegrationEvent event) {
        log.info("📄 Получено событие: {}", event.getEventType());

        try {
            // Обработка различных типов событий
            switch (event.getEventType()) {
                case "CLEAR_TABLE":
                    clearCointPairs(event);
                    break;
                default:
                    log.warn("⚠️ Неизвестный тип события: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при обработке события: {}", e.getMessage(), e);
        }
    }

    private void clearCointPairs(CointegrationEvent event) {
        log.info("📊 Очистка таблицы от устаревших данных");
        try {
            // Логика экспорта торговых данных
            cointPairRepository.deleteAll();
            log.info("📄 Старые пары удалены");
        } catch (Exception e) {
            log.error("❌ Ошибка при очистке таблицы: {}", e.getMessage(), e);
        }
    }
}