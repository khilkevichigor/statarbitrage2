package com.example.changes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.example.changes", "com.example.shared"})
public class ChangesApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChangesApplication.class, args);
        System.out.println("📊 Changes Service запущен успешно!");
    }
}
