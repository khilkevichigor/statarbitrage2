package com.example.core.services;

import com.example.core.repositories.SettingsRepository;
import com.example.shared.events.CointegrationEvent;
import com.example.shared.models.Settings;
import com.example.shared.utils.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsService {
    private final SettingsRepository settingsRepository;
    private final EventPublisher eventPublisher;

    public Settings getSettings() {
        List<Settings> allSettings = settingsRepository.findAll();
        if (allSettings.isEmpty()) {
            log.warn("⚠️ Настройки не найдены в базе данных. Возвращаем временные дефолтные настройки.");
            return createTempDefaultSettings();
        }
        return allSettings.get(0);
    }

    private Settings createTempDefaultSettings() {
        return new Settings();
    }

    public void save(Settings settings) {
        settingsRepository.save(settings); //todo слать эвент на cointegration на очистку найденных пар тк настройки изменились!
        sentEvent(new CointegrationEvent("CLEAR_TABLE"));
    }

    private void sentEvent(CointegrationEvent event) {
        log.debug("Отправка сообщения в cointegration {}", event.toString());
        try {
            eventPublisher.publish("cointegration-events-out-0", event);
        } catch (Exception e) {
            log.error("Ошибка отправки события в cointegration {}", e.getMessage(), e);
        }
    }
}