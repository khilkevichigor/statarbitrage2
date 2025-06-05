package com.example.statarbitrage.api;

import com.example.statarbitrage.model.Candle;
import com.example.statarbitrage.model.Settings;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class OkxClient {
    private static final OkHttpClient client = new OkHttpClient();
    private static final String BASE_URL = "https://www.okx.com";

    public Set<String> getAllSwapTickers() {
        Request request = new Request.Builder()
                .url(BASE_URL + "/api/v5/public/instruments?instType=SWAP")
                .build();

        try {
            Response response = client.newCall(request).execute();
            String json = response.body().string();
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            JsonArray data = obj.getAsJsonArray("data");

            Set<String> result = new HashSet<>();
            for (JsonElement el : data) {
                String instId = el.getAsJsonObject().get("instId").getAsString();
                if (instId.endsWith("-USDT-SWAP")) {
                    result.add(instId);
                }
            }
            return result;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public List<Double> getCloses(String symbol, String timeFrame, int limit) {
        JsonArray candles = getCandles(symbol, timeFrame, limit);
        List<Double> closes = new ArrayList<>();
        for (int i = candles.size() - 1; i >= 0; i--) {
            JsonArray candle = candles.get(i).getAsJsonArray();
            double close = Double.parseDouble(candle.get(4).getAsString()); //начинаем с 300 свечи
            closes.add(close);
        }
        return closes;
    }

    public JsonArray getCandles(String symbol, String timeFrame, int limit) {
        Request request = new Request.Builder()
                .url(BASE_URL + "/api/v5/market/candles?instId=" + symbol + "&bar=" + timeFrame + "&limit=" + limit)
                .build();
        try {
            Response response = client.newCall(request).execute();
            String json = response.body().string();
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            return obj.getAsJsonArray("data");
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public List<Candle> getCandleList(String symbol, String timeFrame, int limit) {
        JsonArray rawCandles = getCandles(symbol, timeFrame, limit);
        List<Candle> candles = new ArrayList<>();

        for (JsonElement el : rawCandles) {
            JsonArray candleArr = el.getAsJsonArray();
            candles.add(Candle.fromJsonArray(candleArr));
        }
        Collections.reverse(candles);
        return candles;
    }

    public JsonArray getTicker(String symbol) {
        Request request = new Request.Builder()
                .url(BASE_URL + "/api/v5/market/ticker?instId=" + symbol)
                .build();

        try {
            Response response = client.newCall(request).execute();
            String json = response.body().string();
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            return obj.getAsJsonArray("data");
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public ConcurrentHashMap<String, List<Candle>> getCandlesMap(Set<String> swapTickers, Settings settings) {
        ExecutorService executor = Executors.newFixedThreadPool(5);
        ConcurrentHashMap<String, List<Candle>> candlesMap = new ConcurrentHashMap<>();
        try {
            List<CompletableFuture<Void>> futures = swapTickers.stream()
                    .map(symbol -> CompletableFuture.runAsync(() -> {
                        try {
                            List<Candle> candles = getCandleList(symbol, settings.getTimeframe(), settings.getCandleLimit());
                            candlesMap.put(symbol, candles);
                        } catch (Exception e) {
                            log.error("Ошибка при обработке {}: {}", symbol, e.getMessage(), e);
                        }
                    }, executor))
                    .toList();

            // Ожидаем завершения всех задач
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            executor.shutdown();
        }
        log.info("Собрали цены для {} монет", candlesMap.size());
        return candlesMap;
    }

    public Set<String> getValidTickers(Set<String> swapTickers, String timeFrame, int limit, double minVolume) {
        AtomicInteger count = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(5);
        Set<String> result = new HashSet<>();
        try {
            List<CompletableFuture<Void>> futures = swapTickers.stream()
                    .map(symbol -> CompletableFuture.runAsync(() -> {
                        try {
                            List<Candle> candles = getCandleList(symbol, timeFrame, limit);
                            if (candles.get(0).getVolume() >= minVolume) {
                                result.add(symbol);
                            } else {
                                count.getAndIncrement();
                            }
                        } catch (Exception e) {
                            log.error("Ошибка при обработке {}: {}", symbol, e.getMessage(), e);
                        }
                    }, executor))
                    .toList();

            // Ожидаем завершения всех задач
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            executor.shutdown();
        }
        log.info("Всего откинули {} тикера с низким volume", count.intValue());
        return result;
    }
}