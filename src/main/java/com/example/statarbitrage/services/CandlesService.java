package com.example.statarbitrage.services;

import com.example.statarbitrage.adapters.ZonedDateTimeAdapter;
import com.example.statarbitrage.api.OkxClient;
import com.example.statarbitrage.model.Candle;
import com.example.statarbitrage.model.Settings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class CandlesService {
    private final OkxClient okxClient;
    private final SettingsService settingsService;
    private static final String CANDLES_JSON_FILE_PATH = "candles.json";
    private static final List<String> BLACK_LIST = List.of("USDC-USDT-SWAP");

    public ConcurrentHashMap<String, List<Candle>> getCandles() {
        Settings settings = settingsService.getSettings();
        List<String> swapTickers = okxClient.getAllSwapTickers();
        ConcurrentHashMap<String, List<Candle>> candlesMap = okxClient.getCandlesMap(swapTickers, settings);
        return filterByBlackList(candlesMap);
    }

    public ConcurrentHashMap<String, List<Candle>> getCandles(List<String> swapTickers) {
        Settings settings = settingsService.getSettings();
        ConcurrentHashMap<String, List<Candle>> candlesMap = okxClient.getCandlesMap(swapTickers, settings);
        return filterByBlackList(candlesMap);
    }

    public List<String> getApplicableTickers(String timeFrame) {
        Settings settings = settingsService.getSettings();
        List<String> swapTickers = okxClient.getAllSwapTickers();
        swapTickers = filterByBlackList(swapTickers);
        return okxClient.getValidTickers(swapTickers, timeFrame, settings.getCandleLimit(), settings.getMinVolume() * 1_000_000);
    }

    public void save(Map<String, List<Candle>> candles) {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter())
                .setPrettyPrinting()
                .create();

        try (FileWriter file = new FileWriter(CANDLES_JSON_FILE_PATH)) {
            gson.toJson(candles, file);
            log.info("Сохранили цены в candles.json");
        } catch (IOException e) {
            log.error("Ошибка при сохранении candles.json: {}", e.getMessage(), e);
        }
    }

    public ConcurrentHashMap<String, List<Candle>> filterByBlackList(ConcurrentHashMap<String, List<Candle>> candlesMap) {
        BLACK_LIST.forEach(candlesMap::remove);
        save(candlesMap);
        log.info("Удалили цены тикеров из черного списка");
        return candlesMap;
    }

    public List<String> filterByBlackList(List<String> swapTickers) {
        BLACK_LIST.forEach(swapTickers::remove);
        log.info("Убрали тикеры из черного списка");
        return swapTickers;
    }
}
