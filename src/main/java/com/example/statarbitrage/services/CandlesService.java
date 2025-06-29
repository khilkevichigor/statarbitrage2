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

import static com.example.statarbitrage.constant.Constants.CANDLES_FILE_NAME;

@Slf4j
@Service
@RequiredArgsConstructor
public class CandlesService {
    private final OkxClient okxClient;
    private static final List<String> BLACK_LIST = List.of("USDC-USDT-SWAP");

    public Map<String, List<Candle>> getCandles(Settings settings, List<String> swapTickers, boolean isSorted) {
        return okxClient.getCandlesMap(swapTickers, settings, isSorted);
    }

    public List<String> getApplicableTickers(Settings settings, String timeFrame, boolean isSorted) {
        List<String> swapTickers = okxClient.getAllSwapTickers(isSorted);
        return okxClient.getValidTickers(swapTickers, timeFrame, settings.getCandleLimit(), settings.getMinVolume() * 1_000_000, isSorted);
    }

    public void save(Map<String, List<Candle>> candles) {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter())
                .setPrettyPrinting()
                .create();

        try (FileWriter file = new FileWriter(CANDLES_FILE_NAME)) {
            gson.toJson(candles, file);
            log.info("Сохранили цены в candles.json");
        } catch (IOException e) {
            log.error("Ошибка при сохранении candles.json: {}", e.getMessage(), e);
        }
    }

    public List<String> filterByBlackList(List<String> swapTickers) {
        BLACK_LIST.forEach(swapTickers::remove);
        log.info("Убрали тикеры из черного списка");
        return swapTickers.stream().sorted().toList();
    }
}
