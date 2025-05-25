package com.example.statarbitrage.checkers;

import com.example.statarbitrage.model.CoinParameters;
import com.example.statarbitrage.model.UserSettings;

public final class EmaEnoughDataChecker {
    private EmaEnoughDataChecker() {
    }

    public static boolean check(UserSettings userSettings, CoinParameters coinParameters, boolean isEnabled) {
        if (!isEnabled) {
            return true;
        }
        Double period1 = userSettings.getHtf().getEma1Period();
        Double period2 = userSettings.getHtf().getEma2Period();
        int size = coinParameters.getHtfCloses().size();

        return (period1 != null && size > period1) &&
                (period2 != null && size > period2);
    }
}
