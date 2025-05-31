package com.example.statarbitrage.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.linear.SingularMatrixException;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ADFService {

    public boolean augmentedDickeyFullerTest(double[] series, int lags) {
        int n = series.length;
        if (n <= lags + 1) return false;

        double[] deltaY = new double[n - lags - 1];
        double[][] regressors = new double[n - lags - 1][1 + lags]; // y_{t-1}, Δy_{t-1}, ..., Δy_{t-lags}

        for (int t = lags + 1; t < n; t++) {
            deltaY[t - lags - 1] = series[t] - series[t - 1];
            regressors[t - lags - 1][0] = series[t - 1]; // y_{t-1}
            for (int i = 1; i <= lags; i++) {
                regressors[t - lags - 1][i] = series[t - i] - series[t - i - 1]; // Δy_{t-i}
            }
        }

        OLSMultipleLinearRegression regression = new OLSMultipleLinearRegression();
        regression.setNoIntercept(true); // мы сами включаем всё вручную

        try {
            regression.newSampleData(deltaY, regressors);
            double[] beta = regression.estimateRegressionParameters();
            double[] stderr = regression.estimateRegressionParametersStandardErrors();
            double adfStat = beta[0] / stderr[0];

            // Пример: 5% критическое значение ≈ -2.86
            return adfStat < -2.86;
        } catch (SingularMatrixException e) {
            System.err.println("ADF ошибка: сингулярная матрица, пропускаем серию.");
            return false;
        }
    }

    public double calculatePValue(double[] series) {
        double testStat = calculateADFTestStatistic(series, 1);
        return approximatePValue(testStat);
    }

    private static double calculateADFTestStatistic(double[] series, int lags) {
        int n = series.length;
        int effectiveSize = n - lags - 1;

        double[] deltaY = new double[effectiveSize];
        double[] yLag1 = new double[effectiveSize];
        double[][] regressors = new double[effectiveSize][1 + lags]; // 1 for y_{t-1}, rest for lagged Δy

        for (int t = lags + 1; t < n; t++) {
            int idx = t - lags - 1;
            deltaY[idx] = series[t] - series[t - 1];
            yLag1[idx] = series[t - 1];

            // y_{t-1}
            regressors[idx][0] = yLag1[idx];

            // lagged Δy terms
            for (int j = 1; j <= lags; j++) {
                regressors[idx][j] = series[t - j] - series[t - j - 1];
            }
        }

        OLSMultipleLinearRegression regression = new OLSMultipleLinearRegression();
        regression.setNoIntercept(false); // добавим константу
        regression.newSampleData(deltaY, regressors);

        double[] beta = regression.estimateRegressionParameters();
        double[] stderr = regression.estimateRegressionParametersStandardErrors();

        // gamma — это коэффициент при y_{t-1}, т.е. beta[1] если есть intercept
        int gammaIdx = 1;
        return beta[gammaIdx] / stderr[gammaIdx]; // t-статистика
    }


    private static double approximatePValue(double testStat) {
        // Приблизительная оценка p-value через аппроксимацию нормального распределения
        // (только для грубого приближения)
        double z = Math.abs(testStat);
        return 2 * (1 - cumulativeStandardNormal(z));
    }

    private static double cumulativeStandardNormal(double z) {
        return 0.5 * (1 + erf(z / Math.sqrt(2)));
    }

    // Быстрая реализация функции ошибок
    private static double erf(double z) {
        double t = 1.0 / (1.0 + 0.5 * Math.abs(z));
        double ans = 1 - t * Math.exp(-z * z - 1.26551223 +
                t * (1.00002368 +
                        t * (0.37409196 +
                                t * (0.09678418 +
                                        t * (-0.18628806 +
                                                t * (0.27886807 +
                                                        t * (-1.13520398 +
                                                                t * (1.48851587 +
                                                                        t * (-0.82215223 +
                                                                                t * 0.17087277)))))))));
        return z >= 0 ? ans : -ans;
    }
}
