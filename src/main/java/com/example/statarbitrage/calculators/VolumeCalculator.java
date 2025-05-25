package com.example.statarbitrage.calculators;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class VolumeCalculator {
    private VolumeCalculator() {
    }

    public static Double calculate24h(JsonArray candles) {
        if (!candles.isEmpty()) {
            JsonObject ticker = candles.get(0).getAsJsonObject();
            double volInCoins = Double.parseDouble(ticker.get("volCcy24h").getAsString());
            double lastPrice = Double.parseDouble(ticker.get("last").getAsString());
            double volInUSDT = volInCoins * lastPrice;
            return volInUSDT / 1_000_000; // округляем до миллионов USDT
        } else {
            return 0.0;
        }
    }
}