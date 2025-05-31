package com.example.statarbitrage.services;

import com.example.statarbitrage.model.Candle;
import com.example.statarbitrage.model.ZScoreEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class CointegrationService {

    private static final int MIN_CANDLES = 50;
    private static final double PVALUE_THRESHOLD = 0.05;
    private static final double ZSCORE_THRESHOLD = 2.0;

    public List<ZScoreEntry> analyzeCointegrationPairs(ConcurrentHashMap<String, List<Candle>> candlesMap) {
        List<String> tickers = new ArrayList<>(candlesMap.keySet());
        List<ZScoreEntry> result = new ArrayList<>();

        int numCoins = tickers.size();
        int totalPairs = (numCoins * (numCoins - 1)) / 2;
        int selectedPairs = 0;

        for (int i = 0; i < tickers.size(); i++) {
            for (int j = i + 1; j < tickers.size(); j++) {
                String t1 = tickers.get(i);
                String t2 = tickers.get(j);

                List<Candle> c1 = candlesMap.get(t1);
                List<Candle> c2 = candlesMap.get(t2);

                if (!isValid(c1, c2)) continue;

                // Проверка на синхронизацию временных рядов
                if (!areTimestampsAligned(c1, c2)) continue;

                double[] x = normalize(c1.stream().mapToDouble(Candle::getClose).toArray());
                double[] y = normalize(c2.stream().mapToDouble(Candle::getClose).toArray());

                double[] residuals = getResiduals(x, y);
                if (residuals == null) continue;

                double zScore = calcZScore(residuals);
                double pValue = calculatePValueForSpread(x, y);
                if (Double.isNaN(pValue)) continue;

                if (pValue < PVALUE_THRESHOLD && Math.abs(zScore) > ZSCORE_THRESHOLD) {
                    selectedPairs++;
                    result.add(ZScoreEntry.builder()
                            .longticker(t1)
                            .shortticker(t2)
                            .zscore(zScore)
                            .pvalue(pValue)
                            .build());
                }
            }
        }

        log.info("Обработано пар: {}, выбрано: {}", totalPairs, selectedPairs);
        return result;
    }

    private boolean isValid(List<Candle> c1, List<Candle> c2) {
        return c1 != null && c2 != null && c1.size() >= MIN_CANDLES && c1.size() == c2.size();
    }

    private boolean areTimestampsAligned(List<Candle> c1, List<Candle> c2) {
        for (int i = 0; i < c1.size(); i++) {
            if (c1.get(i).getTimestamp() != c2.get(i).getTimestamp()) return false;
        }
        return true;
    }

    private double[] normalize(double[] data) {
        double mean = mean(data);
        double std = stdDev(data);
        return Arrays.stream(data).map(d -> (d - mean) / std).toArray();
    }

    private double mean(double[] data) {
        return Arrays.stream(data).average().orElse(0);
    }

    private double stdDev(double[] data) {
        double m = mean(data);
        return Math.sqrt(Arrays.stream(data).map(d -> (d - m) * (d - m)).sum() / data.length);
    }

    private double[] getResiduals(double[] x, double[] y) {
        int n = x.length;
        double meanX = mean(x), meanY = mean(y);
        double covXY = 0, varX = 0;

        for (int i = 0; i < n; i++) {
            covXY += (x[i] - meanX) * (y[i] - meanY);
            varX += (x[i] - meanX) * (x[i] - meanX);
        }
        if (varX == 0) return null;

        double beta = covXY / varX;
        double alpha = meanY - beta * meanX;

        double[] residuals = new double[n];
        for (int i = 0; i < n; i++) {
            residuals[i] = y[i] - (alpha + beta * x[i]);
        }
        return residuals;
    }

    private double calcZScore(double[] data) {
        double mean = mean(data);
        double std = stdDev(data);
        if (std == 0) return 0;
        return (data[data.length - 1] - mean) / std;
    }

    public double calculatePValueForSpread(double[] x, double[] y) {
        double[] spread = calculateSpread(y, x); // y - (α + βx)
        return calculatePValue(spread);
    }

    private double[] calculateSpread(double[] y, double[] x) {
        int n = y.length;
        double[][] regressors = new double[n][2];

        for (int i = 0; i < n; i++) {
            regressors[i][0] = 1.0;
            regressors[i][1] = x[i];
        }

        try {
            OLSMultipleLinearRegression regression = new OLSMultipleLinearRegression();
            regression.newSampleData(y, regressors);
            double[] beta = regression.estimateRegressionParameters();

            double[] spread = new double[n];
            for (int i = 0; i < n; i++) {
                spread[i] = y[i] - (beta[0] + beta[1] * x[i]);
            }
            return spread;
        } catch (Exception e) {
            log.warn("Regression failed: {}", e.getMessage());
            return new double[n];
        }
    }

    private double calculatePValue(double[] series) {
        int n = series.length;
        double[] deltaY = new double[n - 1];
        double[][] regressors = new double[n - 1][1];

        for (int i = 1; i < n; i++) {
            deltaY[i - 1] = series[i] - series[i - 1];
            regressors[i - 1][0] = series[i - 1];
        }

        try {
            OLSMultipleLinearRegression regression = new OLSMultipleLinearRegression();
            regression.setNoIntercept(false);
            regression.newSampleData(deltaY, regressors);

            double[] beta = regression.estimateRegressionParameters();
            double[] stderr = regression.estimateRegressionParametersStandardErrors();

            if (beta.length < 1 || stderr.length < 1 || stderr[0] == 0.0) return Double.NaN;

            double tStat = beta[0] / stderr[0];
            return approximatePValue(tStat);
        } catch (Exception e) {
            log.warn("ADF regression error: {}", e.getMessage());
            return Double.NaN;
        }
    }

    private static double approximatePValue(double tStat) {
        double z = Math.abs(tStat);
        return 2 * (1 - cumulativeStandardNormal(z));
    }

    private static double cumulativeStandardNormal(double z) {
        return 0.5 * (1 + erf(z / Math.sqrt(2)));
    }

    private static double erf(double z) {
        double t = 1.0 / (1.0 + 0.5 * Math.abs(z));
        double ans = 1 - t * Math.exp(-z * z - 1.26551223 +
                t * (1.00002368 + t * (0.37409196 +
                        t * (0.09678418 + t * (-0.18628806 +
                                t * (0.27886807 + t * (-1.13520398 +
                                        t * (1.48851587 + t * (-0.82215223 +
                                                t * 0.17087277)))))))));
        return z >= 0 ? ans : -ans;
    }

    public ZScoreEntry findBestCointegratedPair(List<ZScoreEntry> entries) {
        return entries.stream()
                .filter(e -> e.getPvalue() < PVALUE_THRESHOLD && Math.abs(e.getZscore()) > ZSCORE_THRESHOLD)
                .sorted(Comparator.comparingDouble(ZScoreEntry::getPvalue))
                .findFirst()
                .orElse(null);
    }
}
