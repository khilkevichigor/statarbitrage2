package com.example.chart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.example.chart", "com.example.shared"})
public class ChartApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChartApplication.class, args);
        System.out.println("📈 Chart Service запущен успешно!");
    }
}
