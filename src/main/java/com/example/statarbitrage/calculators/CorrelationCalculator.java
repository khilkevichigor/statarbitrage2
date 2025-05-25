package com.example.statarbitrage.calculators;

import java.util.ArrayList;
import java.util.List;

public final class CorrelationCalculator {
    private CorrelationCalculator() {
    }

    public static double calculate(List<Double> closesX, List<Double> closesY) {
        List<Double> x = calculateReturns(closesX);
        List<Double> y = calculateReturns(closesY);
        if (x.size() != y.size()) {
            return 0.0;
        }

        int n = x.size();
        double avgX = x.stream().mapToDouble(d -> d).average().orElse(0.0);
        double avgY = y.stream().mapToDouble(d -> d).average().orElse(0.0);

        double sumXY = 0.0, sumX2 = 0.0, sumY2 = 0.0;
        for (int i = 0; i < n; i++) {
            double dx = x.get(i) - avgX;
            double dy = y.get(i) - avgY;
            sumXY += dx * dy;
            sumX2 += dx * dx;
            sumY2 += dy * dy;
        }

        double correlationValue = sumX2 == 0 || sumY2 == 0 ? 0.0 : sumXY / Math.sqrt(sumX2 * sumY2);
        return correlationValue * 100; // %
    }

    private static List<Double> calculateReturns(List<Double> closes) {
        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < closes.size(); i++) {
            double ret = (closes.get(i) - closes.get(i - 1)) / closes.get(i - 1);
            returns.add(ret);
        }
        return returns;
    }
}