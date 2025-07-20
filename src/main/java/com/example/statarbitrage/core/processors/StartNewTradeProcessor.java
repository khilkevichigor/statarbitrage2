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
import java.util.Objects;
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
    private final TradeLogService tradeLogService;
    private final CalculateChangesService calculateChangesService;
    private final EntryPointService entryPointService;

    @Transactional
    public PairData startNewTrade(StartNewTradeRequest request) {
        validateRequest(request);

        PairData pairData = request.getPairData();
        Settings settings = settingsService.getSettings();

        log.info("🚀 Начинаем новый трейд для {} - {}", pairData.getLongTicker(), pairData.getShortTicker());

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

        updateZScoreDataCurrent(pairData, zScoreData);

        // Проверка корректности тикеров
        if (!validateTickers(pairData, zScoreData)) {
            return handleTradeError(pairData, StartTradeErrorType.TICKERS_SWITCHED);
        }

        // Проверка автотрейдинга
        if (!validateAutoTrading(pairData, request.isCheckAutoTrading())) {
            return handleTradeError(pairData, StartTradeErrorType.AUTO_TRADING_DISABLED);
        }

        logTradeInfo(zScoreData);

        // Проверка баланса
        if (!validateBalance(pairData)) {
            return handleTradeError(pairData, StartTradeErrorType.INSUFFICIENT_FUNDS);
        }

        // Открытие позиции
        return openTradePosition(pairData, zScoreData, settings);
    }

    private void updateZScoreDataCurrent(PairData pairData, ZScoreData zScoreData) {
        ZScoreParam latestParam = zScoreData.getLastZScoreParam();
        pairData.setZScoreCurrent(latestParam.getZscore());
        pairData.setCorrelationCurrent(latestParam.getCorrelation());
        pairData.setAdfPvalueCurrent(latestParam.getAdfpvalue());
        pairData.setPValueCurrent(latestParam.getPvalue());
        pairData.setMeanCurrent(latestParam.getMean());
        pairData.setStdCurrent(latestParam.getStd());
        pairData.setSpreadCurrent(latestParam.getSpread());
        pairData.setAlphaCurrent(latestParam.getAlpha());
        pairData.setBetaCurrent(latestParam.getBeta());

        // Добавляем новые точки в историю Z-Score при каждом обновлении
        if (zScoreData.getZscoreParams() != null && !zScoreData.getZscoreParams().isEmpty()) {
            // Добавляем всю новую историю из ZScoreData
            for (ZScoreParam param : zScoreData.getZscoreParams()) {
                pairData.addZScorePoint(param);
            }
        } else {
            // Если новой истории нет, добавляем хотя бы текущую точку
            pairData.addZScorePoint(latestParam);
        }
    }

    private void validateRequest(StartNewTradeRequest request) {
        if (request == null || request.getPairData() == null) {
            throw new IllegalArgumentException("Неверный запрос на начало нового трейда");
        }
    }

    private PairData performPreValidation(PairData pairData, Settings settings) {
        if (isLastZLessThenMinZ(pairData, settings)) {
            log.warn("ZCurrent < ZMin для пары {} - {}", pairData.getLongTicker(), pairData.getShortTicker());
            return handleTradeError(pairData, StartTradeErrorType.Z_SCORE_BELOW_MINIMUM);
        }
        return null;
    }

    private boolean isLastZLessThenMinZ(PairData pairData, Settings settings) {
        if (pairData == null) {
            throw new IllegalArgumentException("pairData is null");
        }

        double zScore = pairData.getZScoreCurrent();
        if (zScore < settings.getMinZ()) {
            if (zScore < 0) {
                log.warn("Skip this pair {} - {}. Z-score {} < 0",
                        pairData.getLongTicker(),
                        pairData.getShortTicker(),
                        zScore);
            } else {
                log.warn("Skip this pair {} - {}. Z-score {} < minZ {}",
                        pairData.getLongTicker(),
                        pairData.getShortTicker(),
                        zScore,
                        settings.getMinZ());
            }
            return true;
        }

        return false;
    }

    private ZScoreData calculateAndValidateZScoreData(PairData pairData, Settings settings) {
        Map<String, List<Candle>> candlesMap = candlesService.getApplicableCandlesMap(pairData, settings);
        Optional<ZScoreData> maybeZScoreData = zScoreService.calculateZScoreDataForNewTrade(pairData, settings, candlesMap);

        if (maybeZScoreData.isEmpty()) {
            log.warn("📊 ZScore данные пусты для пары {}/{}", pairData.getLongTicker(), pairData.getShortTicker());
            handleTradeError(pairData, StartTradeErrorType.Z_SCORE_DATA_EMPTY);
            return null;
        }

        return maybeZScoreData.get();
    }

    private boolean validateTickers(PairData pairData, ZScoreData zScoreData) {
        return Objects.equals(pairData.getLongTicker(), zScoreData.getUndervaluedTicker()) &&
                Objects.equals(pairData.getShortTicker(), zScoreData.getOvervaluedTicker());
    }

    private boolean validateAutoTrading(PairData pairData, boolean checkAutoTrading) {
        if (!checkAutoTrading) {
            log.info("🔧 Ручной запуск трейда - проверка автотрейдинга пропущена для пары {} - {}",
                    pairData.getLongTicker(), pairData.getShortTicker());
            return true;
        }

        Settings currentSettings = settingsService.getSettings();
        log.debug("📖 Процессор: Читаем настройки из БД: autoTrading={}", currentSettings.isAutoTradingEnabled());

        if (!currentSettings.isAutoTradingEnabled()) {
            log.warn("🛑 Автотрейдинг отключен! Пропускаю открытие нового трейда для пары {} - {}",
                    pairData.getLongTicker(), pairData.getShortTicker());
            return false;
        }

        log.debug("✅ Процессор: Автотрейдинг включен, продолжаем");
        return true;
    }

    private void logTradeInfo(ZScoreData zScoreData) {
        ZScoreParam latest = zScoreData.getLastZScoreParam();
        log.info(String.format("Наш новый трейд: underValued=%s overValued=%s | p=%.5f | adf=%.5f | z=%.2f | corr=%.2f",
                zScoreData.getUndervaluedTicker(), zScoreData.getOvervaluedTicker(),
                latest.getPvalue(), latest.getAdfpvalue(), latest.getZscore(), latest.getCorrelation()));
    }

    private boolean validateBalance(PairData pairData) {
        if (!tradingIntegrationService.canOpenNewPair()) {
            log.warn("⚠️ Недостаточно средств в торговом депо для открытия пары {}/{}",
                    pairData.getLongTicker(), pairData.getShortTicker());
            return false;
        }
        return true;
    }

    private PairData openTradePosition(PairData pairData, ZScoreData zScoreData, Settings settings) {
        ArbitragePairTradeInfo openResult = tradingIntegrationService.openArbitragePair(pairData); //todo передавать UUID?

        if (openResult == null || !openResult.isSuccess()) {
            log.warn("⚠️ Не удалось открыть арбитражную пару через торговую систему: {}/{}",
                    pairData.getLongTicker(), pairData.getShortTicker());
            return handleTradeError(pairData, StartTradeErrorType.TRADE_OPEN_FAILED);
        }

        return finalizeSuccessfulTrade(pairData, zScoreData, openResult, settings);
    }

    private PairData finalizeSuccessfulTrade(PairData pairData, ZScoreData zScoreData,
                                             ArbitragePairTradeInfo openResult, Settings settings) {

        TradeResult openLongTradeResult = openResult.getLongTradeResult();
        TradeResult openShortTradeResult = openResult.getShortTradeResult();

        log.info("✅ Успешно открыта арбитражная пара через торговую систему: {}/{}",
                pairData.getLongTicker(), pairData.getShortTicker());

        pairData.setStatus(TradeStatus.TRADING);

        Map<String, List<Candle>> candlesMap = candlesService.getApplicableCandlesMap(pairData, settings);

        candlesService.addCurrentPricesFromCandles(pairData, candlesMap);
//        List<Candle> longTickerCandles = candlesMap.get(pairData.getLongTicker());
//        List<Candle> shortTickerCandles = candlesMap.get(pairData.getShortTicker());
//        double longTickerCurrentPrice = CandlesUtil.getLastClose(longTickerCandles);
//        double shortTickerCurrentPrice = CandlesUtil.getLastClose(shortTickerCandles);
//        pairData.setLongTickerCurrentPrice(longTickerCurrentPrice);
//        pairData.setShortTickerCurrentPrice(shortTickerCurrentPrice);

        entryPointService.addEntryPoints(pairData, zScoreData, openLongTradeResult, openShortTradeResult);
//        ZScoreParam latestParam = zScoreData.getLastZScoreParam();
//        pairData.setLongTickerEntryPrice(openLongTradeResult.getExecutionPrice().doubleValue());
//        pairData.setShortTickerEntryPrice(openShortTradeResult.getExecutionPrice().doubleValue());
//        pairData.setZScoreEntry(latestParam.getZscore());
//        pairData.setCorrelationEntry(latestParam.getCorrelation());
//        pairData.setAdfPvalueEntry(latestParam.getAdfpvalue());
//        pairData.setPValueEntry(latestParam.getPvalue());
//        pairData.setMeanEntry(latestParam.getMean());
//        pairData.setStdEntry(latestParam.getStd());
//        pairData.setSpreadEntry(latestParam.getSpread());
//        pairData.setAlphaEntry(latestParam.getAlpha());
//        pairData.setBetaEntry(latestParam.getBeta());
//        // Время входа
//        pairData.setEntryTime(openLongTradeResult.getExecutionTime().atZone(java.time.ZoneId.systemDefault()).toEpochSecond() * 1000);
//
//        log.info("🔹Установлены точки входа: LONG {{}} = {}, SHORT {{}} = {}, Z = {}",
//                pairData.getLongTicker(), pairData.getLongTickerEntryPrice(),
//                pairData.getShortTicker(), pairData.getShortTickerEntryPrice(),
//                pairData.getZScoreEntry());

        pairDataService.save(pairData);

        calculateChangesService.updateChangesFromOpenPositions(pairData);
        tradeLogService.updateTradeLog(pairData, settings);
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
