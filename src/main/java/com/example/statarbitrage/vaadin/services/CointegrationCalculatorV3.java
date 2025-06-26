package com.example.statarbitrage.vaadin.services;

import com.example.statarbitrage.model.Candle;
import com.example.statarbitrage.model.Settings;
import com.example.statarbitrage.model.ZScoreData;
import com.example.statarbitrage.model.ZScoreParam;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CointegrationCalculatorV3 {

    public List<ZScoreData> calculateZScores(Settings settings, Map<String, List<Candle>> candlesMap, boolean isTest) {
        List<ZScoreData> results = new ArrayList<>();
        List<String> tickers = new ArrayList<>(candlesMap.keySet());

        for (int i = 0; i < tickers.size(); i++) {
            for (int j = i + 1; j < tickers.size(); j++) {
                ZScoreData zScoreData = calculatePairZScores(settings, candlesMap, tickers.get(i), tickers.get(j), isTest);
                if (zScoreData != null) {
                    results.add(zScoreData);
                }
            }
        }

        return results;
    }

    private ZScoreData calculatePairZScores(Settings settings, Map<String, List<Candle>> candlesMap,
                                            String ticker1, String ticker2, boolean isTest) {
        List<Candle> candles1 = candlesMap.get(ticker1);
        List<Candle> candles2 = candlesMap.get(ticker2);

        if (candles1 == null || candles2 == null) {
            log.warn("No data for pair {}/{}", ticker1, ticker2);
            return null;
        }

        int windowSize = (int) settings.getWindowSize();

        if (candles1.size() < windowSize || candles2.size() < windowSize) {
            log.debug("Not enough data for pair {}/{} (need {} points, have {}/{})",
                    ticker1, ticker2, windowSize, candles1.size(), candles2.size());
            return null;
        }

        List<Candle> recentCandles1 = candles1.subList(candles1.size() - windowSize, candles1.size());
        List<Candle> recentCandles2 = candles2.subList(candles2.size() - windowSize, candles2.size());

        if (!isTest && !checkVolumeCriteria(recentCandles1, recentCandles2, settings.getMinVolume())) {
            return null;
        }

        double[] prices1 = getClosePrices(recentCandles1);
        double[] prices2 = getClosePrices(recentCandles2);

        CointegrationResult cointResult = testCointegration(prices1, prices2);

        log.debug("Coint for pair {}/{}: p={}, corr={}, stationary={}, beta={}, intercept={}",
                ticker1, ticker2, cointResult.getPValue(), cointResult.getCorrelation(),
                cointResult.isStationary(), cointResult.getBeta(), cointResult.getIntercept());

        return buildZScoreData(ticker1, ticker2, prices1, prices2, cointResult);
    }

    private ZScoreData buildZScoreData(String ticker1, String ticker2,
                                       double[] prices1, double[] prices2,
                                       CointegrationResult cointResult) {
        double[] spread = calculateSpread(prices1, prices2, cointResult.getIntercept(), cointResult.getBeta());
        DescriptiveStatistics spreadStats = new DescriptiveStatistics(spread);

        double currentZ = (spread[spread.length - 1] - spreadStats.getMean()) / spreadStats.getStandardDeviation();

        ZScoreParam param = ZScoreParam.builder()
                .zscore(currentZ)
                .pvalue(cointResult.getPValue())
                .adfpvalue(cointResult.getPValue())
                .correlation(cointResult.getCorrelation())
                .alpha(cointResult.getIntercept())
                .beta(cointResult.getBeta())
                .spread(spread[spread.length - 1])
                .mean(spreadStats.getMean())
                .std(spreadStats.getStandardDeviation())
                .timestamp(System.currentTimeMillis())
                .build();

        return ZScoreData.builder()
                .longTicker(ticker1)
                .shortTicker(ticker2)
                .zscoreParams(List.of(param))
                .build();
    }

    private double[] getClosePrices(List<Candle> candles) {
        return candles.stream()
                .mapToDouble(Candle::getClose)
                .toArray();
    }

    private boolean checkVolumeCriteria(List<Candle> candles1, List<Candle> candles2, double minVolume) {
        try {
            double vol1 = candles1.stream().mapToDouble(Candle::getVolume).average().orElse(0);
            double vol2 = candles2.stream().mapToDouble(Candle::getVolume).average().orElse(0);
            return Math.min(vol1, vol2) >= minVolume;
        } catch (Exception e) {
            log.warn("Volume data not available, skipping volume check");
            return true;
        }
    }

    private CointegrationResult testCointegration(double[] y, double[] x) {
        // 1. Регрессия с intercept
        OLSMultipleLinearRegression regression = new OLSMultipleLinearRegression();
        double[][] xData = new double[x.length][2];
        for (int i = 0; i < x.length; i++) {
            xData[i][0] = 1.0; // intercept term
            xData[i][1] = x[i];
        }
        regression.newSampleData(y, xData);
        double[] params = regression.estimateRegressionParameters();
        double intercept = params[0];
        double beta = params[1];

        // 2. Расчет спреда с учетом intercept
        double[] spread = new double[y.length];
        for (int i = 0; i < y.length; i++) {
            spread[i] = y[i] - (intercept + beta * x[i]);
        }

        // 3. ADF тест на стационарность
        double adfStatistic = calculateADFStatistic(spread);
        double pValue = estimateADFPValue(adfStatistic, y.length);
        boolean isStationary = pValue <= 0.05; // 5% уровень значимости

        // 4. Расчет корреляции
        double correlation = calculatePearsonCorrelation(y, x);

        return new CointegrationResult(intercept, beta, adfStatistic, pValue, correlation, isStationary);
    }

    private double[] calculateSpread(double[] prices1, double[] prices2, double intercept, double beta) {
        double[] spread = new double[prices1.length];
        for (int i = 0; i < prices1.length; i++) {
            spread[i] = prices1[i] - (intercept + beta * prices2[i]);
        }
        return spread;
    }

    private double calculateADFStatistic(double[] series) {
        int n = series.length;
        int lags = (int) Math.max(1, Math.round(Math.pow(n, 1.0 / 3.0))); // Оптимальное количество лагов

        double[] deltaY = new double[n - 1];
        double[] yLag1 = new double[n - 1];

        for (int t = 1; t < n; t++) {
            deltaY[t - 1] = series[t] - series[t - 1];
            yLag1[t - 1] = series[t - 1];
        }

        int effectiveLength = n - 1 - lags;
        double[][] regressors = new double[effectiveLength][1 + lags];
        double[] dependent = new double[effectiveLength];

        for (int t = lags; t < n - 1; t++) {
            int row = t - lags;
            regressors[row][0] = yLag1[t];
            for (int i = 1; i <= lags; i++) {
                regressors[row][i] = deltaY[t - i];
            }
            dependent[row] = deltaY[t];
        }

        OLSMultipleLinearRegression regression = new OLSMultipleLinearRegression();
        regression.newSampleData(dependent, regressors);

        double[] params = regression.estimateRegressionParameters();
        double[] stdErr = regression.estimateRegressionParametersStandardErrors();

        return params[0] / stdErr[0];
    }

    private double estimateADFPValue(double testStatistic, int sampleSize) {
        // Критические значения для ADF теста (с константой, без тренда)
        if (sampleSize <= 50) {
            if (testStatistic <= -4.15) return 0.01;
            if (testStatistic <= -3.50) return 0.05;
            if (testStatistic <= -3.18) return 0.10;
        } else if (sampleSize <= 100) {
            if (testStatistic <= -3.96) return 0.01;
            if (testStatistic <= -3.41) return 0.05;
            if (testStatistic <= -3.12) return 0.10;
        } else {
            if (testStatistic <= -3.90) return 0.01;
            if (testStatistic <= -3.38) return 0.05;
            if (testStatistic <= -3.09) return 0.10;
        }
        return 0.5;
    }

    private double calculatePearsonCorrelation(double[] x, double[] y) {
        DescriptiveStatistics statsX = new DescriptiveStatistics(x);
        DescriptiveStatistics statsY = new DescriptiveStatistics(y);

        double cov = 0;
        for (int i = 0; i < x.length; i++) {
            cov += (x[i] - statsX.getMean()) * (y[i] - statsY.getMean());
        }
        cov /= x.length;

        return cov / (statsX.getStandardDeviation() * statsY.getStandardDeviation());
    }

    @Data
    private static class CointegrationResult {
        private final double intercept;
        private final double beta;
        private final double adfStatistic;
        private final double pValue;
        private final double correlation;
        private final boolean stationary;
    }
}