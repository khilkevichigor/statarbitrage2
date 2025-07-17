package com.example.statarbitrage.core.processors;

import com.example.statarbitrage.common.dto.Candle;
import com.example.statarbitrage.common.dto.ZScoreData;
import com.example.statarbitrage.common.dto.ZScoreParam;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.common.model.TradeLog;
import com.example.statarbitrage.common.model.TradeStatus;
import com.example.statarbitrage.common.utils.CandlesUtil;
import com.example.statarbitrage.core.repositories.TradeLogRepository;
import com.example.statarbitrage.core.services.*;
import com.example.statarbitrage.trading.model.OpenArbitragePairResult;
import com.example.statarbitrage.trading.model.Portfolio;
import com.example.statarbitrage.trading.model.TradeResult;
import com.example.statarbitrage.trading.services.TradingIntegrationService;
import com.example.statarbitrage.ui.dto.StartNewTradeRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
    private final ValidateService validateService;
    private final TradingIntegrationService tradingIntegrationService;
    private final TradeLogRepository tradeLogRepository;

    @Transactional
    public PairData startNewTrade(StartNewTradeRequest request) {
        PairData pairData = request.getPairData();
        boolean checkAutoTrading = request.isCheckAutoTrading();
        log.info("🚀 Начинаем новый трейд для {} - {}", pairData.getLongTicker(), pairData.getShortTicker());
        Settings settings = settingsService.getSettings();

        //Проверка на дурака
        if (validateService.isLastZLessThenMinZ(pairData, settings)) {
            //если впервые прогоняем и Z<ZMin
            pairDataService.delete(pairData);
            log.warn("Удалили пару {} - {} т.к. ZCurrent < ZMin", pairData.getLongTicker(), pairData.getShortTicker());
            return null;
        }

        Map<String, List<Candle>> candlesMap = candlesService.getApplicableCandlesMap(pairData, settings);
        Optional<ZScoreData> maybeZScoreData = zScoreService.calculateZScoreDataForNewTrade(pairData, settings, candlesMap);

        if (maybeZScoreData.isEmpty()) {
            pairDataService.delete(pairData);
            log.warn("📊 Пропускаем создание нового трейда. ZScore данные пусты для пары {}/{}", pairData.getLongTicker(), pairData.getShortTicker());
            return null;
        }

        ZScoreData zScoreData = maybeZScoreData.get();

        ZScoreParam latest = zScoreData.getLastZScoreParam(); // последние params

        if (!Objects.equals(pairData.getLongTicker(), zScoreData.getUndervaluedTicker()) || !Objects.equals(pairData.getShortTicker(), zScoreData.getOvervaluedTicker())) {
            pairDataService.delete(pairData);
            String message = String.format("Ошибка начала нового терейда для пары лонг=%s шорт=%s. Тикеры поменялись местами!!! Торговать нельзя!!!", pairData.getLongTicker(), pairData.getShortTicker());
            log.error(message);
            throw new IllegalArgumentException(message);
        }

        // Проверяем автотрейдинг только если это запрошено (автоматический запуск)
        if (checkAutoTrading) {
            // Получаем СВЕЖИЕ настройки для актуальной проверки автотрейдинга
            Settings currentSettings = settingsService.getSettings();
            log.debug("📖 Процессор: Читаем настройки из БД: autoTrading={}", currentSettings.isAutoTradingEnabled());
            if (!currentSettings.isAutoTradingEnabled()) {
                pairDataService.delete(pairData);
                log.warn("🛑 Автотрейдинг отключен! Пропускаю открытие нового трейда для пары {} - {}", pairData.getLongTicker(), pairData.getShortTicker());
                return null;
            }
            log.debug("✅ Процессор: Автотрейдинг включен, продолжаем");
        } else {
            log.info("🔧 Ручной запуск трейда - проверка автотрейдинга пропущена для пары {} - {}", pairData.getLongTicker(), pairData.getShortTicker());
        }

        log.info(String.format("Наш новый трейд: underValued=%s overValued=%s | p=%.5f | adf=%.5f | z=%.2f | corr=%.2f", zScoreData.getUndervaluedTicker(), zScoreData.getOvervaluedTicker(), latest.getPvalue(), latest.getAdfpvalue(), latest.getZscore(), latest.getCorrelation()));

        // Проверяем, можем ли открыть новую пару на торговом депо
        if (!tradingIntegrationService.canOpenNewPair()) {
            log.warn("⚠️ Недостаточно средств в торговом депо для открытия пары {}/{}",
                    pairData.getLongTicker(), pairData.getShortTicker());

            pairData.setStatus(TradeStatus.ERROR_110);
            pairDataService.save(pairData);
            return pairData;
        }
        // Открываем арбитражную пару через торговую систему СИНХРОННО
        OpenArbitragePairResult openArbitragePairResult = tradingIntegrationService.openArbitragePair(pairData, zScoreData, candlesMap);

        if (openArbitragePairResult == null || !openArbitragePairResult.isSuccess()) {
            log.warn("⚠️ Не удалось открыть арбитражную пару через торговую систему: {}/{}",
                    pairData.getLongTicker(), pairData.getShortTicker());

            pairData.setStatus(TradeStatus.ERROR_100);
            pairDataService.save(pairData);
            return pairData;
        }

        TradeResult openLongTradeResult = openArbitragePairResult.getLongTradeResult();
        TradeResult openShortTradeResult = openArbitragePairResult.getShortTradeResult();

        log.info("✅ Успешно открыта арбитражная пара через торговую систему: {}/{}",
                pairData.getLongTicker(), pairData.getShortTicker());

        pairData.setStatus(TradeStatus.TRADING);
        updateCurrentData(pairData, zScoreData, candlesMap);

        addEntryPoints(pairData, zScoreData.getLastZScoreParam(), openLongTradeResult, openShortTradeResult);
        log.info("🔹Установлены точки входа: LONG {{}} = {}, SHORT {{}} = {}, Z = {}",
                pairData.getLongTicker(), pairData.getLongTickerEntryPrice(),
                pairData.getShortTicker(), pairData.getShortTickerEntryPrice(),
                pairData.getZScoreEntry());

        calculateAndAddChanges(pairData);
        pairDataService.save(pairData);

        TradeLog tradeLog = getTradeLog(pairData, settings);
        tradeLogRepository.save(tradeLog);

        return pairData;
    }

    private void updateCurrentData(PairData pairData, ZScoreData zScoreData, Map<String, List<Candle>> candlesMap) {

        //Обновляем текущие цены
        List<Candle> longTickerCandles = candlesMap.get(pairData.getLongTicker());
        List<Candle> shortTickerCandles = candlesMap.get(pairData.getShortTicker());
        double longTickerCurrentPrice = CandlesUtil.getLastClose(longTickerCandles);
        double shortTickerCurrentPrice = CandlesUtil.getLastClose(shortTickerCandles);
        pairData.setLongTickerCurrentPrice(longTickerCurrentPrice);
        pairData.setShortTickerCurrentPrice(shortTickerCurrentPrice);

        //Обновляем текущие данные коинтеграции
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

    private void addEntryPoints(PairData pairData, ZScoreParam latestParam, TradeResult longResult, TradeResult shortResult) {
        //Обновляем текущие данные коинтеграции
        pairData.setLongTickerEntryPrice(longResult.getExecutionPrice().doubleValue());
        pairData.setShortTickerEntryPrice(shortResult.getExecutionPrice().doubleValue());
        pairData.setZScoreEntry(latestParam.getZscore());
        pairData.setCorrelationEntry(latestParam.getCorrelation());
        pairData.setAdfPvalueEntry(latestParam.getAdfpvalue());
        pairData.setPValueEntry(latestParam.getPvalue());
        pairData.setMeanEntry(latestParam.getMean());
        pairData.setStdEntry(latestParam.getStd());
        pairData.setSpreadEntry(latestParam.getSpread());
        pairData.setAlphaEntry(latestParam.getAlpha());
        pairData.setBetaEntry(latestParam.getBeta());
        // Время входа
        pairData.setEntryTime(longResult.getExecutionTime().atZone(java.time.ZoneId.systemDefault()).toEpochSecond() * 1000);
    }

    public void calculateAndAddChanges(PairData pairData) {
        try {
            // Получаем текущие цены
            BigDecimal longCurrent = BigDecimal.valueOf(pairData.getLongTickerCurrentPrice());
            BigDecimal shortCurrent = BigDecimal.valueOf(pairData.getShortTickerCurrentPrice());
            BigDecimal zScoreEntry = BigDecimal.valueOf(pairData.getZScoreEntry());
            BigDecimal zScoreCurrent = BigDecimal.valueOf(pairData.getZScoreCurrent());
            BigDecimal corrCurrent = BigDecimal.valueOf(pairData.getCorrelationCurrent());

            // Сначала обновляем цены позиций с биржи для актуальных данных
            tradingIntegrationService.updateAllPositions();

            // Затем получаем реальный PnL для данной пары с актуальными ценами
            BigDecimal realPnL = tradingIntegrationService.getPositionPnL(pairData);

            // Расчет процентных изменений позиций на основе текущих и входных цен
            BigDecimal longEntry = BigDecimal.valueOf(pairData.getLongTickerEntryPrice());
            BigDecimal shortEntry = BigDecimal.valueOf(pairData.getShortTickerEntryPrice());

            // Процентное изменение LONG позиции
            BigDecimal longReturnPct = longCurrent.subtract(longEntry)
                    .divide(longEntry, 10, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            // Процентное изменение SHORT позиции (инвертировано)
            BigDecimal shortReturnPct = shortEntry.subtract(shortCurrent)
                    .divide(shortEntry, 10, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            // Получаем информацию о портфолио для расчета процентной прибыли
            Portfolio portfolio = tradingIntegrationService.getPortfolioInfo();
            BigDecimal totalBalance = portfolio != null ? portfolio.getTotalBalance() : BigDecimal.ZERO;

            // Расчет профита в процентах от общего портфолио
            BigDecimal profitPercentFromTotal = BigDecimal.ZERO;
            if (totalBalance.compareTo(BigDecimal.ZERO) > 0) {
                profitPercentFromTotal = realPnL
                        .divide(totalBalance, 10, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            }

            // Текущее время
            long entryTime = pairData.getEntryTime();
            long now = System.currentTimeMillis();
            long currentTimeInMinutes = (now - entryTime) / (1000 * 60);

            // Округления
            BigDecimal longReturnRounded = longReturnPct.setScale(2, RoundingMode.HALF_UP);
            BigDecimal shortReturnRounded = shortReturnPct.setScale(2, RoundingMode.HALF_UP);
            BigDecimal profitRounded = profitPercentFromTotal.setScale(2, RoundingMode.HALF_UP);
            BigDecimal zScoreRounded = zScoreCurrent.subtract(zScoreEntry).setScale(2, RoundingMode.HALF_UP);

            // 🔄 Отслеживание максимумов и минимумов с учетом истории
            BigDecimal currentMinProfit = pairData.getMinProfitRounded();
            BigDecimal currentMaxProfit = pairData.getMaxProfitRounded();
            long currentTimeToMax = pairData.getTimeInMinutesSinceEntryToMax();
            long currentTimeToMin = pairData.getTimeInMinutesSinceEntryToMin();

            // Обновляем максимум прибыли
            BigDecimal maxProfitRounded;
            long timeInMinutesSinceEntryToMax;
            if (currentMaxProfit == null || profitRounded.compareTo(currentMaxProfit) > 0) {
                maxProfitRounded = profitRounded;
                timeInMinutesSinceEntryToMax = currentTimeInMinutes;
                log.debug("🚀 Новый максимум прибыли (реальная торговля): {}% за {} мин", maxProfitRounded, timeInMinutesSinceEntryToMax);
            } else {
                maxProfitRounded = currentMaxProfit;
                timeInMinutesSinceEntryToMax = currentTimeToMax;
            }

            // Обновляем минимум прибыли
            BigDecimal minProfitRounded;
            long timeInMinutesSinceEntryToMin;
            if (currentMinProfit == null || profitRounded.compareTo(currentMinProfit) < 0) {
                minProfitRounded = profitRounded;
                timeInMinutesSinceEntryToMin = currentTimeInMinutes;
                log.debug("📉 Новый минимум прибыли (реальная торговля): {}% за {} мин", minProfitRounded, timeInMinutesSinceEntryToMin);
            } else {
                minProfitRounded = currentMinProfit;
                timeInMinutesSinceEntryToMin = currentTimeToMin;
            }

            // Отслеживание экстремумов других показателей
            BigDecimal minZ = updateMin(pairData.getMinZ(), zScoreCurrent);
            BigDecimal maxZ = updateMax(pairData.getMaxZ(), zScoreCurrent);

            BigDecimal minLong = updateMin(pairData.getMinLong(), longReturnPct);
            BigDecimal maxLong = updateMax(pairData.getMaxLong(), longReturnPct);

            BigDecimal minShort = updateMin(pairData.getMinShort(), shortReturnPct);
            BigDecimal maxShort = updateMax(pairData.getMaxShort(), shortReturnPct);

            BigDecimal minCorr = updateMin(pairData.getMinCorr(), corrCurrent);
            BigDecimal maxCorr = updateMax(pairData.getMaxCorr(), corrCurrent);

            // ✅ Записываем в PairData
            pairData.setLongChanges(longReturnRounded);
            pairData.setShortChanges(shortReturnRounded);
            pairData.setProfitChanges(profitRounded);
            pairData.setZScoreChanges(zScoreRounded);

            pairData.setMinProfitRounded(minProfitRounded);
            pairData.setMaxProfitRounded(maxProfitRounded);
            pairData.setTimeInMinutesSinceEntryToMax(timeInMinutesSinceEntryToMax);
            pairData.setTimeInMinutesSinceEntryToMin(timeInMinutesSinceEntryToMin);

            pairData.setMinZ(minZ);
            pairData.setMaxZ(maxZ);
            pairData.setMinLong(minLong);
            pairData.setMaxLong(maxLong);
            pairData.setMinShort(minShort);
            pairData.setMaxShort(maxShort);
            pairData.setMinCorr(minCorr);
            pairData.setMaxCorr(maxCorr);

            // 📝 Логирование
            log.info("🔴 РЕАЛЬНАЯ ТОРГОВЛЯ - {}/{}", pairData.getLongTicker(), pairData.getShortTicker());
            log.info("📊 LONG {}: Entry: {}, Current: {}, Changes: {}%",
                    pairData.getLongTicker(), longEntry, longCurrent, longReturnRounded);
            log.info("📉 SHORT {}: Entry: {}, Current: {}, Changes: {}%",
                    pairData.getShortTicker(), shortEntry, shortCurrent, shortReturnRounded);
            log.info("📊 Z Entry: {}, Current: {}, ΔZ: {}",
                    zScoreEntry, zScoreCurrent, zScoreRounded);
            log.info("💰 Реальный PnL: {} USDT ({}% от портфолио)",
                    realPnL.setScale(2, RoundingMode.HALF_UP), profitRounded);
            log.info("💼 Общий баланс портфолио: {} USDT", totalBalance.setScale(2, RoundingMode.HALF_UP));
            log.info("📈 Max profit: {}%, Min profit: {}%", maxProfitRounded, minProfitRounded);
            log.info("⏱ Время до max: {} мин, до min: {} мин", timeInMinutesSinceEntryToMax, timeInMinutesSinceEntryToMin);

        } catch (Exception e) {
            log.error("❌ Ошибка при расчете реального профита для пары {}/{}: {}",
                    pairData.getLongTicker(), pairData.getShortTicker(), e.getMessage());
        }
    }

    /**
     * Обновляет минимальное значение
     */
    private BigDecimal updateMin(BigDecimal currentMin, BigDecimal newValue) {
        if (currentMin == null) return newValue;
        return newValue.compareTo(currentMin) < 0 ? newValue : currentMin;
    }

    /**
     * Обновляет максимальное значение
     */
    private BigDecimal updateMax(BigDecimal currentMax, BigDecimal newValue) {
        if (currentMax == null) return newValue;
        return newValue.compareTo(currentMax) > 0 ? newValue : currentMax;
    }

    private TradeLog getTradeLog(PairData pairData, Settings settings) {
        String longTicker = pairData.getLongTicker();
        String shortTicker = pairData.getShortTicker();

        // ищем по паре
        Optional<TradeLog> optional = tradeLogRepository.findLatestByTickers(longTicker, shortTicker);

        TradeLog tradeLog = optional.orElseGet(TradeLog::new);
        tradeLog.setLongTicker(longTicker);
        tradeLog.setShortTicker(shortTicker);

        // мапим поля
        tradeLog.setCurrentProfitPercent(pairData.getProfitChanges());
        tradeLog.setMinProfitPercent(pairData.getMinProfitRounded());
        tradeLog.setMinProfitMinutes(pairData.getTimeInMinutesSinceEntryToMin() + "min");
        tradeLog.setMaxProfitPercent(pairData.getMaxProfitRounded());
        tradeLog.setMaxProfitMinutes(pairData.getTimeInMinutesSinceEntryToMax() + "min");

        tradeLog.setCurrentLongPercent(pairData.getLongChanges());
        tradeLog.setMinLongPercent(pairData.getMinLong());
        tradeLog.setMaxLongPercent(pairData.getMaxLong());

        tradeLog.setCurrentShortPercent(pairData.getShortChanges());
        tradeLog.setMinShortPercent(pairData.getMinShort());
        tradeLog.setMaxShortPercent(pairData.getMaxShort());

        tradeLog.setCurrentZ(pairData.getZScoreCurrent());
        tradeLog.setMinZ(pairData.getMinZ());
        tradeLog.setMaxZ(pairData.getMaxZ());

        tradeLog.setCurrentCorr(pairData.getCorrelationCurrent());
        tradeLog.setMinCorr(pairData.getMinCorr());
        tradeLog.setMaxCorr(pairData.getMaxCorr());

        tradeLog.setExitTake(settings.getExitTake());
        tradeLog.setExitStop(settings.getExitStop());
        tradeLog.setExitZMin(settings.getExitZMin());
        tradeLog.setExitZMax(settings.getExitZMaxPercent());
        tradeLog.setExitTimeHours(settings.getExitTimeHours());

        tradeLog.setExitReason(pairData.getExitReason());

        long entryMillis = pairData.getEntryTime(); // long, например 1721511983000
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        // Преобразуем в LocalDateTime через Instant
        String formattedEntryTime = Instant.ofEpochMilli(entryMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
                .format(formatter);

        tradeLog.setEntryTime(formattedEntryTime);
        tradeLog.setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        return tradeLog;
    }
}
