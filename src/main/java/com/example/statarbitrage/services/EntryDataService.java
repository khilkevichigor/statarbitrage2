package com.example.statarbitrage.services;

import com.example.statarbitrage.model.Candle;
import com.example.statarbitrage.model.ChangesData;
import com.example.statarbitrage.model.EntryData;
import com.example.statarbitrage.model.ZScoreEntry;
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
public class EntryDataService {
    private final ChangesService changesService;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ENTRY_DATA_JSON_FILE_PATH = "entry_data.json";

    public EntryData createEntryData(ZScoreEntry bestPair, ConcurrentHashMap<String, List<Candle>> candlesMap) {
        EntryData entryData = new EntryData();
        entryData.setLongticker(bestPair.getLongticker());
        entryData.setShortticker(bestPair.getShortticker());
//        entryData.setZScoreEntry(bestPair.getZscore()); //todo ошибка

        List<Candle> longTickerCandles = candlesMap.get(bestPair.getLongticker());
        List<Candle> shortTickerCandles = candlesMap.get(bestPair.getShortticker());

        if (longTickerCandles == null || longTickerCandles.isEmpty() ||
                shortTickerCandles == null || shortTickerCandles.isEmpty()) {
            log.warn("Нет данных по свечам для пары: {} - {}", bestPair.getLongticker(), bestPair.getShortticker());
        }

        double longPrice = longTickerCandles.get(longTickerCandles.size() - 1).getClose();
        double shortPrice = shortTickerCandles.get(shortTickerCandles.size() - 1).getClose();

        entryData.setLongTickerCurrentPrice(longPrice);
        entryData.setShortTickerCurrentPrice(shortPrice);

        entryData.setZScoreCurrent(bestPair.getZscore());
        entryData.setCorrelationCurrent(bestPair.getCorrelation());
        entryData.setAdfPvalueCurrent(bestPair.getAdfpvalue());
        entryData.setPValueCurrent(bestPair.getPvalue());
        entryData.setMeanCurrent(bestPair.getMean());
        entryData.setStdCurrent(bestPair.getStd());
        entryData.setSpreadCurrent(bestPair.getSpread());
        entryData.setAlphaCurrent(bestPair.getAlpha());
        entryData.setBetaCurrent(bestPair.getBeta());

        save(entryData);

        log.info("Создали entry_data.json");

        return entryData;
    }

    public EntryData getEntryData() {
        EntryData entryData = readEntryDataJson(ENTRY_DATA_JSON_FILE_PATH);
        if (entryData == null) {
            String message = "⚠️entry_data.json пустой или не найден";
            log.warn(message);
            throw new RuntimeException(message);
        }
        return entryData;
    }

    public EntryData readEntryDataJson(String entryDataJsonFilePath) {
        try {
            return MAPPER.readValue(new File(entryDataJsonFilePath), new TypeReference<>() {
            });
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void save(EntryData entryData) {
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(ENTRY_DATA_JSON_FILE_PATH), entryData);
        } catch (Exception e) {
            log.error("Ошибка при записи entry_data.json: {}", e.getMessage(), e);
        }
    }

    public void updateEntryDataAndSave(EntryData entryData, ZScoreEntry firstPair, ConcurrentHashMap<String, List<Candle>> candles) {
        updateCurrentPrices(entryData, candles);
        updateCurrentCointParams(entryData, firstPair);
        setupEntryPointsIfNeeded(entryData, candles);
        calculateAndSetChanges(entryData);
        save(entryData);
    }

    public void updateCurrentPrices(EntryData entryData, ConcurrentHashMap<String, List<Candle>> candles) {
        try {
            List<Candle> longTickerCandles = candles.get(entryData.getLongticker());
            List<Candle> shortTickerCandles = candles.get(entryData.getShortticker());

            double longPrice = longTickerCandles.get(longTickerCandles.size() - 1).getClose();
            double shortPrice = shortTickerCandles.get(shortTickerCandles.size() - 1).getClose();

            entryData.setLongTickerCurrentPrice(longPrice);
            entryData.setShortTickerCurrentPrice(shortPrice);
        } catch (Exception e) {
            log.error("Ошибка при обновлении текущих цен: {}", e.getMessage(), e);
        }
    }

    public void setupEntryPointsIfNeeded(EntryData entryData, ConcurrentHashMap<String, List<Candle>> candles) {
        if (entryData.getLongTickerEntryPrice() == 0.0 || entryData.getShortTickerEntryPrice() == 0.0) {
            entryData.setLongTickerEntryPrice(entryData.getLongTickerCurrentPrice());
            entryData.setShortTickerEntryPrice(entryData.getShortTickerCurrentPrice());

            entryData.setZScoreEntry(entryData.getZScoreCurrent());
            entryData.setCorrelationEntry(entryData.getCorrelationCurrent());
            entryData.setAdfPvalueEntry(entryData.getAdfPvalueCurrent());
            entryData.setPValueEntry(entryData.getPValueCurrent());
            entryData.setMeanEntry(entryData.getMeanCurrent());
            entryData.setStdEntry(entryData.getStdCurrent());
            entryData.setSpreadEntry(entryData.getSpreadCurrent());
            entryData.setAlphaEntry(entryData.getAlphaCurrent());
            entryData.setBetaEntry(entryData.getBetaCurrent());

            // Ставим время открытия по long-свечке
            entryData.setEntryTime(getEntryTime(entryData.getLongticker(), candles));

            log.info("🔹Установлены точки входа: LONG {{}} = {}, SHORT {{}} = {}, SPREAD = {}, MEAN = {}, Z = {}, ВРЕМЯ = {}",
                    entryData.getLongticker(), entryData.getLongTickerEntryPrice(),
                    entryData.getShortticker(), entryData.getShortTickerEntryPrice(),
                    entryData.getSpreadEntry(), entryData.getMeanEntry(),
                    entryData.getZScoreEntry(),
                    entryData.getEntryTime());
        }
    }

    private long getEntryTime(String longticker, ConcurrentHashMap<String, List<Candle>> candles) {
        List<Candle> longTickerCandles = candles.get(longticker);
        Candle longCandle = longTickerCandles.get(longTickerCandles.size() - 1);
        return longCandle.getTimestamp();
    }

    public void calculateAndSetChanges(EntryData entryData) {
        ChangesData changesData = changesService.calculateChanges(entryData);
        entryData.setProfitStr(changesData.getProfitStr());
        entryData.setProfit(changesData.getProfitRounded());
        entryData.setZScoreChanges(changesData.getZScoreRounded());
        entryData.setChartProfitMessage(changesData.getChartProfitMessage());
    }

    public void updateCurrentCointParams(EntryData entryData, ZScoreEntry firstPair) {
        entryData.setZScoreCurrent(firstPair.getZscore());
        entryData.setCorrelationCurrent(firstPair.getCorrelation());
        entryData.setAdfPvalueCurrent(firstPair.getAdfpvalue());
        entryData.setPValueCurrent(firstPair.getPvalue());
        entryData.setMeanCurrent(firstPair.getMean());
        entryData.setStdCurrent(firstPair.getStd());
        entryData.setSpreadCurrent(firstPair.getSpread());
        entryData.setAlphaCurrent(firstPair.getAlpha());
        entryData.setBetaCurrent(firstPair.getBeta());
    }
}
