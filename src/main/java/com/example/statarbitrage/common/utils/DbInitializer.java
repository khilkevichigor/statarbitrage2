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

                    // Фильтры значений
                    .useMinZFilter(true)
                    .minZ(3.5)

                    .useMaxPValueFilter(true)
                    .maxPValue(0.01)

                    .useMaxAdfValueFilter(true)
                    .maxAdfValue(0.8)

                    .useMinCorrelationFilter(true)
                    .minCorrelation(0.8)

                    .useMinVolumeFilter(true)
                    .minVolume(1.0)

                    .useMinRSquaredFilter(true)
                    .minRSquared(0.8)

                    .useCointegrationStabilityFilter(true)

                    .checkInterval(1)

                    // Маржа и плечо
                    .maxLongMarginSize(5.0)
                    .maxShortMarginSize(5.0)
                    .leverage(5.0)

                    // Стратегии выхода
                    .useExitTake(true)
                    .exitTake(5.0)

                    .useExitStop(true)
                    .exitStop(-5.0)

                    .useExitZMin(false)
                    .exitZMin(0)

                    .useExitZMax(false)
                    .exitZMax(0.5)

                    .useExitZMaxPercent(false)
                    .exitZMaxPercent(0) //от 3.22 + 50% = 4.83

                    .useExitTimeMinutes(false)
                    .exitTimeMinutes(60)

                    .useExitBreakEvenPercent(true)
                    .exitBreakEvenPercent(1)

                    .useExitNegativeZMinProfitPercent(true)
                    .exitNegativeZMinProfitPercent(0.5)

                    .usePairs(1.0)

                    // Автоматическая торговля
                    .autoTradingEnabled(false)

                    // Черные списки и наблюдаемые пары  
                    .minimumLotBlacklist("ETH-USDT-SWAP,BTC-USDT-SWAP")
                    .observedPairs("")

                    // Флаги скоринга
                    .useZScoreScoring(true)
                    .usePixelSpreadScoring(true)
                    .useCointegrationScoring(true)
                    .useModelQualityScoring(true)
                    .useStatisticsScoring(true)
                    .useBonusScoring(true)

                    // Веса скоринга
                    .zScoreScoringWeight(40.0)
                    .pixelSpreadScoringWeight(25.0)
                    .cointegrationScoringWeight(25.0)
                    .modelQualityScoringWeight(20.0)
                    .statisticsScoringWeight(10.0)
                    .bonusScoringWeight(5.0)

                    .build()
            );
            log.info("🔧 Создана запись настроек по умолчанию");
        }
    }
}
