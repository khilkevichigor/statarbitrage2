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
                    .minAdfValue(1.0)

                    .useMinCorrelationFilter(true)
                    .minCorrelation(0.8)

                    .useMinVolumeFilter(true)
                    .minVolume(1.0)

                    .useMinRSquaredFilter(true)
                    .minRSquared(0.8)

                    .checkInterval(1)

                    .maxPositionPercentPerPair(1.0) //для реальных сделок
                    .maxShortMarginSize(100.0)
                    .maxLongMarginSize(100.0)
                    .leverage(10.0)
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

                    .usePairs(10.0)

                    .build()
            );
        }
    }
}