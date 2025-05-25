package com.example.statarbitrage.converters;

import com.example.statarbitrage.calculators.AtrCalculator;

import java.util.ArrayList;
import java.util.List;

public final class RenkoConverter {

    private RenkoConverter() {
    }

    public static List<Double> convertToRenkoCloses(List<Double> closes) {
        List<Double> renkoChart = new ArrayList<>();
        if (closes.isEmpty()) return renkoChart;

        double atr = AtrCalculator.calculateAtr(closes, 14);
        double boxSize = AtrCalculator.roundToNearest(atr);

        if (boxSize == 0.0) {
            System.out.println("⚠️  ATR returned 0. closes: " + closes);
            return renkoChart;
        }

        double prevBrickClose = closes.get(0);
        renkoChart.add(prevBrickClose);

        for (double close : closes) {
            while (Math.abs(close - prevBrickClose) >= boxSize) {
                if (close > prevBrickClose) {
                    prevBrickClose += boxSize;
                } else {
                    prevBrickClose -= boxSize;
                }
                renkoChart.add(prevBrickClose);
            }
        }

        return renkoChart;
    }
}
