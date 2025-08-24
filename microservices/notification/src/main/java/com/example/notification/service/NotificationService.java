package com.example.notification.service;

import com.example.shared.events.NotificationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

@Service
@Slf4j
public class NotificationService {
    
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
    }
    
    private void sendEmail(NotificationEvent event) {
        log.info("📧 Отправка Email: {} для {}", event.getMessage(), event.getRecipient());
        // TODO: Реализовать отправку через SMTP
    }
    
    private void sendSms(NotificationEvent event) {
        log.info("📲 Отправка SMS: {} для {}", event.getMessage(), event.getRecipient());
        // TODO: Реализовать отправку через SMS API
    }
}