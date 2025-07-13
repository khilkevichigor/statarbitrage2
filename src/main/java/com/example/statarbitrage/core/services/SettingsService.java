package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.core.repositories.SettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsService {
    private final SettingsRepository settingsRepository;

    public Settings getSettings() {
        List<Settings> allSettings = settingsRepository.findAll();
        if (allSettings.isEmpty()) {
            log.warn("⚠️ Настройки не найдены в базе данных. Возвращаем временные дефолтные настройки.");
            return createTempDefaultSettings();
        }
        return allSettings.get(0);
    }

    private Settings createTempDefaultSettings() {
        Settings settings = new Settings();
        settings.setMaxPositionLong(10.0);
        settings.setMaxPositionShort(10.0);
        settings.setLeverage(1.0);
        settings.setFeePctPerTrade(0.05);
        settings.setMaxPositionPercentPerPair(1.0);
        return settings;
    }

    public void save(Settings settings) {
        settingsRepository.save(settings);
    }
}