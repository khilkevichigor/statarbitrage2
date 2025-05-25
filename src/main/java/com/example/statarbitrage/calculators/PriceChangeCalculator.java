package com.example.statarbitrage.calculators;

import com.google.gson.JsonArray;

public final class PriceChangeCalculator {
    private PriceChangeCalculator() {
    }

    public static double calculate(JsonArray candles) {
        if (candles.isEmpty()) {
            return 0.0;
        }
        JsonArray todayCandle = candles.get(0).getAsJsonArray(); // текущая дневная свеча
        double open = Double.parseDouble(todayCandle.get(1).getAsString()); // цена открытия
        double close = Double.parseDouble(todayCandle.get(4).getAsString()); // текущая/последняя цена
        return (close - open) / open * 100.0;
    }
}