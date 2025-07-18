package com.example.statarbitrage.core.processors;

import com.example.statarbitrage.common.dto.Candle;
import com.example.statarbitrage.common.dto.ZScoreData;
import com.example.statarbitrage.common.dto.ZScoreParam;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.common.model.TradeStatus;
import com.example.statarbitrage.core.services.*;
import com.example.statarbitrage.trading.model.ArbitragePairTradeInfo;
import com.example.statarbitrage.trading.model.PositionVerificationResult;
import com.example.statarbitrage.trading.services.TradingIntegrationService;
import com.example.statarbitrage.ui.dto.UpdateTradeRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateTradeProcessor {
    private final PairDataService pairDataService;
    private final CandlesService candlesService;
    private final SettingsService settingsService;
    private final TradeLogService tradeLogService;
    private final ZScoreService zScoreService;
    private final TradingIntegrationService tradingIntegrationService;
    private final ExitStrategyService exitStrategyService;
    private final ProfitUpdateService profitUpdateService;

    @Transactional
    public PairData updateTrade(UpdateTradeRequest request) {
        validateRequest(request);

        PairData pairData = loadFreshPairData(request.getPairData());
        if (pairData == null) {
            return request.getPairData();
        }

        Settings settings = settingsService.getSettings();
        if (!tradingIntegrationService.hasOpenPositions(pairData)) {
            return handleNoOpenPositions(pairData, settings);
        }

        ZScoreData zScoreData = calculateZScoreData(pairData, settings);
        logPairInfo(zScoreData);

        updateZScoreDataCurrent(pairData, zScoreData);

        if (request.isCloseManually()) {
            return handleManualClose(pairData, settings);
        }

        // 🎯 КРИТИЧНО: Обновляем профит ДО проверки exit strategy для актуального принятия решений
        updateCurrentProfitBeforeExitCheck(pairData);

        String exitReason = exitStrategyService.getExitReason(pairData, settings);
        if (exitReason != null) {
            return handleAutoClose(pairData, zScoreData, settings, exitReason);
        }

        return updateRegularTrade(pairData, settings);
    }

    private void validateRequest(UpdateTradeRequest request) {
        if (request == null || request.getPairData() == null) {
            throw new IllegalArgumentException("Неверный запрос на обновление трейда");
        }
    }

    private PairData loadFreshPairData(PairData pairData) {
        PairData freshPairData = pairDataService.findById(pairData.getId());
        if (freshPairData == null || freshPairData.getStatus() == TradeStatus.CLOSED) {
            log.debug("⏭️ Пропускаем обновление закрытой пары {}/{}",
                    pairData.getLongTicker(), pairData.getShortTicker());
            return null;
        }

        log.info("🚀 Начинаем обновление трейда для {} - {}",
                freshPairData.getLongTicker(), freshPairData.getShortTicker());
        return freshPairData;
    }

    private ZScoreData calculateZScoreData(PairData pairData, Settings settings) {
        Map<String, List<Candle>> candlesMap = candlesService.getApplicableCandlesMap(pairData, settings);
        return zScoreService.calculateZScoreData(settings, candlesMap);
    }

    private void updateZScoreDataCurrent(PairData pairData, ZScoreData zScoreData) {
        ZScoreParam latestParam = zScoreData.getLastZScoreParam();
        pairData.setZScoreCurrent(latestParam.getZscore());
        pairData.setCorrelationCurrent(latestParam.getCorrelation());
        pairData.setAdfPvalueCurrent(latestParam.getAdfpvalue());
        pairData.setPValueCurrent(latestParam.getPvalue());
        pairData.setMeanCurrent(latestParam.getMean());
        pairData.setStdCurrent(latestParam.getStd());
        pairData.setSpreadCurrent(latestParam.getSpread());
        pairData.setAlphaCurrent(latestParam.getAlpha());
        pairData.setBetaCurrent(latestParam.getBeta());

        // Добавляем новые точки в историю Z-Score при каждом обновлении
        if (zScoreData.getZscoreParams() != null && !zScoreData.getZscoreParams().isEmpty()) {
            // Добавляем всю новую историю из ZScoreData
            for (ZScoreParam param : zScoreData.getZscoreParams()) {
                pairData.addZScorePoint(param);
            }
        } else {
            // Если новой истории нет, добавляем хотя бы текущую точку
            pairData.addZScorePoint(latestParam);
        }
    }

    private void logPairInfo(ZScoreData zScoreData) {
        ZScoreParam latest = zScoreData.getLastZScoreParam();
        log.info(String.format("Наша пара: long=%s short=%s | p=%.5f | adf=%.5f | z=%.2f | corr=%.2f",
                zScoreData.getUndervaluedTicker(), zScoreData.getOvervaluedTicker(),
                latest.getPvalue(), latest.getAdfpvalue(), latest.getZscore(), latest.getCorrelation()
        ));
    }

    private PairData handleManualClose(PairData pairData, Settings settings) {
        ArbitragePairTradeInfo closeInfo = tradingIntegrationService.closeArbitragePair(pairData);
        if (closeInfo == null || !closeInfo.isSuccess()) {
            return handleTradeError(pairData, settings, TradeErrorType.MANUAL_CLOSE_FAILED);
        }

        log.info("✅ Успешно закрыта арбитражная пара через торговую систему: {}/{}",
                pairData.getLongTicker(), pairData.getShortTicker());

        // 🏦 Для реальной торговли: используем фактические данные из closeInfo
        profitUpdateService.updateProfitFromTradeResults(pairData, closeInfo);
        // 🎯 Используем полное сохранение для обновления всех связанных данных
        pairDataService.updateChanges(pairData);
        pairDataService.save(pairData);
        tradeLogService.updateTradeLog(pairData, settings);

        return pairData;
    }

    private PairData handleNoOpenPositions(PairData pairData, Settings settings) {
        log.info("ℹ️ Нет открытых позиций для пары {}/{}! Возможно они были закрыты вручную на бирже.",
                pairData.getLongTicker(), pairData.getShortTicker());

        PositionVerificationResult verificationResult = tradingIntegrationService.verifyPositionsClosed(pairData);
        if (verificationResult.isPositionsClosed()) {
            log.info("✅ Подтверждено: позиции закрыты на бирже для пары {}/{}, PnL: {}",
                    pairData.getLongTicker(), pairData.getShortTicker(), verificationResult.getTotalPnL());
            return handleTradeError(pairData, settings, TradeErrorType.MANUALLY_CLOSED_NO_POSITIONS);
        } else {
            log.warn("⚠️ Позиции не найдены на бирже для пары {}/{}",
                    pairData.getLongTicker(), pairData.getShortTicker());
            return handleTradeError(pairData, settings, TradeErrorType.POSITIONS_NOT_FOUND);
        }
    }

    private PairData handleAutoClose(PairData pairData, ZScoreData zScoreData, Settings settings, String exitReason) {
        log.info("🚪 Найдена причина для выхода из позиции: {} для пары {}/{}",
                exitReason, pairData.getLongTicker(), pairData.getShortTicker());

        ArbitragePairTradeInfo closeResult = tradingIntegrationService.closeArbitragePair(pairData);
        if (closeResult == null || !closeResult.isSuccess()) {
            pairData.setExitReason(exitReason);
            return handleTradeError(pairData, settings, TradeErrorType.AUTO_CLOSE_FAILED);
        }

        log.info("✅ Успешно закрыта арбитражная пара: {}/{}",
                pairData.getLongTicker(), pairData.getShortTicker());

        pairData.setStatus(TradeStatus.CLOSED);
        pairData.setExitReason(exitReason);
        // 🏦 Для реальной торговли: используем фактические данные из closeResult
        profitUpdateService.updateProfitFromTradeResults(pairData, closeResult);
        // 🎯 Используем полное сохранение для обновления всех связанных данных
        pairDataService.updateChanges(pairData);
        pairDataService.save(pairData);
        tradeLogService.updateTradeLog(pairData, settings);
        return pairData;
    }

    private PairData updateRegularTrade(PairData pairData, Settings settings) {

        PositionVerificationResult openPositionsInfo = tradingIntegrationService.getOpenPositionsInfo(pairData);
        if (openPositionsInfo.isPositionsClosed()) {
            log.error("❌ Позиции уже закрыты для пары {}/{}.",
                    pairData.getLongTicker(), pairData.getShortTicker());
            return handleNoOpenPositions(pairData, settings);
        }

        profitUpdateService.updateProfitFromOpenPositions(pairData, openPositionsInfo);
        // 🎯 Используем полное сохранение для обновления всех связанных данных
        pairDataService.updateChanges(pairData);
        pairDataService.save(pairData);
        tradeLogService.updateTradeLog(pairData, settings);
        return pairData;
    }

    private PairData handleTradeError(PairData pairData, Settings settings, TradeErrorType errorType) {
        log.error("❌ Ошибка: {} для пары {}/{}", errorType.getDescription(),
                pairData.getLongTicker(), pairData.getShortTicker());

        pairData.setStatus(errorType.getStatus());
        pairDataService.updateChanges(pairData);
        pairDataService.save(pairData);
        tradeLogService.updateTradeLog(pairData, settings);
        return pairData;
    }

    /**
     * Обновляет актуальный профит перед проверкой exit strategy
     * Критично для правильного срабатывания тейк-профита и стоп-лосса
     */
    private void updateCurrentProfitBeforeExitCheck(PairData pairData) {
        try {
            // Сначала обновляем цены позиций с биржи для актуальных данных
            tradingIntegrationService.updatePositions(List.of(pairData.getLongTicker(), pairData.getShortTicker()));

            // Затем получаем реальный PnL для данной пары с актуальными ценами
            BigDecimal realPnL = tradingIntegrationService.getPositionPnL(pairData);

            pairData.setProfitChanges(realPnL);
            pairDataService.save(pairData);
            log.info("💰 Сохранен пре профит для расчета exit: {}% для пары {}/{}",
                    pairData.getProfitChanges(), pairData.getLongTicker(), pairData.getShortTicker());

        } catch (Exception e) {
            log.error("❌ Ошибка при обновлении профита перед проверкой exit strategy для пары {}/{}: {}",
                    pairData.getLongTicker(), pairData.getShortTicker(), e.getMessage());
        }
    }


}
