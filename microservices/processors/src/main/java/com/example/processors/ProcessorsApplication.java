package com.example.processors;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.example.processors", "com.example.shared"})
public class ProcessorsApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProcessorsApplication.class, args);
        System.out.println("⚙️ Processors Service запущен успешно!");
    }
}
