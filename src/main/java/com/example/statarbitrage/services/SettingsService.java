package com.example.statarbitrage.services;

import com.example.statarbitrage.model.Settings;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class SettingsService {
    private static final String SETTINGS_FILE = "settings.json";
    private final ObjectMapper mapper = new ObjectMapper();
    private Map<Long, Settings> userSettings = new HashMap<>();

    public SettingsService() {
        loadSettings();
    }

    public Settings getSettings(long chatId) {
        if (!userSettings.containsKey(chatId)) {
            Settings defaultSettings = getDefaultSettings();
            userSettings.put(chatId, defaultSettings);
            saveSettings();
        }
        return userSettings.get(chatId);
    }

    private static Settings getDefaultSettings() {
        return Settings.builder()
                .candleLimit(300)
                .depo(1000)
                .maxPairs(1000)
                .maxWorkers(5)
                .positionSize(500)
                .timeframe("15m")
                .significanceLevel(0.05)
                .zscoreEntry(2.0)
                .zscoreExit(0.5)
                .windowSize(20)
                .checkInterval(1)
                .build();
    }

    public void updateAllSettings(long chatId, Settings newSettings) {
        userSettings.put(chatId, newSettings);
        saveSettings();
    }

    private void loadSettings() {
        File file = new File(SETTINGS_FILE);
        if (file.exists()) {
            try {
                userSettings = mapper.readValue(file, new TypeReference<>() {
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void saveSettings() {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(SETTINGS_FILE), userSettings);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void resetSettings(long chatId) {
        Settings defaultSettings = getDefaultSettings(); // с дефолтными значениями
        userSettings.put(chatId, defaultSettings);
        saveSettings();
    }

}
