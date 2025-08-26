package com.example.cointegration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Главный класс микросервиса коинтеграции
 * Отвечает за анализ коинтеграции торговых пар
 */
@Slf4j
@EntityScan(basePackages = {"com.example.shared.models"})
@EnableFeignClients(basePackages = {"com.example.cointegration.client"})
@SpringBootApplication(scanBasePackages = {"com.example.cointegration", "com.example.shared"})
@EnableJpaRepositories(basePackages = {"com.example.cointegration.repositories"})
@EnableScheduling
@EnableAsync
public class CointegrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(CointegrationApplication.class, args);
        log.info("✅ Микросервис Cointegration успешно запущен!");
    }
}