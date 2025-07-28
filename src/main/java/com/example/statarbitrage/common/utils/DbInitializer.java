package com.example.statarbitrage.common.utils;

import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.core.repositories.SettingsRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Order(1)
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
                    .minZ(3.5)

                    .useMinPValueFilter(true)
                    .minPValue(0.01)

                    .useMinAdfValueFilter(true)
                    .minAdfValue(0.8)

                    .useMinCorrelationFilter(true)
                    .minCorrelation(0.8)

                    .useMinVolumeFilter(true)
                    .minVolume(1.0)

                    .useMinRSquaredFilter(true)
                    .minRSquared(0.8)

                    .checkInterval(1)

                    .maxLongMarginSize(5.0)
                    .maxShortMarginSize(5.0)
                    .leverage(5.0)

                    .initialVirtualBalance(10_000.0)
                    .virtualFeePctPerTrade(0.05)

                    .useExitTake(true)
                    .exitTake(10.0)

                    .useExitStop(true)
                    .exitStop(-10.0)

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
