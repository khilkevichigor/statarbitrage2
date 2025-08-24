package com.example.analytics.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * REST контроллер для Analytics Service
 */
@Slf4j
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("service", "Analytics Service");
        response.put("status", "UP");
        response.put("timestamp", LocalDateTime.now());
        response.put("description", "Микросервис аналитики и статистических расчетов");
        
        log.info("Health check запрошен для Analytics Service");
        return ResponseEntity.ok(response);
    }

    /**
     * Информация о сервисе
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        Map<String, Object> response = new HashMap<>();
        response.put("name", "Analytics Service");
        response.put("version", "1.0.0");
        response.put("description", "Микросервис для аналитики и статистических расчетов торговых данных");
        response.put("capabilities", new String[]{
            "statistical_analysis",
            "correlation_calculation", 
            "z_score_analysis",
            "performance_metrics"
        });
        
        return ResponseEntity.ok(response);
    }
}