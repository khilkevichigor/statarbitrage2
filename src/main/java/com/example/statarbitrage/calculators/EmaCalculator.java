package com.example.statarbitrage.calculators;

import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EmaCalculator {

    private EmaCalculator() {
    }

    public static double getDistance(double v1, double v2) {
        return (Math.abs(v1 - v2) / v1) * 100;
    }

    public static double calculateAngle(List<Double> closes, Double emaPeriod) {
        // EMA текущей свечи (последний элемент)
        double emaNow = calculateTa4j(closes, emaPeriod, 0);

        // EMA 5 свечей назад
        double emaBefore = calculateTa4j(closes, emaPeriod, 5);

        // Средняя цена для нормализации
        double avgPrice = (emaNow + emaBefore) / 2.0;

        // Нормализованная разница по оси Y (в процентах)
        double deltaY = (emaNow - emaBefore) / avgPrice;

        // Разница по оси X: 5 свечей
        double deltaX = 5;

        // Вычисление угла в радианах
        double angleRad = Math.atan(deltaY / deltaX);

        // Преобразование в градусы
        return Math.toDegrees(angleRad) * 100;
    }

    public static double calculateTa4j(List<Double> closes, Double period, int targetIndexFromEnd) {
        List<Bar> bars = new ArrayList<>();
        ZonedDateTime currentTime = ZonedDateTime.now();
        double volume = 0.0;

        for (double close : closes) {
            bars.add(new BaseBar(Duration.ofMinutes(1), currentTime, close, close, close, close, volume));
            currentTime = currentTime.plusMinutes(1);
        }

        BaseBarSeries series = new BaseBarSeriesBuilder().withBars(bars).build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        EMAIndicator emaIndicator = new EMAIndicator(closePrice, period.intValue());

        int index = series.getBarCount() - 1 - targetIndexFromEnd;
        return emaIndicator.getValue(index).doubleValue();
    }

    public static List<Double> calculateEmaHistory(List<Double> closes, Double period) {
        if (period == null) {
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
        EMAIndicator emaIndicator = new EMAIndicator(closePrice, period.intValue());

        List<Double> result = new ArrayList<>();
        for (int i = 0; i < series.getBarCount(); i++) {
            double value = emaIndicator.getValue(i).doubleValue();
            result.add(Double.isNaN(value) ? 0.0 : value);
        }

        // Переворачиваем: последнее значение будет первым
        Collections.reverse(result);

        return result;
    }
}