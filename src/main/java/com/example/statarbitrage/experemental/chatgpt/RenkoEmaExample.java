package com.example.statarbitrage.experemental.chatgpt;

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

        // 1. Рассчитываем ATR(14)
        double atr = calculateAtr(closes, 14);
        double brickSize = roundToNearest(atr);
        System.out.println("📏 Brick size: " + brickSize);

        // 2. Строим Renko-график
        List<Double> renkoCloses = buildRenko(closes, brickSize);
        System.out.println("🧱 Renko bricks: " + renkoCloses.size());

        // 3. Считаем EMA50 по последним кирпичам
        double ema50 = calculateEmaFromRenko(renkoCloses, 50);
        System.out.println("📊 EMA50: " + ema50);

        double sma50 = calculateSmaFromRenko(renkoCloses, 50);
        System.out.println("📈 SMA50: " + sma50);
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

    public static double calculateAtr(List<Double> closes, int period) {
        if (closes.size() < period + 1) return 0;
        double sum = 0;
        for (int i = closes.size() - period; i < closes.size(); i++) {
            sum += Math.abs(closes.get(i) - closes.get(i - 1));
        }
        return sum / period;
    }

    public static double roundToNearest(double value) {
        if (value > 1000) return Math.round(value / 10) * 10;
        if (value > 100) return Math.round(value);
        if (value > 1) return Math.round(value * 10) / 10.0;
        return Math.round(value * 100) / 100.0;
    }

    public static List<Double> buildRenko(List<Double> closes, double brickSize) {
        List<Double> renko = new ArrayList<>();
        if (closes.isEmpty()) return renko;

        double lastBrick = closes.get(0);
        renko.add(lastBrick);

        for (double close : closes) {
            while (Math.abs(close - lastBrick) >= brickSize) {
                if (close > lastBrick) lastBrick += brickSize;
                else lastBrick -= brickSize;
                renko.add(lastBrick);
            }
        }
        return renko;
    }

    public static double calculateEmaFromRenko(List<Double> renkoCloses, int emaPeriod) {
        if (renkoCloses.size() < emaPeriod) {
            return 0.0;
        }

        List<Double> last = renkoCloses.subList(renkoCloses.size() - emaPeriod, renkoCloses.size());
        double k = 2.0 / (emaPeriod + 1);
        double ema = last.get(0);

        for (int i = 1; i < last.size(); i++) {
            ema = last.get(i) * k + ema * (1 - k);
        }

        return ema;
    }

    public static double calculateSmaFromRenko(List<Double> renkoCloses, int smaPeriod) {
        if (renkoCloses.size() < smaPeriod) {
            return 0.0;
        }

        List<Double> last = renkoCloses.subList(renkoCloses.size() - smaPeriod, renkoCloses.size());
        double sum = 0.0;
        for (double v : last) {
            sum += v;
        }
        return sum / smaPeriod;
    }

}
