package com.example.core.processors;

import com.example.core.client.CandlesFeignClient;
import com.example.core.services.*;
import com.example.core.trading.services.TradingIntegrationService;
import com.example.shared.enums.PairType;
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

        final Pair pair = request.getTradingPair();
        final Settings settings = settingsService.getSettings();

        log.info("");
        log.info("🚀 Начинаем новый трейд для пары {} Id={}...", pair.getPairName(), pair.getId());

        // 1. Предварительная валидация
        Optional<Pair> preValidationError = preValidate(pair, settings);
        if (preValidationError.isPresent()) {
            return preValidationError.get();
        }

        // 2. Получаем и проверяем ZScore данные
        Optional<ZScoreData> maybeZScoreData = updateZScoreDataForExistingPair(pair, settings);
        if (maybeZScoreData.isEmpty()) {
            return handleTradeError(pair, StartTradeErrorType.Z_SCORE_DATA_EMPTY);
        }

        final ZScoreData zScoreData = maybeZScoreData.get();
        pairService.updateZScoreDataCurrent(pair, zScoreData);

        // 3. Валидация тикеров и автотрейдинга
        if (!startNewTradeValidationService.validateTickers(pair, zScoreData)) {
            return handleTradeError(pair, StartTradeErrorType.TICKERS_SWITCHED);
        }
        if (!startNewTradeValidationService.validateAutoTrading(pair, request.isCheckAutoTrading())) {
            return handleTradeError(pair, StartTradeErrorType.AUTO_TRADING_DISABLED);
        }

        // 4. Проверка фильтра снижения zScore
        if (!startNewTradeValidationService.validateZScoreDeclineFilter(zScoreData, settings)) {
            log.warn("⚠️ Фильтр снижения zScore: условие не выполнено для пары {}", pair.getPairName());
            return handleTradeError(pair, StartTradeErrorType.ZSCORE_DECLINE_FILTER_FAILED);
        }

        logTradeInfo(zScoreData);

        // 5. Проверка баланса
        if (!startNewTradeValidationService.validateBalance(pair, settings)) {
            return handleTradeError(pair, StartTradeErrorType.INSUFFICIENT_FUNDS);
        }

        // 6. Открытие позиции
        return openTradePosition(pair, zScoreData, settings);
    }

    private Optional<Pair> preValidate(Pair pair, Settings settings) {
        if (startNewTradeValidationService.isLastZLessThenMinZ(pair, settings)) {
            log.warn("⚠️ Z-скор текущий < Z-скор Min для пары {}", pair.getPairName());
            return Optional.of(handleTradeError(pair, StartTradeErrorType.Z_SCORE_BELOW_MINIMUM));
        }
        return Optional.empty();
    }

    private Optional<ZScoreData> updateZScoreDataForExistingPair(Pair pair, Settings settings) {
        // Создаем ExtendedCandlesRequest для получения свечей через пагинацию
        ExtendedCandlesRequest request = ExtendedCandlesRequest.builder()
                .timeframe(settings.getTimeframe())
                .candleLimit((int) settings.getCandleLimit())
                .minVolume(settings.getMinVolume() != 0.0 ? settings.getMinVolume() * 1_000_000 : 50_000_000)
                .tickers(List.of(pair.getLongTicker(), pair.getShortTicker()))
                .period(settings.calculateCurrentPeriod())
                .untilDate(StringUtils.getCurrentDateTimeWithZ())
                .excludeTickers(null)
                .exchange("OKX")
                .useCache(true)
                .useMinVolumeFilter(true)
                .minimumLotBlacklist(null)
                .sorted(false)
                .build();

        Map<String, List<Candle>> candlesMap = candlesFeignClient.getValidatedCacheExtended(request);
        return zScoreService.updateZScoreDataForExistingPairBeforeNewTrade(pair, settings, candlesMap);
    }

    private void logTradeInfo(ZScoreData zScoreData) {
        log.debug(String.format("Наш новый трейд: underValued=%s overValued=%s | p=%.5f | adf=%.5f | z=%.2f | corr=%.2f",
                zScoreData.getUnderValuedTicker(), zScoreData.getOverValuedTicker(),
                zScoreData.getJohansenCointPValue(), zScoreData.getAvgAdfPvalue(), zScoreData.getLatestZScore(), zScoreData.getPearsonCorr()));
    }

    private Pair openTradePosition(Pair pair, ZScoreData zScoreData, Settings settings) {
        ArbitragePairTradeInfo openResult = tradingIntegrationService.openArbitragePair(pair, settings);

        if (openResult == null || !openResult.isSuccess()) {
            log.debug("⚠️ Не удалось открыть арбитражную пару через торговую систему: {}", pair.getPairName());
            return handleTradeError(pair, StartTradeErrorType.TRADE_OPEN_FAILED);
        }

        log.debug("✅ Успешно открыта арбитражная пара: {}", pair.getPairName());

        pair.setStatus(TradeStatus.TRADING);
        
        // Переводим пару в статус активной торговли
        pair.setType(PairType.IN_TRADING);
        
        // Сохраняем скор при входе в торговлю, если он еще не установлен
        if (pair.getTotalScoreEntry() == null && pair.getTotalScore() != null) {
            pair.setTotalScoreEntry(pair.getTotalScore());
            log.debug("📊 Установлен скор при входе: {} для пары {}", 
                     pair.getTotalScore(), pair.getPairName());
        }

        pair.setPortfolioBeforeTradeUSDT(openResult.getPortfolioBalanceBeforeTradeUSDT()); // баланс ДО

        TradeResult longTrade = openResult.getLongTradeResult();
        TradeResult shortTrade = openResult.getShortTradeResult();

        pairService.addEntryPoints(pair, zScoreData, longTrade, shortTrade);
        pairService.addChanges(pair);
        pairService.save(pair);

        tradeHistoryService.updateTradeLog(pair, settings);

        return pair;
    }

    private Pair handleTradeError(Pair pair, StartTradeErrorType errorType) {
        log.debug("❌ Ошибка: {} для пары {}", errorType.getDescription(), pair.getPairName());
        pair.setStatus(TradeStatus.ERROR);
        pair.setErrorDescription(errorType.getDescription());
        pairService.save(pair);
        return pair;
    }
}
