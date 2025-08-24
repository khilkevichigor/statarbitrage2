package com.example.cointegration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Главный класс микросервиса коинтеграции
 * Отвечает за анализ коинтеграции торговых пар
 */
@SpringBootApplication(scanBasePackages = {"com.example.cointegration", "com.example.shared"})
public class CointegrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(CointegrationApplication.class, args);
        System.out.println("🔗 Cointegration Service запущен успешно!");
    }
}