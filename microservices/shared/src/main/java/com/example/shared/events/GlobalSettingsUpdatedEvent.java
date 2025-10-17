package com.example.shared.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Событие обновления глобальных настроек таймфреймов и периодов
 * Публикуется при сохранении настроек на вкладке "Настройки"
 * Слушается всеми View для обновления выпадающих списков
 */
@Getter
public class GlobalSettingsUpdatedEvent extends ApplicationEvent {

    private final String updatedGlobalTimeframes;
    private final String updatedGlobalPeriods;

    public GlobalSettingsUpdatedEvent(Object source, String updatedGlobalTimeframes, String updatedGlobalPeriods) {
        super(source);
        this.updatedGlobalTimeframes = updatedGlobalTimeframes;
        this.updatedGlobalPeriods = updatedGlobalPeriods;
    }
}