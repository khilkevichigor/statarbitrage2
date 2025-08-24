package com.example.trading;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Главный класс микросервиса торгового движка
 * Отвечает за исполнение торговых операций
 */
@SpringBootApplication(scanBasePackages = {"com.example.trading", "com.example.shared"})
public class TradingApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradingApplication.class, args);
        System.out.println("💰 Trading Service запущен успешно!");
    }
}