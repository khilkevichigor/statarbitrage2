package com.example.statarbitrage.services;

import com.example.statarbitrage.api.OkxClient;
import com.example.statarbitrage.calculators.*;
import com.example.statarbitrage.converters.RenkoConverter;
import com.example.statarbitrage.model.CoinParameters;
import com.example.statarbitrage.model.UserSettings;
import com.example.statarbitrage.utils.TradingViewUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CoinParametersService {
    private final VolumeService volumeService;
    private final OkxClient okxClient;
    private final PriceChangeService priceChangeService;

    public CoinParameters getParameters(String symbol, UserSettings userSettings, List<Double> btcCloses) {
        List<Double> htfCloses = getCloses(symbol, userSettings.getHtf().getTfName(), userSettings.isUseRenko());
        List<Double> ltfCloses = getCloses(symbol, userSettings.getLtf().getTfName(), userSettings.isUseRenko());

        Double currentPrice = htfCloses.get(htfCloses.size() - 1);

        List<Double> htfEma1 = EmaCalculator.calculateEmaHistory(htfCloses, userSettings.getHtf().getEma1Period());
        List<Double> htfEma2 = EmaCalculator.calculateEmaHistory(htfCloses, userSettings.getHtf().getEma2Period());

        // Объединенные проверки на пустые истории EMA
        Double distancePriceToEma1 = getEmaDistance(currentPrice, htfEma1);
        Double distancePriceToEma2 = getEmaDistance(currentPrice, htfEma2);

        // Расчеты расстояний
        Double distanceEma1ToEma2 = getDistanceBetweenEma(htfEma1, htfEma2);

        // Углы EMA
        Double ema1Angle = getEmaAngle(htfCloses, userSettings.getHtf().getEma1Period());
        Double ema2Angle = getEmaAngle(htfCloses, userSettings.getHtf().getEma2Period());

        // Другие расчеты
        Double htfBtcCorrelation = CorrelationCalculator.calculate(htfCloses, btcCloses);
        Double volatility24h = VolatilityCalculator.calculate24h(htfCloses, userSettings.getHtf().getTfName());
        Double priceChange24h = getPriceChange24h(symbol);
        Double volume24hValue = volumeService.getVolume24h(symbol);

        StochRsiCalculator.StochRsiResult htfStochRsi = StochRsiCalculator.calculateFull(htfCloses, userSettings.getHtf().getStochRsi().getRsiPeriod(), userSettings.getHtf().getStochRsi().getStochPeriod(), userSettings.getHtf().getStochRsi().getKPeriod(), userSettings.getHtf().getStochRsi().getDPeriod());
        StochRsiCalculator.StochRsiResult ltfStochRsi = StochRsiCalculator.calculateFull(ltfCloses, userSettings.getLtf().getStochRsi().getRsiPeriod(), userSettings.getLtf().getStochRsi().getStochPeriod(), userSettings.getLtf().getStochRsi().getKPeriod(), userSettings.getLtf().getStochRsi().getDPeriod());

        List<Double> htfRsi = RsiCalculator.calculateRsiHistory(htfCloses, userSettings.getHtf().getRsi().getRsiPeriod());
        List<Double> ltfRsi = RsiCalculator.calculateRsiHistory(ltfCloses, userSettings.getLtf().getRsi().getRsiPeriod());

        return CoinParameters.builder()
                .symbol(symbol)
                .htfCloses(htfCloses)
                .ltfCloses(ltfCloses)
                .vol24h(volume24hValue)

                .htfEma1(htfEma1)
                .htfEma2(htfEma2)

                .ema1Angle(ema1Angle)
                .ema2Angle(ema2Angle)

                .currentPrice(currentPrice)

                .distPriceToEma1(distancePriceToEma1)
                .distPriceToEma2(distancePriceToEma2)

                .distEma1ToEma2(distanceEma1ToEma2)

                .btcCorr(htfBtcCorrelation)
                .volat24h(volatility24h)
                .chg24h(priceChange24h)
                .htfStochRsi(htfStochRsi)
                .ltfStochRsi(ltfStochRsi)
                .htfRsi(htfRsi)
                .ltfRsi(ltfRsi)

                .tvLink(TradingViewUtil.getTradingViewLink(symbol))
                .build();
    }

    public double getPriceChange24h(String symbol) {
        return priceChangeService.get24hPriceChange(symbol);
    }

    private List<Double> getCloses(String symbol, String timeFrame, boolean useRenko) {
        List<Double> closes = okxClient.getCloses(symbol, timeFrame, 300);
        return useRenko ? RenkoConverter.convertToRenkoCloses(closes) : closes;
    }

    // Упрощенная логика для получения расстояний
    private Double getEmaDistance(Double currentPrice, List<Double> emaHistory) {
        return emaHistory.isEmpty() ? null : EmaCalculator.getDistance(currentPrice, emaHistory.get(0));
    }

    // Расстояние между двумя EMA
    private Double getDistanceBetweenEma(List<Double> ema1History, List<Double> ema2History) {
        if (ema1History.isEmpty() || ema2History.isEmpty()) {
            return null;
        }
        return EmaCalculator.getDistance(ema1History.get(0), ema2History.get(0));
    }

    // Угол EMA
    private Double getEmaAngle(List<Double> closes, Double emaPeriod) {
        return emaPeriod != null ? EmaCalculator.calculateAngle(closes, emaPeriod) : null;
    }

    public Set<String> getTopGainersLosers(Set<String> swapTickers, int topCount) {
        ExecutorService executor = Executors.newFixedThreadPool(5); // максимум 20 одновременных запросов
        List<CompletableFuture<Map.Entry<String, Double>>> futures = swapTickers.stream()
                .map(symbol -> CompletableFuture.supplyAsync(() -> {
                    try {
                        double change = getPriceChange24h(symbol);
                        return Map.entry(symbol, change);
                    } catch (Exception e) {
                        log.error("Ошибка получения изменения цены для {}: {}", symbol, e.getMessage(), e);
                        return null;
                    }
                }, executor))
                .toList();

        Map<String, Double> priceChanges = futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        executor.shutdown();

        List<String> topGainers = priceChanges.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topCount)
                .map(Map.Entry::getKey)
                .toList();

        List<String> topLosers = priceChanges.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(topCount)
                .map(Map.Entry::getKey)
                .toList();

        Set<String> selectedSymbols = new HashSet<>();
        selectedSymbols.addAll(topGainers);
        selectedSymbols.addAll(topLosers);

        return selectedSymbols;
    }
}
