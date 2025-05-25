package com.example.statarbitrage.calculators;

import java.util.List;

public final class AtrCalculator {

    private AtrCalculator() {
    }

    public static double calculateAtr(List<Double> closes, int period) {
        if (closes.size() < period + 1) {
            System.out.println("⚠️  Not enough data to calculate ATR");
            return 0;
        }

        double atrSum = 0.0;
        for (int i = closes.size() - period; i < closes.size(); i++) {
            double high = closes.get(i);
            double low = closes.get(i - 1);
            atrSum += Math.abs(high - low);
        }

        double atr = atrSum / period;
        return atr;
    }

    public static double roundToNearest(double value) {
        if (value == 0.0) return 0.0;

        // Настраиваемое значение округления — можно варьировать
        double step = getStep(value);
        double rounded = Math.round(value / step) * step;

        // Гарантия, что не вернётся 0
        return Math.max(rounded, step);
    }

    private static double getStep(double value) {
        if (value > 1000) return 10;
        if (value > 100) return 1;
        if (value > 1) return 0.1;
        return 0.01;
    }


}
