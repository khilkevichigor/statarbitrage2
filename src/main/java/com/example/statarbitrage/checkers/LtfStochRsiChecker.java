package com.example.statarbitrage.checkers;

import com.example.statarbitrage.model.CoinParameters;
import com.example.statarbitrage.model.UserSettings;

import java.util.List;

public final class LtfStochRsiChecker {
    private LtfStochRsiChecker() {
    }

    public static boolean check(String direction, UserSettings userSettings, CoinParameters coinParameters, boolean isEnabled) {
        if (!isEnabled) {
            return true;
        }
        boolean useLtfStochRsi = userSettings.isUseLtfStochRsi();
        boolean ltfStochRsiCrossIsApplicable = userSettings.ltfStochRsiCrossIsApplicable();

        if (!useLtfStochRsi) {
            return true; // если оба фильтра выключены
        }

        // Проверка LTF
        List<Double> kValues = coinParameters.getLtfStochRsi().getKValues();
        List<Double> dValues = coinParameters.getLtfStochRsi().getDValues();
        Double ltfOversold = userSettings.getLtf().getStochRsi().getOversold();
        Double ltfOverbought = userSettings.getLtf().getStochRsi().getOverbought();
        boolean ltfOversoldIsApplicable = userSettings.ltfStochRsiOversoldIsApplicable();
        boolean ltfOverboughtIsApplicable = userSettings.ltfStochRsiOverboughtIsApplicable();

        if (kValues == null || kValues.isEmpty()) {
            return false;
        }

        double currentK = kValues.get(0);

        if ("LONG".equals(direction)) {
            if (ltfOversoldIsApplicable && currentK >= ltfOversold) {
                return false; // не перепродано
            }
            if (ltfStochRsiCrossIsApplicable && !checkStochRsiCross(direction, userSettings, kValues, dValues)) {
                return false;
            }
        } else if ("SHORT".equals(direction)) {
            if (ltfOverboughtIsApplicable && currentK <= ltfOverbought) {
                return false; // не перекуплено
            }
            if (ltfStochRsiCrossIsApplicable && !checkStochRsiCross(direction, userSettings, kValues, dValues)) {
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
            if (userSettings.isUseLtfStochRsiLevel()) {
                return prevD < userSettings.getLtf().getStochRsi().getOversold() && prevPrevK <= prevPrevD && prevK > prevD;
            }
            return prevPrevK <= prevPrevD && prevK > prevD;
        } else if ("SHORT".equals(direction)) {
            if (userSettings.isUseLtfStochRsiLevel()) {
                return prevD > userSettings.getLtf().getStochRsi().getOverbought() && prevPrevK >= prevPrevD && prevK < prevD;
            }
            return prevPrevK >= prevPrevD && prevK < prevD;
        }

        return false;
    }
}
