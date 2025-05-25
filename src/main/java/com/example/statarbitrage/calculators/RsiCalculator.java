package com.example.statarbitrage.calculators;

import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RsiCalculator {

    private RsiCalculator() {
    }

    public static double calculateRsi(List<Double> closes, int period, int targetIndexFromEnd) {
        if (closes == null || closes.size() < period + targetIndexFromEnd) {
            return 0.0;
        }

        List<Bar> bars = new ArrayList<>();
        ZonedDateTime currentTime = ZonedDateTime.now();
        double volume = 0.0;

        for (double close : closes) {
            bars.add(new BaseBar(Duration.ofMinutes(1), currentTime, close, close, close, close, volume));
            currentTime = currentTime.plusMinutes(1);
        }

        BaseBarSeries series = new BaseBarSeriesBuilder().withBars(bars).build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator rsiIndicator = new RSIIndicator(closePrice, period);

        int index = series.getBarCount() - 1 - targetIndexFromEnd;
        return rsiIndicator.getValue(index).doubleValue();
    }

    public static List<Double> calculateRsiHistory(List<Double> closes, Double period) {
        if (closes == null || closes.size() < period) {
            return Collections.emptyList();
        }

        List<Bar> bars = new ArrayList<>();
        ZonedDateTime currentTime = ZonedDateTime.now();
        double volume = 0.0;

        for (double close : closes) {
            bars.add(new BaseBar(Duration.ofMinutes(1), currentTime, close, close, close, close, volume));
            currentTime = currentTime.plusMinutes(1);
        }

        BaseBarSeries series = new BaseBarSeriesBuilder().withBars(bars).build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator rsiIndicator = new RSIIndicator(closePrice, period.intValue());

        List<Double> result = new ArrayList<>();
        for (int i = 0; i < series.getBarCount(); i++) {
            result.add(rsiIndicator.getValue(i).doubleValue());
        }

        Collections.reverse(result); // последнее значение — первое
        return result;
    }
}
