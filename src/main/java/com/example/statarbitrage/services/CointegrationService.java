package com.example.statarbitrage.services;

import com.example.statarbitrage.model.Candle;
import com.example.statarbitrage.model.ZScoreEntry;
import com.example.statarbitrage.utils.ADFUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CointegrationService {

    private final ZScoreService zScoreService;

    public List<ZScoreEntry> analyzeCointegrationPairs(ConcurrentHashMap<String, List<Candle>> candlesMap) {
        List<String> tickers = new ArrayList<>(candlesMap.keySet());
        List<ZScoreEntry> result = new ArrayList<>();

        for (int i = 0; i < tickers.size(); i++) {
            for (int j = i + 1; j < tickers.size(); j++) {
                String ticker1 = tickers.get(i);
                String ticker2 = tickers.get(j);

                List<Candle> candles1 = candlesMap.get(ticker1);
                List<Candle> candles2 = candlesMap.get(ticker2);
                if (candles1 == null || candles2 == null) continue;

                List<Double> prices1 = candles1.stream().map(Candle::getClose).collect(Collectors.toList());
                List<Double> prices2 = candles2.stream().map(Candle::getClose).collect(Collectors.toList());

                if (prices1.size() != prices2.size() || prices1.size() < 30) continue;

                double[] x = prices1.stream().mapToDouble(Double::doubleValue).toArray();
                double[] y = prices2.stream().mapToDouble(Double::doubleValue).toArray();
                double[] residuals = regressAndGetResiduals(x, y);

                boolean isCointegrated = ADFUtil.augmentedDickeyFullerTest(residuals, 1);
                if (isCointegrated) {
                    ZScoreEntry entry = zScoreService.buildZScoreEntry(ticker1, ticker2, residuals);
                    result.add(entry);
                }
            }
        }

        return result;
    }


    private double[] regressAndGetResiduals(double[] x, double[] y) {
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
    }

//    public Optional<ZScoreEntry> findBestCointegratedPair(List<ZScoreEntry> zScoreEntries) {
//        return zScoreEntries.stream()
//                .filter(entry -> entry.getPvalue() < 0.05) // оставляем только статистически значимые
//                .min(Comparator.comparingDouble(ZScoreEntry::getPvalue)); // выбираем с наименьшим p-value
//    }

    public Optional<ZScoreEntry> findBestCointegratedPair(List<ZScoreEntry> zScoreEntries) {
        return zScoreEntries.stream()
                .filter(entry -> entry.getPvalue() < 0.05 && Math.abs(entry.getZscore()) < 1.0) // z-score ближе к 0 — стабильнее
                .sorted(Comparator.comparingDouble(ZScoreEntry::getPvalue))
                .findFirst();
    }


}
