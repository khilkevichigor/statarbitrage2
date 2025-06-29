package com.example.statarbitrage.services;

import com.example.statarbitrage.model.Candle;
import com.example.statarbitrage.model.PairData;
import com.example.statarbitrage.model.ZScoreData;
import com.example.statarbitrage.model.ZScoreParam;
import com.example.statarbitrage.repositories.PairDataRepository;
import com.example.statarbitrage.utils.CandlesUtil;
import com.example.statarbitrage.vaadin.services.TradeStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static com.example.statarbitrage.constant.Constants.PAIR_DATA_FILE_NAME;

@Slf4j
@Service
@RequiredArgsConstructor
public class PairDataService {
    private final ChangesService changesService;
    private final ExitStrategyService exitStrategyService;
    private final PairDataRepository pairDataRepository;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public PairData createPairData(ZScoreData zScoreData, Map<String, List<Candle>> candlesMap) {
        PairData pairData = new PairData();

        pairData.setLongTicker(zScoreData.getLongTicker());
        pairData.setShortTicker(zScoreData.getShortTicker());

        pairData.setZScoreParams(zScoreData.getZscoreParams());

        pairData.setCandles(candlesMap);

        ZScoreParam latestParam = zScoreData.getLastZScoreParam();

        List<Candle> longTickerCandles = candlesMap.get(zScoreData.getLongTicker());
        List<Candle> shortTickerCandles = candlesMap.get(zScoreData.getShortTicker());

        if (longTickerCandles == null || longTickerCandles.isEmpty() ||
                shortTickerCandles == null || shortTickerCandles.isEmpty()) {
            log.warn("Нет данных по свечам для пары: {} - {}", zScoreData.getLongTicker(), zScoreData.getShortTicker());
        }

        pairData.setLongTickerCurrentPrice(longTickerCandles.get(longTickerCandles.size() - 1).getClose());
        pairData.setShortTickerCurrentPrice(shortTickerCandles.get(shortTickerCandles.size() - 1).getClose());

        pairData.setZScoreCurrent(latestParam.getZscore());
        pairData.setCorrelationCurrent(latestParam.getCorrelation());
        pairData.setAdfPvalueCurrent(latestParam.getAdfpvalue());
        pairData.setPValueCurrent(latestParam.getPvalue());
        pairData.setMeanCurrent(latestParam.getMean());
        pairData.setStdCurrent(latestParam.getStd());
        pairData.setSpreadCurrent(latestParam.getSpread());
        pairData.setAlphaCurrent(latestParam.getAlpha());
        pairData.setBetaCurrent(latestParam.getBeta());

        saveToJson(pairData);

        log.info("Создали pair_data.json");

        return pairData;
    }

    public List<PairData> createPairDataList(List<ZScoreData> top, Map<String, List<Candle>> candlesMap) {
        List<PairData> result = new ArrayList<>();

        for (ZScoreData zScoreData : top) {
            try {
                List<Candle> longCandles = candlesMap.get(zScoreData.getLongTicker());
                List<Candle> shortCandles = candlesMap.get(zScoreData.getShortTicker());
                PairData newPairData = createPairData(zScoreData, longCandles, shortCandles);
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

    private static PairData createPairData(ZScoreData zScoreData, List<Candle> longCandles, List<Candle> shortCandles) {
        // Проверяем наличие данных
        if (longCandles == null || longCandles.isEmpty() ||
                shortCandles == null || shortCandles.isEmpty()) {
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
        pairData.setLongCandles(longCandles);
        pairData.setShortCandles(shortCandles);

        // Получаем последние параметры
        ZScoreParam latestParam = zScoreData.getLastZScoreParam();

        // Устанавливаем текущие цены
        pairData.setLongTickerCurrentPrice(CandlesUtil.getLastClose(longCandles));
        pairData.setShortTickerCurrentPrice(CandlesUtil.getLastClose(shortCandles));

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

    private void calculateAdditionalMetrics(PairData pairData) {
        // Здесь можно добавить дополнительные расчеты метрик
        // Например:
        BigDecimal zScoreChanges = BigDecimal.valueOf(pairData.getZScoreCurrent())
                .subtract(BigDecimal.valueOf(pairData.getZScoreEntry()));
        pairData.setZScoreChanges(zScoreChanges);

        // Добавьте другие расчеты по необходимости
    }

    public PairData getPairData() {
        PairData pairData = readPairDataJson(PAIR_DATA_FILE_NAME);
        if (pairData == null) {
            String message = "⚠️pair_data.json пустой или не найден";
            log.warn(message);
            throw new RuntimeException(message);
        }
        return pairData;
    }

    public PairData readPairDataJson(String entryDataJsonFilePath) {
        try {
            return MAPPER.readValue(new File(entryDataJsonFilePath), new TypeReference<>() {
            });
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void saveToJson(PairData pairData) {
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(PAIR_DATA_FILE_NAME), pairData);
        } catch (Exception e) {
            log.error("Ошибка при записи pair_data.json: {}", e.getMessage(), e);
        }
    }

    public void update(PairData pairData, ZScoreData zScoreData, Map<String, List<Candle>> candles) {
        pairData.setCandles(candles);

        //updateCurrentPrices
        List<Candle> longTickerCandles = candles.get(pairData.getLongTicker());
        List<Candle> shortTickerCandles = candles.get(pairData.getShortTicker());

        double aCurrentPrice = longTickerCandles.get(longTickerCandles.size() - 1).getClose();
        double bCurrentPrice = shortTickerCandles.get(shortTickerCandles.size() - 1).getClose();

        pairData.setLongTickerCurrentPrice(aCurrentPrice);
        pairData.setShortTickerCurrentPrice(bCurrentPrice);

        ZScoreParam latestParam = zScoreData.getLastZScoreParam();

        //setupEntryPointsIfNeeded
        if (pairData.getStatus() == TradeStatus.SELECTED) { //по статусу надежнее

            pairData.setStatus(TradeStatus.TRADING);

            pairData.setLongTickerEntryPrice(aCurrentPrice);
            pairData.setShortTickerEntryPrice(bCurrentPrice);

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

    public int countByStatus(TradeStatus status) {
        return pairDataRepository.countByStatus(status);
    }
}
