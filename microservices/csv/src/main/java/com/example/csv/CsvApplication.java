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
        try {
            //ждем чтобы не мешать логи и было по красоте
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        log.info("");
        log.info("🚀 Csv готов к работе!");
    }
}