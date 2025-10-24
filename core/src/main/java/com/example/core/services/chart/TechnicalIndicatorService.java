package com.example.core.services.chart;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 📊 Сервис для расчета технических индикаторов
 * Содержит реализации EMA, RSI, StochRSI и других индикаторов
 */
@Slf4j
@Service
public class TechnicalIndicatorService {

    /**
     * 📈 Рассчитывает Экспоненциальную Скользящую Среднюю (EMA)
     *
     * @param values Список значений для расчета
     * @param period Период для EMA
     * @return Список значений EMA
     */
    public List<Double> calculateEMA(List<Double> values, int period) {
        if (values == null || values.size() < period) {
            log.debug("📈 Недостаточно данных для расчета EMA({}): размер={}", period,
                    values != null ? values.size() : 0);
            return new ArrayList<>();
        }

        List<Double> emaValues = new ArrayList<>();
        double multiplier = 2.0 / (period + 1);

        // Первое значение EMA = простая средняя первых N значений
        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += values.get(i);
        }
        double firstEma = sum / period;
        emaValues.add(firstEma);

        // Последующие значения EMA
        for (int i = period; i < values.size(); i++) {
            double currentValue = values.get(i);
            double previousEma = emaValues.get(emaValues.size() - 1);
            double ema = (currentValue * multiplier) + (previousEma * (1 - multiplier));
            emaValues.add(ema);
        }

        log.debug("📈 Рассчитан EMA({}) для {} значений, получено {} точек EMA",
                period, values.size(), emaValues.size());

