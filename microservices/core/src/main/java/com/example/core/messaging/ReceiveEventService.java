package com.example.core.messaging;

import com.example.shared.events.CoreEvent;
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

    /**
     * Обработка событий
     */
    @Bean
    public Consumer<CoreEvent> coreEventsConsumer() {
        return this::handleEvent;
    }

    private void handleEvent(CoreEvent event) {
        log.info("📄 Получено событие: {}", event.getEventType());

        try {
            // Обработка различных типов событий
            switch (event.getEventType()) {
                case "NEW_COINT_PAIRS":
                    log.info("...тут якобы что-то делаем...");
                    //проверяем нужно ли брать в торговлю еще пары и торгуем их
                    break;
                default:
                    log.warn("⚠️ Неизвестный тип события: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при обработке события: {}", e.getMessage(), e);
        }
    }
}