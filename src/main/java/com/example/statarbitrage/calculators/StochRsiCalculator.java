package com.example.statarbitrage.calculators;

import lombok.Data;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.StochasticRSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StochRsiCalculator {
    private StochRsiCalculator() {
    }

    @Data
    public static class StochRsiResult {
        public final List<Double> kValues;
        public final List<Double> dValues;

        public StochRsiResult(List<Double> kValues, List<Double> dValues) {
            this.kValues = kValues;
            this.dValues = dValues;
        }
    }

    // Основной метод для вычисления Stochastic RSI
    public static StochRsiResult calculateFull(List<Double> closes, Double rsiPeriod, Double stochRsiPeriod, Double kPeriod, Double dPeriod) {
        // Проверка минимальной длины для расчетов
        if (closes.size() < rsiPeriod + stochRsiPeriod + kPeriod + dPeriod) {
            return new StochRsiResult(Collections.emptyList(), Collections.emptyList());
        }

        // Создаем серию баров
        BarSeries series = new BaseBarSeries("StochRSI_Series");
        ZonedDateTime endTime = ZonedDateTime.now();
        Duration barDuration = Duration.ofMinutes(1);
        double volume = 0.0;

        // Добавляем бары в серию
        for (double close : closes) {
            series.addBar(new BaseBar(barDuration, endTime, close, close, close, close, volume));
            endTime = endTime.plus(barDuration);
        }

        // Инициализация индикаторов
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator rsi = new RSIIndicator(closePrice, rsiPeriod.intValue());
        StochasticRSIIndicator stochRsi = new StochasticRSIIndicator(rsi, stochRsiPeriod.intValue());

        // Список для хранения значений Stochastic RSI
        List<Double> rawStoch = new ArrayList<>();

        // Вычисление значений Stochastic RSI
        for (int i = 0; i < series.getBarCount(); i++) {
            double v = stochRsi.getValue(i).doubleValue();
            rawStoch.add(Double.isNaN(v) ? 0.0 : v * 100); // Если NaN, заменяем на 0
        }

        // Применяем SMA для %K и %D
        List<Double> kValues = sma(rawStoch, kPeriod.intValue());
        List<Double> dValues = sma(kValues, dPeriod.intValue());

        // Переворачиваем списки для возвращаемых значений
        Collections.reverse(kValues);
        Collections.reverse(dValues);

        return new StochRsiResult(kValues, dValues);
    }

    // Простой расчет SMA для списка значений (List<Double>)
    public static List<Double> sma(List<Double> xs, int period) {
        List<Double> out = new ArrayList<>(xs.size());
        double sum = 0;
        for (int i = 0; i < xs.size(); i++) {
            sum += xs.get(i);
            if (i >= period) sum -= xs.get(i - period);
            if (i < period - 1) {
                out.add(0.0); // Пока нет достаточного количества данных
            } else {
                out.add(sum / period);
            }
        }
        return out;
    }

}