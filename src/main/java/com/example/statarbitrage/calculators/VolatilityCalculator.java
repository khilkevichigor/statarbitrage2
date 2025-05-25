package com.example.statarbitrage.calculators;

import java.util.Collections;
import java.util.List;

public final class VolatilityCalculator {
    private VolatilityCalculator() {
    }

    public static double calculate24h(List<Double> closes, String barSize) {
        int candlesNeeded = CandlesCalculator.getCandlesPer24h(barSize);
        if (closes.size() < candlesNeeded) {
            return 0.0;
        }

        List<Double> lastCloses = closes.subList(closes.size() - candlesNeeded, closes.size());
        double max = Collections.max(lastCloses);
        double min = Collections.min(lastCloses);
        double avg = lastCloses.stream().mapToDouble(Double::doubleValue).average().orElse(1.0);

        return (max - min) / avg * 100.0;
    }
}