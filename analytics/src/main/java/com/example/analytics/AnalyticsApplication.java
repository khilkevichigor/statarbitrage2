package com.example.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Главный класс микросервиса аналитики
 * Отвечает за статистические расчеты и аналитику торговых данных
 */
@SpringBootApplication(scanBasePackages = {"com.example.analytics", "com.example.shared"})
public class AnalyticsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalyticsApplication.class, args);
        System.out.println("🔬 Analytics Service запущен успешно!");
    }
}