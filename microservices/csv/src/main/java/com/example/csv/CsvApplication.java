package com.example.csv;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Главный класс микросервиса экспорта CSV
 * Отвечает за экспорт торговых данных в CSV формат
 */
@SpringBootApplication(scanBasePackages = {"com.example.csv", "com.example.shared"})
public class CsvApplication {

    public static void main(String[] args) {
        SpringApplication.run(CsvApplication.class, args);
        System.out.println("📄 CSV Export Service запущен успешно!");
    }
}