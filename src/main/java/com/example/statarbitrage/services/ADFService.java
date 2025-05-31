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

    public double calculatePValue(double[] series) {
        double testStat = calculateADFTestStatistic(series, 1);
        if (Double.isNaN(testStat)) {
            return 1.0; // незначимый результат
        }
        return approximatePValue(testStat);
    }

    private static double calculateADFTestStatistic(double[] series, int lags) {
        int n = series.length;
        int effectiveSize = n - lags - 1;
        if (effectiveSize <= 0) {
            log.warn("Series too short for ADF calculation");
            return Double.NaN;
        }

        double[] deltaY = new double[effectiveSize];
        double[] yLag1 = new double[effectiveSize];
        double[][] regressors = new double[effectiveSize][1 + lags];

        for (int t = lags + 1; t < n; t++) {
            int idx = t - lags - 1;
            deltaY[idx] = series[t] - series[t - 1];
            yLag1[idx] = series[t - 1];
            regressors[idx][0] = yLag1[idx];

            for (int j = 1; j <= lags; j++) {
                regressors[idx][j] = series[t - j] - series[t - j - 1];
            }
        }

        OLSMultipleLinearRegression regression = new OLSMultipleLinearRegression();
        regression.setNoIntercept(false);

        try {
            regression.newSampleData(deltaY, regressors);
            double[] beta = regression.estimateRegressionParameters();
            double[] stderr = regression.estimateRegressionParametersStandardErrors();

            int gammaIdx = 1;
            return beta[gammaIdx] / stderr[gammaIdx];
        } catch (SingularMatrixException e) {
            log.warn("Singular matrix in ADF regression, returning NaN", e);
            return Double.NaN;
        } catch (Exception e) {
            log.error("Unexpected error in ADF regression", e);
            return Double.NaN;
        }
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
