package com.example.core.services;

import com.example.shared.models.Settings;
import com.example.shared.models.Pair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateSettingsParamService {
    public void updateSettingsParam(Pair tradingPair, Settings settings) {
        if (tradingPair == null || settings == null) {
            log.warn("updateSettingsParam: pairData или settings равны null");
            return;
        }
        
        // Основные параметры торговли
        tradingPair.setSettingsCandleLimit(BigDecimal.valueOf(settings.getCandleLimit()));
        tradingPair.setSettingsMinZ(BigDecimal.valueOf(settings.getMinZ()));
        
        // Временные параметры
        if (settings.getTimeframe() != null) {
            tradingPair.setTimeframe(settings.getTimeframe());
        }
        
        // Параметры фильтрации (сохраняем в JSON или отдельные поля если есть)
        Map<String, Object> settingsMap = new HashMap<>();
        settingsMap.put("minWindowSize", settings.getMinWindowSize());
        settingsMap.put("maxPValue", settings.getMaxPValue());
        settingsMap.put("maxAdfValue", settings.getMaxAdfValue());
        settingsMap.put("minRSquared", settings.getMinRSquared());
        settingsMap.put("minCorrelation", settings.getMinCorrelation());
        settingsMap.put("minVolume", settings.getMinVolume());
        settingsMap.put("checkInterval", settings.getCheckInterval());
        settingsMap.put("maxLongMarginSize", settings.getMaxLongMarginSize());
        settingsMap.put("maxShortMarginSize", settings.getMaxShortMarginSize());
        settingsMap.put("leverage", settings.getLeverage());
        settingsMap.put("usePairs", settings.getUsePairs());
        settingsMap.put("autoTradingEnabled", settings.isAutoTradingEnabled());
        
        // Стратегии выхода
        settingsMap.put("exitTake", settings.getExitTake());
        settingsMap.put("exitStop", settings.getExitStop());
        settingsMap.put("exitZMin", settings.getExitZMin());
        settingsMap.put("exitZMax", settings.getExitZMax());
        settingsMap.put("exitZMaxPercent", settings.getExitZMaxPercent());
        settingsMap.put("exitTimeMinutes", settings.getExitTimeMinutes());
        settingsMap.put("exitBreakEvenPercent", settings.getExitBreakEvenPercent());
        settingsMap.put("exitNegativeZMinProfitPercent", settings.getExitNegativeZMinProfitPercent());
        
        // Флаги фильтров
        settingsMap.put("useMinZFilter", settings.isUseMinZFilter());
        settingsMap.put("useMinRSquaredFilter", settings.isUseMinRSquaredFilter());
        settingsMap.put("useMaxPValueFilter", settings.isUseMaxPValueFilter());
        settingsMap.put("useMaxAdfValueFilter", settings.isUseMaxAdfValueFilter());
        settingsMap.put("useMinCorrelationFilter", settings.isUseMinCorrelationFilter());
        settingsMap.put("useMinVolumeFilter", settings.isUseMinVolumeFilter());
        settingsMap.put("useCointegrationStabilityFilter", settings.isUseCointegrationStabilityFilter());
        
        // Флаги стратегий выхода
        settingsMap.put("useExitTake", settings.isUseExitTake());
        settingsMap.put("useExitStop", settings.isUseExitStop());
        settingsMap.put("useExitZMin", settings.isUseExitZMin());
        settingsMap.put("useExitZMax", settings.isUseExitZMax());
        settingsMap.put("useExitZMaxPercent", settings.isUseExitZMaxPercent());
        settingsMap.put("useExitTimeMinutes", settings.isUseExitTimeMinutes());
        settingsMap.put("useExitBreakEvenPercent", settings.isUseExitBreakEvenPercent());
        settingsMap.put("useExitNegativeZMinProfitPercent", settings.isUseExitNegativeZMinProfitPercent());
        
        // Система скоринга - флаги
        settingsMap.put("useZScoreScoring", settings.isUseZScoreScoring());
        settingsMap.put("usePixelSpreadScoring", settings.isUsePixelSpreadScoring());
        settingsMap.put("useCointegrationScoring", settings.isUseCointegrationScoring());
        settingsMap.put("useModelQualityScoring", settings.isUseModelQualityScoring());
        settingsMap.put("useStatisticsScoring", settings.isUseStatisticsScoring());
        settingsMap.put("useBonusScoring", settings.isUseBonusScoring());
        
        // Система скоринга - веса
        settingsMap.put("zScoreScoringWeight", settings.getZScoreScoringWeight());
        settingsMap.put("pixelSpreadScoringWeight", settings.getPixelSpreadScoringWeight());
        settingsMap.put("cointegrationScoringWeight", settings.getCointegrationScoringWeight());
        settingsMap.put("modelQualityScoringWeight", settings.getModelQualityScoringWeight());
        settingsMap.put("statisticsScoringWeight", settings.getStatisticsScoringWeight());
        settingsMap.put("bonusScoringWeight", settings.getBonusScoringWeight());
        
        // Усреднение позиций
        settingsMap.put("autoAveragingEnabled", settings.isAutoAveragingEnabled());
        settingsMap.put("averagingDrawdownThreshold", settings.getAveragingDrawdownThreshold());
        settingsMap.put("averagingVolumeMultiplier", settings.getAveragingVolumeMultiplier());
        settingsMap.put("averagingDrawdownMultiplier", settings.getAveragingDrawdownMultiplier());
        settingsMap.put("maxAveragingCount", settings.getMaxAveragingCount());
        
        // Дополнительные настройки
        settingsMap.put("autoVolumeEnabled", settings.isAutoVolumeEnabled());
        settingsMap.put("minimumLotBlacklist", settings.getMinimumLotBlacklist());
        settingsMap.put("observedPairs", settings.getObservedPairs());
        settingsMap.put("minIntersections", settings.getMinIntersections());
        settingsMap.put("useMinIntersections", settings.isUseMinIntersections());
        
        // Сохраняем все настройки в searchSettings JSON поле
        tradingPair.setSearchSettingsMap(settingsMap);
        
        log.debug("Обновлены ВСЕ настройки для пары {}: candleLimit={}, minZ={}, всего параметров={}", 
                tradingPair.getPairName(), settings.getCandleLimit(), settings.getMinZ(), settingsMap.size());
    }
}
