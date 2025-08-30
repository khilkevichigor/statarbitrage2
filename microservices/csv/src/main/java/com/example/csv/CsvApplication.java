package com.example.csv;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Главный класс микросервиса экспорта CSV
 * Отвечает за экспорт торговых данных в CSV формат
 */
@Slf4j
@SpringBootApplication(scanBasePackages = {"com.example.csv", "com.example.shared"})
public class CsvApplication {

    public static void main(String[] args) {
        SpringApplication.run(CsvApplication.class, args);
        log.info("");
        log.info("🚀 Csv готов к работе!");
    }
}