package com.example.statarbitrage.checkers;

import com.example.statarbitrage.model.CoinParameters;
import com.example.statarbitrage.model.UserSettings;

import java.util.Objects;

public final class EmasPositionChecker {
    private EmasPositionChecker() {
    }

    public static boolean check(String direction, UserSettings userSettings, CoinParameters coinParameters, boolean isEnabled) {
        if (!isEnabled) {
            return true;
        }
        boolean useEma1 = userSettings.isUseEma1();
        boolean useEma2 = userSettings.isUseEma2();
        boolean useEma1ToEma2 = userSettings.isUseEma1ToEma2();

        if (!useEma1 || !useEma2 || !useEma1ToEma2) {
            return true; // фильтр выключен — считаем условие выполненным (инверсия)
        }

        double ema1 = coinParameters.getHtfEma1().get(0);
        double ema2 = coinParameters.getHtfEma2().get(0);

        if (Objects.equals(direction, "LONG")) {
            return ema1 >= ema2; // инверсия условия: EMA1 НЕ выше EMA2
        }

        if (Objects.equals(direction, "SHORT")) {
            return ema1 <= ema2; // инверсия условия: EMA1 НЕ ниже EMA2
        }

        return true; // если направление неизвестно, считаем что всё ок
    }
}
