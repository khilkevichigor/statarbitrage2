package com.example.statarbitrage.utils;

import com.example.statarbitrage.model.Settings;
import com.example.statarbitrage.repositories.SettingsRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DbInitializer {
    private final SettingsRepository settingsRepository;

    @PostConstruct
    public void initialize() {
        List<Settings> all = settingsRepository.findAll();
        if (all.isEmpty()) {
            settingsRepository.save(Settings.builder()
                    .timeframe("5m")
                    .candleLimit(300)
                    .windowSize(250)
                    .significanceLevel(0.01)
                    .adfSignificanceLevel(0.01)
                    .checkInterval(1)
                    .capitalLong(10.0)
                    .capitalShort(10.0)
                    .leverage(10.0)
                    .feePctPerTrade(0.05)
                    .exitTake(4.0)
                    .exitStop(-2.0)
                    .exitZMin(0.5)
                    .exitZMaxPercent(50.0) //от 3.22 + 50% = 4.83
                    .exitTimeHours(8)
                    .minCorrelation(0.8)
                    .minVolume(10.0)
                    .build()
            );
        }
    }

}