        return emaValues;
    }

    /**
     * 📊 Рассчитывает Индекс Относительной Силы (RSI)
     *
     * @param values Список значений для расчета
     * @param period Период для RSI (обычно 14)
     * @return Список значений RSI
     */
    public List<Double> calculateRSI(List<Double> values, int period) {
        if (values == null || values.size() < period + 1) {
            log.debug("📊 Недостаточно данных для расчета RSI({}): размер={}", period,
                    values != null ? values.size() : 0);
            return new ArrayList<>();
        }

        List<Double> rsiValues = new ArrayList<>();
        List<Double> gains = new ArrayList<>();
        List<Double> losses = new ArrayList<>();

        // Рассчитываем изменения (gains и losses)
        for (int i = 1; i < values.size(); i++) {
            double change = values.get(i) - values.get(i - 1);
            gains.add(Math.max(change, 0));
            losses.add(Math.max(-change, 0));
        }

        // Первое значение RSI - простая средняя
        double avgGain = gains.subList(0, period).stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double avgLoss = losses.subList(0, period).stream().mapToDouble(Double::doubleValue).average().orElse(0);

        double rs = avgLoss == 0 ? 100 : avgGain / avgLoss;
        double rsi = 100 - (100 / (1 + rs));
        rsiValues.add(rsi);

        // Последующие значения RSI с экспоненциальной средней
        for (int i = period; i < gains.size(); i++) {
            avgGain = (avgGain * (period - 1) + gains.get(i)) / period;
            avgLoss = (avgLoss * (period - 1) + losses.get(i)) / period;

            rs = avgLoss == 0 ? 100 : avgGain / avgLoss;
            rsi = 100 - (100 / (1 + rs));
            rsiValues.add(rsi);
        }

        log.debug("📊 Рассчитан RSI({}) для {} значений, получено {} точек RSI",
                period, values.size(), rsiValues.size());

        return rsiValues;
    }

    /**
     * 🌊 Рассчитывает Стохастический RSI (StochRSI)
     *
     * @param values      Список значений для расчета
     * @param rsiPeriod   Период для RSI (обычно 14)
     * @param stochPeriod Период для стохастического осциллятора (обычно 3)
     * @param smoothK     Период сглаживания K (обычно 3)
     * @return Список значений StochRSI
     */
    public List<Double> calculateStochRSI(List<Double> values, int rsiPeriod, int stochPeriod, int smoothK) {
        if (values == null || values.size() < rsiPeriod + stochPeriod + smoothK) {
            log.debug("🌊 Недостаточно данных для расчета StochRSI({},{},{}): размер={}",
                    rsiPeriod, stochPeriod, smoothK, values != null ? values.size() : 0);
            return new ArrayList<>();
        }

        // Сначала рассчитываем RSI
        List<Double> rsiValues = calculateRSI(values, rsiPeriod);

        if (rsiValues.size() < stochPeriod) {
            log.debug("🌊 Недостаточно значений RSI для расчета StochRSI: {}", rsiValues.size());
            return new ArrayList<>();
        }

        List<Double> stochRsiRaw = new ArrayList<>();

        // Рассчитываем стохастический осциллятор на основе RSI
        for (int i = stochPeriod - 1; i < rsiValues.size(); i++) {
            List<Double> rsiPeriodValues = rsiValues.subList(i - stochPeriod + 1, i + 1);
            double minRsi = rsiPeriodValues.stream().min(Double::compareTo).orElse(0.0);
            double maxRsi = rsiPeriodValues.stream().max(Double::compareTo).orElse(100.0);
            double currentRsi = rsiValues.get(i);

            double stochRsi;
            if (maxRsi - minRsi == 0) {
                stochRsi = 50.0; // Нейтральное значение при отсутствии диапазона
            } else {
                stochRsi = ((currentRsi - minRsi) / (maxRsi - minRsi)) * 100.0;
            }

            stochRsiRaw.add(stochRsi);
        }

        // Применяем сглаживание K
        if (stochRsiRaw.size() < smoothK) {
            log.debug("🌊 Недостаточно значений для сглаживания StochRSI: {}", stochRsiRaw.size());
            return stochRsiRaw;
        }

        List<Double> smoothedStochRsi = new ArrayList<>();
        for (int i = smoothK - 1; i < stochRsiRaw.size(); i++) {
            double sum = 0;
            for (int j = i - smoothK + 1; j <= i; j++) {
                sum += stochRsiRaw.get(j);
            }
            smoothedStochRsi.add(sum / smoothK);
        }

        log.debug("🌊 Рассчитан StochRSI({},{},{}) для {} значений, получено {} точек",
                rsiPeriod, stochPeriod, smoothK, values.size(), smoothedStochRsi.size());

        return smoothedStochRsi;
    }

    /**
     * 📈 Рассчитывает Простую Скользящую Среднюю (SMA)
     *
     * @param values Список значений
     * @param period Период для SMA
     * @return Список значений SMA
     */
    public List<Double> calculateSMA(List<Double> values, int period) {
        if (values == null || values.size() < period) {
            log.debug("📈 Недостаточно данных для расчета SMA({}): размер={}", period,
                    values != null ? values.size() : 0);
            return new ArrayList<>();
        }

        List<Double> smaValues = new ArrayList<>();

        for (int i = period - 1; i < values.size(); i++) {
            double sum = 0;
            for (int j = i - period + 1; j <= i; j++) {
                sum += values.get(j);
            }
            smaValues.add(sum / period);
        }

        log.debug("📈 Рассчитан SMA({}) для {} значений, получено {} точек",
                period, values.size(), smaValues.size());

        return smaValues;
    }

    /**
     * 📊 Рассчитывает Стандартное Отклонение для указанного периода
     *
     * @param values Список значений
     * @param period Период для расчета
     * @return Список значений стандартного отклонения
     */
    public List<Double> calculateStandardDeviation(List<Double> values, int period) {
        if (values == null || values.size() < period) {
            log.debug("📊 Недостаточно данных для расчета StdDev({}): размер={}", period,
                    values != null ? values.size() : 0);
            return new ArrayList<>();
        }

        List<Double> smaValues = calculateSMA(values, period);
        List<Double> stdDevValues = new ArrayList<>();

        for (int i = 0; i < smaValues.size(); i++) {
            double sma = smaValues.get(i);
            double sum = 0;

            // Рассчитываем сумму квадратов отклонений
            for (int j = 0; j < period; j++) {
                double value = values.get(i + period - 1 - j);
                sum += Math.pow(value - sma, 2);
            }

            double variance = sum / period;
            double stdDev = Math.sqrt(variance);
            stdDevValues.add(stdDev);
        }

        log.debug("📊 Рассчитано стандартное отклонение({}) для {} значений, получено {} точек",
                period, values.size(), stdDevValues.size());

        return stdDevValues;
    }

    /**
     * 🎯 Рассчитывает Z-Score на основе скользящего среднего и стандартного отклонения
     *
     * @param values Список значений
     * @param period Период для расчета среднего и стандартного отклонения
     * @return Список значений Z-Score
     */
    public List<Double> calculateZScore(List<Double> values, int period) {
        if (values == null || values.size() < period) {
            log.debug("🎯 Недостаточно данных для расчета Z-Score({}): размер={}", period,
                    values != null ? values.size() : 0);
            return new ArrayList<>();
        }

        List<Double> smaValues = calculateSMA(values, period);
        List<Double> stdDevValues = calculateStandardDeviation(values, period);
        List<Double> zScoreValues = new ArrayList<>();

        for (int i = 0; i < smaValues.size(); i++) {
            double currentValue = values.get(i + period - 1);
            double sma = smaValues.get(i);
            double stdDev = stdDevValues.get(i);

            double zScore;
            if (stdDev == 0) {
                zScore = 0; // Избегаем деления на ноль
            } else {
                zScore = (currentValue - sma) / stdDev;
            }

            zScoreValues.add(zScore);
        }

        log.debug("🎯 Рассчитан Z-Score({}) для {} значений, получено {} точек",
                period, values.size(), zScoreValues.size());

        return zScoreValues;
    }
}