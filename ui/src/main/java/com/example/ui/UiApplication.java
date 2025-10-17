package com.example.ui;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Главный класс микросервиса пользовательского интерфейса
 * Отвечает за веб-интерфейс и реал-тайм обновления
 */
@SpringBootApplication(scanBasePackages = {"com.example.ui", "com.example.shared"})
public class UiApplication {

    public static void main(String[] args) {
        SpringApplication.run(UiApplication.class, args);
        System.out.println("🌐 UI Service запущен успешно!");
    }
}