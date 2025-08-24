package com.example.defaultservice.service;

import com.example.shared.events.BaseEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

@Service
@Slf4j
public class DefaultService {
    
    @Bean
    public Consumer<BaseEvent> defaultEventsConsumer() {
        return event -> {
            log.info("📨 Получено событие: {}", event.getEventType());
            processEvent(event);
        };
    }
    
    private void processEvent(BaseEvent event) {
        // Здесь обработка входящих событий
        log.info("⚙️ Обработка события {} от {}", event.getEventId(), event.getTimestamp());
        
        // Добавить свою логику здесь
    }
    
    public void doSomething() {
        log.info("🔄 Выполнение основной логики сервиса");
        // Реализация бизнес-логики
    }
}