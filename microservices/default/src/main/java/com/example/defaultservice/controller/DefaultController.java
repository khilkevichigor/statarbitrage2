package com.example.defaultservice.controller;

import com.example.shared.events.BaseEvent;
import com.example.shared.utils.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/default")
@RequiredArgsConstructor
@Slf4j
public class DefaultController {
    
    private final EventPublisher eventPublisher;
    
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "UP",
            "service", "default-service",
            "timestamp", LocalDateTime.now()
        );
    }
    
    @PostMapping("/test-event")
    public Map<String, String> testEvent() {
        // Пример отправки события
        BaseEvent event = new BaseEvent("TEST_EVENT") {};
        eventPublisher.publish("test-events-out-0", event);
        
        return Map.of("message", "Тестовое событие отправлено");
    }
}