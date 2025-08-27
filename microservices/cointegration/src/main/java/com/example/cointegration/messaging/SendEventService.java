package com.example.cointegration.messaging;

import com.example.shared.events.BaseEvent;
import com.example.shared.events.CoreEvent;
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
        log.debug("Отправка события {}", event.toString());
        try {
            eventPublisher.publish(event.getBindingName(), event);
        } catch (Exception e) {
            log.error("Ошибка отправки события {}", e.getMessage(), e);
        }
    }
}