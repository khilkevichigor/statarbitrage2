package com.example.statarbitrage.checkers;

import com.example.statarbitrage.model.CoinParameters;
import com.example.statarbitrage.model.UserSettings;

import java.util.List;

public final class PriceCrossEmaChecker {
    private PriceCrossEmaChecker() {
    }

    public static boolean check(String direction, UserSettings userSettings, CoinParameters coinParameters, boolean isEnabled) {
        if (!isEnabled) {
            return true;
        }
        boolean useEma1 = userSettings.isUseEma1();
        boolean useEma2 = userSettings.isUseEma2();
        boolean usePriceCrossEmas = userSettings.isUsePriceCrossEmas();

        if (!useEma1 && !useEma2) {
            return true;
        }
        if (!usePriceCrossEmas) {
            return true; // фильтр выключен
        }

        List<Double> emaFastList = coinParameters.getHtfEma1(); // быстрая EMA
        List<Double> emaSlowList = coinParameters.getHtfEma2(); // медленная EMA
        List<Double> closePriceList = coinParameters.getHtfCloses(); // цены закрытия

        if (closePriceList.size() < 2) {
            return false;
        }

        double closePrev = closePriceList.get(1);
        double closeCurr = closePriceList.get(0);

        if (useEma1 && emaFastList.size() >= 2) {
            double emaFastPrev = emaFastList.get(1);
            double emaFastCurr = emaFastList.get(0);

            if ("LONG".equals(direction)) {
                if (!(closePrev < emaFastPrev && closeCurr > emaFastCurr)) {
                    return false;
                }
            } else if ("SHORT".equals(direction)) {
                if (!(closePrev > emaFastPrev && closeCurr < emaFastCurr)) {
                    return false;
                }
            }
        }

        if (useEma2 && emaSlowList.size() >= 2) {
            double emaSlowPrev = emaSlowList.get(1);
            double emaSlowCurr = emaSlowList.get(0);

            if ("LONG".equals(direction)) {
                if (!(closePrev < emaSlowPrev && closeCurr > emaSlowCurr)) {
                    return false;
                }
            } else if ("SHORT".equals(direction)) {
                if (!(closePrev > emaSlowPrev && closeCurr < emaSlowCurr)) {
                    return false;
                }
            }
        }

        return true;
    }
}
