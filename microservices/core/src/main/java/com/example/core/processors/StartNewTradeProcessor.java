package com.example.core.processors;

import com.example.core.client.CandlesFeignClient;
import com.example.core.services.*;
import com.example.core.trading.services.TradingIntegrationService;
import com.example.shared.dto.*;
import com.example.shared.enums.TradeStatus;
import com.example.shared.models.Settings;
import com.example.shared.models.TradingPair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartNewTradeProcessor {
    private final TradingPairService tradingPairService;
    private final SettingsService settingsService;
    private final ZScoreService zScoreService;
    private final TradingIntegrationService tradingIntegrationServiceImpl;
    private final TradeHistoryService tradeHistoryService;
    private final StartNewTradeValidationService startNewTradeValidationService;
    private final CandlesFeignClient candlesFeignClient;

    @Transactional
    public TradingPair startNewTrade(StartNewTradeRequest request) {
        startNewTradeValidationService.validateRequest(request);

        final TradingPair tradingPair = request.getTradingPair();
        final Settings settings = settingsService.getSettings();

        log.info("");
        log.info("🚀 Начинаем новый трейд для пары {} tradingPairId={}...", tradingPair.getPairName(), tradingPair.getId());

        // 1. Предварительная валидация
        Optional<TradingPair> preValidationError = preValidate(tradingPair, settings);
        if (preValidationError.isPresent()) return preValidationError.get();

        // 2. Получаем и проверяем ZScore данные
        Optional<ZScoreData> maybeZScoreData = updateZScoreDataForExistingPair(tradingPair, settings);
        if (maybeZScoreData.isEmpty()) return handleTradeError(tradingPair, StartTradeErrorType.Z_SCORE_DATA_EMPTY);

        final ZScoreData zScoreData = maybeZScoreData.get();
        tradingPairService.updateZScoreDataCurrent(tradingPair, zScoreData);

        // 3. Валидация тикеров и автотрейдинга
        if (!startNewTradeValidationService.validateTickers(tradingPair, zScoreData)) {
            return handleTradeError(tradingPair, StartTradeErrorType.TICKERS_SWITCHED);
        }
        if (!startNewTradeValidationService.validateAutoTrading(tradingPair, request.isCheckAutoTrading())) {
            return handleTradeError(tradingPair, StartTradeErrorType.AUTO_TRADING_DISABLED);
        }

        logTradeInfo(zScoreData);

        // 4. Проверка баланса
        if (!startNewTradeValidationService.validateBalance(tradingPair, settings)) {
            return handleTradeError(tradingPair, StartTradeErrorType.INSUFFICIENT_FUNDS);
        }

        // 5. Открытие позиции
        return openTradePosition(tradingPair, zScoreData, settings);
    }

    private Optional<TradingPair> preValidate(TradingPair tradingPair, Settings settings) {
        if (startNewTradeValidationService.isLastZLessThenMinZ(tradingPair, settings)) {
            log.warn("⚠️ Z-скор текущий < Z-скор Min для пары {}", tradingPair.getPairName());
            return Optional.of(handleTradeError(tradingPair, StartTradeErrorType.Z_SCORE_BELOW_MINIMUM));
        }
        return Optional.empty();
    }

    private Optional<ZScoreData> updateZScoreDataForExistingPair(TradingPair tradingPair, Settings settings) {
        CandlesRequest request = new CandlesRequest(tradingPair, settings);
        Map<String, List<Candle>> candlesMap = candlesFeignClient.getApplicableCandlesMap(request);
        return zScoreService.updateZScoreDataForExistingPairBeforeNewTrade(tradingPair, settings, candlesMap);
    }

    private void logTradeInfo(ZScoreData zScoreData) {
        log.debug(String.format("Наш новый трейд: underValued=%s overValued=%s | p=%.5f | adf=%.5f | z=%.2f | corr=%.2f",
                zScoreData.getUnderValuedTicker(), zScoreData.getOverValuedTicker(),
                zScoreData.getJohansenCointPValue(), zScoreData.getAvgAdfPvalue(), zScoreData.getLatestZScore(), zScoreData.getPearsonCorr()));
    }

    private TradingPair openTradePosition(TradingPair tradingPair, ZScoreData zScoreData, Settings settings) {
        ArbitragePairTradeInfo openResult = tradingIntegrationServiceImpl.openArbitragePair(tradingPair, settings);

        if (openResult == null || !openResult.isSuccess()) {
            log.debug("⚠️ Не удалось открыть арбитражную пару через торговую систему: {}", tradingPair.getPairName());
            return handleTradeError(tradingPair, StartTradeErrorType.TRADE_OPEN_FAILED);
        }

        log.debug("✅ Успешно открыта арбитражная пара: {}", tradingPair.getPairName());

        tradingPair.setStatus(TradeStatus.TRADING);

        tradingPair.setPortfolioBeforeTradeUSDT(openResult.getPortfolioBalanceBeforeTradeUSDT()); // баланс ДО

        TradeResult longTrade = openResult.getLongTradeResult();
        TradeResult shortTrade = openResult.getShortTradeResult();

        tradingPairService.addEntryPoints(tradingPair, zScoreData, longTrade, shortTrade);
        tradingPairService.addChanges(tradingPair);
        tradingPairService.save(tradingPair);

        tradeHistoryService.updateTradeLog(tradingPair, settings);

        return tradingPair;
    }

    private TradingPair handleTradeError(TradingPair tradingPair, StartTradeErrorType errorType) {
        log.debug("❌ Ошибка: {} для пары {}", errorType.getDescription(), tradingPair.getPairName());
        tradingPair.setStatus(TradeStatus.ERROR);
        tradingPair.setErrorDescription(errorType.getDescription());
        tradingPairService.save(tradingPair); //todo падаем
        return tradingPair;
    }
}
