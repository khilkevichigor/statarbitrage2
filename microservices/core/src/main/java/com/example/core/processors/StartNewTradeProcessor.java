package com.example.core.processors;

import com.example.core.client.CandlesFeignClient;
import com.example.core.services.*;
import com.example.core.trading.services.TradingIntegrationService;
import com.example.shared.dto.CandlesRequest;
import com.example.shared.dto.StartNewTradeRequest;
import com.example.shared.dto.ZScoreData;
import com.example.shared.models.*;
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
    private final PairDataService pairDataService;
    private final SettingsService settingsService;
    private final ZScoreService zScoreService;
    private final TradingIntegrationService tradingIntegrationServiceImpl;
    private final TradeHistoryService tradeHistoryService;
    private final StartNewTradeValidationService startNewTradeValidationService;
    private final CandlesFeignClient candlesFeignClient;

    @Transactional
    public PairData startNewTrade(StartNewTradeRequest request) {
        startNewTradeValidationService.validateRequest(request);

        final PairData pairData = request.getPairData();
        final Settings settings = settingsService.getSettings();

        log.info("");
        log.info("🚀 Начинаем новый трейд для {}...", pairData.getPairName());

        // 1. Предварительная валидация
        Optional<PairData> preValidationError = preValidate(pairData, settings);
        if (preValidationError.isPresent()) return preValidationError.get();

        // 2. Получаем и проверяем ZScore данные
        Optional<ZScoreData> maybeZScoreData = updateZScoreDataForExistingPair(pairData, settings);
        if (maybeZScoreData.isEmpty()) return handleTradeError(pairData, StartTradeErrorType.Z_SCORE_DATA_EMPTY);

        final ZScoreData zScoreData = maybeZScoreData.get();
        pairDataService.updateZScoreDataCurrent(pairData, zScoreData);

        // 3. Валидация тикеров и автотрейдинга
        if (!startNewTradeValidationService.validateTickers(pairData, zScoreData)) {
            return handleTradeError(pairData, StartTradeErrorType.TICKERS_SWITCHED);
        }
        if (!startNewTradeValidationService.validateAutoTrading(pairData, request.isCheckAutoTrading())) {
            return handleTradeError(pairData, StartTradeErrorType.AUTO_TRADING_DISABLED);
        }

        logTradeInfo(zScoreData);

        // 4. Проверка баланса
        if (!startNewTradeValidationService.validateBalance(pairData, settings)) {
            return handleTradeError(pairData, StartTradeErrorType.INSUFFICIENT_FUNDS);
        }

        // 5. Открытие позиции
        return openTradePosition(pairData, zScoreData, settings);
    }

    private Optional<PairData> preValidate(PairData pairData, Settings settings) {
        if (startNewTradeValidationService.isLastZLessThenMinZ(pairData, settings)) {
            log.warn("⚠️ Z-скор текущий < Z-скор Min для пары {}", pairData.getPairName());
            return Optional.of(handleTradeError(pairData, StartTradeErrorType.Z_SCORE_BELOW_MINIMUM));
        }
        return Optional.empty();
    }

    private Optional<ZScoreData> updateZScoreDataForExistingPair(PairData pairData, Settings settings) {
        CandlesRequest request = new CandlesRequest(pairData, settings);
        Map<String, List<Candle>> candlesMap = candlesFeignClient.getApplicableCandlesMap(request);
        return zScoreService.updateZScoreDataForExistingPairBeforeNewTrade(pairData, settings, candlesMap);
    }

    private void logTradeInfo(ZScoreData zScoreData) {
        log.debug(String.format("Наш новый трейд: underValued=%s overValued=%s | p=%.5f | adf=%.5f | z=%.2f | corr=%.2f",
                zScoreData.getUnderValuedTicker(), zScoreData.getOverValuedTicker(),
                zScoreData.getJohansenCointPValue(), zScoreData.getAvgAdfPvalue(), zScoreData.getLatestZScore(), zScoreData.getPearsonCorr()));
    }

    private PairData openTradePosition(PairData pairData, ZScoreData zScoreData, Settings settings) {
        ArbitragePairTradeInfo openResult = tradingIntegrationServiceImpl.openArbitragePair(pairData, settings);

        if (openResult == null || !openResult.isSuccess()) {
            log.debug("⚠️ Не удалось открыть арбитражную пару через торговую систему: {}", pairData.getPairName());
            return handleTradeError(pairData, StartTradeErrorType.TRADE_OPEN_FAILED);
        }

        log.debug("✅ Успешно открыта арбитражная пара: {}", pairData.getPairName());

        pairData.setStatus(TradeStatus.TRADING);

        pairData.setPortfolioBeforeTradeUSDT(openResult.getPortfolioBalanceBeforeTradeUSDT()); // баланс ДО

        TradeResult longTrade = openResult.getLongTradeResult();
        TradeResult shortTrade = openResult.getShortTradeResult();

        pairDataService.addEntryPoints(pairData, zScoreData, longTrade, shortTrade);
        pairDataService.addChanges(pairData);
        pairDataService.save(pairData);

        tradeHistoryService.updateTradeLog(pairData, settings);

        return pairData;
    }

    private PairData handleTradeError(PairData pairData, StartTradeErrorType errorType) {
        log.debug("❌ Ошибка: {} для пары {}", errorType.getDescription(), pairData.getPairName());
        pairData.setStatus(TradeStatus.ERROR);
        pairData.setErrorDescription(errorType.getDescription());
        pairDataService.save(pairData);
        return pairData;
    }
}
