package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.dto.Candle;
import com.example.statarbitrage.common.dto.ZScoreData;
import com.example.statarbitrage.common.dto.ZScoreParam;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.TradeStatus;
import com.example.statarbitrage.common.utils.CandlesUtil;
import com.example.statarbitrage.core.repositories.PairDataRepository;
import com.example.statarbitrage.trading.model.Portfolio;
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

    public void updateCurrentDataAndSave(PairData pairData, ZScoreData zScoreData, Map<String, List<Candle>> candlesMap) {

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

        save(pairData);
    }

    public void addEntryPointsAndSave(PairData pairData, ZScoreParam latestParam, TradeResult longResult, TradeResult shortResult) {
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

        save(pairData);
    }

    /**
     * Расчет профита для реальной торговли на основе открытых позиций
     */
    public void updateChangesAndSave(PairData pairData) {
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

            pairDataRepository.save(pairData);

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

    public void updateCurrentDataAndSave(PairData pairData, ZScoreData zScoreData, TradeResult longResult, TradeResult shortResult) {
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
