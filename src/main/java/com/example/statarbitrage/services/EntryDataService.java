package com.example.statarbitrage.services;

import com.example.statarbitrage.model.Candle;
import com.example.statarbitrage.model.EntryData;
import com.example.statarbitrage.model.ZScoreEntry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class EntryDataService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ENTRY_DATA_JSON_FILE_PATH = "entry_data.json";

    public EntryData createEntryData(ZScoreEntry topPair) {
        EntryData entryData = new EntryData();
        entryData.setLongticker(topPair.getLongticker());
        entryData.setShortticker(topPair.getShortticker());
        save(Collections.singletonList(entryData));
        log.info("Создали entry_data.json");
        return entryData;
    }

    public EntryData getEntryData() {
        List<EntryData> entryData = readEntryDataJson(ENTRY_DATA_JSON_FILE_PATH);
        if (entryData == null || entryData.isEmpty()) {
            String message = "⚠️entry_data.json пустой или не найден";
            log.warn(message);
            throw new RuntimeException(message);
        }
        return entryData.get(0);
    }

    public List<EntryData> readEntryDataJson(String entryDataJsonFilePath) {
        try {
            return MAPPER.readValue(new File(entryDataJsonFilePath), new TypeReference<>() {
            });
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void save(List<EntryData> entries) {
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(ENTRY_DATA_JSON_FILE_PATH), entries);
        } catch (Exception e) {
            log.error("Ошибка при записи entry_data.json: {}", e.getMessage(), e);
        }
    }

    public void updateCurrentPrices(EntryData entryData, ConcurrentHashMap<String, List<Candle>> allCandles) {
        try {
            String longTicker = entryData.getLongticker();
            String shortTicker = entryData.getShortticker();

            List<Candle> longTickerCandles = allCandles.get(longTicker);
            List<Candle> shortTickerCandles = allCandles.get(shortTicker);

            if (longTickerCandles == null || longTickerCandles.isEmpty() ||
                    shortTickerCandles == null || shortTickerCandles.isEmpty()) {
                log.warn("Нет данных по свечам для пары: {} - {}", longTicker, shortTicker);
                return;
            }

            double longPrice = longTickerCandles.get(longTickerCandles.size() - 1).getClose();
            double shortPrice = shortTickerCandles.get(shortTickerCandles.size() - 1).getClose();

            entryData.setLongTickerCurrentPrice(longPrice);
            entryData.setShortTickerCurrentPrice(shortPrice);

            save(Collections.singletonList(entryData));
            log.info("Обогатили entry_data.json ценами из candles.json");
        } catch (Exception e) {
            log.error("Ошибка при обогащении z_score.json из свечей: {}", e.getMessage(), e);
        }
    }

    public void setupEntryPointsIfNeededFromCandles(EntryData entryData, ZScoreEntry topPair, ConcurrentHashMap<String, List<Candle>> topPairCandles) {
        if (entryData.getLongTickerEntryPrice() == 0.0 || entryData.getShortTickerEntryPrice() == 0.0) {
            entryData.setLongticker(topPair.getLongticker());
            entryData.setShortticker(topPair.getShortticker());

            List<Candle> longTickerCandles = topPairCandles.get(topPair.getLongticker());
            List<Candle> shortTickerCandles = topPairCandles.get(topPair.getShortticker());

            if (longTickerCandles == null || longTickerCandles.isEmpty() ||
                    shortTickerCandles == null || shortTickerCandles.isEmpty()) {
                log.warn("Нет данных по свечам для установки точек входа: {} - {}", topPair.getLongticker(), topPair.getShortticker());
                return;
            }

            Candle longCandle = longTickerCandles.get(longTickerCandles.size() - 1);
            Candle shortCandle = shortTickerCandles.get(shortTickerCandles.size() - 1);

            double longEntryPrice = longCandle.getClose();
            double shortEntryPrice = shortCandle.getClose();

            entryData.setLongTickerEntryPrice(longEntryPrice);
            entryData.setShortTickerEntryPrice(shortEntryPrice);
            entryData.setMeanEntry(topPair.getMean());
            entryData.setSpreadEntry(topPair.getSpread());

            // Ставим время открытия по long-свечке (можно и усреднить, если нужно)
            entryData.setEntryTime(longCandle.getTimestamp());

            save(Collections.singletonList(entryData));

            log.info("🔹Установлены точки входа: LONG {{}} = {}, SHORT {{}} = {}, SPREAD = {}, MEAN = {}, ВРЕМЯ = {}",
                    entryData.getLongticker(), longEntryPrice,
                    entryData.getShortticker(), shortEntryPrice,
                    entryData.getSpreadEntry(), entryData.getMeanEntry(),
                    entryData.getEntryTime());
        }
    }
}
