package com.example.statarbitrage.checkers;

import com.example.statarbitrage.model.CoinParameters;
import com.example.statarbitrage.model.UserSettings;

import java.util.List;

public final class HtfRsiLevelChecker {

    private HtfRsiLevelChecker() {
    }

    public static boolean check(String direction, UserSettings userSettings, CoinParameters coinParameters, boolean isEnabled) {
        if (!isEnabled) return true;

        List<Double> closes = coinParameters.getLtfCloses();
        if (closes == null || closes.size() < 15) return false;

        Double rsi = coinParameters.getHtfRsi().get(0);

        if (direction.equals("LONG")) {
            return rsi < userSettings.getHtf().getRsi().getOversold();
        } else if (direction.equals("SHORT")) {
            return rsi > userSettings.getHtf().getRsi().getOverbought();
        }

        return false;
    }
}
