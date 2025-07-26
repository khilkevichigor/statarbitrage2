package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.dto.Candle;
import com.example.statarbitrage.common.dto.ChangesData;
import com.example.statarbitrage.common.dto.ZScoreData;
import com.example.statarbitrage.common.dto.ZScoreParam;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.TradeStatus;
import com.example.statarbitrage.common.utils.CandlesUtil;
import com.example.statarbitrage.core.repositories.PairDataRepository;
import com.example.statarbitrage.trading.model.TradeResult;
import com.example.statarbitrage.trading.services.TradingIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PairDataService {
    private final PairDataRepository pairDataRepository;
    private final TradingIntegrationService tradingIntegrationService;
    private final CalculateChangesService calculateChangesService;
    private final EntryPointService entryPointService;

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

    private PairData createPairData(ZScoreData zScoreData, List<Candle> underValuedTickerCandles, List<Candle> overValuedTickerCandles) {
        // Проверяем наличие данных
        if (underValuedTickerCandles == null || underValuedTickerCandles.isEmpty() || overValuedTickerCandles == null || overValuedTickerCandles.isEmpty()) {
            log.warn("⚠️ Нет данных по свечам для пары: {} - {}", zScoreData.getUndervaluedTicker(), zScoreData.getOvervaluedTicker());
            throw new IllegalArgumentException("⚠️ Отсутствуют данные свечей");
        }

        PairData pairData = new PairData();

        pairData.setStatus(TradeStatus.SELECTED);

        // Устанавливаем основные параметры
        pairData.setLongTicker(zScoreData.getUndervaluedTicker());
        pairData.setShortTicker(zScoreData.getOvervaluedTicker());

        // Устанавливаем текущие цены
        pairData.setLongTickerCurrentPrice(CandlesUtil.getLastClose(underValuedTickerCandles));
        pairData.setShortTickerCurrentPrice(CandlesUtil.getLastClose(overValuedTickerCandles));

        updateZScoreDataCurrent(pairData, zScoreData);

        return pairData;
    }

    public void updateZScoreDataCurrent(PairData pairData, ZScoreData zScoreData) {
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

    public void excludeExistingTradingPairs(List<ZScoreData> zScoreDataList) {
        if (zScoreDataList == null || zScoreDataList.isEmpty()) {
            return;
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
                .map(PairData::getProfitPercentChanges)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Deprecated
    public void addCurrentPricesFromCandles(PairData pairData, Map<String, List<Candle>> candlesMap) {
        List<Candle> longTickerCandles = candlesMap.get(pairData.getLongTicker());
        List<Candle> shortTickerCandles = candlesMap.get(pairData.getShortTicker());
        double longTickerCurrentPrice = CandlesUtil.getLastClose(longTickerCandles);
        double shortTickerCurrentPrice = CandlesUtil.getLastClose(shortTickerCandles);
        pairData.setLongTickerCurrentPrice(longTickerCurrentPrice);
        pairData.setShortTickerCurrentPrice(shortTickerCurrentPrice);
    }

    public void addEntryPoints(PairData pairData, ZScoreData zScoreData, TradeResult openLongTradeResult, TradeResult openShortTradeResult) {
        entryPointService.addEntryPoints(pairData, zScoreData, openLongTradeResult, openShortTradeResult);
//        ZScoreParam latestParam = zScoreData.getLastZScoreParam();
//
//        pairData.setLongTickerEntryPrice(openLongTradeResult.getExecutionPrice().doubleValue());
//        pairData.setShortTickerEntryPrice(openShortTradeResult.getExecutionPrice().doubleValue());
//
//        pairData.setZScoreEntry(latestParam.getZscore());
//        pairData.setCorrelationEntry(latestParam.getCorrelation());
//        pairData.setAdfPvalueEntry(latestParam.getAdfpvalue());
//        pairData.setPValueEntry(latestParam.getPvalue());
//        pairData.setMeanEntry(latestParam.getMean());
//        pairData.setStdEntry(latestParam.getStd());
//        pairData.setSpreadEntry(latestParam.getSpread());
//        pairData.setAlphaEntry(latestParam.getAlpha());
//        pairData.setBetaEntry(latestParam.getBeta());
//        // Время входа
//        pairData.setEntryTime(openLongTradeResult.getExecutionTime().atZone(java.time.ZoneId.systemDefault()).toEpochSecond() * 1000);
//
//        log.info("🔹Установлены точки входа: LONG {{}} = {}, SHORT {{}} = {}, Z = {}",
//                pairData.getLongTicker(), pairData.getLongTickerEntryPrice(),
//                pairData.getShortTicker(), pairData.getShortTickerEntryPrice(),
//                pairData.getZScoreEntry());
    }

    public void addChanges(PairData pairData) {
        ChangesData changes = calculateChangesService.getChanges(pairData);

        pairData.setMinLong(changes.getMinLong());
        pairData.setMaxLong(changes.getMaxLong());
        pairData.setLongChanges(changes.getLongChanges());
        pairData.setLongTickerCurrentPrice(changes.getLongCurrentPrice().doubleValue());

        pairData.setMinShort(changes.getMinShort());
        pairData.setMaxShort(changes.getMaxShort());
        pairData.setShortChanges(changes.getShortChanges());
        pairData.setShortTickerCurrentPrice(changes.getShortCurrentPrice().doubleValue());

        changes.setMinZ(changes.getMinZ());
        pairData.setMaxZ(changes.getMaxZ());

        pairData.setMinCorr(changes.getMinCorr());
        pairData.setMaxCorr(changes.getMaxCorr());

        pairData.setMinProfitChanges(changes.getMinProfitChanges());
        pairData.setMaxProfitChanges(changes.getMaxProfitChanges());
        pairData.setProfitPercentChanges(changes.getProfitPercentChanges());
        pairData.setProfitUSDTChanges(changes.getProfitUSDTChanges());

        pairData.setTimeInMinutesSinceEntryToMin(changes.getTimeInMinutesSinceEntryToMin());
        pairData.setTimeInMinutesSinceEntryToMax(changes.getTimeInMinutesSinceEntryToMax());

        pairData.setZScoreChanges(changes.getZScoreChanges());
    }
}
