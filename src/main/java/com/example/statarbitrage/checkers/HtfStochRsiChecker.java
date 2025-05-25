package com.example.statarbitrage.checkers;

import com.example.statarbitrage.model.CoinParameters;
import com.example.statarbitrage.model.UserSettings;

import java.util.List;

public final class HtfStochRsiChecker {
    private HtfStochRsiChecker() {
    }

    public static boolean check(String direction, UserSettings userSettings, CoinParameters coinParameters, boolean isEnabled) {
        if (!isEnabled) {
            return true;
        }
        boolean useHtfStochRsi = userSettings.isUseHtfStochRsi();
        boolean htfStochRsiCrossIsApplicable = userSettings.htfStochRsiCrossIsApplicable();

        if (!useHtfStochRsi) {
            return true; // если оба фильтра выключены
        }

        List<Double> kValues = coinParameters.getHtfStochRsi().getKValues();
        List<Double> dValues = coinParameters.getHtfStochRsi().getDValues();
        Double htfOversold = userSettings.getHtf().getStochRsi().getOversold();
        Double htfOverbought = userSettings.getHtf().getStochRsi().getOverbought();
        boolean htfOversoldIsApplicable = userSettings.htfStochRsiOversoldIsApplicable();
        boolean htfOverboughtIsApplicable = userSettings.htfStochRsiOverboughtIsApplicable();

        if (kValues == null || kValues.isEmpty()) {
            return false;
        }

        double currentK = kValues.get(0);

        if ("LONG".equals(direction)) {
            if (htfOversoldIsApplicable && currentK >= htfOversold) {
                return false; // не перепродано
            }
            if (htfStochRsiCrossIsApplicable && !checkStochRsiCross(direction, userSettings, kValues, dValues)) {
                return false;
            }
        } else if ("SHORT".equals(direction)) {
            if (htfOverboughtIsApplicable && currentK <= htfOverbought) {
                return false; // не перекуплено
            }
            if (htfStochRsiCrossIsApplicable && !checkStochRsiCross(direction, userSettings, kValues, dValues)) {
                return false;
            }
        }

        return true;
    }

    private static boolean checkStochRsiCross(String direction, UserSettings userSettings, List<Double> kValues, List<Double> dValues) {
        if (kValues == null || dValues == null || kValues.size() < 3 || dValues.size() < 3) {
            return false; // недостаточно данных
        }

        boolean useCurrBar = userSettings.isUseCurrBar(); //currBar==true значит без подтверждения
        double prevPrevK = kValues.get(useCurrBar ? 1 : 2);
        double prevPrevD = dValues.get(useCurrBar ? 1 : 2);
        double prevK = kValues.get(useCurrBar ? 0 : 1);
        double prevD = dValues.get(useCurrBar ? 0 : 1);

        if ("LONG".equals(direction)) {
            if (userSettings.isUseHtfStochRsiLevel()) {
                return prevD < userSettings.getHtf().getStochRsi().getOversold() && prevPrevK <= prevPrevD && prevK > prevD;
            }
            return prevPrevK <= prevPrevD && prevK > prevD;
        } else if ("SHORT".equals(direction)) {
            if (userSettings.isUseHtfStochRsiLevel()) {
                return prevD > userSettings.getHtf().getStochRsi().getOverbought() && prevPrevK >= prevPrevD && prevK < prevD;
            }
            return prevPrevK >= prevPrevD && prevK < prevD;
        }

        return false;
    }
}
