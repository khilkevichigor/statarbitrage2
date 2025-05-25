package com.example.statarbitrage.checkers;

import com.example.statarbitrage.model.CoinParameters;
import com.example.statarbitrage.model.UserSettings;

public final class DirectionTopGainersLosersChecker {
    private DirectionTopGainersLosersChecker() {
    }

    //todo по сути не нужен тк есть PriceAboveBelowEmaChecker, a top-ы берем в самом начале
    public static boolean check(String direction, UserSettings userSettings, CoinParameters coinParameters, boolean isEnabled) {
        if (!isEnabled) {
            return true;
        }
        boolean topGainersLosersIsApplicable = userSettings.topGainersLosersIsApplicable();
        if (!topGainersLosersIsApplicable) {
            return true;
        }
        // Новое условие для топов
        Double chg24h = coinParameters.getChg24h(); // предполагаю, что есть такой геттер
        if (chg24h == null) {
            return false; // нет данных об изменении цены
        }
        if ("LONG".equals(direction) && chg24h <= 0) {
            return false; // для LONG нужно положительное изменение
        }
        if ("SHORT".equals(direction) && chg24h >= 0) {
            return false; // для SHORT нужно отрицательное изменение
        }
        return true;
    }
}
