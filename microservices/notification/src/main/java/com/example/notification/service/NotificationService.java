package com.example.notification.service;

import com.example.notification.bot.BotConfig;
import com.example.notification.events.SendAsTextEvent;
import com.example.shared.events.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {
    private final BotConfig botConfig;
    private final EventSendService eventSendService;

    @Bean
    public Consumer<NotificationEvent> notificationEventsConsumer() {
        return event -> {
            log.info("📨 Получено уведомление: {}", event.getMessage());
            processNotification(event);
        };
    }

    private void processNotification(NotificationEvent event) {
        switch (event.getType()) {
            case TELEGRAM -> sendTelegram(event);
            case EMAIL -> sendEmail(event);
            case SMS -> sendSms(event);
            default -> log.warn("⚠️ Неизвестный тип уведомления: {}", event.getType());
        }
    }

    private void sendTelegram(NotificationEvent event) {
        log.info("📤 Отправка Telegram: {} для {}", event.getMessage(), event.getRecipient());
        // TODO: Реализовать отправку через Telegram Bot API
        sendNotification(event.getMessage());
    }

    private void sendEmail(NotificationEvent event) {
        log.info("📧 Отправка Email: {} для {}", event.getMessage(), event.getRecipient());
        // TODO: Реализовать отправку через SMTP
    }

    private void sendSms(NotificationEvent event) {
        log.info("📲 Отправка SMS: {} для {}", event.getMessage(), event.getRecipient());
        // TODO: Реализовать отправку через SMS API
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