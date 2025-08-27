package com.example.notification.messaging;

import com.example.notification.bot.BotConfig;
import com.example.notification.events.SendAsTextEvent;
import com.example.notification.service.EventSendService;
import com.example.shared.events.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReceiveEventService {
    private final BotConfig botConfig;
    private final EventSendService eventSendService;

    @Bean
    public Consumer<NotificationEvent> csvEventsConsumer() {
        return this::handleEvent;
    }

    private void handleEvent(NotificationEvent event) {
        log.info("📨 Получено событие: {}", event.getMessage());
        switch (event.getType()) {
            case TELEGRAM -> sendTelegram(event);
            default -> log.warn("⚠️ Неизвестный тип уведомления: {}", event.getType());
        }
    }

    private void sendTelegram(NotificationEvent event) {
        log.info("📤 Отправка Telegram: {} для {}", event.getMessage(), event.getRecipient());
        sendNotification(event.getMessage());
    }

    private void sendNotification(String text) {
        SendAsTextEvent event = SendAsTextEvent.builder()
                .chatId(String.valueOf(botConfig.getOwnerChatId()))
                .text(text)
                .enableMarkdown(false)
                .build();
        try {
            eventSendService.sendTelegramMessageAsTextEvent(event);
        } catch (Exception e) {
            log.error("Ошибка отправки сообщения в телеграм {}", e.getMessage(), e);
        }
    }
}