package com.example.candles;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"com.example.candles", "com.example.shared"})
@EnableFeignClients(basePackages = {"com.example.candles.client"})
public class CandlesApplication {
    public static void main(String[] args) {
        SpringApplication.run(CandlesApplication.class, args);
        System.out.println("🕯️ Candles Service запущен успешно!");
    }
}
