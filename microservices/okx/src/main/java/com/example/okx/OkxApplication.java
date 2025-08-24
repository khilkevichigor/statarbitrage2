package com.example.okx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Главный класс микросервиса OKX
 * Отвечает за интеграцию с биржей OKX
 */
@SpringBootApplication(scanBasePackages = {"com.example.okx", "com.example.shared"})
public class OkxApplication {

    public static void main(String[] args) {
        SpringApplication.run(OkxApplication.class, args);
        System.out.println("🏦 OKX Service запущен успешно!");
    }
}