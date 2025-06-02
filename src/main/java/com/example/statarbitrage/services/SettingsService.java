package com.example.statarbitrage.services;

import com.example.statarbitrage.model.Settings;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SETTINGS_JSON_FILE_PATH = "settings.json";
    private static final long CHAT_ID = 159178617;

    @PostConstruct
    public Settings getSettings() {
        Map<Long, Settings> settings = loadSettings(SETTINGS_JSON_FILE_PATH);
        if (settings == null) {
            settings = new HashMap<>();
            Settings defaultSettings = getDefaultSettings();
            settings.put(CHAT_ID, defaultSettings);
            saveSettings(settings);
        }
        return settings.get(CHAT_ID);
    }

    private static Settings getDefaultSettings() {
        return Settings.builder()
                .timeframe("15m")
                .candleLimit(300)
                .significanceLevel(0.05)
                .adfSignificanceLevel(0.05)
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
        saveSettings(userSettings);
    }

    public void resetSettings(long chatId) {
        Settings defaultSettings = getDefaultSettings();
        Map<Long, Settings> userSettings = new HashMap<>();
        userSettings.put(chatId, defaultSettings);
        saveSettings(userSettings);
    }

    public void saveSettings(Map<Long, Settings> userSettings) {
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(SETTINGS_JSON_FILE_PATH), userSettings);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Map<Long, Settings> loadSettings(String settingsJsonFilePath) {
        File file = new File(settingsJsonFilePath);
        if (file.exists()) {
            try {
                return MAPPER.readValue(file, new TypeReference<>() {
                });
            } catch (IOException e) {
                log.error("Ошибка при получении settings.json: {}", e.getMessage(), e);
                throw new RuntimeException("Ошибка при получении settings.json");
            }
        }
        return null;
    }
}
