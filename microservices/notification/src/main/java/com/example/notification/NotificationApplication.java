package com.example.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import lombok.extern.slf4j.Slf4j;

@SpringBootApplication(scanBasePackages = {"com.example.notification", "com.example.shared"})
@Slf4j
public class NotificationApplication {
    
    public static void main(String[] args) {
        log.info("📱 Запуск Notification Service...");
        SpringApplication.run(NotificationApplication.class, args);
        log.info("✅ Notification Service успешно запущен!");
    }
}