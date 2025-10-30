package com.example.core.messaging;

import com.example.shared.events.rabbit.BaseEvent;
import com.example.shared.events.rabbit.CoreEvent;
import com.example.shared.utils.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SendEventService {
    private final EventPublisher eventPublisher;

    public void sendCoreEvent(CoreEvent event) {
        send(event);
    }

    private void send(BaseEvent event) {
        log.info("Отправка события {}", event.getEventId());
        try {
            eventPublisher.publish(event.getBindingName(), event);
        } catch (Exception e) {
            log.error("Ошибка отправки события {}", e.getMessage(), e);
        }
    }
}