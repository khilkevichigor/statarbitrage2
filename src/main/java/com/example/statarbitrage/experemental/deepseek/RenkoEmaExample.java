package com.example.statarbitrage.experemental.deepseek;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RenkoEmaExample {

    public static void main(String[] args) throws Exception {
        List<Double> closes = fetch15mCloses("BTC-USDT", 500);
        if (closes.size() < 60) {
            System.out.println("Not enough closes for Renko calculation");
            return;
        }

        // Параметры Renko
        double brickSize = calculateBrickSize(closes); // автоматический расчет размера кирпича
        System.out.println("Calculated Renko brick size: " + brickSize);

        // Строим кирпичи Renko
        List<RenkoBrick> renkoBricks = buildRenkoBricks(closes, brickSize);
        System.out.println("Generated " + renkoBricks.size() + " Renko bricks");

        // Рассчитываем EMA50 по Renko кирпичам
        double[] ema50 = calculateEMA(renkoBricks, 50);

        // Выводим результаты
        System.out.println("\nLast 10 Renko bricks with EMA50:");
        int start = Math.max(0, renkoBricks.size() - 10);
        for (int i = start; i < renkoBricks.size(); i++) {
            RenkoBrick brick = renkoBricks.get(i);
            System.out.printf("Brick %d: Price=%.2f, Type=%s, EMA50=%.2f%n",
                    i + 1, brick.getPrice(), brick.isUp() ? "UP" : "DOWN", ema50[i]);
        }
    }

    public static List<Double> fetch15mCloses(String symbol, int limit) throws Exception {
        String url = "https://www.okx.com/api/v5/market/candles?instId=" + symbol +
                "&bar=15m&limit=" + limit;
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder json = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) json.append(line);
        reader.close();

        JsonArray data = JsonParser.parseString(json.toString())
                .getAsJsonObject().getAsJsonArray("data");

        List<Double> closes = new ArrayList<>();
        for (JsonElement e : data) {
            JsonArray arr = e.getAsJsonArray();
            closes.add(Double.parseDouble(arr.get(4).getAsString())); // close price
        }

        Collections.reverse(closes); // чтобы в порядке по времени
        return closes;
    }

    // Рассчитываем размер кирпича Renko на основе ATR
    private static double calculateBrickSize(List<Double> prices) {
        int atrPeriod = 14;
        if (prices.size() < atrPeriod + 1) {
            return 10.0; // значение по умолчанию, если данных недостаточно
        }

        double sumTrueRanges = 0;
        for (int i = 1; i <= atrPeriod; i++) {
            double high = prices.get(i);
            double low = prices.get(i);
            double prevClose = prices.get(i - 1);
            double trueRange = Math.max(high - low, Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));
            sumTrueRanges += trueRange;
        }

        double atr = sumTrueRanges / atrPeriod;
        return Math.round(atr * 100.0) / 100.0; // округляем до 2 знаков
    }

    // Строим кирпичи Renko из цен закрытия
    private static List<RenkoBrick> buildRenkoBricks(List<Double> closes, double brickSize) {
        List<RenkoBrick> bricks = new ArrayList<>();
        if (closes.isEmpty()) return bricks;

        double basePrice = closes.get(0);
        bricks.add(new RenkoBrick(basePrice, true)); // первый кирпич

        for (int i = 1; i < closes.size(); i++) {
            double currentPrice = closes.get(i);
            double lastBrickPrice = bricks.get(bricks.size() - 1).getPrice();
            boolean lastBrickUp = bricks.get(bricks.size() - 1).isUp();

            if (lastBrickUp) {
                // Проверяем на новый верхний кирпич
                while (currentPrice >= lastBrickPrice + brickSize) {
                    lastBrickPrice += brickSize;
                    bricks.add(new RenkoBrick(lastBrickPrice, true));
                }
                // Проверяем на разворот вниз
                while (currentPrice <= lastBrickPrice - 2 * brickSize) {
                    lastBrickPrice -= brickSize;
                    bricks.add(new RenkoBrick(lastBrickPrice, false));
                }
            } else {
                // Проверяем на новый нижний кирпич
                while (currentPrice <= lastBrickPrice - brickSize) {
                    lastBrickPrice -= brickSize;
                    bricks.add(new RenkoBrick(lastBrickPrice, false));
                }
                // Проверяем на разворот вверх
                while (currentPrice >= lastBrickPrice + 2 * brickSize) {
                    lastBrickPrice += brickSize;
                    bricks.add(new RenkoBrick(lastBrickPrice, true));
                }
            }
        }

        return bricks;
    }

    // Расчет EMA
    private static double[] calculateEMA(List<RenkoBrick> bricks, int period) {
        double[] ema = new double[bricks.size()];
        if (bricks.size() < period) return ema;

        double multiplier = 2.0 / (period + 1);

        // Первое значение EMA - это простая средняя
        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += bricks.get(i).getPrice();
        }
        ema[period - 1] = sum / period;

        // Последующие значения EMA
        for (int i = period; i < bricks.size(); i++) {
            ema[i] = (bricks.get(i).getPrice() - ema[i - 1]) * multiplier + ema[i - 1];
        }

        return ema;
    }

    // Класс для хранения кирпича Renko
    static class RenkoBrick {
        private final double price;
        private final boolean up;

        public RenkoBrick(double price, boolean up) {
            this.price = price;
            this.up = up;
        }

        public double getPrice() {
            return price;
        }

        public boolean isUp() {
            return up;
        }
    }
}