package com.example.okx;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Главный класс микросервиса OKX
 * Отвечает за интеграцию с биржей OKX
 */
@Slf4j
@SpringBootApplication(scanBasePackages = {"com.example.okx", "com.example.shared"})
public class OkxApplication {

    public static void main(String[] args) {
        SpringApplication.run(OkxApplication.class, args);
        try {
            //ждем чтобы не мешать логи и было по красоте
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        log.info("");
        log.info("🚀 Okx готов к работе!");
    }
}