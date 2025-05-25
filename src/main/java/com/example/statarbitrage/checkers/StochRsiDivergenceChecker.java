package com.example.statarbitrage.checkers;

import com.example.statarbitrage.model.CoinParameters;
import com.example.statarbitrage.model.UserSettings;

import java.util.ArrayList;
import java.util.List;

//todo raw
public final class StochRsiDivergenceChecker {
    private StochRsiDivergenceChecker() {
    }

    public static boolean check(String direction, UserSettings userSettings, CoinParameters coinParameters, boolean isEnabled) {
        if (!isEnabled) {
            return true;
        }
        boolean useStochRsiDiver = userSettings.isUseStochRsiDiver();
        if (!useStochRsiDiver) {
            return true;
        }

        List<Double> prices = coinParameters.getHtfCloses();
        List<Double> rsis = coinParameters.getHtfStochRsi().getKValues();

        if (prices == null || rsis == null || prices.size() < 5 || rsis.size() < 5) {
            return false;
        }

        // Поиск двух последних экстремумов цены
        List<Integer> priceExtremes = findLastTwoExtremes(prices, "LONG".equals(direction));
        List<Integer> rsiExtremes = findLastTwoExtremes(rsis, "LONG".equals(direction));

        if (priceExtremes.size() < 2 || rsiExtremes.size() < 2) return false;

        double price1 = prices.get(priceExtremes.get(0));
        double price2 = prices.get(priceExtremes.get(1));
        double rsi1 = rsis.get(rsiExtremes.get(0));
        double rsi2 = rsis.get(rsiExtremes.get(1));

        if ("LONG".equals(direction)) {
            return price2 < price1 && rsi2 > rsi1;
        } else if ("SHORT".equals(direction)) {
            return price2 > price1 && rsi2 < rsi1;
        }

        return false;
    }

    private static List<Integer> findLastTwoExtremes(List<Double> list, boolean lookForMin) {
        List<Integer> extremes = new ArrayList<>();
        for (int i = list.size() - 2; i > 0; i--) {
            double prev = list.get(i - 1);
            double curr = list.get(i);
            double next = list.get(i + 1);

            boolean isExtreme = lookForMin ? (curr < prev && curr < next) : (curr > prev && curr > next);
            if (isExtreme) {
                extremes.add(0, i);
                if (extremes.size() == 2) break;
            }
        }
        return extremes;
    }
}
