package com.example.statarbitrage.checkers;

import com.example.statarbitrage.calculators.RsiCalculator;
import com.example.statarbitrage.model.CoinParameters;
import com.example.statarbitrage.model.UserSettings;

import java.util.List;

public final class LtfRsiLevelChecker {

    private LtfRsiLevelChecker() {
    }

    public static boolean check(String direction, UserSettings userSettings, CoinParameters coinParameters, boolean isEnabled) {
        if (!isEnabled) return true;

        List<Double> closes = coinParameters.getLtfCloses(); // или .getCloses()
        if (closes == null || closes.size() < 15) return false;

        double rsi = RsiCalculator.calculateRsi(closes, 14, 0);

        if (direction.equals("LONG")) {
            return rsi < 30.0;
        } else if (direction.equals("SHORT")) {
            return rsi > 70.0;
        }

        return false;
    }
}
