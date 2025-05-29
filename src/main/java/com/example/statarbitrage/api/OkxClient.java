package com.example.statarbitrage.api;

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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class OkxClient {
    private static final OkHttpClient client = new OkHttpClient();
    private static final String BASE_URL = "https://www.okx.com";

    public Set<String> getSwapTickers() {
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
}