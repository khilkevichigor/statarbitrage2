package com.example.statarbitrage.core.processors;

import com.example.statarbitrage.common.dto.Candle;
import com.example.statarbitrage.common.dto.ZScoreData;
import com.example.statarbitrage.common.dto.ZScoreParam;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.common.model.TradeStatus;
import com.example.statarbitrage.core.dto.UpdatePairDataRequest;
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
        //todo подумать над тем что бы сделать UUID для пары и передавать его на биржу при открытии сделок!
        // Что бы потом при закрытии или получении инфы об открытых/закрытых сделках было проще идентифицировать их тк монеты могут повторяться и могут быть баги
        // в том ту ли сделку мы нашли или это сделка на бирже совсем старая. За день может быть несколько сделок по одной и той же монете.
        ArbitragePairTradeInfo openResult = tradingIntegrationService.openArbitragePair(pairData);

        if (openResult == null || !openResult.isSuccess()) {
            log.warn("⚠️ Не удалось открыть арбитражную пару через торговую систему: {}/{}",
                    pairData.getLongTicker(), pairData.getShortTicker());
            return handleTradeError(pairData, StartTradeErrorType.TRADE_OPEN_FAILED);
        }

        Map<String, List<Candle>> candlesMap = candlesService.getApplicableCandlesMap(pairData, settings);
        return finalizeSuccessfulTrade(pairData, zScoreData, openResult, settings, candlesMap);
    }

    private PairData finalizeSuccessfulTrade(PairData pairData, ZScoreData zScoreData,
                                             ArbitragePairTradeInfo openResult, Settings settings,
                                             Map<String, List<Candle>> candlesMap) {
        TradeResult openLongTradeResult = openResult.getLongTradeResult();
        TradeResult openShortTradeResult = openResult.getShortTradeResult();

        log.info("✅ Успешно открыта арбитражная пара через торговую систему: {}/{}",
                pairData.getLongTicker(), pairData.getShortTicker());

        pairData.setStatus(TradeStatus.TRADING);

        pairDataService.updateCurrentDataAndSave(UpdatePairDataRequest.builder()
                .isAddEntryPoints(true)
                .pairData(pairData)
                .zScoreData(zScoreData)
                .candlesMap(candlesMap)
                .tradeResultLong(openLongTradeResult)
                .tradeResultShort(openShortTradeResult)
                .isUpdateChanges(true)
                .isUpdateTradeLog(true)
                .settings(settings)
                .build());

//        pairDataService.updateChangesAndSave(pairData);
//        tradeLogService.updateTradeLog(pairData, settings);

        return pairData;
    }

    private PairData handleTradeError(PairData pairData, StartTradeErrorType errorType) {
        log.error("❌ Ошибка: {} для пары {}/{}", errorType.getDescription(),
                pairData.getLongTicker(), pairData.getShortTicker());

        pairData.setStatus(errorType.getStatus());
        pairDataService.save(pairData);
        return pairData;
    }
}
