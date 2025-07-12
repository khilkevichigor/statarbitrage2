package com.example.statarbitrage.core.processors;

import com.example.statarbitrage.common.dto.Candle;
import com.example.statarbitrage.common.dto.ZScoreData;
import com.example.statarbitrage.common.dto.ZScoreParam;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.common.model.TradeStatus;
import com.example.statarbitrage.core.services.*;
import com.example.statarbitrage.trading.model.CloseArbitragePairResult;
import com.example.statarbitrage.trading.model.TradeResult;
import com.example.statarbitrage.trading.services.TradingIntegrationService;
import com.example.statarbitrage.trading.services.TradingProviderFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.example.statarbitrage.common.constant.Constants.EXIT_REASON_MANUALLY;

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
    private final TradingProviderFactory tradingProviderFactory;
    private final ChangesService changesService;
    private final ExitStrategyService exitStrategyService;

    @Transactional
    public PairData updateTrade(PairData pairData, boolean isCloseManually) {
        boolean isVirtual = tradingProviderFactory.getCurrentProvider().getProviderType().isVirtual();
        if (isVirtual) {
            return updateVirtualTrade(pairData, isCloseManually);
        } else {
            return updateRealTrade(pairData, isCloseManually);
        }
    }

    private PairData updateVirtualTrade(PairData pairData, boolean isCloseManually) {
        log.info("🚀 Начинаем обновление трейда для {} - {}", pairData.getLongTicker(), pairData.getShortTicker());
        Settings settings = settingsService.getSettings();

        Map<String, List<Candle>> candlesMap = candlesService.getApplicableCandlesMap(pairData, settings);
        ZScoreData zScoreData = zScoreService.calculateZScoreData(settings, candlesMap);

        logData(zScoreData);

        pairDataService.updateVirtual(pairData, zScoreData, candlesMap);

        changesService.calculateVirtual(pairData);

        String exitReason = exitStrategyService.getExitReason(pairData);
        if (exitReason != null) {
            pairData.setExitReason(exitReason);
            pairData.setStatus(TradeStatus.CLOSED);
        }

        //после всех обновлений профита закрываем если нужно
        if (isCloseManually) {
            pairData.setStatus(TradeStatus.CLOSED);
            pairData.setExitReason(EXIT_REASON_MANUALLY);
        }

        pairDataService.save(pairData);

        tradeLogService.saveLog(pairData);
        return pairData;
    }

    private PairData updateRealTrade(PairData pairData, boolean isCloseManually) {
        log.info("🚀 Начинаем обновление трейда для {} - {}", pairData.getLongTicker(), pairData.getShortTicker());
        Settings settings = settingsService.getSettings();

        Map<String, List<Candle>> candlesMap = candlesService.getApplicableCandlesMap(pairData, settings);
        ZScoreData zScoreData = zScoreService.calculateZScoreData(settings, candlesMap);

        logData(zScoreData);

//        // Обновляем реальный PnL из торговой системы (берём с OKX)
//        BigDecimal realPnL = tradingIntegrationService.getPositionPnL(pairData);
//        if (realPnL.compareTo(BigDecimal.ZERO) != 0) {
//            // Конвертируем в проценты для совместимости с существующей системой
//            pairData.setProfitChanges(realPnL);
//            log.debug("🔄 Обновлен реальный PnL для пары {} - {}: {}", pairData.getLongTicker(), pairData.getShortTicker(), realPnL);
//        }

        pairDataService.updateReal(pairData, zScoreData, candlesMap);
        changesService.calculateReal(pairData);

        // Если нажали на "Закрыть позицию", закрываем позиции в торговой системе СИНХРОННО
        if (isCloseManually) {
            // Проверяем наличие открытых позиций перед попыткой закрытия
            if (tradingIntegrationService.hasOpenPositions(pairData)) {
                CloseArbitragePairResult closeArbitragePairResult = tradingIntegrationService.closeArbitragePair(pairData);
                if (closeArbitragePairResult != null && closeArbitragePairResult.isSuccess()) {
                    log.info("✅ Успешно закрыта арбитражная пара через торговую систему: {}/{}",
                            pairData.getLongTicker(), pairData.getShortTicker());

                    TradeResult closeLongTradeResult = closeArbitragePairResult.getLongTradeResult();
                    TradeResult closeShortTradeResult = closeArbitragePairResult.getShortTradeResult();
                    pairDataService.updateReal(pairData, zScoreData, candlesMap, closeLongTradeResult, closeShortTradeResult);
                    changesService.calculateReal(pairData);
                    tradeLogService.saveLog(pairData);
                } else {
                    log.warn("⚠️ Не удалось закрыть арбитражную пару через торговую систему: {}/{}",
                            pairData.getLongTicker(), pairData.getShortTicker());

                    pairData.setStatus(TradeStatus.ERROR_200);
                    pairDataService.save(pairData);
                }
            } else {
                log.info("ℹ️ Позиции для пары {}/{} уже были закрыты вручную на бирже. Только обновляем статус.",
                        pairData.getLongTicker(), pairData.getShortTicker());

                // Если позиции уже закрыты на бирже, просто сохраняем лог без попытки закрытия
                tradeLogService.saveLog(pairData);
            }

        } else {
            String exitReason = exitStrategyService.getExitReason(pairData);
            if (exitReason != null) {
                log.info("🚪 Найдена причина для выхода из позиции: {} для пары {}/{}",
                        exitReason, pairData.getLongTicker(), pairData.getShortTicker());

                // Закрываем арбитражную пару через торговую систему СИНХРОННО
                CloseArbitragePairResult closeArbitragePairResult = tradingIntegrationService.closeArbitragePair(pairData);
                if (closeArbitragePairResult != null && closeArbitragePairResult.isSuccess()) {
                    log.info("✅ Успешно закрыта арбитражная пара: {}/{}",
                            pairData.getLongTicker(), pairData.getShortTicker());

                    TradeResult closeLongTradeResult = closeArbitragePairResult.getLongTradeResult();
                    TradeResult closeShortTradeResult = closeArbitragePairResult.getShortTradeResult();
                    pairDataService.updateReal(pairData, zScoreData, candlesMap, closeLongTradeResult, closeShortTradeResult);
                    // Снова обновляем данные после закрытия позиций
                    changesService.calculateReal(pairData);

                    pairData.setExitReason(exitReason);
                    pairData.setStatus(TradeStatus.CLOSED);
                } else {
                    log.error("❌ Ошибка при закрытии арбитражной пары: {}/{}",
                            pairData.getLongTicker(), pairData.getShortTicker());

                    pairData.setExitReason("ERROR_CLOSING: " + exitReason);
                    pairData.setStatus(TradeStatus.ERROR_200);
                }
            }
        }

        pairDataService.save(pairData);
        changesService.calculateReal(pairData);

        tradeLogService.saveLog(pairData);

        return pairData;
    }

    private static void logData(ZScoreData zScoreData) {
        ZScoreParam latest = zScoreData.getLastZScoreParam(); // последние params
        log.info(String.format("Наша пара: underValued=%s overValued=%s | p=%.5f | adf=%.5f | z=%.2f | corr=%.2f",
                zScoreData.getUndervaluedTicker(), zScoreData.getOvervaluedTicker(),
                latest.getPvalue(), latest.getAdfpvalue(), latest.getZscore(), latest.getCorrelation()
        ));
    }
}
