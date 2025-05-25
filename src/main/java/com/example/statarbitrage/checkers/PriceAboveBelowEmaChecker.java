package com.example.statarbitrage.checkers;

import com.example.statarbitrage.model.CoinParameters;
import com.example.statarbitrage.model.UserSettings;

public final class PriceAboveBelowEmaChecker {
    private PriceAboveBelowEmaChecker() {
    }

    public static boolean check(String direction, UserSettings userSettings, CoinParameters coinParameters, boolean isEnabled) {
        if (!isEnabled) {
            return true;
        }
        boolean usePriceToEma1 = userSettings.isUsePriceToEma1();
        boolean usePriceToEma2 = userSettings.isUsePriceToEma2();
        boolean useEma1 = userSettings.isUseEma1();
        boolean useEma2 = userSettings.isUseEma2();
        if (!usePriceToEma1 && !usePriceToEma2) {
            return true;
        }
        if (!useEma1 && !useEma2) {
            return true;
        }
        double price = coinParameters.getCurrentPrice();
        double ema1 = coinParameters.getHtfEma1().get(0);
        double ema2 = coinParameters.getHtfEma2().get(0);

        if ("LONG".equals(direction)) {
            if (useEma1 && usePriceToEma1 && price <= ema1) {
                return false;
            }
            if (useEma2 && usePriceToEma2 && price <= ema2) {
                return false;
            }
            return true;
        }

        if ("SHORT".equals(direction)) {
            if (useEma1 && usePriceToEma1 && price >= ema1) {
                return false;
            }
            if (useEma2 && usePriceToEma2 && price >= ema2) {
                return false;
            }
            return true;
        }

        return false; // если направление непонятное
    }
}
