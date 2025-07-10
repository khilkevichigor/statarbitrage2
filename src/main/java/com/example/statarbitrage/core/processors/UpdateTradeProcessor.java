package com.example.statarbitrage.core.processors;

import com.example.statarbitrage.common.dto.Candle;
import com.example.statarbitrage.common.dto.ZScoreData;
import com.example.statarbitrage.common.dto.ZScoreParam;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.common.model.TradeStatus;
import com.example.statarbitrage.core.services.*;
import com.example.statarbitrage.trading.services.TradingIntegrationService;
import com.example.statarbitrage.trading.services.TradingProviderFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
    private final TradingProviderFactory tradingProviderFactory;

    public void updateTrade(PairData pair, boolean isCloseManually) {
        boolean isVirtual = tradingProviderFactory.getCurrentProvider().getProviderType().isVirtual();
        if (isVirtual) {
            updateVirtualTrade(pair, isCloseManually);
        } else {
            updateRealTrade(pair, isCloseManually);
        }
    }

    private void updateVirtualTrade(PairData pairData, boolean isCloseManually) {
        Settings settings = settingsService.getSettings();
        log.info("🚀 Начинаем обновление трейда...");

        Map<String, List<Candle>> candlesMap = candlesService.getApplicableCandlesMap(pairData, settings);
        ZScoreData zScoreData = zScoreService.calculateZScoreData(settings, candlesMap);
        logData(zScoreData);

        List<Candle> longTickerCandles = candlesMap.get(pairData.getLongTicker());
        List<Candle> shortTickerCandles = candlesMap.get(pairData.getShortTicker());

        pairDataService.update(pairData, zScoreData, longTickerCandles, shortTickerCandles, isCloseManually);
        tradeLogService.saveFromPairData(pairData);
    }

    private void updateRealTrade(PairData pairData, boolean isCloseManually) {
        Settings settings = settingsService.getSettings();
        log.info("🚀 Начинаем обновление трейда...");

        Map<String, List<Candle>> candlesMap = candlesService.getApplicableCandlesMap(pairData, settings);
        ZScoreData zScoreData = zScoreService.calculateZScoreData(settings, candlesMap);
        logData(zScoreData);

        // Сохраняем статус до обновления для проверки изменения
        TradeStatus statusBefore = pairData.getStatus();

        //todo допилить

        // Обновляем реальный PnL из торговой системы
        BigDecimal realPnL = tradingIntegrationService.getPositionPnL(pairData);
        if (realPnL.compareTo(BigDecimal.ZERO) != 0) {
            // Конвертируем в проценты для совместимости с существующей системой
            pairData.setProfitChanges(realPnL);
            log.debug("🔄 Обновлен реальный PnL для пары {}/{}: {}", pairData.getLongTicker(), pairData.getShortTicker(), realPnL);
        }

        // Если статус изменился на CLOSED, закрываем позиции в торговой системе СИНХРОННО
        if (statusBefore == TradeStatus.TRADING && isCloseManually) {
            boolean success = tradingIntegrationService.closeArbitragePair(pairData);
            if (success) {
                log.info("✅ Успешно закрыта арбитражная пара через торговую систему: {}/{}",
                        pairData.getLongTicker(), pairData.getShortTicker());

                tradeLogService.saveFromPairData(pairData);
            } else {
                log.warn("⚠️ Не удалось закрыть арбитражную пару через торговую систему: {}/{}",
                        pairData.getLongTicker(), pairData.getShortTicker());

                pairData.setStatus(TradeStatus.ERROR_200);
                pairDataService.save(pairData);
            }
        }

    }

    private static void logData(ZScoreData zScoreData) {
        ZScoreParam latest = zScoreData.getLastZScoreParam(); // последние params
        log.info(String.format("Наша пара: underValued=%s overValued=%s | p=%.5f | adf=%.5f | z=%.2f | corr=%.2f",
                zScoreData.getUndervaluedTicker(), zScoreData.getOvervaluedTicker(),
                latest.getPvalue(), latest.getAdfpvalue(), latest.getZscore(), latest.getCorrelation()
        ));
    }
}
