package com.example.shared.utils;

import com.example.shared.events.BaseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class EventPublisher {
    private final StreamBridge streamBridge;

    public void publish(String bindingName, BaseEvent event) {
        try {
            log.info("📤 Отправка события {} в канал {}", event.getEventType(), bindingName);

            // Реальная отправка через Spring Cloud Stream
            streamBridge.send(bindingName, event);

            log.info("✅ Событие {} успешно отправлено", event.getEventId());

        } catch (Exception e) {
            log.error("❌ Ошибка отправки события {} в канал {}: {}", event.getEventId(), bindingName, e.getMessage(), e);
        }
    }
}