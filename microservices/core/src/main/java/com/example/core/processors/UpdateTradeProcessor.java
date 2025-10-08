package com.example.core.processors;

import com.example.core.client.CandlesFeignClient;
import com.example.core.messaging.SendEventService;
import com.example.core.services.*;
import com.example.core.trading.interfaces.TradingProvider;
import com.example.core.trading.services.TradingIntegrationService;
import com.example.core.trading.services.TradingProviderFactory;
import com.example.shared.dto.*;
import com.example.shared.enums.TradeStatus;
import com.example.shared.events.rabbit.CoreEvent;
import com.example.shared.models.Pair;
import com.example.shared.models.Settings;
import com.example.shared.utils.FormatUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateTradeProcessor {
    private final PairService pairService;
    private final SettingsService settingsService;
    private final TradeHistoryService tradeHistoryService;
    private final ZScoreService zScoreService;
    private final TradingIntegrationService tradingIntegrationServiceImpl;
    private final ExitStrategyService exitStrategyService;
    private final SendEventService sendEventService;
    private final ChartService chartService;
    private final AveragingService averagingService;
    private final TradingProviderFactory tradingProviderFactory;
    private final CandlesFeignClient candlesFeignClient;
    private final UpdateZScoreDataCurrentService updateZScoreDataCurrentService;


    //todo сделать проверку zScore - что он пересекал +3 и -3 несколько раз - говорит о том что пара гуляет туда-сюда
    //todo сохранять в пару начальное плечо и маржу тк настройки могут быть изменены а усреднение делается по настройкам
    //todo подумать о том что бы брать больше 300 свечей, и может сразу для нескольких ТФ что бы в чарте переключаться и смотреть что происходит
    //todo система очков для прибыльных/убыточных пар через бд - сделать таблицу торгуемых пар! начислять баллы по профиту! при отборе новых пар - брать былллы из этой таблицы

    //todo колонки min/max для лонг и шорт на UI
    //todo на чарт в тайтл выводить профит, цены монет, з-скор, уровень пиксельного спреда
    //todo выводить стату по среднему времени timeToMin/Max для анализа и подстройки Settings

    //todo через телегу получить трейдинг пары, закрыть все, перевести в бу, вкл/откл автотрейдинг
    //todo в updateTrade() добавить быстрый чек коинтеграции и отображать на UI в виде зеленого, желтого, красного флага (гуд, ухудшилась, ушла) - добавил лог с галочками и предупреждениями
    //todo сделать анализатор - будет следить за одной парой с построением з-скора и горизонталкой точки входа (если чарт уйдет далеко и вертикалка исчезнет) - отдельная вкладка и таблица как для ТРЕЙДИНГ пар что бы можно было видеть поведение - как ходит z-скор на долгосрок
    //todo добавить проверку в updateTrade() или отдельно - "если есть открытые позиции а пар нету!" - может не актуально тк при запуске мы берем позиции из бд и обновляем пары

    // ? todo сделать быструю проверку профита и только потом коинтеграции что бы минимизировать убыток - не получится тк сложно - нужно все делать вместе - и считать z-скор по хорошему
    // ? todo если закрылись с убытком (стоп или еще что) то следующая пара должна быть другой а не то что только что принесло убыток - может и не надо тк вероятность разворота тоже есть
    // ? todo сделать колонку максимальная просадка по профиту USDT (%) - может и не надо тк есть время до мин/макс

    // +/- todo сделать колонку максимальная просадка по Z-скор (ПРОВЕРИТЬ)

    // + todo сделать кнопку к паре "усреднить" (если коинтеграция еще не ушла, ну или самому смотреть и усреднять как посчитаешь)
    // + todo пофиксить чарт наложенных цен - диапазон отображения меньше или уменьшается и чарт съезжает вправо когда выбираешь пиксельный или zscore спред
    // + todo экспорт закрытых сделок в csv
    // + todo добавить чарт профита под чарт z-скор (не работает)
    // + todo добавить колонку "время жизни"
    // + todo Position в бд а не в мапу - чтобы не терять трейды при перезапуске
    // + todo точка на чарте профита что бы было лучше видно где последнее значение

    @Transactional
    public Pair updateTrade(UpdateTradeRequest request) {
        validateRequest(request);

        final Pair tradingPair = loadFreshPairData(request.getTradingPair());
        if (tradingPair == null) {
            return request.getTradingPair();
        }
        log.info("");
        log.info("🔄 Обновление пары {}...", tradingPair.getPairName());

        final Settings settings = settingsService.getSettings();

        //todo здесь сетить настройки в пару для дальнейшей аналитики чатЖПТ
        pairService.updateSettingsParam(tradingPair, settings);

        TradingProvider provider = tradingProviderFactory.getCurrentProvider();
        provider.updatePositionPrices(List.of(tradingPair.getLongTicker(), tradingPair.getShortTicker()));

        if (arePositionsClosed(tradingPair)) {
            return handleNoOpenPositions(tradingPair);
        }

        final Map<String, Object> cointegrationData = updateZScoreDataForExistingPair(tradingPair, settings);
        final ZScoreData zScoreData = (ZScoreData) cointegrationData.get("zScoreData");
        final Map<String, List<Candle>> candlesMap = (Map<String, List<Candle>>) cointegrationData.get("candlesMap");

        tradingPair.setLongTickerCandles(candlesMap.get(tradingPair.getLongTicker()));
        tradingPair.setShortTickerCandles(candlesMap.get(tradingPair.getShortTicker()));

        logPairInfo(zScoreData, settings);

        pairService.updateZScoreDataCurrent(tradingPair, zScoreData);

        // Обновляем пиксельный спред синхронно с Z-Score и ценами
        log.debug("🔢 Обновляем пиксельный спред для пары {}", tradingPair.getPairName());
        chartService.calculatePixelSpreadIfNeeded(tradingPair); // Инициализация при первом запуске
        chartService.addCurrentPixelSpreadPoint(tradingPair); // Добавляем новую точку

        pairService.addChanges(tradingPair); // обновляем профит до проверки стратегии выхода

        // Проверяем автоусреднение после обновления профита
        if (averagingService.shouldPerformAutoAveraging(tradingPair, settings)) {
            AveragingService.AveragingResult averagingResult = averagingService.performAutoAveraging(tradingPair, settings);
            if (averagingResult.isSuccess()) {
                log.info("✅ Автоусреднение выполнено для пары {}: {}", tradingPair.getPairName(), averagingResult.getMessage());
            } else {
                log.warn("⚠️ Автоусреднение не удалось для пары {}: {}", tradingPair.getPairName(), averagingResult.getMessage());
            }
        }

        if (request.isCloseManually()) {
            return handleManualClose(tradingPair, settings);
        }

        final String exitReason = exitStrategyService.getExitReason(tradingPair, settings);
        if (exitReason != null) {
            return handleAutoClose(tradingPair, settings, exitReason);
        }

        pairService.save(tradingPair);
        tradeHistoryService.updateTradeLog(tradingPair, settings);
        return tradingPair;
    }

    @Transactional
    public void updateObservedPair(Pair tradingPair) {
        final Pair freshPair = loadFreshPairData(tradingPair);
        if (freshPair == null) {
            return;
        }

        try {
            final Settings settings = settingsService.getSettings();
            final Map<String, Object> cointegrationData = updateZScoreDataForExistingPair(freshPair, settings);
            final ZScoreData zScoreData = (ZScoreData) cointegrationData.get("zScoreData");
            final Map<String, List<Candle>> candlesMap = (Map<String, List<Candle>>) cointegrationData.get("candlesMap");

            if (zScoreData != null) {
                freshPair.setLongTickerCandles(candlesMap.get(freshPair.getLongTicker()));
                freshPair.setShortTickerCandles(candlesMap.get(freshPair.getShortTicker()));
                pairService.updateZScoreDataCurrent(freshPair, zScoreData);

                // Обновляем пиксельный спред для наблюдаемой пары
                log.debug("🔢 Обновляем пиксельный спред для наблюдаемой пары {}", freshPair.getPairName());
                chartService.calculatePixelSpreadIfNeeded(freshPair); // Инициализация при первом запуске
                chartService.addCurrentPixelSpreadPoint(freshPair); // Добавляем новую точку

                // ВАЖНО: Обновляем z-Score данные и историю (как для торговых пар)
                updateZScoreDataCurrentService.updateCurrent(freshPair, zScoreData);
                
                pairService.save(freshPair);
                
                // Сохраняем историю z-Score для графика
                tradeHistoryService.updateTradeLog(freshPair, settings);
            }
        } catch (Exception e) {
            log.warn("⚠️ Ошибка при обновлении наблюдаемой пары {}: {}",
                    freshPair.getPairName(), e.getMessage());
            // Не выбрасываем исключение дальше, чтобы не нарушить работу планировщика
        }
    }

    private void validateRequest(UpdateTradeRequest request) {
        if (request == null || request.getTradingPair() == null) {
            throw new IllegalArgumentException("Неверный запрос на обновление трейда");
        }
    }

    private Pair loadFreshPairData(Pair tradingPair) {
        final Pair freshPair = pairService.findById(tradingPair.getId());
        if (freshPair == null || freshPair.getStatus() == TradeStatus.CLOSED) {
            log.debug("⏭️ Пропускаем обновление закрытой пары {}", tradingPair.getPairName());
            return null;
        }

        log.debug("🚀 Начинаем обновление трейда для {}/{}", freshPair.getLongTicker(), freshPair.getShortTicker());
        return freshPair;
    }

    private boolean arePositionsClosed(Pair tradingPair) {
        final Positioninfo openPositionsInfo = tradingIntegrationServiceImpl.getOpenPositionsInfo(tradingPair);
        if (openPositionsInfo.isPositionsClosed()) {
            log.error("❌ Позиции уже закрыты для пары {}.", tradingPair.getPairName());
            return true;
        }
        return false;
    }

    private Map<String, Object> updateZScoreDataForExistingPair(Pair tradingPair, Settings settings) {

        // Создаем ExtendedCandlesRequest для получения свечей через пагинацию
        ExtendedCandlesRequest extendedRequest = ExtendedCandlesRequest.builder()
                .timeframe(settings.getTimeframe())
                .candleLimit((int) settings.getCandleLimit()) //todo check
                .minVolume(0.001) //todo для уже торгуемой пары просто обновляем без фильтра по объему - сетим минималку
                .tickers(List.of(tradingPair.getLongTicker(), tradingPair.getShortTicker()))
                .period(settings.calculateCurrentPeriod()) //todo берем из настроек
                .build();

        // Получаем все свечи через расширенный эндпоинт с пагинацией
        Map<String, List<Candle>> allCandlesMap = candlesFeignClient.getValidatedCacheExtended(extendedRequest);

        // Проверяем, что получены данные свечей
        if (allCandlesMap == null || allCandlesMap.isEmpty()) {
            log.warn("⚠️ Данные свечей не получены для пары {} — пропуск обновления", tradingPair.getPairName());
            throw new RuntimeException("Данные свечей не получены — пропуск анализа");
        }

        // Фильтруем только нужные тикеры для данной пары
        Map<String, List<Candle>> candlesMap = new HashMap<>();
        String longTicker = tradingPair.getLongTicker();
        String shortTicker = tradingPair.getShortTicker();

        if (allCandlesMap.containsKey(longTicker)) {
            candlesMap.put(longTicker, allCandlesMap.get(longTicker));
        }
        if (allCandlesMap.containsKey(shortTicker)) {
            candlesMap.put(shortTicker, allCandlesMap.get(shortTicker));
        }

        // Проверяем, что получены свечи для обоих тикеров
        if (!candlesMap.containsKey(longTicker) ||
                !candlesMap.containsKey(shortTicker) ||
                candlesMap.get(longTicker).isEmpty() ||
                candlesMap.get(shortTicker).isEmpty()) {
            log.warn("⚠️ Недостаточно данных свечей для пары {} (long: {}, short: {}) — пропуск обновления",
                    tradingPair.getPairName(),
                    candlesMap.containsKey(longTicker) ? candlesMap.get(longTicker).size() : 0,
                    candlesMap.containsKey(shortTicker) ? candlesMap.get(shortTicker).size() : 0);
            throw new RuntimeException("Недостаточно данных свечей — пропуск анализа");
        }

        ZScoreData zScoreData = zScoreService.calculateZScoreData(settings, candlesMap);
        Map<String, Object> result = new HashMap<>();
        result.put("candlesMap", candlesMap);
        result.put("zScoreData", zScoreData);
        return result;
    }

    private void logPairInfo(ZScoreData zScoreData, Settings settings) {
        if (zScoreData == null) {
            log.warn("ZScoreData is null, cannot log pair info.");
            return;
        }

        StringBuilder logMessage = new StringBuilder();

        logMessage.append(String.format("Наша пара: long=%s, short=%s | cointegrated=%s | p=%.5f | adf=%.5f | z=%.2f | corr=%.2f",
                zScoreData.getUnderValuedTicker(),
                zScoreData.getOverValuedTicker(),
                zScoreData.isJohansenIsCoint(),
                zScoreData.getJohansenCointPValue(),
                zScoreData.getAvgAdfPvalue(),
                zScoreData.getLatestZScore(),
                zScoreData.getPearsonCorr()));

        logMessage.append(String.format(" | avgAdf=%.2f | avgR=%.2f | stablePeriods=%d",
                zScoreData.getAvgAdfPvalue(),
                zScoreData.getAvgRSquared(),
                zScoreData.getStablePeriods()));

        logMessage.append(String.format(
                " | traceStat=%.2f | criticalValue95=%.2f | eigenSize=%d | vectorSize=%d | errors=%s",
                zScoreData.getJohansenTraceStatistic() != null ? zScoreData.getJohansenTraceStatistic() : 0.0,
                zScoreData.getJohansenCriticalValue95() != null ? zScoreData.getJohansenCriticalValue95() : 0.0,
                zScoreData.getJohansenEigenValues() != null ? zScoreData.getJohansenEigenValues().size() : 0,
                zScoreData.getJohansenCointegratingVector() != null ? zScoreData.getJohansenCointegratingVector().size() : 0,
                zScoreData.getJohansenError() != null ? zScoreData.getJohansenError() : "N/A"));

        logMessage.append(String.format(". Чек: pValue=%s, ADF=%s, R2=%s, stablePeriods=%d",
                FormatUtil.color(zScoreData.getJohansenCointPValue(), settings.getMaxPValue()),
                FormatUtil.color(zScoreData.getAvgAdfPvalue(), settings.getMaxAdfValue()),
                FormatUtil.color(zScoreData.getAvgRSquared(), settings.getMinRSquared()),
                zScoreData.getStablePeriods()));

        log.info(logMessage.toString());
    }

    private Pair handleManualClose(Pair tradingPair, Settings settings) {
        final ArbitragePairTradeInfo closeInfo = tradingIntegrationServiceImpl.closeArbitragePair(tradingPair);
        if (closeInfo == null || !closeInfo.isSuccess()) {
            return handleTradeError(tradingPair, UpdateTradeErrorType.MANUAL_CLOSE_FAILED);
        }

        log.info("✅ Успешно закрыта арбитражная пара через торговую систему: {}", tradingPair.getPairName());

        tradingPair.setStatus(TradeStatus.CLOSED);
        tradingPair.setExitReason(ExitReasonType.EXIT_REASON_MANUALLY.name());
        finalizeClosedTrade(tradingPair, settings);
//        notificationService.sendTelegramClosedPair(tradingPair);
        sendEventService.sendCoreEvent(new CoreEvent(tradingPair, CoreEvent.Type.CLOSED_MESSAGE_TO_TELEGRAM));
        sendEventService.sendCoreEvent(new CoreEvent(tradingPair, CoreEvent.Type.ADD_CLOSED_TO_CSV));
        settings.setMinimumLotBlacklist("");
        return tradingPair;
    }

    private void finalizeClosedTrade(Pair tradingPair, Settings settings) {
        pairService.addChanges(tradingPair);
        pairService.updatePortfolioBalanceAfterTradeUSDT(tradingPair); //баланс после
        tradingIntegrationServiceImpl.deletePositions(tradingPair);
        pairService.save(tradingPair);
        tradeHistoryService.updateTradeLog(tradingPair, settings);
    }

    private Pair handleNoOpenPositions(Pair tradingPair) {
        log.debug("==> handleNoOpenPositions: НАЧАЛО для пары {}", tradingPair.getPairName());
        log.debug("i️ Нет открытых позиций для пары {}! Возможно они были закрыты вручную на бирже.", tradingPair.getPairName());

        final Positioninfo verificationResult = tradingIntegrationServiceImpl.verifyPositionsClosed(tradingPair);
        log.debug("Результат верификации закрытия позиций: {}", verificationResult);

        if (verificationResult.isPositionsClosed()) {
            log.debug("✅ Подтверждено: позиции закрыты на бирже для пары {}, PnL: {} USDT ({} %)", tradingPair.getPairName(), verificationResult.getTotalPnLUSDT(), verificationResult.getTotalPnLPercent());
            Pair result = handleTradeError(tradingPair, UpdateTradeErrorType.MANUALLY_CLOSED_NO_POSITIONS);
            log.debug("<== handleNoOpenPositions: КОНЕЦ (позиции закрыты) для пары {}", tradingPair.getPairName());
            return result;
        } else {
            log.warn("⚠️ Позиции НЕ найдены на бирже для пары {}. Это может быть ошибка синхронизации.", tradingPair.getPairName());
            Pair result = handleTradeError(tradingPair, UpdateTradeErrorType.POSITIONS_NOT_FOUND);
            log.debug("<== handleNoOpenPositions: КОНЕЦ (позиции не найдены) для пары {}", tradingPair.getPairName());
            return result;
        }
    }

    private Pair handleAutoClose(Pair tradingPair, Settings settings, String exitReason) {
        log.info("🚪 Найдена причина для выхода из позиции: {} для пары {}", exitReason, tradingPair.getPairName());

        final ArbitragePairTradeInfo closeResult = tradingIntegrationServiceImpl.closeArbitragePair(tradingPair);
        if (closeResult == null || !closeResult.isSuccess()) {
            tradingPair.setExitReason(exitReason);
            return handleTradeError(tradingPair, UpdateTradeErrorType.AUTO_CLOSE_FAILED);
        }

        log.info("✅ Успешно закрыта арбитражная пара: {}", tradingPair.getPairName());

        tradingPair.setStatus(TradeStatus.CLOSED);
        tradingPair.setExitReason(exitReason);
        finalizeClosedTrade(tradingPair, settings);
//        notificationService.sendTelegramClosedPair(tradingPair);
        sendEventService.sendCoreEvent(new CoreEvent(tradingPair, CoreEvent.Type.CLOSED_MESSAGE_TO_TELEGRAM));
        sendEventService.sendCoreEvent(new CoreEvent(tradingPair, CoreEvent.Type.ADD_CLOSED_TO_CSV));
        settings.setMinimumLotBlacklist("");
        return tradingPair;
    }

    private Pair handleTradeError(Pair tradingPair, UpdateTradeErrorType errorType) {
        log.error("❌ Ошибка: {} для пары {}", errorType.getDescription(), tradingPair.getPairName());

        tradingPair.setStatus(TradeStatus.ERROR);
        tradingPair.setErrorDescription(errorType.getDescription());
        pairService.save(tradingPair);
        // не обновляем другие данные тк нужны реальные данные по сделкам!
        return tradingPair;
    }
}