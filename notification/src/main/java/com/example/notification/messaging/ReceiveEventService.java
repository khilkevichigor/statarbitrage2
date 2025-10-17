package com.example.notification.messaging;

import com.example.notification.service.TelegramNotificationService;
import com.example.shared.events.rabbit.CoreEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReceiveEventService {
    private final TelegramNotificationService telegramNotificationService;

    @Bean
    public Consumer<CoreEvent> coreEventsConsumer() {
        return this::handleEvent;
    }

    private void handleEvent(CoreEvent event) {
        log.info("📨 Получено событие: {}", event.toString());
        switch (event.getType()) {
            case CLOSED_MESSAGE_TO_TELEGRAM -> telegramNotificationService.sendTelegramClosedPair(event.getTradingPair());
            case MESSAGE_TO_TELEGRAM -> telegramNotificationService.sendTelegramMessage(event.getMessage());
            case ENTRY_INTERSECTION_CHART -> {
                log.info("📊 Получен чарт пересечений для отправки в Telegram: {} байт", 
                        event.getData() != null ? event.getData().length : 0);
                telegramNotificationService.sendTelegramPhoto(event.getData(), event.getMessage());
            }
            default -> log.warn("⚠️ Неизвестный тип уведомления: {}", event.getType());
        }
    }
}