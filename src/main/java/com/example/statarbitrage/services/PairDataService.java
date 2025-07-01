package com.example.statarbitrage.services;

import com.example.statarbitrage.dto.Candle;
import com.example.statarbitrage.dto.ZScoreData;
import com.example.statarbitrage.dto.ZScoreParam;
import com.example.statarbitrage.model.PairData;
import com.example.statarbitrage.repositories.PairDataRepository;
import com.example.statarbitrage.utils.CandlesUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ChangesService changesService;
    private final ExitStrategyService exitStrategyService;
    private final PairDataRepository pairDataRepository;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public List<PairData> createPairDataList(List<ZScoreData> top, Map<String, List<Candle>> candlesMap) {
        List<PairData> result = new ArrayList<>();

        for (ZScoreData zScoreData : top) {
            try {
                List<Candle> longTickerCandles = candlesMap.get(zScoreData.getLongTicker());
                List<Candle> shortTickerCandles = candlesMap.get(zScoreData.getShortTicker());
                PairData newPairData = createPairData(zScoreData, longTickerCandles, shortTickerCandles);
                result.add(newPairData);
            } catch (Exception e) {
                log.error("Ошибка при создании PairData для пары {}/{}: {}",
                        zScoreData.getLongTicker(),
                        zScoreData.getShortTicker(),
                        e.getMessage());
            }
        }

        result.forEach(this::saveToDb);

        return result;
    }

    private static PairData createPairData(ZScoreData zScoreData, List<Candle> longTickerCandles, List<Candle> shortTickerCandles) {
        // Проверяем наличие данных
        if (longTickerCandles == null || longTickerCandles.isEmpty() ||
                shortTickerCandles == null || shortTickerCandles.isEmpty()) {
            log.warn("Нет данных по свечам для пары: {} - {}",
                    zScoreData.getLongTicker(), zScoreData.getShortTicker());
            throw new IllegalArgumentException("Отсутствуют данные свечей");
        }

        PairData pairData = new PairData();

        pairData.setStatus(TradeStatus.SELECTED);

        // Устанавливаем основные параметры
        pairData.setLongTicker(zScoreData.getLongTicker());
        pairData.setShortTicker(zScoreData.getShortTicker());
        pairData.setZScoreParams(zScoreData.getZscoreParams());
        pairData.setLongTickerCandles(longTickerCandles);
        pairData.setShortTickerCandles(shortTickerCandles);

        // Получаем последние параметры
        ZScoreParam latestParam = zScoreData.getLastZScoreParam();

        // Устанавливаем текущие цены
        pairData.setLongTickerCurrentPrice(CandlesUtil.getLastClose(longTickerCandles));
        pairData.setShortTickerCurrentPrice(CandlesUtil.getLastClose(shortTickerCandles));

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

        return pairData;
    }

    public void update(PairData pairData, ZScoreData zScoreData, List<Candle> longTickerCandles, List<Candle> shortTickerCandles) {
        // Проверяем наличие данных
        if (longTickerCandles == null || longTickerCandles.isEmpty() ||
                shortTickerCandles == null || shortTickerCandles.isEmpty()) {
            log.warn("Нет данных по свечам для пары: {} - {}",
                    zScoreData.getLongTicker(), zScoreData.getShortTicker());
            throw new IllegalArgumentException("Отсутствуют данные свечей");
        }

        double longTickerCurrentPrice = CandlesUtil.getLastClose(longTickerCandles);
        double shortTickerCurrentPrice = CandlesUtil.getLastClose(shortTickerCandles);

        pairData.setLongTickerCurrentPrice(longTickerCurrentPrice);
        pairData.setShortTickerCurrentPrice(shortTickerCurrentPrice);

        ZScoreParam latestParam = zScoreData.getLastZScoreParam();

        //Точки входа
        if (pairData.getStatus() == TradeStatus.SELECTED) { //по статусу надежнее

            pairData.setStatus(TradeStatus.TRADING);

            pairData.setLongTickerEntryPrice(longTickerCurrentPrice);
            pairData.setShortTickerEntryPrice(shortTickerCurrentPrice);

            pairData.setZScoreEntry(latestParam.getZscore());
            pairData.setCorrelationEntry(latestParam.getCorrelation());
            pairData.setAdfPvalueEntry(latestParam.getAdfpvalue());
            pairData.setPValueEntry(latestParam.getPvalue());
            pairData.setMeanEntry(latestParam.getMean());
            pairData.setStdEntry(latestParam.getStd());
            pairData.setSpreadEntry(latestParam.getSpread());
            pairData.setAlphaEntry(latestParam.getAlpha());
            pairData.setBetaEntry(latestParam.getBeta());

            // Ставим время открытия по long-свечке
            pairData.setEntryTime(latestParam.getTimestamp());

            log.info("🔹Установлены точки входа: LONG {{}} = {}, SHORT {{}} = {}, Z = {}",
                    pairData.getLongTicker(), pairData.getLongTickerEntryPrice(),
                    pairData.getShortTicker(), pairData.getShortTickerEntryPrice(),
                    pairData.getZScoreEntry());
        }

        //updateCurrentCointParams
        pairData.setZScoreCurrent(latestParam.getZscore());
        pairData.setCorrelationCurrent(latestParam.getCorrelation());
        pairData.setAdfPvalueCurrent(latestParam.getAdfpvalue());
        pairData.setPValueCurrent(latestParam.getPvalue());
        pairData.setMeanCurrent(latestParam.getMean());
        pairData.setStdCurrent(latestParam.getStd());
        pairData.setSpreadCurrent(latestParam.getSpread());
        pairData.setAlphaCurrent(latestParam.getAlpha());
        pairData.setBetaCurrent(latestParam.getBeta());

        changesService.calculateAndAdd(pairData);

        pairData.setZScoreParams(zScoreData.getZscoreParams()); //обновляем

        String exitReason = exitStrategyService.getExitReason(pairData);
        if (exitReason != null) {
            pairData.setExitReason(exitReason);
            pairData.setStatus(TradeStatus.CLOSED);
        }

        saveToDb(pairData);
    }

    public void saveToDb(PairData pairData) {
        pairDataRepository.save(pairData);
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
        if (zScoreDataList == null || zScoreDataList.isEmpty()) return;

        // Получаем список уже торгующихся пар
        List<PairData> tradingPairs = findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);

        // Собираем множество идентификаторов пар в торговле (например, "BTC-USDT-ETH-USDT")
        Set<String> tradingSet = tradingPairs.stream()
                .map(pair -> buildKey(pair.getLongTicker(), pair.getShortTicker()))
                .collect(Collectors.toSet());

        // Удаляем из списка те пары, которые уже торгуются
        zScoreDataList.removeIf(z -> {
            String key = buildKey(z.getLongTicker(), z.getShortTicker());
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
                .map(PairData::getProfitChanges)
                .filter(p -> p != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

}
