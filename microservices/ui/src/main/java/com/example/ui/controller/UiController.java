package com.example.ui.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * REST контроллер для UI Service
 */
@Slf4j
@RestController
@RequestMapping("/api/ui")
public class UiController {

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("service", "UI Service");
        response.put("status", "UP");
        response.put("timestamp", LocalDateTime.now());
        response.put("description", "Микросервис пользовательского интерфейса");
        
        log.info("Health check запрошен для UI Service");
        return ResponseEntity.ok(response);
    }

    /**
     * Информация о сервисе
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        Map<String, Object> response = new HashMap<>();
        response.put("name", "UI Service");
        response.put("version", "1.0.0");
        response.put("description", "Микросервис пользовательского интерфейса");
        response.put("capabilities", new String[]{
            "real_time_updates",
            "trading_dashboard", 
            "charts_visualization",
            "portfolio_management"
        });
        
        return ResponseEntity.ok(response);
    }
}