package com.example.commas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.example.commas", "com.example.shared"})
public class CommasApplication {
    public static void main(String[] args) {
        SpringApplication.run(CommasApplication.class, args);
        System.out.println("🔗 3Commas Service запущен успешно!");
    }
}
