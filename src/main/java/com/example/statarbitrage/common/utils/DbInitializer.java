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

                    .useMinZFilter(true)
                    .minZ(3.0)

                    .useMinPValueFilter(true)
                    .minPValue(0.01)

                    .useMinAdfValueFilter(true)
                    .minAdfValue(0.5)

                    .useMinCorrelationFilter(true)
                    .minCorrelation(0.8)

                    .useMinVolumeFilter(true)
                    .minVolume(1.0)

                    .useMinRSquaredFilter(true)
                    .minRSquared(0.8)

                    .checkInterval(1)

                    .maxPositionPercentPerPair(1.0) //для реальных сделок
                    .capitalLong(10.0)
                    .capitalShort(10.0)
                    .leverage(1.0)
                    .feePctPerTrade(0.05)

                    .useExitTake(true)
                    .exitTake(1.0)

                    .useExitStop(false)
                    .exitStop(-3.0)

                    .useExitZMin(false)
                    .exitZMin(0)

                    .useExitZMax(false)
                    .exitZMax(0.5)

                    .useExitZMaxPercent(false)
                    .exitZMaxPercent(0) //от 3.22 + 50% = 4.83

                    .useExitTimeHours(false)
                    .exitTimeHours(3)

                    .usePairs(1.0)

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
