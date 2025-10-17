package com.example.python;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.example.python", "com.example.shared"})
public class PythonApplication {
    public static void main(String[] args) {
        SpringApplication.run(PythonApplication.class, args);
        System.out.println("🐍 Python Service запущен успешно!");
    }
}
