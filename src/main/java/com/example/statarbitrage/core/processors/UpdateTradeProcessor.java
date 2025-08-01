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

    //todo добавить проверку в updateTrade() или отдельно - "если есть открытые позиции а пар нету!"
    //todo добавить колонку "время жизни"
    //todo сделать колонку максимальная просадка по профиту USDT (%)
    //todo сделать колонку максимальная просадка по Z-скор
    //todo сделать чекбокс на UI "закрыться в + при первой же возможности (минимальный профит)"
    //todo сделать кнопку "закрыть все позиции"
    //todo сделать кнопку "усреднить"
    @Transactional
    public PairData updateTrade(UpdateTradeRequest request) {
        validateRequest(request);

        final PairData pairData = loadFreshPairData(request.getPairData());
        if (pairData == null) {
            return request.getPairData();
        }

        final Settings settings = settingsService.getSettings();

        if (arePositionsClosed(pairData)) {
            return handleNoOpenPositions(pairData);
        }

        final ZScoreData zScoreData = calculateZScoreData(pairData, settings);
        logPairInfo(zScoreData);

        pairDataService.updateZScoreDataCurrent(pairData, zScoreData);
        pairDataService.addChanges(pairData); // обновляем профит до проверки стратегии выхода

        if (request.isCloseManually()) {
            return handleManualClose(pairData, settings);
        }

        final String exitReason = exitStrategyService.getExitReason(pairData, settings);
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
        final PairData freshPairData = pairDataService.findById(pairData.getId());
        if (freshPairData == null || freshPairData.getStatus() == TradeStatus.CLOSED) {
            log.debug("⏭️ Пропускаем обновление закрытой пары {}", pairData.getPairName());
            return null;
        }

        log.info("🚀 Начинаем обновление трейда для {}/{}", freshPairData.getLongTicker(), freshPairData.getShortTicker());
        return freshPairData;
    }

    private boolean arePositionsClosed(PairData pairData) {
        final Positioninfo openPositionsInfo = tradingIntegrationService.getOpenPositionsInfo(pairData);
        if (openPositionsInfo.isPositionsClosed()) {
            log.error("❌ Позиции уже закрыты для пары {}.", pairData.getPairName());
            return true;
        }
        return false;
    }

    private ZScoreData calculateZScoreData(PairData pairData, Settings settings) {
        final Map<String, List<Candle>> candlesMap = candlesService.getApplicableCandlesMap(pairData, settings);
        return zScoreService.calculateZScoreData(settings, candlesMap);
    }

    private void logPairInfo(ZScoreData zScoreData) {
        final ZScoreParam latest = zScoreData.getLastZScoreParam();
        log.info(String.format("Наша пара: long=%s short=%s | p=%.5f | adf=%.5f | z=%.2f | corr=%.2f",
                zScoreData.getUndervaluedTicker(), zScoreData.getOvervaluedTicker(),
                latest.getPvalue(), latest.getAdfpvalue(), latest.getZscore(), latest.getCorrelation()));
    }

    private PairData handleManualClose(PairData pairData, Settings settings) {
        final ArbitragePairTradeInfo closeInfo = tradingIntegrationService.closeArbitragePair(pairData);
        if (closeInfo == null || !closeInfo.isSuccess()) {
            return handleTradeError(pairData, UpdateTradeErrorType.MANUAL_CLOSE_FAILED);
        }

        log.info("✅ Успешно закрыта арбитражная пара через торговую систему: {}", pairData.getPairName());

        pairData.setStatus(TradeStatus.CLOSED);
        pairData.setExitReason(ExitReasonType.EXIT_REASON_MANUALLY.getDescription());
        finalizeClosedTrade(pairData, settings);
        return pairData;
    }

    private void finalizeClosedTrade(PairData pairData, Settings settings) {
        pairDataService.addChanges(pairData);
        tradingIntegrationService.removePairFromLocalStorage(pairData);
        pairDataService.save(pairData);
        tradeHistoryService.updateTradeLog(pairData, settings);
    }

    private PairData handleNoOpenPositions(PairData pairData) {
        log.info("==> handleNoOpenPositions: НАЧАЛО для пары {}", pairData.getPairName());
        log.info("ℹ️ Нет открытых позиций для пары {}! Возможно они были закрыты вручную на бирже.", pairData.getPairName());

        final Positioninfo verificationResult = tradingIntegrationService.verifyPositionsClosed(pairData);
        log.info("Результат верификации закрытия позиций: {}", verificationResult);

        if (verificationResult.isPositionsClosed()) {
            log.info("✅ Подтверждено: позиции закрыты на бирже для пары {}, PnL: {} USDT ({} %)", pairData.getPairName(), verificationResult.getTotalPnLUSDT(), verificationResult.getTotalPnLPercent());
            PairData result = handleTradeError(pairData, UpdateTradeErrorType.MANUALLY_CLOSED_NO_POSITIONS);
            log.info("<== handleNoOpenPositions: КОНЕЦ (позиции закрыты) для пары {}", pairData.getPairName());
            return result;
        } else {
            log.warn("⚠️ Позиции НЕ найдены на бирже для пары {}. Это может быть ошибка синхронизации.", pairData.getPairName());
            PairData result = handleTradeError(pairData, UpdateTradeErrorType.POSITIONS_NOT_FOUND);
            log.info("<== handleNoOpenPositions: КОНЕЦ (позиции не найдены) для пары {}", pairData.getPairName());
            return result;
        }
    }

    private PairData handleAutoClose(PairData pairData, Settings settings, String exitReason) {
        log.info("🚪 Найдена причина для выхода из позиции: {} для пары {}", exitReason, pairData.getPairName());

        final ArbitragePairTradeInfo closeResult = tradingIntegrationService.closeArbitragePair(pairData);
        if (closeResult == null || !closeResult.isSuccess()) {
            pairData.setExitReason(exitReason);
            return handleTradeError(pairData, UpdateTradeErrorType.AUTO_CLOSE_FAILED);
        }

        log.info("✅ Успешно закрыта арбитражная пара: {}", pairData.getPairName());

        pairData.setStatus(TradeStatus.CLOSED);
        pairData.setExitReason(exitReason);
        finalizeClosedTrade(pairData, settings);
        return pairData;
    }

    private PairData handleTradeError(PairData pairData, UpdateTradeErrorType errorType) {
        log.error("❌ Ошибка: {} для пары {}", errorType.getDescription(), pairData.getPairName());

        pairData.setStatus(TradeStatus.ERROR);
        pairData.setErrorDescription(errorType.getDescription());
        pairDataService.save(pairData);
        // не обновляем другие данные тк нужны реальные данные по сделкам!
        return pairData;
    }
}