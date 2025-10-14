package com.example.candles;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@SpringBootApplication(scanBasePackages = {"com.example.candles", "com.example.shared"})
@EnableFeignClients(basePackages = {"com.example.candles.client"})
@EnableJpaRepositories(basePackages = {"com.example.candles.repositories"})
@EntityScan(basePackages = {"com.example.shared.models", "com.example.candles.model"})
@EnableScheduling
@EnableAsync
public class CandlesApplication {
    public static void main(String[] args) {
        SpringApplication.run(CandlesApplication.class, args);
        log.info("");
        log.info("🚀 Candles готов к работе!");
    }
}
