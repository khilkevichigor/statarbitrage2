package com.example.shared.config;

import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class MessagingConfig {
    
    public MessagingConfig() {
        log.info("📨 Messaging Configuration загружена");
        // Простая конфигурация для разработки
        // В продакшене здесь будут настройки RabbitMQ
    }
}