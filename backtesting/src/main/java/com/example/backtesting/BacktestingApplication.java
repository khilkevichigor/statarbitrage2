package com.example.backtesting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.example.backtesting", "com.example.shared"})
public class BacktestingApplication {
    public static void main(String[] args) {
        SpringApplication.run(BacktestingApplication.class, args);
        System.out.println("📊 Backtesting Service запущен успешно!");
    }
}
