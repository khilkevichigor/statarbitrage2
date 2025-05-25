package com.example.statarbitrage.checkers;

import com.example.statarbitrage.model.CoinParameters;
import com.example.statarbitrage.model.UserSettings;

public final class PriceToEmaDistanceChecker {
    private PriceToEmaDistanceChecker() {
    }

    public static boolean check(UserSettings userSettings, CoinParameters coinParameters, boolean isEnabled) {
        if (!isEnabled) {
            return true;
        }
        boolean useEma1 = userSettings.isUseEma1();
        boolean useEma2 = userSettings.isUseEma2();
        boolean usePriceToEma1 = userSettings.isUsePriceToEma1();
        boolean usePriceToEma2 = userSettings.isUsePriceToEma2();
        if (!useEma1 && !useEma2) {
            return true;
        }
        if (!usePriceToEma1 && !usePriceToEma2) {
            return true;
        }
        // ema1
        if (useEma1 && usePriceToEma1 && userSettings.getDistances().getPriceToEma1Min() != null) {
            if (coinParameters.getDistPriceToEma1() < userSettings.getDistances().getPriceToEma1Min()) {
                return false;
            }
        }
        if (useEma1 && usePriceToEma1 && userSettings.getDistances().getPriceToEma1Max() != null) {
            if (coinParameters.getDistPriceToEma1() > userSettings.getDistances().getPriceToEma1Max()) {
                return false;
            }
        }

        // ema2
        if (useEma2 && usePriceToEma2 && userSettings.getDistances().getPriceToEma2Min() != null) {
            if (coinParameters.getDistPriceToEma2() < userSettings.getDistances().getPriceToEma2Min()) {
                return false;
            }
        }
        if (useEma2 && usePriceToEma2 && userSettings.getDistances().getPriceToEma2Max() != null) {
            if (coinParameters.getDistPriceToEma2() > userSettings.getDistances().getPriceToEma2Max()) {
                return false;
            }
        }

        return true;
    }
}
