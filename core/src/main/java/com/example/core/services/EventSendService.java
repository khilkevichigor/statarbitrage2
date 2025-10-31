package com.example.core.services;

import com.example.shared.events.UpdateUiEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventSendService {
    private final ApplicationEventPublisher applicationEventPublisher;

    public void updateUI(UpdateUiEvent event) {
        try {
            log.debug("📡 EventSendService: Отправляем событие UpdateUiEvent через ApplicationEventPublisher");
            log.debug("📡 EventSendService: Событие объект: {}", event);
            log.debug("📡 EventSendService: Thread: {}", Thread.currentThread().getName());
            applicationEventPublisher.publishEvent(event);
            log.debug("✅ EventSendService: Событие UpdateUiEvent успешно отправлено");
        } catch (Exception e) {
            log.error("❌ EventSendService: Ошибка при отправке события UpdateUiEvent: {}", e.getMessage(), e);
            throw e;
        }
    }
}