package com.example.core.services;

import com.example.core.messaging.SendEventService;
import com.example.core.repositories.SettingsRepository;
import com.example.shared.events.CoreEvent;
import com.example.shared.models.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsService {
    private final SettingsRepository settingsRepository;
    private final SendEventService sendEventService;

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
        settingsRepository.save(settings);
        sendEventService.sendCoreEvent(new CoreEvent(null, CoreEvent.Type.CLEAR_COINT_PAIRS)); //todo слать эвент на cointegration на очистку найденных пар тк настройки изменились! НЕ АКТУАЛЬНО после отправки эвента о найденных парах
    }
}