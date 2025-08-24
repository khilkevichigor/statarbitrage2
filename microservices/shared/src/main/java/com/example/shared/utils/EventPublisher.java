package com.example.shared.utils;

import com.example.shared.events.BaseEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class EventPublisher {
    
    public void publish(String bindingName, BaseEvent event) {
        try {
            log.info("📤 Отправка события {} в канал {}", event.getEventType(), bindingName);
            log.info("✅ Событие {} готово к отправке", event.getEventId());
            
            // Простая реализация для разработки
            // В продакшене здесь будет интеграция с RabbitMQ
            
        } catch (Exception e) {
            log.error("❌ Ошибка отправки события {} в канал {}: {}", event.getEventId(), bindingName, e.getMessage(), e);
        }
    }
}