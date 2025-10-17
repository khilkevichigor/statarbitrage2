package com.example.csv.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * REST контроллер для CSV Export Service
 */
@Slf4j
@RestController
@RequestMapping("/api/csv")
public class CsvController {

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("service", "CSV Export Service");
        response.put("status", "UP");
        response.put("timestamp", LocalDateTime.now());
        response.put("description", "Микросервис экспорта данных в CSV формат");
        
        log.info("Health check запрошен для CSV Export Service");
        return ResponseEntity.ok(response);
    }

    /**
     * Информация о сервисе
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        Map<String, Object> response = new HashMap<>();
        response.put("name", "CSV Export Service");
        response.put("version", "1.0.0");
        response.put("description", "Микросервис для экспорта торговых данных в CSV формат");
        response.put("capabilities", new String[]{
            "trade_export",
            "portfolio_export", 
            "analytics_export",
            "custom_reports"
        });
        
        return ResponseEntity.ok(response);
    }
}