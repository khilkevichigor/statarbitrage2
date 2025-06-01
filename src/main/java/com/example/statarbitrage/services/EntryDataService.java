package com.example.statarbitrage.services;

import com.example.statarbitrage.model.Candle;
import com.example.statarbitrage.model.EntryData;
import com.example.statarbitrage.model.ProfitData;
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
    private final ProfitService profitService;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ENTRY_DATA_JSON_FILE_PATH = "entry_data.json";

    public EntryData createEntryData(ZScoreEntry bestPair, ConcurrentHashMap<String, List<Candle>> candlesMap) {
        EntryData entryData = new EntryData();
        entryData.setLongticker(bestPair.getLongticker());
        entryData.setShortticker(bestPair.getShortticker());

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

        save(entryData);

        log.info("Обогатили entry_data.json ценами из candles.json");

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

    public void updateCurrentPrices(EntryData entryData, ConcurrentHashMap<String, List<Candle>> candles) {
        try {
            String longTicker = entryData.getLongticker();
            String shortTicker = entryData.getShortticker();

            List<Candle> longTickerCandles = candles.get(longTicker);
            List<Candle> shortTickerCandles = candles.get(shortTicker);

            if (longTickerCandles == null || longTickerCandles.isEmpty() ||
                    shortTickerCandles == null || shortTickerCandles.isEmpty()) {
                log.warn("Нет данных по свечам для пары: {} - {}", longTicker, shortTicker);
                return;
            }

            double longPrice = longTickerCandles.get(longTickerCandles.size() - 1).getClose();
            double shortPrice = shortTickerCandles.get(shortTickerCandles.size() - 1).getClose();

            entryData.setLongTickerCurrentPrice(longPrice);
            entryData.setShortTickerCurrentPrice(shortPrice);

            save(entryData);
            log.info("Обогатили entry_data.json ценами из candles.json");
        } catch (Exception e) {
            log.error("Ошибка при обогащении z_score.json из свечей: {}", e.getMessage(), e);
        }
    }

    public void setupEntryPointsIfNeededFromCandles(EntryData entryData, ZScoreEntry bestPair, ConcurrentHashMap<String, List<Candle>> candles) {
        if (entryData.getLongTickerEntryPrice() == 0.0 || entryData.getShortTickerEntryPrice() == 0.0) {
            entryData.setLongticker(bestPair.getLongticker());
            entryData.setShortticker(bestPair.getShortticker());

            List<Candle> longTickerCandles = candles.get(bestPair.getLongticker());
            List<Candle> shortTickerCandles = candles.get(bestPair.getShortticker());

            if (longTickerCandles == null || longTickerCandles.isEmpty() ||
                    shortTickerCandles == null || shortTickerCandles.isEmpty()) {
                log.warn("Нет данных по свечам для установки точек входа: {} - {}", bestPair.getLongticker(), bestPair.getShortticker());
                return;
            }

            Candle longCandle = longTickerCandles.get(longTickerCandles.size() - 1);
            Candle shortCandle = shortTickerCandles.get(shortTickerCandles.size() - 1);

            double longEntryPrice = longCandle.getClose();
            double shortEntryPrice = shortCandle.getClose();

            entryData.setLongTickerEntryPrice(longEntryPrice);
            entryData.setShortTickerEntryPrice(shortEntryPrice);
            entryData.setMeanEntry(bestPair.getMean());
            entryData.setSpreadEntry(bestPair.getSpread());

            // Ставим время открытия по long-свечке (можно и усреднить, если нужно)
            entryData.setEntryTime(longCandle.getTimestamp());

            save(entryData);

            log.info("🔹Установлены точки входа: LONG {{}} = {}, SHORT {{}} = {}, SPREAD = {}, MEAN = {}, ВРЕМЯ = {}",
                    entryData.getLongticker(), longEntryPrice,
                    entryData.getShortticker(), shortEntryPrice,
                    entryData.getSpreadEntry(), entryData.getMeanEntry(),
                    entryData.getEntryTime());
        }
    }

    public ProfitData calculateAndSetProfit(EntryData entryData) {
        ProfitData profitData = profitService.calculateProfit(entryData);
        entryData.setProfit(profitData.getProfitStr());
        save(entryData);
        return profitData;
    }
}
