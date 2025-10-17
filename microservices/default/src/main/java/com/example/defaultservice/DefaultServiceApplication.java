package com.example.defaultservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import lombok.extern.slf4j.Slf4j;

@SpringBootApplication(scanBasePackages = {"com.example.defaultservice", "com.example.shared"})
@Slf4j
public class DefaultServiceApplication {
    
    public static void main(String[] args) {
        log.info("🚀 Запуск Default Service...");
        SpringApplication.run(DefaultServiceApplication.class, args);
        log.info("✅ Default Service успешно запущен!");
    }
}