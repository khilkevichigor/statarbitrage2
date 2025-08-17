package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateSettingsParamService {
    public void updateSettingsParam(PairData pairData, Settings settings) {
        if (pairData == null || settings == null) {
            log.warn("updateSettingsParam: pairData или settings равны null");
            return;
        }
        pairData.setSettingsTimeframe(settings.getTimeframe());
        pairData.setSettingsCandleLimit(settings.getCandleLimit());
        pairData.setSettingsMinZ(settings.getMinZ());
        pairData.setSettingsMinWindowSize(settings.getMinWindowSize());
        pairData.setSettingsMinPValue(settings.getMaxPValue());
        pairData.setSettingsMaxAdfValue(settings.getMaxAdfValue());
        pairData.setSettingsMinRSquared(settings.getMinRSquared());
        pairData.setSettingsMinCorrelation(settings.getMinCorrelation());
        pairData.setSettingsMinVolume(settings.getMinVolume());
        pairData.setSettingsCheckInterval(settings.getCheckInterval());
        pairData.setSettingsMaxLongMarginSize(settings.getMaxLongMarginSize());
        pairData.setSettingsMaxShortMarginSize(settings.getMaxShortMarginSize());
        pairData.setSettingsLeverage(settings.getLeverage());
        pairData.setSettingsExitTake(settings.getExitTake());
        pairData.setSettingsExitStop(settings.getExitStop());
        pairData.setSettingsExitZMin(settings.getExitZMin());
        pairData.setSettingsExitZMax(settings.getExitZMax());
        pairData.setSettingsExitZMaxPercent(settings.getExitZMaxPercent());
        pairData.setSettingsExitTimeMinutes(settings.getExitTimeMinutes());
        pairData.setSettingsExitBreakEvenPercent(settings.getExitBreakEvenPercent());
        pairData.setSettingsUsePairs(settings.getUsePairs());
        pairData.setSettingsAutoTradingEnabled(settings.isAutoTradingEnabled());
        pairData.setSettingsUseMinZFilter(settings.isUseMinZFilter());
        pairData.setSettingsUseMinRSquaredFilter(settings.isUseMinRSquaredFilter());
        pairData.setSettingsUseMinPValueFilter(settings.isUseMaxPValueFilter());
        pairData.setSettingsUseMaxAdfValueFilter(settings.isUseMaxAdfValueFilter());
        pairData.setSettingsUseMinCorrelationFilter(settings.isUseMinCorrelationFilter());
        pairData.setSettingsUseMinVolumeFilter(settings.isUseMinVolumeFilter());
        pairData.setSettingsUseExitTake(settings.isUseExitTake());
        pairData.setSettingsUseExitStop(settings.isUseExitStop());
        pairData.setSettingsUseExitZMin(settings.isUseExitZMin());
        pairData.setSettingsUseExitZMax(settings.isUseExitZMax());
        pairData.setSettingsUseExitZMaxPercent(settings.isUseExitZMaxPercent());
        pairData.setSettingsUseExitTimeHours(settings.isUseExitTimeMinutes());
        pairData.setSettingsUseExitBreakEvenPercent(settings.isUseExitBreakEvenPercent());
        pairData.setSettingsExitNegativeZMinProfitPercent(settings.getExitNegativeZMinProfitPercent());
        pairData.setSettingsUseExitNegativeZMinProfitPercent(settings.isUseExitNegativeZMinProfitPercent());
        pairData.setSettingsMinimumLotBlacklist(settings.getMinimumLotBlacklist());
        pairData.setUseZScoreScoring(settings.isUseZScoreScoring());
        pairData.setZScoreScoringWeight(settings.getZScoreScoringWeight());
        pairData.setUsePixelSpreadScoring(settings.isUsePixelSpreadScoring());
        pairData.setPixelSpreadScoringWeight(settings.getPixelSpreadScoringWeight());
        pairData.setUseCointegrationScoring(settings.isUseCointegrationScoring());
        pairData.setCointegrationScoringWeight(settings.getCointegrationScoringWeight());
        pairData.setUseModelQualityScoring(settings.isUseModelQualityScoring());
        pairData.setModelQualityScoringWeight(settings.getModelQualityScoringWeight());
        pairData.setUseStatisticsScoring(settings.isUseStatisticsScoring());
        pairData.setStatisticsScoringWeight(settings.getStatisticsScoringWeight());
        pairData.setUseBonusScoring(settings.isUseBonusScoring());
        pairData.setBonusScoringWeight(settings.getBonusScoringWeight());
    }
}
