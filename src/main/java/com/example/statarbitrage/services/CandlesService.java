package com.example.statarbitrage.services;

import com.example.statarbitrage.adapters.ZonedDateTimeAdapter;
import com.example.statarbitrage.model.Candle;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class CandlesService {
    private static final String CANDLES_JSON_FILE_PATH = "candles.json";
    private final EntryDataService entryDataService;

    public void save(Map<String, List<Candle>> candles) {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter())
                .setPrettyPrinting()
                .create();

        try (FileWriter file = new FileWriter(CANDLES_JSON_FILE_PATH)) {
            gson.toJson(candles, file);
        } catch (IOException e) {
            log.error("Ошибка при сохранении candles.json: {}", e.getMessage(), e);
        }
    }


}
