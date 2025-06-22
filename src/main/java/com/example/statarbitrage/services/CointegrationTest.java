package com.example.statarbitrage.services;

import lombok.Data;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;
import org.apache.commons.math3.stat.regression.SimpleRegression;

import java.util.Map;

public class CointegrationTest {

    public static CointegrationResult testCointegration(double[] y, double[] x, int maxLag) {
        // 1. Проверка входных данных
        if (y.length != x.length) {
            throw new IllegalArgumentException("Arrays must be of equal length");
        }

        // 2. Оценивание коинтеграционной регрессии
        OLSMultipleLinearRegression regression = new OLSMultipleLinearRegression();
        double[][] xData = new double[x.length][1];
        for (int i = 0; i < x.length; i++) {
            xData[i][0] = x[i];
        }
        regression.newSampleData(y, xData);
        double[] residuals = regression.estimateResiduals();

        // 3. Проверка остатков на стационарность (ADF тест)
        ADFTestResult adfResult = performADFTest(residuals, maxLag);

        // 4. Формирование результата
        double beta = regression.estimateRegressionParameters()[1];
        return new CointegrationResult(
                beta,
                adfResult.getTestStatistic(),
                adfResult.getPValue(),
                adfResult.getCriticalValues()
        );
    }

    private static ADFTestResult performADFTest(double[] series, int maxLag) {
        // Реализация Augmented Dickey-Fuller теста
        DescriptiveStatistics stats = new DescriptiveStatistics(series);
        double mean = stats.getMean();

        // Рассчитываем первые разности
        double[] deltaY = new double[series.length - 1];
        for (int i = 1; i < series.length; i++) {
            deltaY[i - 1] = series[i] - series[i - 1];
        }

        // Строим регрессию для ADF теста
        SimpleRegression adfRegression = new SimpleRegression();
        for (int i = maxLag + 1; i < series.length; i++) {
            double y = deltaY[i - 1];
            double x1 = series[i - 1] - mean; // лагированное значение
            adfRegression.addData(x1, y);

            // Добавляем лагированные разности (для augmented теста)
            for (int lag = 1; lag <= maxLag; lag++) {
                if (i - lag - 1 >= 0) {
                    adfRegression.addData(deltaY[i - lag - 1], y);
                }
            }
        }

        // Рассчитываем тестовую статистику
        double testStatistic = adfRegression.getSlope() / adfRegression.getSlopeStdErr();

        // Критические значения (упрощённые, для примера)
        Map<Double, Double> criticalValues = Map.of(
                0.01, -3.96,
                0.05, -3.41,
                0.10, -3.12
        );

        // Рассчитываем p-value (упрощённо)
        double pValue = calculateADFPValue(testStatistic);

        return new ADFTestResult(testStatistic, pValue, criticalValues);
    }

    private static double calculateADFPValue(double testStatistic) {
        // Эмпирическая аппроксимация p-value для ADF теста
        // В реальной реализации следует использовать точные табличные значения
        if (testStatistic <= -3.96) return 0.01;
        if (testStatistic <= -3.41) return 0.05;
        if (testStatistic <= -3.12) return 0.10;
        return 0.50; // не отвергаем H0
    }

    // Классы для хранения результатов
    @Data
    public static class CointegrationResult {
        private final double beta;
        private final double testStatistic;
        private final double pValue;
        private final Map<Double, Double> criticalValues;

        // Конструктор, геттеры...
    }

    @Data
    public static class ADFTestResult {
        private final double testStatistic;
        private final double pValue;
        private final Map<Double, Double> criticalValues;

        // Конструктор, геттеры...
    }
}