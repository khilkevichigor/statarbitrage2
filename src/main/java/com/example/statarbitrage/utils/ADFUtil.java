package com.example.statarbitrage.utils;

import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;
import org.springframework.stereotype.Service;

@Service
public class ADFUtil {
//    public static boolean augmentedDickeyFullerTest(double[] residuals, int lags) {
//        int n = residuals.length;
//        double[] delta = new double[n - 1];
//        for (int i = 1; i < n; i++) {
//            delta[i - 1] = residuals[i] - residuals[i - 1];
//        }
//
//        List<double[]> xMatrix = new ArrayList<>();
//        List<Double> yVector = new ArrayList<>();
//
//        for (int t = lags + 1; t < delta.length; t++) {
//            double[] row = new double[1 + lags];
//            row[0] = residuals[t]; // lagged level
//            for (int i = 1; i <= lags; i++) {
//                row[i] = delta[t - i];
//            }
//            xMatrix.add(row);
//            yVector.add(delta[t]);
//        }
//
//        int m = xMatrix.size();
//        int p = xMatrix.get(0).length;
//
//        double[][] X = new double[m][p];
//        double[] Y = new double[m];
//        for (int i = 0; i < m; i++) {
//            X[i] = xMatrix.get(i);
//            Y[i] = yVector.get(i);
//        }
//
//        OLSMultipleLinearRegression ols = new OLSMultipleLinearRegression();
//        ols.newSampleData(Y, X);
//        double[] beta = ols.estimateRegressionParameters();
//        double[] stdErr = ols.estimateRegressionParametersStandardErrors();
//
//        double beta1 = beta[1];
//        double se1 = stdErr[1];
//        double tStat = beta1 / se1;
//
//        return tStat < -2.9;
//    }

    public static boolean augmentedDickeyFullerTest(double[] series, int lags) {
        double testStat = calculateADFTestStatistic(series, lags);
        // Грубый критический уровень для 95% доверия ~ -2.86
        return testStat < -2.86;
    }

    public static double calculatePValue(double[] series) {
        double testStat = calculateADFTestStatistic(series, 1);
        return approximatePValue(testStat);
    }

    private static double calculateADFTestStatistic(double[] series, int lags) {
        int n = series.length;
        double[] deltaY = new double[n - 1];
        for (int i = 1; i < n; i++) {
            deltaY[i - 1] = series[i] - series[i - 1];
        }

        // Y(t-1)
        double[] yLag1 = new double[n - 1];
        System.arraycopy(series, 0, yLag1, 0, n - 1);

        double[][] x = new double[n - 1][1];
        for (int i = 0; i < n - 1; i++) {
            x[i][0] = yLag1[i];
        }

        OLSMultipleLinearRegression regression = new OLSMultipleLinearRegression();
        regression.setNoIntercept(true);
        regression.newSampleData(deltaY, x);

        double[] beta = regression.estimateRegressionParameters();
        double[] stderr = regression.estimateRegressionParametersStandardErrors();

        return beta[0] / stderr[0]; // t-статистика на коэф. при y(t-1)
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
