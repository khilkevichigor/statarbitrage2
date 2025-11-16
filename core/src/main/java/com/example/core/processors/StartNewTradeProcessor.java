package com.example.core.processors;

import com.example.core.client.CandlesFeignClient;
import com.example.core.services.*;
import com.example.core.trading.services.TradingIntegrationService;
import com.example.shared.utils.StringUtils;
import com.example.shared.dto.*;
import com.example.shared.enums.TradeStatus;
import com.example.shared.models.Pair;
import com.example.shared.models.Settings;
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
    private final PairService pairService;
    private final SettingsService settingsService;
    private final ZScoreService zScoreService;
    private final TradingIntegrationService tradingIntegrationService;
    private final TradeHistoryService tradeHistoryService;
    private final StartNewTradeValidationService startNewTradeValidationService;
    private final CandlesFeignClient candlesFeignClient;

    @Transactional
    public Pair startNewTrade(StartNewTradeRequest request) {
        startNewTradeValidationService.validateRequest(request);

        final Pair tradingPair = request.getTradingPair();
        final Settings settings = settingsService.getSettings();

        log.info("");
        log.info("🚀 Начинаем новый трейд для пары {} tradingPairId={}...", tradingPair.getPairName(), tradingPair.getId());

        // 1. Предварительная валидация
        Optional<Pair> preValidationError = preValidate(tradingPair, settings);
        if (preValidationError.isPresent()) {
            return preValidationError.get();
        }

        // 2. Получаем и проверяем ZScore данные
        Optional<ZScoreData> maybeZScoreData = updateZScoreDataForExistingPair(tradingPair, settings);
        if (maybeZScoreData.isEmpty()) {
            return handleTradeError(tradingPair, StartTradeErrorType.Z_SCORE_DATA_EMPTY);
        }

        final ZScoreData zScoreData = maybeZScoreData.get();
        pairService.updateZScoreDataCurrent(tradingPair, zScoreData);

        // 3. Валидация тикеров и автотрейдинга
        if (!startNewTradeValidationService.validateTickers(tradingPair, zScoreData)) {
            return handleTradeError(tradingPair, StartTradeErrorType.TICKERS_SWITCHED);
        }
        if (!startNewTradeValidationService.validateAutoTrading(tradingPair, request.isCheckAutoTrading())) {
            return handleTradeError(tradingPair, StartTradeErrorType.AUTO_TRADING_DISABLED);
        }

        // 4. Проверка фильтра снижения zScore
        if (!startNewTradeValidationService.validateZScoreDeclineFilter(zScoreData, settings)) {
            log.warn("⚠️ Фильтр снижения zScore: условие не выполнено для пары {}", tradingPair.getPairName());
            return handleTradeError(tradingPair, StartTradeErrorType.ZSCORE_DECLINE_FILTER_FAILED);
        }

        logTradeInfo(zScoreData);

        // 5. Проверка баланса
        if (!startNewTradeValidationService.validateBalance(tradingPair, settings)) {
            return handleTradeError(tradingPair, StartTradeErrorType.INSUFFICIENT_FUNDS);
        }

        // 6. Открытие позиции
        return openTradePosition(tradingPair, zScoreData, settings);
    }

    private Optional<Pair> preValidate(Pair tradingPair, Settings settings) {
        if (startNewTradeValidationService.isLastZLessThenMinZ(tradingPair, settings)) {
            log.warn("⚠️ Z-скор текущий < Z-скор Min для пары {}", tradingPair.getPairName());
            return Optional.of(handleTradeError(tradingPair, StartTradeErrorType.Z_SCORE_BELOW_MINIMUM));
        }
        return Optional.empty();
    }

    private Optional<ZScoreData> updateZScoreDataForExistingPair(Pair tradingPair, Settings settings) {

        // Определяем правильный порядок тикеров для Python API на основе ожидаемого Z-Score
        List<String> tickersForAPI;
        if (tradingPair.getZScoreCurrent() != null && tradingPair.getZScoreCurrent().doubleValue() > 0) {
            // Для положительного Z-Score (обычные пары): long = undervalued, short = overvalued
            tickersForAPI = List.of(tradingPair.getLongTicker(), tradingPair.getShortTicker());
            log.debug("🔄 Для положительного Z-Score: тикеры в порядке [{}, {}]", 
                     tradingPair.getLongTicker(), tradingPair.getShortTicker());
        } else {
            // Для отрицательного Z-Score или зеркальных пар: меняем порядок
            tickersForAPI = List.of(tradingPair.getShortTicker(), tradingPair.getLongTicker());
            log.debug("🔄 Для отрицательного Z-Score/зеркальной пары: тикеры в порядке [{}, {}]", 
                     tradingPair.getShortTicker(), tradingPair.getLongTicker());
        }

        // Создаем ExtendedCandlesRequest для получения свечей через пагинацию
        ExtendedCandlesRequest request = ExtendedCandlesRequest.builder()
                .timeframe(settings.getTimeframe())
                .candleLimit((int) settings.getCandleLimit())
                .minVolume(settings.getMinVolume() != 0.0 ? settings.getMinVolume() * 1_000_000 : 50_000_000)
                .tickers(tickersForAPI) // Правильный порядок тикеров для API
                .period(settings.calculateCurrentPeriod())
                .untilDate(StringUtils.getCurrentDateTimeWithZ())
                .excludeTickers(null)
                .exchange("OKX")
                .useCache(true)
                .useMinVolumeFilter(true)
                .minimumLotBlacklist(null)
                .build();

        Map<String, List<Candle>> candlesMap = candlesFeignClient.getValidatedCacheExtended(request);
        return zScoreService.updateZScoreDataForExistingPairBeforeNewTrade(tradingPair, settings, candlesMap);
    }

    private void logTradeInfo(ZScoreData zScoreData) {
        log.debug(String.format("Наш новый трейд: underValued=%s overValued=%s | p=%.5f | adf=%.5f | z=%.2f | corr=%.2f",
                zScoreData.getUnderValuedTicker(), zScoreData.getOverValuedTicker(),
                zScoreData.getJohansenCointPValue(), zScoreData.getAvgAdfPvalue(), zScoreData.getLatestZScore(), zScoreData.getPearsonCorr()));
    }

    private Pair openTradePosition(Pair tradingPair, ZScoreData zScoreData, Settings settings) {
        ArbitragePairTradeInfo openResult = tradingIntegrationService.openArbitragePair(tradingPair, settings);

        if (openResult == null || !openResult.isSuccess()) {
            log.debug("⚠️ Не удалось открыть арбитражную пару через торговую систему: {}", tradingPair.getPairName());
            return handleTradeError(tradingPair, StartTradeErrorType.TRADE_OPEN_FAILED);
        }

        log.debug("✅ Успешно открыта арбитражная пара: {}", tradingPair.getPairName());

        tradingPair.setStatus(TradeStatus.TRADING);
        
        // Сохраняем скор при входе в торговлю, если он еще не установлен
        if (tradingPair.getTotalScoreEntry() == null && tradingPair.getTotalScore() != null) {
            tradingPair.setTotalScoreEntry(tradingPair.getTotalScore());
            log.debug("📊 Установлен скор при входе: {} для пары {}", 
                     tradingPair.getTotalScore(), tradingPair.getPairName());
        }

        tradingPair.setPortfolioBeforeTradeUSDT(openResult.getPortfolioBalanceBeforeTradeUSDT()); // баланс ДО

        TradeResult longTrade = openResult.getLongTradeResult();
        TradeResult shortTrade = openResult.getShortTradeResult();

        pairService.addEntryPoints(tradingPair, zScoreData, longTrade, shortTrade);
        pairService.addChanges(tradingPair);
        pairService.save(tradingPair);

        tradeHistoryService.updateTradeLog(tradingPair, settings);

        return tradingPair;
    }

    private Pair handleTradeError(Pair tradingPair, StartTradeErrorType errorType) {
        log.debug("❌ Ошибка: {} для пары {}", errorType.getDescription(), tradingPair.getPairName());
        tradingPair.setStatus(TradeStatus.ERROR);
        tradingPair.setErrorDescription(errorType.getDescription());
        pairService.save(tradingPair);
        return tradingPair;
    }
}
