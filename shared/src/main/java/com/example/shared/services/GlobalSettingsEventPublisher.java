package com.example.shared.services;

import com.example.shared.events.GlobalSettingsUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Сервис для публикации событий обновления глобальных настроек
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalSettingsEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    /**
     * Публикует событие обновления глобальных настроек таймфреймов и периодов
     */
    public void publishGlobalSettingsUpdated(String updatedTimeframes, String updatedPeriods) {
        log.debug("📢 Публикуется событие обновления глобальных настроек:");
        log.debug("📊 Новые активные таймфреймы: {}", updatedTimeframes);
        log.debug("📅 Новые активные периоды: {}", updatedPeriods);

        GlobalSettingsUpdatedEvent event = new GlobalSettingsUpdatedEvent(
                this, updatedTimeframes, updatedPeriods);
        eventPublisher.publishEvent(event);

        log.debug("✅ Событие GlobalSettingsUpdatedEvent опубликовано");
    }
}