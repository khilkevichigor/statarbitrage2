package com.example.statarbitrage.core.processors;

import com.example.statarbitrage.common.dto.Candle;
import com.example.statarbitrage.common.dto.ZScoreData;
import com.example.statarbitrage.common.dto.ZScoreParam;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.common.model.TradeStatus;
import com.example.statarbitrage.core.services.*;
import com.example.statarbitrage.trading.model.ArbitragePairTradeInfo;
import com.example.statarbitrage.trading.model.Positioninfo;
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
    private final TradeHistoryService tradeHistoryService;
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

        Positioninfo openPositionsInfo = tradingIntegrationService.getOpenPositionsInfo(pairData);
        if (openPositionsInfo.isPositionsClosed()) {
            log.error("❌ Позиции уже закрыты для пары {}/{}.",
                    pairData.getLongTicker(), pairData.getShortTicker());
            return handleNoOpenPositions(pairData);
        }

        ZScoreData zScoreData = calculateZScoreData(pairData, settings);
        logPairInfo(zScoreData);

        pairDataService.updateZScoreDataCurrent(pairData, zScoreData);

        // 🎯 КРИТИЧНО: Обновляем профит ДО проверки exit strategy для актуального принятия решений
        pairDataService.addChanges(pairData);

        if (request.isCloseManually()) {
            return handleManualClose(pairData, settings);
        }

        String exitReason = exitStrategyService.getExitReason(pairData, settings);
        if (exitReason != null) {
            return handleAutoClose(pairData, settings, exitReason);
        }

        pairDataService.save(pairData);
        tradeHistoryService.updateTradeLog(pairData, settings);
        return pairData;
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

        log.info("🚀 Начинаем обновление трейда для {} / {}",
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

    private PairData handleManualClose(PairData pairData, Settings settings) {
        ArbitragePairTradeInfo closeInfo = tradingIntegrationService.closeArbitragePair(pairData);
        if (closeInfo == null || !closeInfo.isSuccess()) {
            return handleTradeError(pairData, UpdateTradeErrorType.MANUAL_CLOSE_FAILED);
        }

        log.info("✅ Успешно закрыта арбитражная пара через торговую систему: {} / {}",
                pairData.getLongTicker(), pairData.getShortTicker());

        pairData.setStatus(TradeStatus.CLOSED);
        pairData.setExitReason(ExitReasonType.EXIT_REASON_MANUALLY.getDescription());
        addChangesAndRemovePairFromLocalStorage(pairData);
        pairDataService.save(pairData);
        tradeHistoryService.updateTradeLog(pairData, settings);

        return pairData;
    }

    //добавляем changes и удаляет пару из реестра тк трейды уже закрыты
    private void addChangesAndRemovePairFromLocalStorage(PairData pairData) {
        pairDataService.addChanges(pairData);
        tradingIntegrationService.removePairFromLocalStorage(pairData); //после расчета changes можно удалить связи
    }

    private PairData handleNoOpenPositions(PairData pairData) {
        log.info("ℹ️ Нет открытых позиций для пары {} / {}! Возможно они были закрыты вручную на бирже.",
                pairData.getLongTicker(), pairData.getShortTicker());

        Positioninfo verificationResult = tradingIntegrationService.verifyPositionsClosed(pairData);
        if (verificationResult.isPositionsClosed()) {
            log.info("✅ Подтверждено: позиции закрыты на бирже для пары {} / {}, PnL: {}",
                    pairData.getLongTicker(), pairData.getShortTicker(), verificationResult.getTotalPnL());
            return handleTradeError(pairData, UpdateTradeErrorType.MANUALLY_CLOSED_NO_POSITIONS);
        } else {
            log.warn("⚠️ Позиции не найдены на бирже для пары {} / {}",
                    pairData.getLongTicker(), pairData.getShortTicker());
            return handleTradeError(pairData, UpdateTradeErrorType.POSITIONS_NOT_FOUND);
        }
    }

    private PairData handleAutoClose(PairData pairData, Settings settings, String exitReason) {
        log.info("🚪 Найдена причина для выхода из позиции: {} для пары {} / {}",
                exitReason, pairData.getLongTicker(), pairData.getShortTicker());

        ArbitragePairTradeInfo closeResult = tradingIntegrationService.closeArbitragePair(pairData);
        if (closeResult == null || !closeResult.isSuccess()) {
            pairData.setExitReason(exitReason);
            return handleTradeError(pairData, UpdateTradeErrorType.AUTO_CLOSE_FAILED);
        }

        log.info("✅ Успешно закрыта арбитражная пара: {} / {}",
                pairData.getLongTicker(), pairData.getShortTicker());

        pairData.setStatus(TradeStatus.CLOSED);
        pairData.setExitReason(exitReason);
        addChangesAndRemovePairFromLocalStorage(pairData);
        pairDataService.save(pairData);
        tradeHistoryService.updateTradeLog(pairData, settings);
        return pairData;
    }

    private PairData handleTradeError(PairData pairData, UpdateTradeErrorType errorType) {
        log.error("❌ Ошибка: {} для пары {} / {}", errorType.getDescription(),
                pairData.getLongTicker(), pairData.getShortTicker());

        pairData.setStatus(TradeStatus.ERROR);
        pairData.setErrorDescription(errorType.getDescription());
        pairDataService.save(pairData);
        //не обновляем другие данные тк нужны реальные данные по сделкам!
        return pairData;
    }
}
