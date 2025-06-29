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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class OkxClient {
    private static final OkHttpClient client = new OkHttpClient();
    private static final String BASE_URL = "https://www.okx.com";

    public List<String> getAllSwapTickers(boolean isSorted) {
        Request request = new Request.Builder()
                .url(BASE_URL + "/api/v5/public/instruments?instType=SWAP")
                .build();

        try {
            Response response = client.newCall(request).execute();
            String json = response.body().string();
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            JsonArray data = obj.getAsJsonArray("data");

            List<String> result = new ArrayList<>();
            for (JsonElement el : data) {
                String instId = el.getAsJsonObject().get("instId").getAsString();
                if (instId.endsWith("-USDT-SWAP")) {
                    result.add(instId);
                }
            }

            return isSorted ? result.stream().sorted().toList() : result;

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

    public JsonArray getCandles(String symbol, String timeFrame, double limit) {
        int candlesLimit = (int) limit;
        Request request = new Request.Builder()
                .url(BASE_URL + "/api/v5/market/candles?instId=" + symbol + "&bar=" + timeFrame + "&limit=" + candlesLimit)
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

    public List<Candle> getCandleList(String symbol, String timeFrame, double limit) {
        int candlesLimit = (int) limit;
        JsonArray rawCandles = getCandles(symbol, timeFrame, candlesLimit);
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

    public Map<String, List<Candle>> getCandlesMap(List<String> swapTickers, Settings settings, boolean isSorted) {
        ExecutorService executor = Executors.newFixedThreadPool(5); //todo пофиксить ошибки при 8
        Map<String, List<Candle>> candlesMap = new LinkedHashMap<>(); //важен порядок чтобы скрипт не менял свечи и знак z
        if (isSorted) {
            swapTickers = swapTickers.stream().sorted().toList();
        }
        int candleLimit = (int) settings.getCandleLimit();
        String timeframe = settings.getTimeframe();
        try {
            List<CompletableFuture<Void>> futures = swapTickers.stream()
                    .map(symbol -> CompletableFuture.runAsync(() -> {
                        try {
                            List<Candle> candles = getCandleList(symbol, timeframe, candleLimit);
                            if (candles.size() == candleLimit) {
                                candlesMap.put(symbol, candles);
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
        log.info("Собрали цены для {} монет", candlesMap.size());
        return candlesMap;
    }

    public List<String> getValidTickers(List<String> swapTickers, String timeFrame, double limit, double minVolume, boolean isSorted) {
        AtomicInteger count = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(5); //todo пофиксить ошибки при 8
        List<String> result = new ArrayList<>();
        int volumeAverageCount = 2; // можно сделать настраиваемым
        int candleLimit = (int) limit;
        try {
            List<CompletableFuture<Void>> futures = swapTickers.stream()
                    .map(symbol -> CompletableFuture.runAsync(() -> {
                        try {
                            List<Candle> candles = getCandleList(symbol, timeFrame, candleLimit);
                            if (candles.size() < volumeAverageCount) {
                                log.warn("Недостаточно свечей для {}", symbol);
                                count.getAndIncrement();
                                return;
                            }

                            // берём последние N свечей и считаем средний объем
                            List<Candle> lastCandles = candles.subList(candles.size() - volumeAverageCount, candles.size());
                            double averageVolume = lastCandles.stream()
                                    .mapToDouble(Candle::getVolume)
                                    .average()
                                    .orElse(0.0);

                            if (averageVolume >= minVolume) {
                                result.add(symbol);
                            } else {
                                count.getAndIncrement();
                            }
                        } catch (Exception e) {
                            log.error("Ошибка при обработке {}: {}", symbol, e.getMessage(), e);
                        }
                    }, executor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            executor.shutdown();
        }

        log.info("Всего откинули {} тикера с низким volume", count.intValue());
        log.info("Всего отобрано {} тикеров", result.size());

        return isSorted ? result.stream().sorted().toList() : result;
    }

}