package com.example.core.core.processors;

import com.example.core.common.dto.ZScoreData;
import com.example.core.common.utils.FormatUtil;
import com.example.core.core.services.*;
import com.example.core.trading.interfaces.TradingProvider;
import com.example.core.trading.services.TradingIntegrationService;
import com.example.core.trading.services.TradingProviderFactory;
import com.example.core.ui.dto.UpdateTradeRequest;
import com.example.shared.models.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateTradeProcessor {
    private final PairDataService pairDataService;
    private final CandlesService candlesService;
    private final SettingsService settingsService;
    private final TradeHistoryService tradeHistoryService;
    private final ZScoreService zScoreService;
    private final TradingIntegrationService tradingIntegrationServiceImpl;
    private final ExitStrategyService exitStrategyService;
    private final NotificationService notificationService;
    private final CsvExportService csvExportService;
    private final ChartService chartService;
    private final AveragingService averagingService;
    private final TradingProviderFactory tradingProviderFactory;


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
    public PairData updateTrade(UpdateTradeRequest request) {
        validateRequest(request);

        final PairData pairData = loadFreshPairData(request.getPairData());
        if (pairData == null) {
            return request.getPairData();
        }
        log.info("");
        log.info("🔄 Обновление пары {}...", pairData.getPairName());

        final Settings settings = settingsService.getSettings();

        //todo здесь сетить настройки в пару для дальнейшей аналитики чатЖПТ
        pairDataService.updateSettingsParam(pairData, settings);

        TradingProvider provider = tradingProviderFactory.getCurrentProvider();
        provider.updatePositionPrices(List.of(pairData.getLongTicker(), pairData.getShortTicker()));

        if (arePositionsClosed(pairData)) {
            return handleNoOpenPositions(pairData);
        }

        final Map<String, Object> cointegrationData = updateZScoreDataForExistingPair(pairData, settings);
        final ZScoreData zScoreData = (ZScoreData) cointegrationData.get("zScoreData");
        final Map<String, List<Candle>> candlesMap = (Map<String, List<Candle>>) cointegrationData.get("candlesMap");

        pairData.setLongTickerCandles(candlesMap.get(pairData.getLongTicker()));
        pairData.setShortTickerCandles(candlesMap.get(pairData.getShortTicker()));

        logPairInfo(zScoreData, settings);

        pairDataService.updateZScoreDataCurrent(pairData, zScoreData);

        // Обновляем пиксельный спред синхронно с Z-Score и ценами
        log.debug("🔢 Обновляем пиксельный спред для пары {}", pairData.getPairName());
        chartService.calculatePixelSpreadIfNeeded(pairData); // Инициализация при первом запуске
        chartService.addCurrentPixelSpreadPoint(pairData); // Добавляем новую точку

        pairDataService.addChanges(pairData); // обновляем профит до проверки стратегии выхода

        // Проверяем автоусреднение после обновления профита
        if (averagingService.shouldPerformAutoAveraging(pairData, settings)) {
            AveragingService.AveragingResult averagingResult = averagingService.performAutoAveraging(pairData, settings);
            if (averagingResult.isSuccess()) {
                log.info("✅ Автоусреднение выполнено для пары {}: {}", pairData.getPairName(), averagingResult.getMessage());
            } else {
                log.warn("⚠️ Автоусреднение не удалось для пары {}: {}", pairData.getPairName(), averagingResult.getMessage());
            }
        }

        if (request.isCloseManually()) {
            return handleManualClose(pairData, settings);
        }

        final String exitReason = exitStrategyService.getExitReason(pairData, settings);
        if (exitReason != null) {
            return handleAutoClose(pairData, settings, exitReason);
        }

        pairDataService.save(pairData);
        tradeHistoryService.updateTradeLog(pairData, settings);
        return pairData;
    }

    @Transactional
    public void updateObservedPair(PairData pairData) {
        final PairData freshPairData = loadFreshPairData(pairData);
        if (freshPairData == null) {
            return;
        }

        final Settings settings = settingsService.getSettings();
        final Map<String, Object> cointegrationData = updateZScoreDataForExistingPair(freshPairData, settings);
        final ZScoreData zScoreData = (ZScoreData) cointegrationData.get("zScoreData");
        final Map<String, List<Candle>> candlesMap = (Map<String, List<Candle>>) cointegrationData.get("candlesMap");

        if (zScoreData != null) {
            freshPairData.setLongTickerCandles(candlesMap.get(freshPairData.getLongTicker()));
            freshPairData.setShortTickerCandles(candlesMap.get(freshPairData.getShortTicker()));
            pairDataService.updateZScoreDataCurrent(freshPairData, zScoreData);

            // Обновляем пиксельный спред для наблюдаемой пары
            log.debug("🔢 Обновляем пиксельный спред для наблюдаемой пары {}", freshPairData.getPairName());
            chartService.calculatePixelSpreadIfNeeded(freshPairData); // Инициализация при первом запуске
            chartService.addCurrentPixelSpreadPoint(freshPairData); // Добавляем новую точку

            pairDataService.save(freshPairData);
        }
    }

    private void validateRequest(UpdateTradeRequest request) {
        if (request == null || request.getPairData() == null) {
            throw new IllegalArgumentException("Неверный запрос на обновление трейда");
        }
    }

    private PairData loadFreshPairData(PairData pairData) {
        final PairData freshPairData = pairDataService.findById(pairData.getId());
        if (freshPairData == null || freshPairData.getStatus() == TradeStatus.CLOSED) {
            log.debug("⏭️ Пропускаем обновление закрытой пары {}", pairData.getPairName());
            return null;
        }

        log.debug("🚀 Начинаем обновление трейда для {}/{}", freshPairData.getLongTicker(), freshPairData.getShortTicker());
        return freshPairData;
    }

    private boolean arePositionsClosed(PairData pairData) {
        final Positioninfo openPositionsInfo = tradingIntegrationServiceImpl.getOpenPositionsInfo(pairData);
        if (openPositionsInfo.isPositionsClosed()) {
            log.error("❌ Позиции уже закрыты для пары {}.", pairData.getPairName());
            return true;
        }
        return false;
    }

    private Map<String, Object> updateZScoreDataForExistingPair(PairData pairData, Settings settings) {
        final Map<String, List<Candle>> candlesMap = candlesService.getApplicableCandlesMap(pairData, settings);
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

    private PairData handleManualClose(PairData pairData, Settings settings) {
        final ArbitragePairTradeInfo closeInfo = tradingIntegrationServiceImpl.closeArbitragePair(pairData);
        if (closeInfo == null || !closeInfo.isSuccess()) {
            return handleTradeError(pairData, UpdateTradeErrorType.MANUAL_CLOSE_FAILED);
        }

        log.info("✅ Успешно закрыта арбитражная пара через торговую систему: {}", pairData.getPairName());

        pairData.setStatus(TradeStatus.CLOSED);
        pairData.setExitReason(ExitReasonType.EXIT_REASON_MANUALLY.name());
        finalizeClosedTrade(pairData, settings);
        notificationService.notifyClose(pairData);
        csvExportService.appendPairDataToCsv(pairData);
        return pairData;
    }

    private void finalizeClosedTrade(PairData pairData, Settings settings) {
        pairDataService.addChanges(pairData);
        pairDataService.updatePortfolioBalanceAfterTradeUSDT(pairData); //баланс после
        tradingIntegrationServiceImpl.removePairFromLocalStorage(pairData);
        pairDataService.save(pairData);
        tradeHistoryService.updateTradeLog(pairData, settings);
    }

    private PairData handleNoOpenPositions(PairData pairData) {
        log.debug("==> handleNoOpenPositions: НАЧАЛО для пары {}", pairData.getPairName());
        log.debug("ℹ️ Нет открытых позиций для пары {}! Возможно они были закрыты вручную на бирже.", pairData.getPairName());

        final Positioninfo verificationResult = tradingIntegrationServiceImpl.verifyPositionsClosed(pairData);
        log.debug("Результат верификации закрытия позиций: {}", verificationResult);

        if (verificationResult.isPositionsClosed()) {
            log.debug("✅ Подтверждено: позиции закрыты на бирже для пары {}, PnL: {} USDT ({} %)", pairData.getPairName(), verificationResult.getTotalPnLUSDT(), verificationResult.getTotalPnLPercent());
            PairData result = handleTradeError(pairData, UpdateTradeErrorType.MANUALLY_CLOSED_NO_POSITIONS);
            log.debug("<== handleNoOpenPositions: КОНЕЦ (позиции закрыты) для пары {}", pairData.getPairName());
            return result;
        } else {
            log.warn("⚠️ Позиции НЕ найдены на бирже для пары {}. Это может быть ошибка синхронизации.", pairData.getPairName());
            PairData result = handleTradeError(pairData, UpdateTradeErrorType.POSITIONS_NOT_FOUND);
            log.debug("<== handleNoOpenPositions: КОНЕЦ (позиции не найдены) для пары {}", pairData.getPairName());
            return result;
        }
    }

    private PairData handleAutoClose(PairData pairData, Settings settings, String exitReason) {
        log.info("🚪 Найдена причина для выхода из позиции: {} для пары {}", exitReason, pairData.getPairName());

        final ArbitragePairTradeInfo closeResult = tradingIntegrationServiceImpl.closeArbitragePair(pairData);
        if (closeResult == null || !closeResult.isSuccess()) {
            pairData.setExitReason(exitReason);
            return handleTradeError(pairData, UpdateTradeErrorType.AUTO_CLOSE_FAILED);
        }

        log.info("✅ Успешно закрыта арбитражная пара: {}", pairData.getPairName());

        pairData.setStatus(TradeStatus.CLOSED);
        pairData.setExitReason(exitReason);
        finalizeClosedTrade(pairData, settings);
        notificationService.notifyClose(pairData);
        csvExportService.appendPairDataToCsv(pairData);
        return pairData;
    }

    private PairData handleTradeError(PairData pairData, UpdateTradeErrorType errorType) {
        log.error("❌ Ошибка: {} для пары {}", errorType.getDescription(), pairData.getPairName());

        pairData.setStatus(TradeStatus.ERROR);
        pairData.setErrorDescription(errorType.getDescription());
        pairDataService.save(pairData);
        // не обновляем другие данные тк нужны реальные данные по сделкам!
        return pairData;
    }
}