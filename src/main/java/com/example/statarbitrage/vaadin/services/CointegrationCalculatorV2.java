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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CointegrationCalculatorV2 {

    public List<ZScoreData> calculateZScores(Settings settings, Map<String, List<Candle>> candlesMap, boolean isTest) {
        List<ZScoreData> results = new ArrayList<>();

        // Получаем список всех тикеров
        List<String> tickers = new ArrayList<>(candlesMap.keySet());

        // Проверяем все возможные пары
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
        // Получаем свечи для обоих тикеров
        List<Candle> candles1 = candlesMap.get(ticker1);
        List<Candle> candles2 = candlesMap.get(ticker2);

        // Проверяем наличие данных
        if (candles1 == null || candles2 == null) {
            log.warn("No data for pair {}/{}", ticker1, ticker2);
            return null;
        }

        int windowSize = (int) settings.getWindowSize();

        // Проверяем достаточность данных
        if (candles1.size() < windowSize || candles2.size() < windowSize) {
            log.debug("Not enough data for pair {}/{} (need {} points, have {}/{})",
                    ticker1, ticker2, windowSize, candles1.size(), candles2.size());
            return null;
        }

        // Берем только последние windowSize свечей
        List<Candle> recentCandles1 = candles1.subList(candles1.size() - windowSize, candles1.size());
        List<Candle> recentCandles2 = candles2.subList(candles2.size() - windowSize, candles2.size());

        // Проверка объема (если данные доступны)
        if (!isTest) {
            if (!checkVolumeCriteria(recentCandles1, recentCandles2, settings.getMinVolume())) {
                return null;
            }
        }

        // Получение и нормализация цен
        double[] prices1 = getClosePrices(recentCandles1);
        double[] prices2 = getClosePrices(recentCandles2);
        double[] normPrices1 = normalizePrices(prices1);
        double[] normPrices2 = normalizePrices(prices2);

        // Тест коинтеграции
        CointegrationResult cointResult = testCointegration(normPrices1, normPrices2);

        log.debug("Coint for pair {}/{}: p = {}, corr = {}, stationary = {}",
                ticker1, ticker2, cointResult.getPValue(), cointResult.getCorrelation(), cointResult.isStationary());


//        if (isTest) {
//            return buildZScoreData(ticker1, ticker2, prices1, prices2, cointResult);
//        } else if (cointResult.getPValue() < settings.getSignificanceLevel() && //todo это условие фильтрует все что есть - оно здесь не нужно
//                Math.abs(cointResult.getCorrelation()) > settings.getMinCorrelation() &&
//                cointResult.isStationary()) {
//
//            return buildZScoreData(ticker1, ticker2, prices1, prices2, cointResult);
//        } else {
//            return null;
//        }

        return buildZScoreData(ticker1, ticker2, prices1, prices2, cointResult);
    }

    private ZScoreData buildZScoreData(String ticker1, String ticker2,
                                       double[] prices1, double[] prices2,
                                       CointegrationResult cointResult) {
        double[] spread = calculateSpread(prices1, prices2, cointResult.getBeta());
        DescriptiveStatistics spreadStats = new DescriptiveStatistics(spread);

        double currentZ = (spread[spread.length - 1] - spreadStats.getMean()) / spreadStats.getStandardDeviation();

        ZScoreParam param = ZScoreParam.builder()
                .zscore(currentZ)
                .pvalue(cointResult.getPValue())
                .adfpvalue(cointResult.getPValue())
                .correlation(cointResult.getCorrelation())
                .alpha(spreadStats.getMean())
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

    private double[] normalizePrices(double[] prices) {
        double firstPrice = prices[0];
        return Arrays.stream(prices).map(p -> p / firstPrice).toArray();
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
        // 1. Регрессия для нахождения бета
        OLSMultipleLinearRegression regression = new OLSMultipleLinearRegression();
        double[][] xData = new double[x.length][1];
        for (int i = 0; i < x.length; i++) {
            xData[i][0] = x[i];
        }
        regression.newSampleData(y, xData);
        double beta = regression.estimateRegressionParameters()[1];

        // 2. Расчет спреда
        double[] spread = calculateSpread(y, x, beta);

        // 3. ADF тест на стационарность спреда
        double adfStatistic = calculateADFStatistic(spread);
        double pValue = estimateADFPValue(adfStatistic);
        boolean isStationary = adfStatistic <= -3.12; // 10% уровень значимости

        // 4. Расчет корреляции
        double correlation = calculatePearsonCorrelation(y, x);

        return new CointegrationResult(beta, adfStatistic, pValue, correlation, isStationary);
    }

    private double[] calculateSpread(double[] prices1, double[] prices2, double beta) {
        double[] spread = new double[prices1.length];
        for (int i = 0; i < prices1.length; i++) {
            spread[i] = prices1[i] - beta * prices2[i];
        }
        return spread;
    }

    private double calculateADFStatistic(double[] series) {
        int n = series.length;
        int lags = 1; // можно сделать параметром
        double[] deltaY = new double[n - 1];
        double[] yLag1 = new double[n - 1];

        for (int t = 1; t < n; t++) {
            deltaY[t - 1] = series[t] - series[t - 1];
            yLag1[t - 1] = series[t - 1];
        }

        int effectiveLength = n - 1 - lags;

        double[][] regressors = new double[effectiveLength][1 + lags]; // 1 - lagged y, lags - delta lags
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

        // Параметр при y[t-1] — первый коэффициент (при лаге уровня)
        return params[0] / stdErr[0];
    }

    private double estimateADFPValue(double testStatistic) {
        // Таблица критических значений для модели с константой (без тренда)
        if (testStatistic <= -3.96) return 0.01;  // 1% уровень значимости
        if (testStatistic <= -3.41) return 0.05;  // 5% уровень значимости
        if (testStatistic <= -3.12) return 0.10;  // 10% уровень значимости
        return 0.5; // выше критических значений => нестационарный
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
        private final double beta;
        private final double adfStatistic;
        private final double pValue;
        private final double correlation;
        private final boolean stationary;
    }
}