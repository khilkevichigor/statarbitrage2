package com.example.statarbitrage.checkers;

import com.example.statarbitrage.model.CoinParameters;
import com.example.statarbitrage.model.UserSettings;

public final class EmaAngleChecker {
    private EmaAngleChecker() {
    }

    public static boolean check(String direction, UserSettings userSettings, CoinParameters coinParameters, boolean isEnabled) {
        if (!isEnabled) {
            return true;
        }
        Double ema1Angle = coinParameters.getEma1Angle();
        Double ema2Angle = coinParameters.getEma2Angle();
        boolean useEma1 = userSettings.isUseEma1();
        boolean useEma2 = userSettings.isUseEma2();
        boolean useEma1Angle = userSettings.isUseEma1Angle();
        boolean useEma2Angle = userSettings.isUseEma2Angle();
        if (!useEma1 && !useEma2) {
            return true;
        }
        if (!useEma1Angle && !useEma2Angle) {
            return true;
        }
        if ("LONG".equals(direction)) {
            if (useEma1 && useEma1Angle) {
                if (userSettings.getEmaAngles().getEma1Min() != null && ema1Angle < userSettings.getEmaAngles().getEma1Min()) {
                    return false;
                }
                if (userSettings.getEmaAngles().getEma1Max() != null && ema1Angle > userSettings.getEmaAngles().getEma1Max()) {
                    return false;
                }
            }

            if (useEma2 && useEma2Angle) {
                if (userSettings.getEmaAngles().getEma2Min() != null && ema2Angle < userSettings.getEmaAngles().getEma2Min()) {
                    return false;
                }
                if (userSettings.getEmaAngles().getEma2Max() != null && ema2Angle > userSettings.getEmaAngles().getEma2Max()) {
                    return false;
                }
            }

            return true;
        }

        if ("SHORT".equals(direction)) {
            if (useEma1 && useEma1Angle) {
                if (userSettings.getEmaAngles().getEma1Min() != null && ema1Angle > -userSettings.getEmaAngles().getEma1Min()) {
                    return false;
                }
                if (userSettings.getEmaAngles().getEma1Max() != null && ema1Angle < -userSettings.getEmaAngles().getEma1Max()) {
                    return false;
                }
            }

            if (useEma2 && useEma2Angle) {
                if (userSettings.getEmaAngles().getEma2Min() != null && ema2Angle > -userSettings.getEmaAngles().getEma2Min()) {
                    return false;
                }
                if (userSettings.getEmaAngles().getEma2Max() != null && ema2Angle < -userSettings.getEmaAngles().getEma2Max()) {
                    return false;
                }
            }

            return true;
        }

        return false;
    }
}
