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
        log.info("📢 Публикуется событие обновления глобальных настроек:");
        log.info("📊 Новые активные таймфреймы: {}", updatedTimeframes);
        log.info("📅 Новые активные периоды: {}", updatedPeriods);

        GlobalSettingsUpdatedEvent event = new GlobalSettingsUpdatedEvent(
                this, updatedTimeframes, updatedPeriods);
        eventPublisher.publishEvent(event);

        log.info("✅ Событие GlobalSettingsUpdatedEvent опубликовано");
    }
}