package com.example.database;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Главный класс микросервиса базы данных
 * Отвечает за управление данными и персистентность
 */
@SpringBootApplication(scanBasePackages = {"com.example.database", "com.example.shared"})
public class DatabaseApplication {

    public static void main(String[] args) {
        SpringApplication.run(DatabaseApplication.class, args);
        System.out.println("🗄️ Database Service запущен успешно!");
    }
}