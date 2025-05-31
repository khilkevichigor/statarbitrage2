package com.example.statarbitrage.services;

import com.example.statarbitrage.model.Settings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class SettingsService {
    @Autowired
    private FileService fileService;

    public Settings getSettings(long chatId) {
        Map<Long, Settings> settings = fileService.loadSettings();
        if (!settings.containsKey(chatId)) {
            Settings defaultSettings = getDefaultSettings();
            settings.put(chatId, defaultSettings);
            fileService.saveSettings(settings);
        }
        return settings.get(chatId);
    }

    private static Settings getDefaultSettings() {
        return Settings.builder()
                .timeframe("15m")
                .candleLimit(100)
                .significanceLevel(0.05)
                .zscoreEntry(2.0)
                .zscoreExit(0.5)
                .windowSize(20)
                .checkInterval(1)
                .capitalLong(1000.0)
                .capitalShort(1000.0)
                .leverage(10.0)
                .feePctPerTrade(0.05)
                .build();
    }

    public void updateAllSettings(long chatId, Settings newSettings) {
        Map<Long, Settings> userSettings = new HashMap<>();
        userSettings.put(chatId, newSettings);
        fileService.saveSettings(userSettings);
    }

    public void resetSettings(long chatId) {
        Settings defaultSettings = getDefaultSettings();
        Map<Long, Settings> userSettings = new HashMap<>();
        userSettings.put(chatId, defaultSettings);
        fileService.saveSettings(userSettings);
    }
}
