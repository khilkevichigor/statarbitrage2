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
        PairData pairData = request.getPairData();
        boolean isCloseManually = request.isCloseManually();
        // Перезагружаем пару из БД для получения актуального статуса
        PairData freshPairData = pairDataService.findById(pairData.getId());
        if (freshPairData == null || freshPairData.getStatus() == TradeStatus.CLOSED) {
            log.debug("⏭️ Пропускаем обновление закрытой пары {}/{}", pairData.getLongTicker(), pairData.getShortTicker());
            return freshPairData != null ? freshPairData : pairData;
        }

        // Используем свежие данные из БД
        pairData = freshPairData;

        log.info("🚀 Начинаем обновление трейда для {} - {}", pairData.getLongTicker(), pairData.getShortTicker());
        Settings settings = settingsService.getSettings();

        Map<String, List<Candle>> candlesMap = candlesService.getApplicableCandlesMap(pairData, settings);
        ZScoreData zScoreData = zScoreService.calculateZScoreData(settings, candlesMap);

        ZScoreParam latest = zScoreData.getLastZScoreParam(); // последние params
        log.info(String.format("Наша пара: underValued=%s overValued=%s | p=%.5f | adf=%.5f | z=%.2f | corr=%.2f",
                zScoreData.getUndervaluedTicker(), zScoreData.getOvervaluedTicker(),
                latest.getPvalue(), latest.getAdfpvalue(), latest.getZscore(), latest.getCorrelation()
        ));

        // Если нажали на "Закрыть позицию", закрываем позиции в торговой системе СИНХРОННО
        if (isCloseManually) {
            // Проверяем наличие открытых позиций перед попыткой закрытия
            if (!tradingIntegrationService.hasOpenPositions(pairData)) {
                log.info("ℹ️ Нет открытых позиции для пары {}/{}! Возможно они были закрыты вручную на бирже.",
                        pairData.getLongTicker(), pairData.getShortTicker());
                // Проверяем что позиции действительно закрыты на бирже и получаем PnL
                PositionVerificationResult verificationResult = tradingIntegrationService.verifyPositionsClosed(pairData);
                if (verificationResult.isPositionsClosed()) {
                    log.info("✅ Подтверждено: позиции закрыты на бирже для пары {}/{}, PnL: {}",
                            pairData.getLongTicker(), pairData.getShortTicker(), verificationResult.getTotalPnL());
                    pairData.setStatus(TradeStatus.ERROR_800);
                    pairDataService.updateChangesWithSpecificPnLAndSave(pairData, verificationResult.getTotalPnL());
                    tradeLogService.updateTradeLog(pairData, settings);
                    return pairData;
                } else {
                    log.warn("⚠️ Позиции не найдены на бирже для пары {}/{}",
                            pairData.getLongTicker(), pairData.getShortTicker());
                    pairData.setStatus(TradeStatus.ERROR_810);
                    pairDataService.save(pairData);
                    tradeLogService.updateTradeLog(pairData, settings);
                    return pairData;
                }
            }
            //Закрываем
            CloseArbitragePairResult closeArbitragePairResult = tradingIntegrationService.closeArbitragePair(pairData);
            if (closeArbitragePairResult == null || !closeArbitragePairResult.isSuccess()) {
                log.warn("⚠️ Не удалось закрыть арбитражную пару через торговую систему: {}/{}",
                        pairData.getLongTicker(), pairData.getShortTicker());

                pairData.setStatus(TradeStatus.ERROR_710);
                pairDataService.updateCurrentDataAndSave(pairData, zScoreData, candlesMap);
                pairDataService.updateChangesAndSave(pairData);
                tradeLogService.updateTradeLog(pairData, settings);
                return pairData;
            }
            log.info("✅ Успешно закрыта арбитражная пара через торговую систему: {}/{}",
                    pairData.getLongTicker(), pairData.getShortTicker());

            TradeResult closeLongTradeResult = closeArbitragePairResult.getLongTradeResult();
            TradeResult closeShortTradeResult = closeArbitragePairResult.getShortTradeResult();
            pairDataService.updateCurrentDataAndSave(pairData, zScoreData, candlesMap, closeLongTradeResult, closeShortTradeResult);
            pairDataService.updateChangesAndSave(pairData);
            tradeLogService.updateTradeLog(pairData, settings);
            return pairData;
        }

        String exitReason = exitStrategyService.getExitReason(pairData);
        if (exitReason != null) {
            log.info("🚪 Найдена причина для выхода из позиции: {} для пары {}/{}",
                    exitReason, pairData.getLongTicker(), pairData.getShortTicker());

            // Закрываем арбитражную пару через торговую систему СИНХРОННО
            CloseArbitragePairResult closeArbitragePairResult = tradingIntegrationService.closeArbitragePair(pairData);
            if (closeArbitragePairResult == null || !closeArbitragePairResult.isSuccess()) {
                log.error("❌ Ошибка при закрытии арбитражной пары: {}/{}",
                        pairData.getLongTicker(), pairData.getShortTicker());

                pairData.setExitReason(exitReason);
                pairData.setStatus(TradeStatus.ERROR_200);
                pairDataService.updateCurrentDataAndSave(pairData, zScoreData, candlesMap);
                pairDataService.updateChangesAndSave(pairData);
                tradeLogService.updateTradeLog(pairData, settings);
                return pairData;
            }
            log.info("✅ Успешно закрыта арбитражная пара: {}/{}",
                    pairData.getLongTicker(), pairData.getShortTicker());

            TradeResult closeLongTradeResult = closeArbitragePairResult.getLongTradeResult();
            TradeResult closeShortTradeResult = closeArbitragePairResult.getShortTradeResult();
            pairDataService.updateCurrentDataAndSave(pairData, zScoreData, candlesMap, closeLongTradeResult, closeShortTradeResult);
            pairData.setStatus(TradeStatus.CLOSED);
            pairData.setExitReason(exitReason);
            pairDataService.updateCurrentDataAndSave(pairData, zScoreData, candlesMap);
            pairDataService.updateChangesAndSave(pairData);
            tradeLogService.updateTradeLog(pairData, settings);
            return pairData;
        }

        pairDataService.updateCurrentDataAndSave(pairData, zScoreData, candlesMap);
        // Получаем актуальную информацию по открытым позициям и обновляем changes
        PositionVerificationResult openPositionsInfo = tradingIntegrationService.getOpenPositionsInfo(pairData);
        if (!openPositionsInfo.isPositionsClosed()) {
            // Позиции открыты - используем актуальный PnL
            pairDataService.updateChangesWithSpecificPnLAndSave(pairData, openPositionsInfo.getTotalPnL());
        } else {
            // Позиции закрыты или не найдены - используем обычный метод
            pairDataService.updateChangesAndSave(pairData);
        }
        tradeLogService.updateTradeLog(pairData, settings);

        return pairData;
    }
}
