package com.example.statarbitrage.core.processors;

import com.example.statarbitrage.common.dto.Candle;
import com.example.statarbitrage.common.dto.ZScoreData;
import com.example.statarbitrage.common.dto.ZScoreParam;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.common.model.TradeStatus;
import com.example.statarbitrage.core.services.*;
import com.example.statarbitrage.trading.model.CloseArbitragePairResult;
import com.example.statarbitrage.trading.model.PositionVerificationResult;
import com.example.statarbitrage.trading.model.TradeResult;
import com.example.statarbitrage.trading.services.TradingIntegrationService;
import com.example.statarbitrage.ui.dto.UpdateTradeRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    @Transactional
    public PairData updateTrade(UpdateTradeRequest request) {
        validateRequest(request);

        PairData pairData = loadFreshPairData(request.getPairData());
        if (pairData == null) {
            return request.getPairData();
        }

        Settings settings = settingsService.getSettings();
        ZScoreData zScoreData = calculateZScoreData(pairData, settings);

        logPairInfo(zScoreData);

        if (request.isCloseManually()) {
            return handleManualClose(pairData, zScoreData, settings);
        }

        String exitReason = exitStrategyService.getExitReason(pairData);
        if (exitReason != null) {
            return handleAutoClose(pairData, zScoreData, settings, exitReason);
        }

        return updateRegularTrade(pairData, zScoreData, settings);
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

    private void logPairInfo(ZScoreData zScoreData) {
        ZScoreParam latest = zScoreData.getLastZScoreParam();
        log.info(String.format("Наша пара: long=%s short=%s | p=%.5f | adf=%.5f | z=%.2f | corr=%.2f",
                zScoreData.getUndervaluedTicker(), zScoreData.getOvervaluedTicker(),
                latest.getPvalue(), latest.getAdfpvalue(), latest.getZscore(), latest.getCorrelation()
        ));
    }

    private PairData handleManualClose(PairData pairData, ZScoreData zScoreData, Settings settings) {
        if (!tradingIntegrationService.hasOpenPositions(pairData)) {
            return handleNoOpenPositions(pairData, settings);
        }

        // 🎯 Дифференцированный подход в зависимости от типа торговли
        boolean isVirtualTrading = tradingIntegrationService.getCurrentTradingMode().name().contains("VIRTUAL");

        if (isVirtualTrading) {
            // 📊 Для виртуальной торговли: рассчитываем профит ДО закрытия позиций
            Map<String, List<Candle>> candlesMap = candlesService.getApplicableCandlesMap(pairData, settings);
            pairDataService.updateCurrentDataAndSave(pairData, zScoreData, candlesMap);
            pairDataService.updateChangesAndSave(pairData);
        }

        CloseArbitragePairResult closeResult = tradingIntegrationService.closeArbitragePair(pairData);
        if (closeResult == null || !closeResult.isSuccess()) {
            return handleTradeError(pairData, settings, TradeErrorType.MANUAL_CLOSE_FAILED);
        }

        log.info("✅ Успешно закрыта арбитражная пара через торговую систему: {}/{}",
                pairData.getLongTicker(), pairData.getShortTicker());

        updatePairDataAfterClose(pairData, zScoreData, closeResult);

        if (isVirtualTrading) {
            // 💾 Для виртуальной торговли: сохраняем без повторного расчета профита
            pairDataService.save(pairData);
            tradeLogService.updateTradeLog(pairData, settings);
        } else {
            // 🏦 Для реальной торговли: используем фактические данные из closeResult
            updateProfitFromCloseResult(pairData, closeResult);
            savePairDataWithUpdates(pairData, settings);
        }

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

        // 🎯 Дифференцированный подход в зависимости от типа торговли
        boolean isVirtualTrading = tradingIntegrationService.getCurrentTradingMode().name().contains("VIRTUAL");

        if (isVirtualTrading) {
            // 📊 Для виртуальной торговли: рассчитываем профит ДО закрытия позиций
            Map<String, List<Candle>> candlesMap = candlesService.getApplicableCandlesMap(pairData, settings);
            pairDataService.updateCurrentDataAndSave(pairData, zScoreData, candlesMap);
            pairDataService.updateChangesAndSave(pairData);
        }

        CloseArbitragePairResult closeResult = tradingIntegrationService.closeArbitragePair(pairData);
        if (closeResult == null || !closeResult.isSuccess()) {
            pairData.setExitReason(exitReason);
            return handleTradeError(pairData, settings, TradeErrorType.AUTO_CLOSE_FAILED);
        }

        log.info("✅ Успешно закрыта арбитражная пара: {}/{}",
                pairData.getLongTicker(), pairData.getShortTicker());

        updatePairDataAfterClose(pairData, zScoreData, closeResult);
        pairData.setStatus(TradeStatus.CLOSED);
        pairData.setExitReason(exitReason);

        if (isVirtualTrading) {
            // 💾 Для виртуальной торговли: сохраняем без повторного расчета профита
            pairDataService.save(pairData);
            tradeLogService.updateTradeLog(pairData, settings);
        } else {
            // 🏦 Для реальной торговли: используем фактические данные из closeResult
            updateProfitFromCloseResult(pairData, closeResult);
            savePairDataWithUpdates(pairData, settings);
        }

        return pairData;
    }

    private PairData updateRegularTrade(PairData pairData, ZScoreData zScoreData, Settings settings) {
        Map<String, List<Candle>> candlesMap = candlesService.getApplicableCandlesMap(pairData, settings);
        pairDataService.updateCurrentDataAndSave(pairData, zScoreData, candlesMap);
        savePairDataWithUpdates(pairData, settings);
        return pairData;
    }

    private PairData handleTradeError(PairData pairData, Settings settings, TradeErrorType errorType) {
        log.error("❌ Ошибка: {} для пары {}/{}", errorType.getDescription(),
                pairData.getLongTicker(), pairData.getShortTicker());

        pairData.setStatus(errorType.getStatus());
        savePairDataWithUpdates(pairData, settings);
        return pairData;
    }

    private void updatePairDataAfterClose(PairData pairData, ZScoreData zScoreData, CloseArbitragePairResult closeResult) {
        TradeResult closeLongTradeResult = closeResult.getLongTradeResult();
        TradeResult closeShortTradeResult = closeResult.getShortTradeResult();
        pairDataService.updateCurrentDataAndSave(pairData, zScoreData, closeLongTradeResult, closeShortTradeResult);
    }

    private void savePairDataWithUpdates(PairData pairData, Settings settings) {
        pairDataService.save(pairData);
        pairDataService.updateChangesAndSave(pairData);
        tradeLogService.updateTradeLog(pairData, settings);
    }

    /**
     * Обновляет профит на основе фактических результатов закрытия позиций
     * Используется для реальной торговли
     */
    private void updateProfitFromCloseResult(PairData pairData, CloseArbitragePairResult closeResult) {
        try {
            TradeResult longResult = closeResult.getLongTradeResult();
            TradeResult shortResult = closeResult.getShortTradeResult();

            if (longResult == null || shortResult == null) {
                log.warn("⚠️ Не удалось получить результаты закрытия для пары {}/{}",
                        pairData.getLongTicker(), pairData.getShortTicker());
                return;
            }

            // 💰 Используем фактические цены закрытия
            pairData.setLongTickerCurrentPrice(longResult.getExecutionPrice().doubleValue());
            pairData.setShortTickerCurrentPrice(shortResult.getExecutionPrice().doubleValue());

            // 📊 Рассчитываем профит на основе фактических PnL
            BigDecimal totalPnL = longResult.getPnl().add(shortResult.getPnl());
            BigDecimal totalFees = longResult.getFees().add(shortResult.getFees());
            BigDecimal netPnL = totalPnL.subtract(totalFees);

            // 📈 Конвертируем в процент от позиции
            BigDecimal longEntryPrice = BigDecimal.valueOf(pairData.getLongTickerEntryPrice());
            BigDecimal shortEntryPrice = BigDecimal.valueOf(pairData.getShortTickerEntryPrice());
            BigDecimal avgEntryPrice = longEntryPrice.add(shortEntryPrice).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);

            if (avgEntryPrice.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal profitPercent = netPnL.divide(avgEntryPrice, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                pairData.setProfitChanges(profitPercent);
            }

            log.info("🏦 Обновлен профит для реальной торговли {}/{}: {}% (PnL: {}, комиссии: {})",
                    pairData.getLongTicker(), pairData.getShortTicker(),
                    pairData.getProfitChanges(), totalPnL, totalFees);

        } catch (Exception e) {
            log.error("❌ Ошибка при обновлении профита из результатов закрытия для пары {}/{}: {}",
                    pairData.getLongTicker(), pairData.getShortTicker(), e.getMessage());
        }
    }
}
