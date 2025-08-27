package com.example.cointegration.service;

import com.example.cointegration.repositories.CointPairRepository;
import com.example.shared.events.CointegrationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * Основной сервис для экспорта данных в CSV формат
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiveEventService {
    private final CointPairRepository cointPairRepository;

    /**
     * Обработка событий для экспорта в CSV
     */
    @Bean
    public Consumer<CointegrationEvent> cointegrationEventsConsumer() {
        return this::handleEvent;
    }

    private void handleEvent(CointegrationEvent event) {
        log.info("📄 Получено событие: {}", event.getEventType());

        try {
            // Обработка различных типов событий для экспорта
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

    /**
     * Экспорт торговых данных в CSV
     */
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