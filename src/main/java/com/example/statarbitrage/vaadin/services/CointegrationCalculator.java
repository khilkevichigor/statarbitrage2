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
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CointegrationCalculator {

    // В CointegrationCalculator добавляем метод для обработки одной пары
    public ZScoreData calculatePairZScores(Settings settings, String ticker1, String ticker2,
                                           double[] prices1, double[] prices2) {
        CointegrationResult cointResult = testCointegration(prices1, prices2,
                (int) settings.getWindowSize());

        if (cointResult.getPValue() < settings.getPvalue()
                && Math.abs(cointResult.getCorrelation()) > settings.getMinCorrelation()) {
            return calculateZScore(ticker1, ticker2, prices1, prices2, cointResult, settings);
        }
        return null;
    }

    public List<ZScoreData> calculateZScores(Settings settings, Map<String, List<Candle>> candlesMap) {
        // 1. Подготовка данных
        List<String> tickers = new ArrayList<>(candlesMap.keySet());
        List<ZScoreData> results = new ArrayList<>();

        // 2. Перебор всех возможных пар
        for (int i = 0; i < tickers.size(); i++) {
            for (int j = i + 1; j < tickers.size(); j++) {
                String ticker1 = tickers.get(i);
                String ticker2 = tickers.get(j);

                // 3. Получение цен закрытия
                double[] prices1 = getClosePrices(candlesMap.get(ticker1));
                double[] prices2 = getClosePrices(candlesMap.get(ticker2));

                // 4. Проверка коинтеграции
                CointegrationResult cointResult = testCointegration(prices1, prices2,
                        (int) settings.getWindowSize());

                // 5. Фильтрация по настройкам
                if (cointResult.getPValue() < settings.getPvalue()
                        && Math.abs(cointResult.getCorrelation()) > settings.getMinCorrelation()) {

                    // 6. Расчет Z-показателя
                    ZScoreData zScoreData = calculateZScore(
                            ticker1, ticker2, prices1, prices2, cointResult, settings);

                    results.add(zScoreData);
                }
            }
        }

        // 7. Сортировка и выбор лучших пар
        return filterAndSortResults(results, settings);
    }

    private double[] getClosePrices(List<Candle> candles) {
        return candles.stream()
                .mapToDouble(Candle::getClose)
                .toArray();
    }

    private CointegrationResult testCointegration(double[] y, double[] x, int windowSize) {
        OLSMultipleLinearRegression regression = new OLSMultipleLinearRegression();
        double[][] xData = new double[x.length][1];
        for (int i = 0; i < x.length; i++) {
            xData[i][0] = x[i];
        }
        regression.newSampleData(y, xData);

        double[] residuals = regression.estimateResiduals();
        double beta = regression.estimateRegressionParameters()[1];

        // Упрощенный ADF тест
        double adfStatistic = calculateADFStatistic(residuals);
        double pValue = estimateADFPValue(adfStatistic);
        double correlation = calculateCorrelation(y, x);

        return new CointegrationResult(beta, adfStatistic, pValue, correlation);
    }

    private double calculateADFStatistic(double[] series) {
        DescriptiveStatistics stats = new DescriptiveStatistics(series);
        double[] diff = new double[series.length - 1];
        double[] lagged = new double[series.length - 1];

        for (int i = 1; i < series.length; i++) {
            diff[i - 1] = series[i] - series[i - 1];
            lagged[i - 1] = series[i - 1];
        }

        SimpleRegression adfRegression = new SimpleRegression();
        for (int i = 0; i < diff.length; i++) {
            adfRegression.addData(lagged[i], diff[i]);
        }

        return adfRegression.getSlope() / adfRegression.getSlopeStdErr();
    }

    private double estimateADFPValue(double testStatistic) {
        // Упрощенная версия, в реальности используйте точные таблицы
        if (testStatistic <= -3.96) return 0.01;
        if (testStatistic <= -3.41) return 0.05;
        if (testStatistic <= -3.12) return 0.10;
        return 0.5;
    }

    private double calculateCorrelation(double[] x, double[] y) {
        DescriptiveStatistics statsX = new DescriptiveStatistics(x);
        DescriptiveStatistics statsY = new DescriptiveStatistics(y);

        double cov = 0;
        for (int i = 0; i < x.length; i++) {
            cov += (x[i] - statsX.getMean()) * (y[i] - statsY.getMean());
        }
        cov /= x.length;

        return cov / (statsX.getStandardDeviation() * statsY.getStandardDeviation());
    }

    private ZScoreData calculateZScore(String ticker1, String ticker2,
                                       double[] prices1, double[] prices2,
                                       CointegrationResult cointResult, Settings settings) {

        double[] spread = new double[prices1.length];
        for (int i = 0; i < prices1.length; i++) {
            spread[i] = prices1[i] - cointResult.getBeta() * prices2[i];
        }

        DescriptiveStatistics spreadStats = new DescriptiveStatistics(spread);
        double currentZ = (spread[spread.length - 1] - spreadStats.getMean()) / spreadStats.getStandardDeviation();

        ZScoreParam param = ZScoreParam.builder()
                .zscore(currentZ)
                .pvalue(cointResult.getPValue())
                .adfpvalue(cointResult.getPValue()) // для упрощения
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

    private List<ZScoreData> filterAndSortResults(List<ZScoreData> results, Settings settings) {
        return results.stream()
                .filter(z -> {
                    double lastZ = z.getZscoreParams().get(z.getZscoreParams().size() - 1).getZscore();
                    return Math.abs(lastZ) > settings.getExitZMin();
                })
                .sorted((z1, z2) -> {
                    double z1Abs = Math.abs(z1.getZscoreParams().get(0).getZscore());
                    double z2Abs = Math.abs(z2.getZscoreParams().get(0).getZscore());
                    return Double.compare(z2Abs, z1Abs); // сортировка по убыванию
                })
                .limit((long) settings.getCandleLimit()) // ограничение количества пар
                .collect(Collectors.toList());
    }

    @Data
    private static class CointegrationResult {
        private final double beta;
        private final double adfStatistic;
        private final double pValue;
        private final double correlation;
    }
}