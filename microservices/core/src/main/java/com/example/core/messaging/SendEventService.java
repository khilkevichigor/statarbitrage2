package com.example.core.messaging;

import com.example.shared.events.*;
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

    public void sendCsvEvent(CsvEvent event) {
        send(event);
    }

    public void sendNotificationEvent(NotificationEvent event) {
        send(event);
    }

    public void sendCointegrationEvent(CointegrationEvent event) {
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