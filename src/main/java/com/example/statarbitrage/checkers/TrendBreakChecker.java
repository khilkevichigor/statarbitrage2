package com.example.statarbitrage.checkers;

import com.example.statarbitrage.model.CoinParameters;
import com.example.statarbitrage.model.UserSettings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

//todo raw
public final class TrendBreakChecker {
    private TrendBreakChecker() {
    }

    public static boolean check(String direction, UserSettings userSettings, CoinParameters coinParameters, boolean isEnabled) {
        if (!isEnabled) {
            return true;
        }
        boolean useTrendBreak = userSettings.isUseTrendBreak();
        if (!useTrendBreak) {
            return true;
        }

        List<Double> closes = coinParameters.getHtfCloses();
        if (closes == null || closes.size() < 10) return false;

        double currentPrice = closes.get(closes.size() - 1);

        // Найдём локальные экстремумы
        List<Integer> extremaIndices = new ArrayList<>();
        for (int i = 1; i < closes.size() - 1; i++) {
            double prev = closes.get(i - 1);
            double curr = closes.get(i);
            double next = closes.get(i + 1);

            if ("LONG".equals(direction) && curr < prev && curr < next) {
                extremaIndices.add(i); // локальные минимумы
            } else if ("SHORT".equals(direction) && curr > prev && curr > next) {
                extremaIndices.add(i); // локальные максимумы
            }
        }

        if (extremaIndices.size() < 2) return false;

        // Берём два последних экстремума
        int first = extremaIndices.get(extremaIndices.size() - 2);
        int second = extremaIndices.get(extremaIndices.size() - 1);

        int start = Math.min(first, second);
        int end = Math.max(first, second);

        double middleExtreme = "LONG".equals(direction)
                ? Collections.max(closes.subList(start, end + 1)) // хай между лоями
                : Collections.min(closes.subList(start, end + 1)); // лой между хаями

        return "LONG".equals(direction)
                ? currentPrice > middleExtreme
                : currentPrice < middleExtreme;
    }
}
