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
                    .timeframe("1m")

                    .candleLimit(300)
                    .minWindowSize(20)
                    .minZ(0.5)
                    .minPvalue(0.1)
                    .minAdfValue(0.1)
                    .minCorrelation(0.5)
                    .minVolume(1.0)

                    .checkInterval(1)

                    .capitalLong(10.0)
                    .capitalShort(10.0)
                    .leverage(10.0)
                    .feePctPerTrade(0.05)

                    .exitTake(1.0)
                    .exitStop(0.0)
                    .exitZMin(0.0)
                    .exitZMaxPercent(0.0) //от 3.22 + 50% = 4.83
                    .exitTimeHours(1)

                    .usePairs(1.0)

                    .build()
            );
        }
    }

}
