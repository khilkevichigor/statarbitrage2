package com.example.statarbitrage.core.processors;

import com.example.statarbitrage.common.dto.Candle;
import com.example.statarbitrage.common.dto.ZScoreData;
import com.example.statarbitrage.common.dto.ZScoreParam;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.common.model.TradeStatus;
import com.example.statarbitrage.core.services.*;
import com.example.statarbitrage.trading.model.ArbitragePairTradeInfo;
import com.example.statarbitrage.trading.model.TradeResult;
import com.example.statarbitrage.trading.services.TradingIntegrationService;
import com.example.statarbitrage.ui.dto.StartNewTradeRequest;
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
    private final CandlesService candlesService;
    private final SettingsService settingsService;
    private final ZScoreService zScoreService;
    private final TradingIntegrationService tradingIntegrationService;
    private final TradeHistoryService tradeHistoryService;
    private final StartNewTradeValidationService startNewTradeValidationService;

    @Transactional
    public PairData startNewTrade(StartNewTradeRequest request) {
        startNewTradeValidationService.validateRequest(request);

        PairData pairData = request.getPairData();
        Settings settings = settingsService.getSettings();

        log.info("🚀 Начинаем новый трейд для {}/{}", pairData.getLongTicker(), pairData.getShortTicker());

        // Предварительные проверки
        PairData validationResult = performPreValidation(pairData, settings);
        if (validationResult != null) {
            return validationResult;
        }

        // Получение и проверка данных
        ZScoreData zScoreData = calculateAndValidateZScoreData(pairData, settings);
        if (zScoreData == null) {
            return pairData;
        }

        pairDataService.updateZScoreDataCurrent(pairData, zScoreData);

        // Проверка корректности тикеров
        if (!startNewTradeValidationService.validateTickers(pairData, zScoreData)) {
            return handleTradeError(pairData, StartTradeErrorType.TICKERS_SWITCHED);
        }

        // Проверка автотрейдинга
        if (!startNewTradeValidationService.validateAutoTrading(pairData, request.isCheckAutoTrading())) {
            return handleTradeError(pairData, StartTradeErrorType.AUTO_TRADING_DISABLED);
        }

        logTradeInfo(zScoreData);

        // Проверка баланса
        if (!startNewTradeValidationService.validateBalance(pairData)) {
            return handleTradeError(pairData, StartTradeErrorType.INSUFFICIENT_FUNDS);
        }

        // Открытие позиции
        return openTradePosition(pairData, zScoreData, settings);
    }

    private PairData performPreValidation(PairData pairData, Settings settings) {
        if (startNewTradeValidationService.isLastZLessThenMinZ(pairData, settings)) {
            log.warn("⚠️ Z-скор текущий < Z-скор Min для пары {}/{}", pairData.getLongTicker(), pairData.getShortTicker());
            return handleTradeError(pairData, StartTradeErrorType.Z_SCORE_BELOW_MINIMUM);
        }
        return null;
    }

    private ZScoreData calculateAndValidateZScoreData(PairData pairData, Settings settings) {
        Map<String, List<Candle>> candlesMap = candlesService.getApplicableCandlesMap(pairData, settings);
        Optional<ZScoreData> maybeZScoreData = zScoreService.calculateZScoreDataForNewTrade(pairData, settings, candlesMap);

        if (maybeZScoreData.isEmpty()) {
            log.warn("⚠️ ZScore данные пусты для пары {}/{}", pairData.getLongTicker(), pairData.getShortTicker());
            handleTradeError(pairData, StartTradeErrorType.Z_SCORE_DATA_EMPTY);
            return null;
        }

        return maybeZScoreData.get();
    }

    private void logTradeInfo(ZScoreData zScoreData) {
        ZScoreParam latest = zScoreData.getLastZScoreParam();
        log.info(String.format("Наш новый трейд: underValued=%s overValued=%s | p=%.5f | adf=%.5f | z=%.2f | corr=%.2f",
                zScoreData.getUndervaluedTicker(), zScoreData.getOvervaluedTicker(),
                latest.getPvalue(), latest.getAdfpvalue(), latest.getZscore(), latest.getCorrelation()));
    }

    private PairData openTradePosition(PairData pairData, ZScoreData zScoreData, Settings settings) {
        ArbitragePairTradeInfo openResult = tradingIntegrationService.openArbitragePair(pairData, settings); //todo передавать UUID?

        if (openResult == null || !openResult.isSuccess()) {
            log.warn("⚠️ Не удалось открыть арбитражную пару через торговую систему: {}/{}",
                    pairData.getLongTicker(), pairData.getShortTicker());
            return handleTradeError(pairData, StartTradeErrorType.TRADE_OPEN_FAILED);
        }

        log.info("✅ Успешно открыта арбитражная пара через торговую систему: {}/{}",
                pairData.getLongTicker(), pairData.getShortTicker());

        pairData.setStatus(TradeStatus.TRADING);

        TradeResult openLongTradeResult = openResult.getLongTradeResult();
        TradeResult openShortTradeResult = openResult.getShortTradeResult();

        pairDataService.addEntryPoints(pairData, zScoreData, openLongTradeResult, openShortTradeResult);

        pairDataService.addChanges(pairData);

        pairDataService.save(pairData);

        tradeHistoryService.updateTradeLog(pairData, settings);

        return pairData;
    }

    private PairData handleTradeError(PairData pairData, StartTradeErrorType errorType) {
        log.error("❌ Ошибка: {} для пары {}/{}", errorType.getDescription(),
                pairData.getLongTicker(), pairData.getShortTicker());

        pairData.setStatus(TradeStatus.ERROR);
        pairData.setErrorDescription(errorType.getDescription());
        pairDataService.save(pairData);
        return pairData;
    }
}
