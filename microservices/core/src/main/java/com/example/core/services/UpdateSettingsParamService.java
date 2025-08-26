package com.example.core.services;

import com.example.shared.models.Settings;
import com.example.shared.models.TradingPair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateSettingsParamService {
    public void updateSettingsParam(TradingPair tradingPair, Settings settings) {
        if (tradingPair == null || settings == null) {
            log.warn("updateSettingsParam: pairData или settings равны null");
            return;
        }
        tradingPair.setSettingsTimeframe(settings.getTimeframe());
        tradingPair.setSettingsCandleLimit(settings.getCandleLimit());
        tradingPair.setSettingsMinZ(settings.getMinZ());
        tradingPair.setSettingsMinWindowSize(settings.getMinWindowSize());
        tradingPair.setSettingsMinPValue(settings.getMaxPValue());
        tradingPair.setSettingsMaxAdfValue(settings.getMaxAdfValue());
        tradingPair.setSettingsMinRSquared(settings.getMinRSquared());
        tradingPair.setSettingsMinCorrelation(settings.getMinCorrelation());
        tradingPair.setSettingsMinVolume(settings.getMinVolume());
        tradingPair.setSettingsCheckInterval(settings.getCheckInterval());
        tradingPair.setSettingsMaxLongMarginSize(settings.getMaxLongMarginSize());
        tradingPair.setSettingsMaxShortMarginSize(settings.getMaxShortMarginSize());
        tradingPair.setSettingsLeverage(settings.getLeverage());
        tradingPair.setSettingsExitTake(settings.getExitTake());
        tradingPair.setSettingsExitStop(settings.getExitStop());
        tradingPair.setSettingsExitZMin(settings.getExitZMin());
        tradingPair.setSettingsExitZMax(settings.getExitZMax());
        tradingPair.setSettingsExitZMaxPercent(settings.getExitZMaxPercent());
        tradingPair.setSettingsExitTimeMinutes(settings.getExitTimeMinutes());
        tradingPair.setSettingsExitBreakEvenPercent(settings.getExitBreakEvenPercent());
        tradingPair.setSettingsUsePairs(settings.getUsePairs());
        tradingPair.setSettingsAutoTradingEnabled(settings.isAutoTradingEnabled());
        tradingPair.setSettingsUseMinZFilter(settings.isUseMinZFilter());
        tradingPair.setSettingsUseMinRSquaredFilter(settings.isUseMinRSquaredFilter());
        tradingPair.setSettingsUseMinPValueFilter(settings.isUseMaxPValueFilter());
        tradingPair.setSettingsUseMaxAdfValueFilter(settings.isUseMaxAdfValueFilter());
        tradingPair.setSettingsUseMinCorrelationFilter(settings.isUseMinCorrelationFilter());
        tradingPair.setSettingsUseMinVolumeFilter(settings.isUseMinVolumeFilter());
        tradingPair.setSettingsUseExitTake(settings.isUseExitTake());
        tradingPair.setSettingsUseExitStop(settings.isUseExitStop());
        tradingPair.setSettingsUseExitZMin(settings.isUseExitZMin());
        tradingPair.setSettingsUseExitZMax(settings.isUseExitZMax());
        tradingPair.setSettingsUseExitZMaxPercent(settings.isUseExitZMaxPercent());
        tradingPair.setSettingsUseExitTimeHours(settings.isUseExitTimeMinutes());
        tradingPair.setSettingsUseExitBreakEvenPercent(settings.isUseExitBreakEvenPercent());
        tradingPair.setSettingsExitNegativeZMinProfitPercent(settings.getExitNegativeZMinProfitPercent());
        tradingPair.setSettingsUseExitNegativeZMinProfitPercent(settings.isUseExitNegativeZMinProfitPercent());
        tradingPair.setSettingsMinimumLotBlacklist(settings.getMinimumLotBlacklist());
        tradingPair.setUseZScoreScoring(settings.isUseZScoreScoring());
        tradingPair.setZScoreScoringWeight(settings.getZScoreScoringWeight());
        tradingPair.setUsePixelSpreadScoring(settings.isUsePixelSpreadScoring());
        tradingPair.setPixelSpreadScoringWeight(settings.getPixelSpreadScoringWeight());
        tradingPair.setUseCointegrationScoring(settings.isUseCointegrationScoring());
        tradingPair.setCointegrationScoringWeight(settings.getCointegrationScoringWeight());
        tradingPair.setUseModelQualityScoring(settings.isUseModelQualityScoring());
        tradingPair.setModelQualityScoringWeight(settings.getModelQualityScoringWeight());
        tradingPair.setUseStatisticsScoring(settings.isUseStatisticsScoring());
        tradingPair.setStatisticsScoringWeight(settings.getStatisticsScoringWeight());
        tradingPair.setUseBonusScoring(settings.isUseBonusScoring());
        tradingPair.setBonusScoringWeight(settings.getBonusScoringWeight());
    }
}
