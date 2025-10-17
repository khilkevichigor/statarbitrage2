package com.example.csv.messaging;

import com.example.csv.service.CsvExportService;
import com.example.shared.events.rabbit.CoreEvent;
import com.example.shared.models.Pair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * Основной сервис получения событий
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiveEventService {
    private final CsvExportService csvExportService;

    /**
     * Обработка событий
     */
    @Bean
    public Consumer<CoreEvent> coreEventsConsumer() {
        return this::handleEvent;
    }

    private void handleEvent(CoreEvent event) {
        log.info("📄 Получено событие: {}", event.toString());

        try {
            // Обработка различных типов событий для экспорта
            switch (event.getType()) {
                case ADD_CLOSED_TO_CSV:
                    addToCsv(event.getTradingPair());
                    break;
                default:
                    log.warn("⚠️ Неизвестный тип события для CSV экспорта: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при экспорте в CSV: {}", e.getMessage(), e);
        }
    }

    private void addToCsv(Pair tradingPair) {
        log.info("📋 Добавление закрытой пары в CSV");

        try {
            csvExportService.addClosedPairToCsv(tradingPair);
            log.info("Пара {} успешно добавлена в csv файл.", tradingPair.getPairName());
        } catch (Exception e) {
            log.error("❌ Ошибка при добавлении пары в CSV: {}", e.getMessage(), e);
        }
    }
}