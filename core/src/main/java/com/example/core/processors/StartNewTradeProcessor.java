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
//        Optional<Pair> preValidationError = preValidate(pair, settings);
//        if (preValidationError.isPresent()) {
//            return preValidationError.get();
//        }

        if (startNewTradeValidationService.isLastZLessThenMinZ(pair, settings)) {
            log.warn("⚠️ Z-скор текущий < Z-скор Min для пары {}", pair.getPairName());
            return handleTradeError(pair, StartTradeErrorType.Z_SCORE_BELOW_MINIMUM);
        }

        // 2. Получаем и проверяем ZScore данные
        // Используем уже рассчитанные данные из созданной пары вместо пересчета
        ZScoreData zScoreData = createZScoreDataFromPair(pair);
        if (zScoreData == null) {
            return handleTradeError(pair, StartTradeErrorType.Z_SCORE_DATA_EMPTY);
        }

        // Данные уже актуальные, обновление не требуется
        // pairService.updateZScoreDataCurrent(pair, zScoreData);

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

        // 5. Проверка баланса
        if (!startNewTradeValidationService.validateBalance(pair, settings)) {
            return handleTradeError(pair, StartTradeErrorType.INSUFFICIENT_FUNDS);
        }

        logTradeInfo(zScoreData);

        // 6. Открытие позиции
        return openTradePosition(pair, zScoreData, settings);
    }

//    private Optional<Pair> preValidate(Pair pair, Settings settings) {
//        if (startNewTradeValidationService.isLastZLessThenMinZ(pair, settings)) {
//            log.warn("⚠️ Z-скор текущий < Z-скор Min для пары {}", pair.getPairName());
//            return Optional.of(handleTradeError(pair, StartTradeErrorType.Z_SCORE_BELOW_MINIMUM));
//        }
//        return Optional.empty();
//    }

