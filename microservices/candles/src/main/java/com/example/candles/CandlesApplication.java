package com.example.candles;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@Slf4j
@SpringBootApplication(scanBasePackages = {"com.example.candles", "com.example.shared"})
@EnableFeignClients(basePackages = {"com.example.candles.client"})
public class CandlesApplication {
    public static void main(String[] args) {
        SpringApplication.run(CandlesApplication.class, args);
        log.info("🕯️ Candles Service запущен успешно!");
    }
}
