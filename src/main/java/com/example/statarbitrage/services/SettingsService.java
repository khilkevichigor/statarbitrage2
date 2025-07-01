package com.example.statarbitrage.services;

import com.example.statarbitrage.model.Settings;
import com.example.statarbitrage.repositories.SettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsService {
    private final SettingsRepository settingsRepository;

    public Settings getSettingsFromDb() {
        return settingsRepository.findAll().get(0);
    }

    public void saveSettingsInDb(Settings settings) {
        settingsRepository.save(settings);
    }
}