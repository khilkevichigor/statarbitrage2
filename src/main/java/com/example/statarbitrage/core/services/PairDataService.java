package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.dto.Candle;
import com.example.statarbitrage.common.dto.ZScoreData;
import com.example.statarbitrage.common.dto.ZScoreParam;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.TradeStatus;
import com.example.statarbitrage.common.utils.CandlesUtil;
import com.example.statarbitrage.core.repositories.PairDataRepository;
import com.example.statarbitrage.trading.model.ArbitragePairTradeInfo;
import com.example.statarbitrage.trading.model.Position;
import com.example.statarbitrage.trading.model.PositionVerificationResult;
import com.example.statarbitrage.trading.model.TradeResult;
import com.example.statarbitrage.trading.services.TradingIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PairDataService {
    private final PairDataRepository pairDataRepository;
    private final TradingIntegrationService tradingIntegrationService;
    private final SettingsService settingsService;
    private final TradeLogService tradeLogService;

    public List<PairData> createPairDataList(List<ZScoreData> top, Map<String, List<Candle>> candlesMap) {
        List<PairData> result = new ArrayList<>();

        for (ZScoreData zScoreData : top) {
            try {
                List<Candle> underValuedTickerCandles = candlesMap.get(zScoreData.getUndervaluedTicker());
                List<Candle> overValuedTickerCandles = candlesMap.get(zScoreData.getOvervaluedTicker());
                PairData newPairData = createPairData(zScoreData, underValuedTickerCandles, overValuedTickerCandles);
                result.add(newPairData);
            } catch (Exception e) {
                log.error("Ошибка при создании PairData для пары {}/{}: {}",
                        zScoreData.getUndervaluedTicker(),
                        zScoreData.getOvervaluedTicker(),
                        e.getMessage());
            }
        }

        // Сохраняем с обработкой конфликтов
        List<PairData> savedPairs = new ArrayList<>();
        for (PairData pair : result) {
            try {
                save(pair);
                savedPairs.add(pair);
            } catch (RuntimeException e) {
                log.warn("⚠️ Не удалось сохранить пару {}/{}: {} - пропускаем",
                        pair.getLongTicker(), pair.getShortTicker(), e.getMessage());
                // Продолжаем обработку остальных пар
            }
        }

        log.info("✅ Успешно сохранено {}/{} пар", savedPairs.size(), result.size());

        return result;
    }

    private static PairData createPairData(ZScoreData zScoreData, List<Candle> underValuedTickerCandles, List<Candle> overValuedTickerCandles) {
        // Проверяем наличие данных
        if (underValuedTickerCandles == null || underValuedTickerCandles.isEmpty() || overValuedTickerCandles == null || overValuedTickerCandles.isEmpty()) {
            log.warn("Нет данных по свечам для пары: {} - {}", zScoreData.getUndervaluedTicker(), zScoreData.getOvervaluedTicker());
            throw new IllegalArgumentException("Отсутствуют данные свечей");
        }

        PairData pairData = new PairData();

        pairData.setStatus(TradeStatus.SELECTED);

        // Устанавливаем основные параметры
        pairData.setLongTicker(zScoreData.getUndervaluedTicker());
        pairData.setShortTicker(zScoreData.getOvervaluedTicker());

        // Получаем последние параметры
        ZScoreParam latestParam = zScoreData.getLastZScoreParam();

        // Устанавливаем текущие цены
        pairData.setLongTickerCurrentPrice(CandlesUtil.getLastClose(underValuedTickerCandles));
        pairData.setShortTickerCurrentPrice(CandlesUtil.getLastClose(overValuedTickerCandles));

        // Устанавливаем статистические параметры
        pairData.setZScoreCurrent(latestParam.getZscore());
        pairData.setCorrelationCurrent(latestParam.getCorrelation());
        pairData.setAdfPvalueCurrent(latestParam.getAdfpvalue());
        pairData.setPValueCurrent(latestParam.getPvalue());
        pairData.setMeanCurrent(latestParam.getMean());
        pairData.setStdCurrent(latestParam.getStd());
        pairData.setSpreadCurrent(latestParam.getSpread());
        pairData.setAlphaCurrent(latestParam.getAlpha());
        pairData.setBetaCurrent(latestParam.getBeta());

        if (pairData.getZScoreEntry() != 0) {
            BigDecimal zScoreChanges = BigDecimal.valueOf(latestParam.getZscore()).subtract(BigDecimal.valueOf(pairData.getZScoreEntry()));
            pairData.setZScoreChanges(zScoreChanges);
        }

        // Добавляем всю историю Z-Score из ZScoreData
        if (zScoreData.getZscoreParams() != null && !zScoreData.getZscoreParams().isEmpty()) {
            // Добавляем всю историю, если она есть
            for (ZScoreParam param : zScoreData.getZscoreParams()) {
                pairData.addZScorePoint(param);
            }
        } else {
            // Если истории нет, добавляем хотя бы текущую точку
            pairData.addZScorePoint(latestParam);
        }

        return pairData;
    }

//    public void updateByRequest(UpdatePairDataRequest request) {
//        boolean addEntryPoints = request.isAddEntryPoints();
//        PairData pairData = request.getPairData();
//        ZScoreData zScoreData = request.getZScoreData();
//        Map<String, List<Candle>> candlesMap = request.getCandlesMap();
//        TradeResult tradeResultLong = request.getTradeResultLong();
//        TradeResult tradeResultShort = request.getTradeResultShort();
//        boolean updateChanges = request.isUpdateChanges();
//        boolean updateTradeLog = request.isUpdateTradeLog();
//        Settings settings = request.getSettings();
//
//        //Обновляем текущие цены
//        List<Candle> longTickerCandles = candlesMap.get(pairData.getLongTicker());
//        List<Candle> shortTickerCandles = candlesMap.get(pairData.getShortTicker());
//        double longTickerCurrentPrice = CandlesUtil.getLastClose(longTickerCandles);
//        double shortTickerCurrentPrice = CandlesUtil.getLastClose(shortTickerCandles);
//        pairData.setLongTickerCurrentPrice(longTickerCurrentPrice);
//        pairData.setShortTickerCurrentPrice(shortTickerCurrentPrice);
//
//        //Обновляем текущие данные коинтеграции
//        ZScoreParam latestParam = zScoreData.getLastZScoreParam();
//        pairData.setZScoreCurrent(latestParam.getZscore());
//        pairData.setCorrelationCurrent(latestParam.getCorrelation());
//        pairData.setAdfPvalueCurrent(latestParam.getAdfpvalue());
//        pairData.setPValueCurrent(latestParam.getPvalue());
//        pairData.setMeanCurrent(latestParam.getMean());
//        pairData.setStdCurrent(latestParam.getStd());
//        pairData.setSpreadCurrent(latestParam.getSpread());
//        pairData.setAlphaCurrent(latestParam.getAlpha());
//        pairData.setBetaCurrent(latestParam.getBeta());
//
//        // Добавляем новые точки в историю Z-Score при каждом обновлении
//        if (zScoreData.getZscoreParams() != null && !zScoreData.getZscoreParams().isEmpty()) {
//            // Добавляем всю новую историю из ZScoreData
//            for (ZScoreParam param : zScoreData.getZscoreParams()) {
//                pairData.addZScorePoint(param);
//            }
//        } else {
//            // Если новой истории нет, добавляем хотя бы текущую точку
//            pairData.addZScorePoint(latestParam);
//        }
//
//        if (addEntryPoints) {
//            addEntryPoints(pairData, zScoreData.getLastZScoreParam(), tradeResultLong, tradeResultShort);
//        }
//
//        if (updateChanges) {
//            updateChangesAndSave(pairData);
//        }
//
//        if (updateTradeLog) {
//            tradeLogService.updateTradeLog(pairData, settings);
//        }
//
//        pairDataRepository.save(pairData);
//    }

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

        log.info("🔹Установлены точки входа: LONG {{}} = {}, SHORT {{}} = {}, Z = {}",
                pairData.getLongTicker(), pairData.getLongTickerEntryPrice(),
                pairData.getShortTicker(), pairData.getShortTickerEntryPrice(),
                pairData.getZScoreEntry());
    }

    /**
     * Расчет профита для реальной торговли на основе открытых позиций (дефолтный метод)
     */
    public void preUpdateChanges(PairData pairData) {
        try {
            BigDecimal longCurrent = BigDecimal.valueOf(pairData.getLongTickerCurrentPrice());
            BigDecimal shortCurrent = BigDecimal.valueOf(pairData.getShortTickerCurrentPrice());
            BigDecimal zScoreEntry = BigDecimal.valueOf(pairData.getZScoreEntry());
            BigDecimal zScoreCurrent = BigDecimal.valueOf(pairData.getZScoreCurrent());
            BigDecimal corrCurrent = BigDecimal.valueOf(pairData.getCorrelationCurrent());

            // Сначала обновляем цены позиций с биржи для актуальных данных
            tradingIntegrationService.updatePositions(List.of(pairData.getLongTicker(), pairData.getShortTicker()));

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

            // 🎯 ИСПРАВЛЕНИЕ: Расчет размера позиции для правильного расчета профита
            BigDecimal positionSize = tradingIntegrationService.getPositionSize(pairData);
            if (positionSize == null || positionSize.compareTo(BigDecimal.ZERO) <= 0) {
                // Fallback: примерный расчет размера позиции на основе цен входа
                BigDecimal longPositionValue = longEntry.multiply(BigDecimal.valueOf(50)); // Примерно 50 USDT на позицию
                BigDecimal shortPositionValue = shortEntry.multiply(BigDecimal.valueOf(50)); // Примерно 50 USDT на позицию
                positionSize = longPositionValue.add(shortPositionValue);
            }

            // 🎯 ИСПРАВЛЕНИЕ: Расчет профита в процентах от размера позиции, а не от общего баланса
            BigDecimal profitPercentFromPosition = BigDecimal.ZERO;
            if (positionSize.compareTo(BigDecimal.ZERO) > 0) {
                profitPercentFromPosition = realPnL
                        .divide(positionSize, 10, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            }

            // Текущее время
            long entryTime = pairData.getEntryTime();
            long now = System.currentTimeMillis();
            long currentTimeInMinutes = (now - entryTime) / (1000 * 60);

            // Округления
            BigDecimal longReturnRounded = longReturnPct.setScale(2, RoundingMode.HALF_UP);
            BigDecimal shortReturnRounded = shortReturnPct.setScale(2, RoundingMode.HALF_UP);
            BigDecimal profitRounded = profitPercentFromPosition.setScale(2, RoundingMode.HALF_UP);
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

            // Обновляем экстремумы всех метрик
            updateExtremumValues(pairData, longReturnPct, shortReturnPct, zScoreCurrent, corrCurrent);

            // ✅ Записываем в PairData
            pairData.setLongChanges(longReturnRounded);
            pairData.setShortChanges(shortReturnRounded);
            pairData.setProfitChanges(profitRounded);
            pairData.setZScoreChanges(zScoreRounded);
            pairData.setMinProfitRounded(minProfitRounded);
            pairData.setMaxProfitRounded(maxProfitRounded);
            pairData.setTimeInMinutesSinceEntryToMax(timeInMinutesSinceEntryToMax);
            pairData.setTimeInMinutesSinceEntryToMin(timeInMinutesSinceEntryToMin);

            log.info("Пре обновление изменений для определения выхода для пары {}/{}:", pairData.getLongTicker(), pairData.getShortTicker());
            log.info("📊 LONG {}: Entry: {}, Current: {}, Changes: {}%", pairData.getLongTicker(), longEntry, longCurrent, longReturnRounded);
            log.info("📉 SHORT {}: Entry: {}, Current: {}, Changes: {}%", pairData.getShortTicker(), shortEntry, shortCurrent, shortReturnRounded);
            log.info("💰 Текущий профит: {}%", profitRounded);
            log.info("📈 Max profit: {}%, Min profit: {}%", maxProfitRounded, minProfitRounded);

        } catch (Exception e) {
            log.error("❌ Ошибка при расчете реального профита для пары {}/{}: {}",
                    pairData.getLongTicker(), pairData.getShortTicker(), e.getMessage());
        }
    }

    /**
     * Обновляет экстремумы всех метрик
     */
    private void updateExtremumValues(PairData pairData, BigDecimal longReturnPct, BigDecimal shortReturnPct,
                                      BigDecimal zScoreCurrent, BigDecimal corrCurrent) {
        BigDecimal minZ = updateMin(pairData.getMinZ(), zScoreCurrent);
        BigDecimal maxZ = updateMax(pairData.getMaxZ(), zScoreCurrent);
        BigDecimal minLong = updateMin(pairData.getMinLong(), longReturnPct);
        BigDecimal maxLong = updateMax(pairData.getMaxLong(), longReturnPct);
        BigDecimal minShort = updateMin(pairData.getMinShort(), shortReturnPct);
        BigDecimal maxShort = updateMax(pairData.getMaxShort(), shortReturnPct);
        BigDecimal minCorr = updateMin(pairData.getMinCorr(), corrCurrent);
        BigDecimal maxCorr = updateMax(pairData.getMaxCorr(), corrCurrent);

        pairData.setMinZ(minZ);
        pairData.setMaxZ(maxZ);
        pairData.setMinLong(minLong);
        pairData.setMaxLong(maxLong);
        pairData.setMinShort(minShort);
        pairData.setMaxShort(maxShort);
        pairData.setMinCorr(minCorr);
        pairData.setMaxCorr(maxCorr);
    }

    /**
     * Обновляет статистику и экстремумы для пары
     */
    private void updatePairDataStatistics(PairData pairData) {
        BigDecimal longCurrent = BigDecimal.valueOf(pairData.getLongTickerCurrentPrice());
        BigDecimal shortCurrent = BigDecimal.valueOf(pairData.getShortTickerCurrentPrice());

        BigDecimal zScoreEntry = BigDecimal.valueOf(pairData.getZScoreEntry());
        BigDecimal zScoreCurrent = BigDecimal.valueOf(pairData.getZScoreCurrent());
        BigDecimal corrCurrent = BigDecimal.valueOf(pairData.getCorrelationCurrent());

        // Расчет процентных изменений позиций
        BigDecimal longEntry = BigDecimal.valueOf(pairData.getLongTickerEntryPrice());
        BigDecimal shortEntry = BigDecimal.valueOf(pairData.getShortTickerEntryPrice());

        BigDecimal longReturnPct = longCurrent.subtract(longEntry)
                .divide(longEntry, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        BigDecimal shortReturnPct = shortEntry.subtract(shortCurrent)
                .divide(shortEntry, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        // Текущее время
        long entryTime = pairData.getEntryTime();
        long now = System.currentTimeMillis();
        long currentTimeInMinutes = (now - entryTime) / (1000 * 60);

        // Округления
        BigDecimal longReturnRounded = longReturnPct.setScale(2, RoundingMode.HALF_UP);
        BigDecimal shortReturnRounded = shortReturnPct.setScale(2, RoundingMode.HALF_UP);
        BigDecimal zScoreRounded = zScoreCurrent.subtract(zScoreEntry).setScale(2, RoundingMode.HALF_UP);

        // Используем уже рассчитанный профит для статистики
        BigDecimal currentProfitForStats = pairData.getProfitChanges() != null ? pairData.getProfitChanges() : BigDecimal.ZERO;

        // Обновляем min/max профита
        BigDecimal currentMinProfit = pairData.getMinProfitRounded();
        BigDecimal currentMaxProfit = pairData.getMaxProfitRounded();
        long currentTimeToMax = pairData.getTimeInMinutesSinceEntryToMax();
        long currentTimeToMin = pairData.getTimeInMinutesSinceEntryToMin();

        BigDecimal maxProfitRounded;
        long timeInMinutesSinceEntryToMax;
        if (currentMaxProfit == null || currentProfitForStats.compareTo(currentMaxProfit) > 0) {
            maxProfitRounded = currentProfitForStats;
            timeInMinutesSinceEntryToMax = currentTimeInMinutes;
            log.debug("🚀 Новый максимум прибыли: {}% за {} мин", maxProfitRounded, timeInMinutesSinceEntryToMax);
        } else {
            maxProfitRounded = currentMaxProfit;
            timeInMinutesSinceEntryToMax = currentTimeToMax;
        }

        BigDecimal minProfitRounded;
        long timeInMinutesSinceEntryToMin;
        if (currentMinProfit == null || currentProfitForStats.compareTo(currentMinProfit) < 0) {
            minProfitRounded = currentProfitForStats;
            timeInMinutesSinceEntryToMin = currentTimeInMinutes;
            log.debug("📉 Новый минимум прибыли: {}% за {} мин", minProfitRounded, timeInMinutesSinceEntryToMin);
        } else {
            minProfitRounded = currentMinProfit;
            timeInMinutesSinceEntryToMin = currentTimeToMin;
        }

        // Обновляем экстремумы всех метрик
        updateExtremumValues(pairData, longReturnPct, shortReturnPct, zScoreCurrent, corrCurrent);

        // Записываем в PairData (профит НЕ трогаем - он уже обновлен через ProfitUpdateService)
        pairData.setLongChanges(longReturnRounded);
        pairData.setShortChanges(shortReturnRounded);
        pairData.setZScoreChanges(zScoreRounded);
        pairData.setMinProfitRounded(minProfitRounded);
        pairData.setMaxProfitRounded(maxProfitRounded);
        pairData.setTimeInMinutesSinceEntryToMax(timeInMinutesSinceEntryToMax);
        pairData.setTimeInMinutesSinceEntryToMin(timeInMinutesSinceEntryToMin);

        log.info("Финальное обновление изменений для пары {}/{}", pairData.getLongTicker(), pairData.getShortTicker());
        log.info("📊 LONG {}: Entry: {}, Current: {}, Changes: {}%", pairData.getLongTicker(), longEntry, longCurrent, longReturnRounded);
        log.info("📉 SHORT {}: Entry: {}, Current: {}, Changes: {}%", pairData.getShortTicker(), shortEntry, shortCurrent, shortReturnRounded);
        log.info("💰 Текущий профит: {}%", currentProfitForStats);
        log.info("📈 Max profit: {}%, Min profit: {}%", maxProfitRounded, minProfitRounded);
    }

    /**
     * Обновляет все данные используя ProfitUpdateService для открытых позиций
     */
    public void updateChangesFromOpenPositions(PairData pairData) {
        try {
            // Получаем данные об открытых позициях
            PositionVerificationResult openPositionsInfo = tradingIntegrationService.getOpenPositionsInfo(pairData);

            Position longPosition = openPositionsInfo.getLongPosition();
            Position shortPosition = openPositionsInfo.getShortPosition();

            if (longPosition == null || shortPosition == null) {
                log.warn("⚠️ Не удалось получить информацию о позициях для пары {}/{}",
                        pairData.getLongTicker(), pairData.getShortTicker());
                return;
            }

            // Обновляем текущие цены
            pairData.setLongTickerCurrentPrice(longPosition.getCurrentPrice().doubleValue());
            pairData.setShortTickerCurrentPrice(shortPosition.getCurrentPrice().doubleValue());

            // Рассчитываем текущий нереализованный профит
            BigDecimal totalPnL = longPosition.getUnrealizedPnL().add(shortPosition.getUnrealizedPnL());
            BigDecimal totalFees = longPosition.getOpeningFees().add(shortPosition.getOpeningFees());
            BigDecimal netPnL = totalPnL.subtract(totalFees);

            // Конвертируем в процент от позиции
            BigDecimal profitPercent = calculateProfitPercent(
                    netPnL,
                    pairData.getLongTickerEntryPrice(),
                    pairData.getShortTickerEntryPrice()
            );

            pairData.setProfitChanges(profitPercent);

            log.info("📊 Обновлен профит из открытых позиций {}/{}: {}% (PnL: {}, комиссии: {})",
                    pairData.getLongTicker(), pairData.getShortTicker(),
                    profitPercent, totalPnL, totalFees);

            // Обновляем статистику и экстремумы
            updatePairDataStatistics(pairData);

            log.info("✅ Обновлены данные из открытых позиций для пары {}/{}",
                    pairData.getLongTicker(), pairData.getShortTicker());

        } catch (Exception e) {
            log.error("❌ Ошибка при обновлении данных из открытых позиций для пары {}/{}: {}",
                    pairData.getLongTicker(), pairData.getShortTicker(), e.getMessage());
        }
    }

    /**
     * Обновляет все данные используя ProfitUpdateService для закрытых позиций
     */
    public void updateChangesFromTradeResults(PairData pairData, ArbitragePairTradeInfo tradeInfo) {
        try {
            TradeResult longResult = tradeInfo.getLongTradeResult();
            TradeResult shortResult = tradeInfo.getShortTradeResult();

            if (longResult == null || shortResult == null) {
                log.warn("⚠️ Не удалось получить результаты закрытия для пары {}/{}",
                        pairData.getLongTicker(), pairData.getShortTicker());
                return;
            }

            // Обновляем текущие цены на основе фактических цен исполнения
            pairData.setLongTickerCurrentPrice(longResult.getExecutionPrice().doubleValue());
            pairData.setShortTickerCurrentPrice(shortResult.getExecutionPrice().doubleValue());

            // Рассчитываем чистый профит
            BigDecimal totalPnL = longResult.getPnl().add(shortResult.getPnl());
            BigDecimal totalFees = longResult.getFees().add(shortResult.getFees());
            BigDecimal netPnL = totalPnL.subtract(totalFees);

            // Конвертируем в процент от позиции
            BigDecimal profitPercent = calculateProfitPercent(
                    netPnL,
                    pairData.getLongTickerEntryPrice(),
                    pairData.getShortTickerEntryPrice()
            );

            pairData.setProfitChanges(profitPercent);

            log.info("🏦 Обновлен профит из результатов закрытия {}/{}: {}% (PnL: {}, комиссии: {})",
                    pairData.getLongTicker(), pairData.getShortTicker(),
                    profitPercent, totalPnL, totalFees);

            // Обновляем статистику и экстремумы
            updatePairDataStatistics(pairData);

            log.info("✅ Обновлены данные из результатов закрытия для пары {}/{}",
                    pairData.getLongTicker(), pairData.getShortTicker());

        } catch (Exception e) {
            log.error("❌ Ошибка при обновлении данных из результатов закрытия для пары {}/{}: {}",
                    pairData.getLongTicker(), pairData.getShortTicker(), e.getMessage());
        }
    }

    /**
     * Рассчитывает процент профита от средней входной цены
     * Единая логика расчета для всех типов операций
     */
    private BigDecimal calculateProfitPercent(BigDecimal netPnL, double longEntryPrice, double shortEntryPrice) {
        try {
            BigDecimal longEntry = BigDecimal.valueOf(longEntryPrice);
            BigDecimal shortEntry = BigDecimal.valueOf(shortEntryPrice);
            BigDecimal avgEntryPrice = longEntry.add(shortEntry).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);

            if (avgEntryPrice.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("⚠️ Средняя входная цена меньше или равна нулю: {}", avgEntryPrice);
                return BigDecimal.ZERO;
            }

            return netPnL.divide(avgEntryPrice, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

        } catch (Exception e) {
            log.error("❌ Ошибка при расчете процента профита: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
     * Обновляет все данные кроме профита (общий метод для избежания дублирования)
     */
    private void updateAllOtherData(PairData pairData) {
        BigDecimal longCurrent = BigDecimal.valueOf(pairData.getLongTickerCurrentPrice());
        BigDecimal shortCurrent = BigDecimal.valueOf(pairData.getShortTickerCurrentPrice());
        BigDecimal zScoreEntry = BigDecimal.valueOf(pairData.getZScoreEntry());
        BigDecimal zScoreCurrent = BigDecimal.valueOf(pairData.getZScoreCurrent());
        BigDecimal corrCurrent = BigDecimal.valueOf(pairData.getCorrelationCurrent());

        // Расчет процентных изменений позиций
        BigDecimal longEntry = BigDecimal.valueOf(pairData.getLongTickerEntryPrice());
        BigDecimal shortEntry = BigDecimal.valueOf(pairData.getShortTickerEntryPrice());

        BigDecimal longReturnPct = longCurrent.subtract(longEntry)
                .divide(longEntry, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        BigDecimal shortReturnPct = shortEntry.subtract(shortCurrent)
                .divide(shortEntry, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        // Текущее время
        long entryTime = pairData.getEntryTime();
        long now = System.currentTimeMillis();
        long currentTimeInMinutes = (now - entryTime) / (1000 * 60);

        // Округления
        BigDecimal longReturnRounded = longReturnPct.setScale(2, RoundingMode.HALF_UP);
        BigDecimal shortReturnRounded = shortReturnPct.setScale(2, RoundingMode.HALF_UP);
        BigDecimal zScoreRounded = zScoreCurrent.subtract(zScoreEntry).setScale(2, RoundingMode.HALF_UP);

        // Используем уже рассчитанный профит для статистики
        BigDecimal currentProfitForStats = pairData.getProfitChanges() != null ? pairData.getProfitChanges() : BigDecimal.ZERO;

        // Обновляем min/max профита
        BigDecimal currentMinProfit = pairData.getMinProfitRounded();
        BigDecimal currentMaxProfit = pairData.getMaxProfitRounded();
        long currentTimeToMax = pairData.getTimeInMinutesSinceEntryToMax();
        long currentTimeToMin = pairData.getTimeInMinutesSinceEntryToMin();

        BigDecimal maxProfitRounded;
        long timeInMinutesSinceEntryToMax;
        if (currentMaxProfit == null || currentProfitForStats.compareTo(currentMaxProfit) > 0) {
            maxProfitRounded = currentProfitForStats;
            timeInMinutesSinceEntryToMax = currentTimeInMinutes;
            log.debug("🚀 Новый максимум прибыли: {}% за {} мин", maxProfitRounded, timeInMinutesSinceEntryToMax);
        } else {
            maxProfitRounded = currentMaxProfit;
            timeInMinutesSinceEntryToMax = currentTimeToMax;
        }

        BigDecimal minProfitRounded;
        long timeInMinutesSinceEntryToMin;
        if (currentMinProfit == null || currentProfitForStats.compareTo(currentMinProfit) < 0) {
            minProfitRounded = currentProfitForStats;
            timeInMinutesSinceEntryToMin = currentTimeInMinutes;
            log.debug("📉 Новый минимум прибыли: {}% за {} мин", minProfitRounded, timeInMinutesSinceEntryToMin);
        } else {
            minProfitRounded = currentMinProfit;
            timeInMinutesSinceEntryToMin = currentTimeToMin;
        }

        // Обновляем экстремумы других показателей
        BigDecimal minZ = updateMin(pairData.getMinZ(), zScoreCurrent);
        BigDecimal maxZ = updateMax(pairData.getMaxZ(), zScoreCurrent);
        BigDecimal minLong = updateMin(pairData.getMinLong(), longReturnPct);
        BigDecimal maxLong = updateMax(pairData.getMaxLong(), longReturnPct);
        BigDecimal minShort = updateMin(pairData.getMinShort(), shortReturnPct);
        BigDecimal maxShort = updateMax(pairData.getMaxShort(), shortReturnPct);
        BigDecimal minCorr = updateMin(pairData.getMinCorr(), corrCurrent);
        BigDecimal maxCorr = updateMax(pairData.getMaxCorr(), corrCurrent);

        // Записываем в PairData (профит НЕ трогаем - он уже обновлен через ProfitUpdateService)
        pairData.setLongChanges(longReturnRounded);
        pairData.setShortChanges(shortReturnRounded);
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

        // Логирование
        log.info("📊 LONG {}: Entry: {}, Current: {}, Changes: {}%",
                pairData.getLongTicker(), longEntry, longCurrent, longReturnRounded);
        log.info("📉 SHORT {}: Entry: {}, Current: {}, Changes: {}%",
                pairData.getShortTicker(), shortEntry, shortCurrent, shortReturnRounded);
        log.info("💰 Текущий профит: {}%", currentProfitForStats);
        log.info("📈 Max profit: {}%, Min profit: {}%", maxProfitRounded, minProfitRounded);
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

    private void updateByRequest(PairData pairData, ZScoreData zScoreData, TradeResult longResult, TradeResult shortResult) {
        //Обновляем текущие цены
        pairData.setLongTickerCurrentPrice(longResult.getExecutionPrice().doubleValue());
        pairData.setShortTickerCurrentPrice(shortResult.getExecutionPrice().doubleValue());

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

        save(pairData);
    }

    /**
     * Обновляет актуальный профит перед проверкой exit strategy
     * Критично для правильного срабатывания тейк-профита и стоп-лосса
     */
    public void updateCurrentProfitBeforeExitCheck(PairData pairData) {
        try {
            // Сначала обновляем цены позиций с биржи для актуальных данных
            tradingIntegrationService.updatePositions(List.of(pairData.getLongTicker(), pairData.getShortTicker()));

            // Затем получаем реальный PnL для данной пары с актуальными ценами
            BigDecimal realPnL = tradingIntegrationService.getPositionPnL(pairData);

            pairData.setProfitChanges(realPnL);
            log.info("💰 Сохранен пре профит для расчета exit: {}% для пары {}/{}",
                    pairData.getProfitChanges(), pairData.getLongTicker(), pairData.getShortTicker());

        } catch (Exception e) {
            log.error("❌ Ошибка при обновлении профита перед проверкой exit strategy для пары {}/{}: {}",
                    pairData.getLongTicker(), pairData.getShortTicker(), e.getMessage());
        }
    }

    public void save(PairData pairData) {
        pairDataRepository.save(pairData);
    }

    public PairData findById(Long id) {
        return pairDataRepository.findById(id).orElse(null);
    }

    public List<PairData> findAllByStatusOrderByEntryTimeDesc(TradeStatus status) {
        return pairDataRepository.findAllByStatusOrderByEntryTimeDesc(status);
    }

    public List<PairData> findAllByStatusOrderByUpdatedTimeDesc(TradeStatus status) {
        return pairDataRepository.findAllByStatusOrderByUpdatedTimeDesc(status);
    }

    public int deleteAllByStatus(TradeStatus status) {
        return pairDataRepository.deleteAllByStatus(status);
    }

    public void delete(PairData pairData) {
        pairDataRepository.delete(pairData);
    }

    public int excludeExistingTradingPairs(List<ZScoreData> zScoreDataList) {
        if (zScoreDataList == null || zScoreDataList.isEmpty()) {
            return 0;
        }

        // Получаем список уже торгующихся пар
        List<PairData> tradingPairs = findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);

        // Собираем множество идентификаторов пар в торговле (например, "BTC-USDT-ETH-USDT")
        Set<String> tradingSet = tradingPairs.stream()
                .map(pair -> buildKey(pair.getLongTicker(), pair.getShortTicker()))
                .collect(Collectors.toSet());

        // Удаляем из списка те пары, которые уже торгуются
        zScoreDataList.removeIf(z -> {
            String key = buildKey(z.getUndervaluedTicker(), z.getOvervaluedTicker());
            return tradingSet.contains(key);
        });
        return zScoreDataList.size();
    }

    // Приватный метод для создания уникального ключа пары, независимо от порядка
    private String buildKey(String ticker1, String ticker2) {
        List<String> sorted = Arrays.asList(ticker1, ticker2);
        Collections.sort(sorted);
        return sorted.get(0) + "-" + sorted.get(1);
    }

    public BigDecimal getUnrealizedProfitTotal() {
        List<PairData> tradingPairs = findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);
        return tradingPairs.stream()
                .map(PairData::getProfitChanges)
                .filter(p -> p != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
