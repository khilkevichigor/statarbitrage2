package com.example.statarbitrage.experemental.chatgpt;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StatArbitrage {

    private static final OkHttpClient client = new OkHttpClient();
    private static final String BASE_URL = "https://www.okx.com";
    private static final Logger log = LoggerFactory.getLogger(StatArbitrage.class);
    private static final int LIMIT = 100;
    private static final int WINDOW = 20;

    public static void main(String[] args) {
        OkxClient okxClient = new OkxClient();
        Set<String> tickers = okxClient.getSwapTickers();
        System.out.println("Всего тикеров: " + tickers.size());

        List<String> list = new ArrayList<>(tickers);

        // Ограничим для теста
//        int maxPairs = tickers.size(); // например, 5 пар
        int maxPairs = 5; // например, 5 пар
        int count = 0;

        for (int i = 0; i < list.size() && count < maxPairs; i++) {
            for (int j = i + 1; j < list.size() && count < maxPairs; j++) {
                String a = list.get(i);
                String b = list.get(j);

                System.out.printf("Анализ пары: %s vs %s%n", a, b);
                runStatArbitrage(okxClient, a, b, "1m", LIMIT, WINDOW);
                count++;
            }
        }
    }

    public static void runStatArbitrage(OkxClient okxClient, String symbolA, String symbolB, String timeframe, int limit, int window) {
        List<Double> closesA = okxClient.getCloses(symbolA, timeframe, limit);
        List<Double> closesB = okxClient.getCloses(symbolB, timeframe, limit);

        if (closesA.size() != closesB.size() || closesA.size() < window) {
            log.error("Недостаточно данных или размеры списков не совпадают для {} и {}", symbolA, symbolB);
            return;
        }

        double correlation = computePearsonCorrelation(closesA, closesB);
        if (correlation < 0.9) {
            log.info("Низкая корреляция ({}) между {} и {}, пропускаем", correlation, symbolA, symbolB);
            return;
        }

        int lastIndex = closesA.size() - 1;

        // Можно запускать цикл, но смотреть только последний бар
        for (int i = window; i < closesA.size(); i++) {
            double spread = closesA.get(i) - closesB.get(i);
            DescriptiveStatistics stats = new DescriptiveStatistics();

            for (int j = i - window; j < i; j++) {
                stats.addValue(closesA.get(j) - closesB.get(j));
            }

            double mean = stats.getMean();
            double std = stats.getStandardDeviation();
            double z = (spread - mean) / std;

            // Выводим данные для всех баров (можно убрать, если не нужно)
            log.info("Время {} | Spread: {} | Z-score: {}", i, spread, z);

            // Логика сигналов только для последнего бара
            if (i == lastIndex) {
                if (z > 2) {
                    log.warn("Сигнал: SHORT {} и LONG {}", symbolA, symbolB);
                } else if (z < -2) {
                    log.warn("Сигнал: LONG {} и SHORT {}", symbolA, symbolB);
                } else if (Math.abs(z) < 0.5) {
                    log.info("Сигнал: ЗАКРЫТЬ ПОЗИЦИИ");
                } else {
                    log.info("Сигнал: ЖДАТЬ");
                }
            }
        }
    }

    public static double computePearsonCorrelation(List<Double> x, List<Double> y) {
        int n = x.size();
        double sumX = 0, sumY = 0, sumX2 = 0, sumY2 = 0, sumXY = 0;

        for (int i = 0; i < n; i++) {
            double xi = x.get(i);
            double yi = y.get(i);

            sumX += xi;
            sumY += yi;
            sumX2 += xi * xi;
            sumY2 += yi * yi;
            sumXY += xi * yi;
        }

        double numerator = n * sumXY - sumX * sumY;
        double denominator = Math.sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY));

        if (denominator == 0) return 0;

        return numerator / denominator;
    }


    public static class OkxClient {

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
                log.error("Ошибка при получении тикеров: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }

        public List<Double> getCloses(String symbol, String timeFrame, int limit) {
            List<Double> closes = new ArrayList<>();
            try {
                Request request = new Request.Builder()
                        .url(BASE_URL + "/api/v5/market/candles?instId=" + symbol + "&bar=" + timeFrame + "&limit=" + limit)
                        .build();

                Response response = client.newCall(request).execute();
                String json = response.body().string();
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                JsonArray data = obj.getAsJsonArray("data");

                for (int i = data.size() - 1; i >= 0; i--) {
                    JsonArray candle = data.get(i).getAsJsonArray();
                    closes.add(Double.parseDouble(candle.get(4).getAsString()));
                }
            } catch (Exception e) {
                log.error("Ошибка получения свечей для {}: {}", symbol, e.getMessage(), e);
            }
            return closes;
        }
    }
}
