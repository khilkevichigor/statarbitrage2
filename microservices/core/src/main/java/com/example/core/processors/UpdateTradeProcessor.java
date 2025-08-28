package com.example.core.processors;

import com.example.core.client.CandlesFeignClient;
import com.example.core.messaging.SendEventService;
import com.example.core.services.*;
import com.example.core.trading.interfaces.TradingProvider;
import com.example.core.trading.services.TradingIntegrationService;
import com.example.core.trading.services.TradingProviderFactory;
import com.example.shared.dto.CandlesRequest;
import com.example.shared.dto.UpdateTradeRequest;
import com.example.shared.dto.ZScoreData;
import com.example.shared.events.CoreEvent;
import com.example.shared.models.*;
import com.example.shared.utils.FormatUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateTradeProcessor {
    private final TradingPairService tradingPairService;
    private final SettingsService settingsService;
    private final TradeHistoryService tradeHistoryService;
    private final ZScoreService zScoreService;
    private final TradingIntegrationService tradingIntegrationServiceImpl;
    private final ExitStrategyService exitStrategyService;
    private final NotificationService notificationService;
    private final SendEventService sendEventService;
    private final ChartService chartService;
    private final AveragingService averagingService;
    private final TradingProviderFactory tradingProviderFactory;
    private final CandlesFeignClient candlesFeignClient;


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
    public TradingPair updateTrade(UpdateTradeRequest request) {
        validateRequest(request);

        final TradingPair tradingPair = loadFreshPairData(request.getTradingPair());
        if (tradingPair == null) {
            return request.getTradingPair();
        }
        log.info("");
        log.info("🔄 Обновление пары {}...", tradingPair.getPairName());

        final Settings settings = settingsService.getSettings();

        //todo здесь сетить настройки в пару для дальнейшей аналитики чатЖПТ
        tradingPairService.updateSettingsParam(tradingPair, settings);

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

        tradingPairService.updateZScoreDataCurrent(tradingPair, zScoreData);

        // Обновляем пиксельный спред синхронно с Z-Score и ценами
        log.debug("🔢 Обновляем пиксельный спред для пары {}", tradingPair.getPairName());
        chartService.calculatePixelSpreadIfNeeded(tradingPair); // Инициализация при первом запуске
        chartService.addCurrentPixelSpreadPoint(tradingPair); // Добавляем новую точку

        tradingPairService.addChanges(tradingPair); // обновляем профит до проверки стратегии выхода

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

        tradingPairService.save(tradingPair);
        tradeHistoryService.updateTradeLog(tradingPair, settings);
        return tradingPair;
    }

    @Transactional
    public void updateObservedPair(TradingPair tradingPair) {
        final TradingPair freshTradingPair = loadFreshPairData(tradingPair);
        if (freshTradingPair == null) {
            return;
        }

        final Settings settings = settingsService.getSettings();
        final Map<String, Object> cointegrationData = updateZScoreDataForExistingPair(freshTradingPair, settings);
        final ZScoreData zScoreData = (ZScoreData) cointegrationData.get("zScoreData");
        final Map<String, List<Candle>> candlesMap = (Map<String, List<Candle>>) cointegrationData.get("candlesMap");

        if (zScoreData != null) {
            freshTradingPair.setLongTickerCandles(candlesMap.get(freshTradingPair.getLongTicker()));
            freshTradingPair.setShortTickerCandles(candlesMap.get(freshTradingPair.getShortTicker()));
            tradingPairService.updateZScoreDataCurrent(freshTradingPair, zScoreData);

            // Обновляем пиксельный спред для наблюдаемой пары
            log.debug("🔢 Обновляем пиксельный спред для наблюдаемой пары {}", freshTradingPair.getPairName());
            chartService.calculatePixelSpreadIfNeeded(freshTradingPair); // Инициализация при первом запуске
            chartService.addCurrentPixelSpreadPoint(freshTradingPair); // Добавляем новую точку

            tradingPairService.save(freshTradingPair);
        }
    }

    private void validateRequest(UpdateTradeRequest request) {
        if (request == null || request.getTradingPair() == null) {
            throw new IllegalArgumentException("Неверный запрос на обновление трейда");
        }
    }

    private TradingPair loadFreshPairData(TradingPair tradingPair) {
        final TradingPair freshTradingPair = tradingPairService.findById(tradingPair.getId());
        if (freshTradingPair == null || freshTradingPair.getStatus() == TradeStatus.CLOSED) {
            log.debug("⏭️ Пропускаем обновление закрытой пары {}", tradingPair.getPairName());
            return null;
        }

        log.debug("🚀 Начинаем обновление трейда для {}/{}", freshTradingPair.getLongTicker(), freshTradingPair.getShortTicker());
        return freshTradingPair;
    }

    private boolean arePositionsClosed(TradingPair tradingPair) {
        final Positioninfo openPositionsInfo = tradingIntegrationServiceImpl.getOpenPositionsInfo(tradingPair);
        if (openPositionsInfo.isPositionsClosed()) {
            log.error("❌ Позиции уже закрыты для пары {}.", tradingPair.getPairName());
            return true;
        }
        return false;
    }

    private Map<String, Object> updateZScoreDataForExistingPair(TradingPair tradingPair, Settings settings) {
        CandlesRequest request = new CandlesRequest(tradingPair, settings);
        Map<String, List<Candle>> candlesMap = candlesFeignClient.getApplicableCandlesMap(request);
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

        logMessage.append(String.format(". Чек: pValue=%s, ADF=%s, R²=%s, stablePeriods=%d",
                FormatUtil.color(zScoreData.getJohansenCointPValue(), settings.getMaxPValue()),
                FormatUtil.color(zScoreData.getAvgAdfPvalue(), settings.getMaxAdfValue()),
                FormatUtil.color(zScoreData.getAvgRSquared(), settings.getMinRSquared()),
                zScoreData.getStablePeriods()));

        log.info(logMessage.toString());
    }

    private TradingPair handleManualClose(TradingPair tradingPair, Settings settings) {
        final ArbitragePairTradeInfo closeInfo = tradingIntegrationServiceImpl.closeArbitragePair(tradingPair);
        if (closeInfo == null || !closeInfo.isSuccess()) {
            return handleTradeError(tradingPair, UpdateTradeErrorType.MANUAL_CLOSE_FAILED);
        }

        log.info("✅ Успешно закрыта арбитражная пара через торговую систему: {}", tradingPair.getPairName());

        tradingPair.setStatus(TradeStatus.CLOSED);
        tradingPair.setExitReason(ExitReasonType.EXIT_REASON_MANUALLY.name());
        finalizeClosedTrade(tradingPair, settings);
        notificationService.sendTelegramClosedPair(tradingPair);
        sendEventService.sendCoreEvent(new CoreEvent(Collections.singletonList(tradingPair), CoreEvent.Type.ADD_CLOSED_TO_CSV));
        return tradingPair;
    }

    private void finalizeClosedTrade(TradingPair tradingPair, Settings settings) {
        tradingPairService.addChanges(tradingPair);
        tradingPairService.updatePortfolioBalanceAfterTradeUSDT(tradingPair); //баланс после
        tradingIntegrationServiceImpl.deletePositions(tradingPair);
        tradingPairService.save(tradingPair);
        tradeHistoryService.updateTradeLog(tradingPair, settings);
    }

    private TradingPair handleNoOpenPositions(TradingPair tradingPair) {
        log.debug("==> handleNoOpenPositions: НАЧАЛО для пары {}", tradingPair.getPairName());
        log.debug("ℹ️ Нет открытых позиций для пары {}! Возможно они были закрыты вручную на бирже.", tradingPair.getPairName());

        final Positioninfo verificationResult = tradingIntegrationServiceImpl.verifyPositionsClosed(tradingPair);
        log.debug("Результат верификации закрытия позиций: {}", verificationResult);

        if (verificationResult.isPositionsClosed()) {
            log.debug("✅ Подтверждено: позиции закрыты на бирже для пары {}, PnL: {} USDT ({} %)", tradingPair.getPairName(), verificationResult.getTotalPnLUSDT(), verificationResult.getTotalPnLPercent());
            TradingPair result = handleTradeError(tradingPair, UpdateTradeErrorType.MANUALLY_CLOSED_NO_POSITIONS);
            log.debug("<== handleNoOpenPositions: КОНЕЦ (позиции закрыты) для пары {}", tradingPair.getPairName());
            return result;
        } else {
            log.warn("⚠️ Позиции НЕ найдены на бирже для пары {}. Это может быть ошибка синхронизации.", tradingPair.getPairName());
            TradingPair result = handleTradeError(tradingPair, UpdateTradeErrorType.POSITIONS_NOT_FOUND);
            log.debug("<== handleNoOpenPositions: КОНЕЦ (позиции не найдены) для пары {}", tradingPair.getPairName());
            return result;
        }
    }

    private TradingPair handleAutoClose(TradingPair tradingPair, Settings settings, String exitReason) {
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
        notificationService.sendTelegramClosedPair(tradingPair);
        sendEventService.sendCoreEvent(new CoreEvent(Collections.singletonList(tradingPair), CoreEvent.Type.ADD_CLOSED_TO_CSV));
        return tradingPair;
    }

    private TradingPair handleTradeError(TradingPair tradingPair, UpdateTradeErrorType errorType) {
        log.error("❌ Ошибка: {} для пары {}", errorType.getDescription(), tradingPair.getPairName());

        tradingPair.setStatus(TradeStatus.ERROR);
        tradingPair.setErrorDescription(errorType.getDescription());
        tradingPairService.save(tradingPair);
        // не обновляем другие данные тк нужны реальные данные по сделкам!
        return tradingPair;
    }
}