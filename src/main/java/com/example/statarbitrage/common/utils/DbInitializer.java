package com.example.statarbitrage.common.utils;

import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.core.repositories.SettingsRepository;
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
                    .timeframe("15m")

                    .candleLimit(300)
                    .minWindowSize(100)
                    .minZ(3.0)
                    .minPValue(0.001)
                    .minAdfValue(0.1)
                    .minCorrelation(0.95)
                    .minVolume(1.0)
                    .minRSquared(0.8)

                    .checkInterval(1)

                    .capitalLong(10.0)
                    .capitalShort(10.0)
                    .leverage(10.0)
                    .feePctPerTrade(0.05)

                    .exitTake(1.0)
                    .exitStop(0.0)
                    .exitZMin(-3.0)
                    .exitZMax(3.5)
                    .exitZMaxPercent(0.0) //от 3.22 + 50% = 4.83
                    .exitTimeHours(8)

                    .usePairs(10.0)

                    .build()
            );
        }
    }

}

/*
Норм настройки
Settings.builder()
                    .timeframe("15m")

                    .candleLimit(300)
                    .minWindowSize(100)
                    .minZ(3.0)
                    .minPValue(0.001)
                    .minAdfValue(0.1)
                    .minCorrelation(0.95)
                    .minVolume(1.0)
                    .minRSquared(0.8)

                    .checkInterval(1)

                    .capitalLong(10.0)
                    .capitalShort(10.0)
                    .leverage(10.0)
                    .feePctPerTrade(0.05)

                    .exitTake(1.0)
                    .exitStop(0.0)
                    .exitZMin(-3.0)
                    .exitZMax(3.5)
                    .exitZMaxPercent(0.0) //от 3.22 + 50% = 4.83
                    .exitTimeHours(8)

                    .usePairs(10.0)

                    .build()
 */
