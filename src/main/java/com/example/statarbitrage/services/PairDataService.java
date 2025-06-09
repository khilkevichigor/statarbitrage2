package com.example.statarbitrage.services;

import com.example.statarbitrage.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class PairDataService {
    private final ChangesService changesService;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PAIR_DATA_JSON_FILE_PATH = "pair_data.json";

    public PairData createPairData(ZScoreData zScoreData, ConcurrentHashMap<String, List<Candle>> candlesMap) {
        PairData pairData = new PairData();

        pairData.setZScoreParams(zScoreData.getZscoreParams());

        pairData.setCandles(candlesMap);

        ZScoreParam latestParam = zScoreData.getZscoreParams().get(zScoreData.getZscoreParams().size() - 1);

        pairData.setLongTicker(zScoreData.getLongTicker());
        pairData.setShortTicker(zScoreData.getShortTicker());

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

        save(pairData);

        log.info("Создали pair_data.json");

        return pairData;
    }

    public PairData getPairData() {
        PairData pairData = readPairDataJson(PAIR_DATA_JSON_FILE_PATH);
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

    public void save(PairData pairData) {
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(PAIR_DATA_JSON_FILE_PATH), pairData);
        } catch (Exception e) {
            log.error("Ошибка при записи pair_data.json: {}", e.getMessage(), e);
        }
    }

    public void update(PairData pairData, ZScoreData zScoreData, ConcurrentHashMap<String, List<Candle>> candles) {
        pairData.setCandles(candles);

        //updateCurrentPrices
        List<Candle> longTickerCandles = candles.get(pairData.getLongTicker());
        List<Candle> shortTickerCandles = candles.get(pairData.getShortTicker());

        double aCurrentPrice = longTickerCandles.get(longTickerCandles.size() - 1).getClose();
        double bCurrentPrice = shortTickerCandles.get(shortTickerCandles.size() - 1).getClose();

        pairData.setLongTickerCurrentPrice(aCurrentPrice);
        pairData.setShortTickerCurrentPrice(bCurrentPrice);

        ZScoreParam latestParam = zScoreData.getZscoreParams().get(zScoreData.getZscoreParams().size() - 1);

        //setupEntryPointsIfNeeded
        if (pairData.getLongTickerEntryPrice() == 0.0 || pairData.getShortTickerEntryPrice() == 0.0) {
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

        //calculateAndSetChanges
        ChangesData changesData = changesService.calculate(pairData);
        pairData.setLongChanges(changesData.getLongReturnRounded());
        pairData.setShortChanges(changesData.getShortReturnRounded());
        pairData.setProfitChanges(changesData.getProfitRounded());
        pairData.setZScoreChanges(changesData.getZScoreRounded());
        pairData.setTimeInMinutesSinceEntryToMax(changesData.getTimeInMinutesSinceEntryToMax());
        pairData.setTimeInMinutesSinceEntryToMin(changesData.getTimeInMinutesSinceEntryToMin());
        pairData.setMinProfitRounded(changesData.getMinProfitRounded());
        pairData.setMaxProfitRounded(changesData.getMaxProfitRounded());

        pairData.setZScoreParams(zScoreData.getZscoreParams()); //обновляем

        save(pairData);
    }
}
