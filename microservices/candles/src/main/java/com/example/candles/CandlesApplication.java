package com.example.candles;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.example.candles", "com.example.shared"})
public class CandlesApplication {
    public static void main(String[] args) {
        SpringApplication.run(CandlesApplication.class, args);
        System.out.println("🕯️ Candles Service запущен успешно!");
    }
}
