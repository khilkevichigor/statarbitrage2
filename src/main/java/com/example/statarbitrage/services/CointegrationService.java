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
    private final ZScoreService zScoreService;
    private final ADFService adfService;

    public List<ZScoreEntry> analyzeCointegrationPairs(ConcurrentHashMap<String, List<Candle>> candlesMap) {
        List<String> tickers = new ArrayList<>(candlesMap.keySet());
        List<ZScoreEntry> result = new ArrayList<>();

        int numCoins = tickers.size();
        int totalPairs = (numCoins * (numCoins - 1)) / 2;
        int selectedPairs = 0;
        int errorPairs = 0;

        for (int i = 0; i < tickers.size(); i++) {
            for (int j = i + 1; j < tickers.size(); j++) {
                String ticker1 = tickers.get(i);
                String ticker2 = tickers.get(j);

                List<Candle> candles1 = candlesMap.get(ticker1);
                List<Candle> candles2 = candlesMap.get(ticker2);
                if (candles1 == null || candles2 == null) continue;

                List<Double> prices1 = candles1.stream().map(Candle::getClose).toList();
                List<Double> prices2 = candles2.stream().map(Candle::getClose).toList();

                if (prices1.size() != prices2.size() || prices1.size() < 30) continue;

                double[] x = prices1.stream().mapToDouble(Double::doubleValue).toArray();
                double[] y = prices2.stream().mapToDouble(Double::doubleValue).toArray();

                double[] residuals = regressAndGetResiduals(x, y);
                if (residuals == null) {
                    continue;
                }


                double pValue = adfService.calculatePValue(residuals);

                if (Double.isNaN(pValue)) {
                    errorPairs++;
                    log.debug("ADF вернул NaN для пары: {} и {}", ticker1, ticker2);
                    continue;
                }

                if (pValue < 0.05) {  // фильтруем по p-value
                    ZScoreEntry entry = zScoreService.buildZScoreEntry(ticker1, ticker2, residuals);
                    entry.setPvalue(pValue);  // если нет — добавить поле в ZScoreEntry
                    result.add(entry);
                    selectedPairs++;
                }
            }
        }
        log.info("Всего монет: {}, пар: {}, коинтегрированных пар: {}, ошибок-NaN: {}", numCoins, totalPairs, selectedPairs, errorPairs);
        return result;
    }

    private double[] regressAndGetResiduals(double[] x, double[] y) {
        if (x.length < 3 || y.length < 3) {
            log.warn("Недостаточно данных для регрессии: x.length={}, y.length={}", x.length, y.length);
            return null;
        }

        double stdX = calculateStdDev(x);
        double stdY = calculateStdDev(y);

        if (stdX == 0.0 || stdY == 0.0) {
            log.warn("Нулевая дисперсия: stdX={}, stdY={}", stdX, stdY);
            return null;
        }

        try {
            OLSMultipleLinearRegression regression = new OLSMultipleLinearRegression();
            double[][] xData = Arrays.stream(x).mapToObj(val -> new double[]{val}).toArray(double[][]::new);
            regression.newSampleData(y, xData);
            double[] params = regression.estimateRegressionParameters();
            double alpha = params[0];
            double beta = params[1];

            double[] residuals = new double[x.length];
            for (int i = 0; i < x.length; i++) {
                residuals[i] = y[i] - (alpha + beta * x[i]);
            }
            return residuals;
        } catch (Exception e) {
            log.warn("Регрессия не выполнена из-за ошибки: {}", e.getMessage());
            return null;
        }
    }

    private double calculateStdDev(double[] data) {
        double mean = Arrays.stream(data).average().orElse(0);
        double variance = Arrays.stream(data).map(val -> (val - mean) * (val - mean)).average().orElse(0);
        return Math.sqrt(variance);
    }


    public ZScoreEntry findBestCointegratedPair(List<ZScoreEntry> zScoreEntries) {
        return zScoreEntries.stream()
                .filter(entry -> entry.getPvalue() < 0.05 && Math.abs(entry.getZscore()) > 2.0) // z-score ближе к 0 — стабильнее
                .sorted(Comparator.comparingDouble(ZScoreEntry::getPvalue))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Cointegration pair not found"));
    }
}
