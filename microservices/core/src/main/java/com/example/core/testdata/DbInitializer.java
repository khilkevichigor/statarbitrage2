package com.example.core.testdata;

import com.example.core.repositories.SettingsRepository;
import com.example.core.services.TradingPairService;
import com.example.shared.models.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Order(1)
public class DbInitializer {
    private final SettingsRepository settingsRepository;
    private final TradingPairService tradingPairService;

    @EventListener(ApplicationReadyEvent.class)
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
                    .maxPValue(0.1)

                    .useMaxAdfValueFilter(true)
                    .maxAdfValue(0.1)

                    .useMinCorrelationFilter(true)
                    .minCorrelation(0.8)

                    .useMinVolumeFilter(true)
                    .minVolume(15.0)

                    .useMinRSquaredFilter(true)
                    .minRSquared(0.8)

                    .useCointegrationStabilityFilter(true)

                    .checkInterval(1)

                    // Маржа и плечо
                    .maxLongMarginSize(15.0)
                    .maxShortMarginSize(15.0)
                    .leverage(2.0)

                    // Стратегии выхода
                    .useExitTake(true)
                    .exitTake(2.0)

                    .useExitStop(false)
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

                    .useExitNegativeZMinProfitPercent(false)
                    .exitNegativeZMinProfitPercent(0.5)

                    .usePairs(1.0)

                    // Автоматическая торговля
                    .autoTradingEnabled(false)

                    // Черные списки и наблюдаемые пары  
                    .minimumLotBlacklist("")
                    .observedPairs("")

                    // Флаги скоринга
                    .useZScoreScoring(true)
                    .usePixelSpreadScoring(true)
                    .useCointegrationScoring(true)
                    .useModelQualityScoring(true)
                    .useStatisticsScoring(true)
                    .useBonusScoring(true)

                    // Веса скоринга
                    .zScoreScoringWeight(5.0)
                    .pixelSpreadScoringWeight(5.0)
                    .cointegrationScoringWeight(5.0)
                    .modelQualityScoringWeight(5.0)
                    .statisticsScoringWeight(5.0)
                    .bonusScoringWeight(5.0)

                    .autoAveragingEnabled(false)
                    .averagingDrawdownThreshold(10.0)
                    .averagingVolumeMultiplier(1.5)

                    .build()
            );
            log.info("🔧 Создана запись настроек по умолчанию");
        }

//        tradingPairService.save(TradingPair.builder()
//                .uuid(UUID.randomUUID())
//                .pairName("test")
//                .build());
    }
}