//    private Optional<ZScoreData> updateZScoreDataForExistingPair(Pair pair, Settings settings) {
//        // Создаем ExtendedCandlesRequest для получения свечей через пагинацию
//        ExtendedCandlesRequest request = ExtendedCandlesRequest.builder()
//                .timeframe(settings.getTimeframe())
//                .candleLimit((int) settings.getCandleLimit())
//                .minVolume(settings.getMinVolume() != 0.0 ? settings.getMinVolume() * 1_000_000 : 50_000_000)
//                .tickers(List.of(pair.getLongTicker(), pair.getShortTicker()))
//                .period(settings.calculateCurrentPeriod())
//                .untilDate(StringUtils.getCurrentDateTimeWithZ())
//                .excludeTickers(null)
//                .exchange("OKX")
//                .useCache(true)
//                .useMinVolumeFilter(true)
//                .minimumLotBlacklist(null)
//                .sorted(false)
//                .build();
//
//        Map<String, List<Candle>> candlesMap = candlesFeignClient.getValidatedCacheExtended(request);
//        return zScoreService.updateZScoreDataForExistingPairBeforeNewTrade(pair, settings, candlesMap);
//    }

    private void logTradeInfo(ZScoreData zScoreData) {
        log.debug(String.format("Наш новый трейд: underValued=%s overValued=%s | p=%.5f | adf=%.5f | z=%.2f | corr=%.2f",
                zScoreData.getUnderValuedTicker(), zScoreData.getOverValuedTicker(),
                zScoreData.getJohansenCointPValue(), zScoreData.getAvgAdfPvalue(), zScoreData.getLatestZScore(), zScoreData.getPearsonCorr()));
    }

    private Pair openTradePosition(Pair pair, ZScoreData zScoreData, Settings settings) {
        ArbitragePairTradeInfo openResult = tradingIntegrationService.openArbitragePair(pair, settings);

        if (openResult == null || !openResult.isSuccess()) {
            log.warn("⚠️ Не удалось открыть арбитражную пару через торговую систему: {}", pair.getPairName());
            return handleTradeError(pair, StartTradeErrorType.TRADE_OPEN_FAILED);
        }

        log.info("✅ Успешно открыта арбитражная пара: {}", pair.getPairName());

        pair.setStatus(TradeStatus.TRADING);
        
        // Переводим пару в статус активной торговли
        pair.setType(PairType.IN_TRADING);
        
        // Сохраняем скор при входе в торговлю, если он еще не установлен
        if (pair.getTotalScoreEntry() == null && pair.getTotalScore() != null) {
            pair.setTotalScoreEntry(pair.getTotalScore());
            log.info("📊 Установлен скор при входе: {} для пары {}",
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
        
        // Если пара типа FETCHED, удаляем её вместо сохранения с ошибкой
        if (PairType.FETCHED.equals(pair.getType())) {
            log.info("🗑️ Удаляем FETCHED пару {} из-за ошибки: {}", pair.getPairName(), errorType.getDescription());
            pairService.delete(pair);
            return pair; // Возвращаем пару для логирования, но она уже удалена
        }
        
        pair.setStatus(TradeStatus.ERROR);
        pair.setErrorDescription(errorType.getDescription());
        pairService.save(pair);
        return pair;
    }

    /**
     * Создает ZScoreData объект из текущих данных пары без пересчета
     * Используется для сохранения переворота пары, сделанного в CreatePairDataService
     */
    private ZScoreData createZScoreDataFromPair(Pair pair) {
        log.debug("📋 Используем существующие данные Z-Score из пары {} (без пересчета)", pair.getPairName());

        ZScoreData zScoreData = new ZScoreData();

        // Устанавливаем тикеры
        zScoreData.setUnderValuedTicker(pair.getLongTicker());
        zScoreData.setOverValuedTicker(pair.getShortTicker());

        // Получаем timestamp последней точки из истории или используем текущее время
        long lastTimestamp = System.currentTimeMillis();
        if (pair.getZScoreHistory() != null && !pair.getZScoreHistory().isEmpty()) {
            lastTimestamp = pair.getZScoreHistory().get(pair.getZScoreHistory().size() - 1).getTimestamp();
        }

        // Создаем актуальный ZScoreParam из текущих данных пары
        ZScoreParam currentParam = ZScoreParam.builder()
                .zscore(pair.getZScoreCurrent() != null ? pair.getZScoreCurrent().doubleValue() : 0.0)
                .pvalue(pair.getPValueCurrent() != null ? pair.getPValueCurrent().doubleValue() : 0.0)
                .adfpvalue(pair.getAdfPvalueCurrent() != null ? pair.getAdfPvalueCurrent().doubleValue() : 0.0)
                .correlation(pair.getCorrelationCurrent() != null ? pair.getCorrelationCurrent().doubleValue() : 0.0)
                .mean(pair.getMeanCurrent() != null ? pair.getMeanCurrent().doubleValue() : 0.0)
                .std(pair.getStdCurrent() != null ? pair.getStdCurrent().doubleValue() : 0.0)
                .spread(pair.getSpreadCurrent() != null ? pair.getSpreadCurrent().doubleValue() : 0.0)
                .alpha(pair.getAlphaCurrent() != null ? pair.getAlphaCurrent().doubleValue() : 0.0)
                .beta(pair.getBetaCurrent() != null ? pair.getBetaCurrent().doubleValue() : 1.0)
                .timestamp(lastTimestamp)
                .build();

        // Устанавливаем данные в ZScoreData
        zScoreData.setLatestZScore(currentParam.getZscore());
        zScoreData.setJohansenCointPValue(currentParam.getPvalue());
        zScoreData.setAvgAdfPvalue(currentParam.getAdfpvalue());
        zScoreData.setPearsonCorr(currentParam.getCorrelation());

        // Используем всю историю Z-Score из пары для прохождения фильтра снижения
        if (pair.getZScoreHistory() != null && !pair.getZScoreHistory().isEmpty()) {
            zScoreData.setZScoreHistory(pair.getZScoreHistory());
            log.debug("📊 Восстановлена история Z-Score: {} точек", pair.getZScoreHistory().size());
        } else {
            // Если истории нет, создаем минимальную для совместимости
            zScoreData.setZScoreHistory(List.of(currentParam));
            log.debug("📊 История Z-Score отсутствует, создана точка для совместимости");
        }

        // Устанавливаем коинтеграцию как true (поскольку пара уже создана)
        zScoreData.setJohansenIsCoint(true);

        // Базовые значения для совместимости
        zScoreData.setAvgRSquared(0.8);
        zScoreData.setStablePeriods(100);
        zScoreData.setTotalObservations(pair.getCandleCount() != null ? pair.getCandleCount() : 1000);

        log.debug("✅ Z-Score данные восстановлены из пары: z={}, p={}, adf={}, corr={}",
                zScoreData.getLatestZScore(), zScoreData.getJohansenCointPValue(),
                zScoreData.getAvgAdfPvalue(), zScoreData.getPearsonCorr());

        return zScoreData;
    }
}
