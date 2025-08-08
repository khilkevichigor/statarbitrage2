package com.example.statarbitrage.core.processors;

import com.example.statarbitrage.common.dto.Candle;
import com.example.statarbitrage.common.dto.ZScoreData;
import com.example.statarbitrage.common.dto.ZScoreParam;
import com.example.statarbitrage.common.dto.cointegration.CointegrationDetails;
import com.example.statarbitrage.common.dto.cointegration.DataQuality;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.common.model.TradeStatus;
import com.example.statarbitrage.common.utils.FormatUtil;
import com.example.statarbitrage.core.services.*;
import com.example.statarbitrage.notifications.NotificationService;
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
    private final TradingIntegrationService tradingIntegrationServiceImpl;
    private final ExitStrategyService exitStrategyService;
    private final NotificationService notificationService;

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
        logPairInfo(zScoreData, settings);

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

        log.debug("🚀 Начинаем обновление трейда для {}/{}", freshPairData.getLongTicker(), freshPairData.getShortTicker());
        return freshPairData;
    }

    private boolean arePositionsClosed(PairData pairData) {
        final Positioninfo openPositionsInfo = tradingIntegrationServiceImpl.getOpenPositionsInfo(pairData);
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

    private void logPairInfo(ZScoreData zScoreData, Settings settings) {
        final ZScoreParam latest = zScoreData.getLastZScoreParam();
        log.info(String.format("Наша пара: long=%s short=%s | cointegrated=%s | p=%.5f | adf=%.5f | z=%.2f | corr=%.2f",
                        zScoreData.getUndervaluedTicker(), zScoreData.getOvervaluedTicker(),
                        zScoreData.getIsCointegrated(),
                        latest.getPvalue(),
                        latest.getAdfpvalue(),
                        latest.getZscore(),
                        latest.getCorrelation()
                )
        );

        DataQuality dataQuality = zScoreData.getDataQuality();
        if (dataQuality != null) {
            log.info(String.format("- data quality: avgAdf=%.2f | avgR=%.2f | stablePeriods=%d",
                            dataQuality.getAvg_adf_pvalue(),
                            dataQuality.getAvg_r_squared(),
                            dataQuality.getStable_periods()
                    )
            );
        }

        CointegrationDetails details = zScoreData.getCointegrationDetails();
        if (details != null) {
            log.info(String.format(
                    "- cointegration details: traceStat=%.2f | criticalValue95=%.2f | eigenSize=%d | vectorSize=%d | errors=%s",
                    details.getTrace_statistic() != null ? details.getTrace_statistic() : 0.0,
                    details.getCritical_value_95() != null ? details.getCritical_value_95() : 0.0,
                    details.getEigenvalues() != null ? details.getEigenvalues().size() : 0,
                    details.getCointegrating_vector() != null ? details.getCointegrating_vector().size() : 0,
                    details.getError() != null ? details.getError() : "N/A"
            ));
        }

        log.info("🧪 Проверка: pValue={}, ADF={}, R²={}, stablePeriods={}",
                FormatUtil.color(zScoreData.getCointegration_pvalue(), settings.getMinPValue()),
                FormatUtil.color(zScoreData.getDataQuality().getAvg_adf_pvalue(), settings.getMaxAdfValue()),
                FormatUtil.color(zScoreData.getDataQuality().getAvg_r_squared(), settings.getMinRSquared()),
                zScoreData.getDataQuality().getStable_periods()
        );
    }

    private PairData handleManualClose(PairData pairData, Settings settings) {
        final ArbitragePairTradeInfo closeInfo = tradingIntegrationServiceImpl.closeArbitragePair(pairData);
        if (closeInfo == null || !closeInfo.isSuccess()) {
            return handleTradeError(pairData, UpdateTradeErrorType.MANUAL_CLOSE_FAILED);
        }

        log.info("✅ Успешно закрыта арбитражная пара через торговую систему: {}", pairData.getPairName());

        pairData.setStatus(TradeStatus.CLOSED);
        pairData.setExitReason(ExitReasonType.EXIT_REASON_MANUALLY.getDescription());
        finalizeClosedTrade(pairData, settings);
        notificationService.notifyClose(pairData);
        return pairData;
    }

    private void finalizeClosedTrade(PairData pairData, Settings settings) {
        pairDataService.addChanges(pairData);
        pairDataService.updatePortfolioBalanceAfterTradeUSDT(pairData); //баланс после
        tradingIntegrationServiceImpl.removePairFromLocalStorage(pairData);
        pairDataService.save(pairData);
        tradeHistoryService.updateTradeLog(pairData, settings);
    }

    private PairData handleNoOpenPositions(PairData pairData) {
        log.debug("==> handleNoOpenPositions: НАЧАЛО для пары {}", pairData.getPairName());
        log.debug("ℹ️ Нет открытых позиций для пары {}! Возможно они были закрыты вручную на бирже.", pairData.getPairName());

        final Positioninfo verificationResult = tradingIntegrationServiceImpl.verifyPositionsClosed(pairData);
        log.debug("Результат верификации закрытия позиций: {}", verificationResult);

        if (verificationResult.isPositionsClosed()) {
            log.debug("✅ Подтверждено: позиции закрыты на бирже для пары {}, PnL: {} USDT ({} %)", pairData.getPairName(), verificationResult.getTotalPnLUSDT(), verificationResult.getTotalPnLPercent());
            PairData result = handleTradeError(pairData, UpdateTradeErrorType.MANUALLY_CLOSED_NO_POSITIONS);
            log.debug("<== handleNoOpenPositions: КОНЕЦ (позиции закрыты) для пары {}", pairData.getPairName());
            return result;
        } else {
            log.warn("⚠️ Позиции НЕ найдены на бирже для пары {}. Это может быть ошибка синхронизации.", pairData.getPairName());
            PairData result = handleTradeError(pairData, UpdateTradeErrorType.POSITIONS_NOT_FOUND);
            log.debug("<== handleNoOpenPositions: КОНЕЦ (позиции не найдены) для пары {}", pairData.getPairName());
            return result;
        }
    }

    private PairData handleAutoClose(PairData pairData, Settings settings, String exitReason) {
        log.info("🚪 Найдена причина для выхода из позиции: {} для пары {}", exitReason, pairData.getPairName());

        final ArbitragePairTradeInfo closeResult = tradingIntegrationServiceImpl.closeArbitragePair(pairData);
        if (closeResult == null || !closeResult.isSuccess()) {
            pairData.setExitReason(exitReason);
            return handleTradeError(pairData, UpdateTradeErrorType.AUTO_CLOSE_FAILED);
        }

        log.info("✅ Успешно закрыта арбитражная пара: {}", pairData.getPairName());

        pairData.setStatus(TradeStatus.CLOSED);
        pairData.setExitReason(exitReason);
        finalizeClosedTrade(pairData, settings);
        notificationService.notifyClose(pairData);
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