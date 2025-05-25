package com.example.statarbitrage.conditionals;

import com.example.statarbitrage.model.CoinParameters;
import com.example.statarbitrage.model.UserSettings;

public final class RenkoConditions {
    private RenkoConditions() {
    }

    public static boolean check(UserSettings userSettings, CoinParameters coinParameters) {
        boolean filterLong = userSettings.isUseLong();
        boolean filterShort = userSettings.isUseShort();
        if (!filterLong && !filterShort) {
            return false;
        }

        if (coinParameters.getHtfCloses().size() < userSettings.getHtf().getEma1Period()) {
            return false;
        }

//        if (userSettings.isFilterWithVolume()) {
//            if (coinParameters.getVolume24h() < userSettings.getVolumeThresholdMillionsSetpoint()) {
//                return false;
//            }
//        }

//        if (userSettings.isFilterWithEmaAngle()) {
//            if (coinParameters.getEmaAngleDegValue() < userSettings.getMinAngleDegreeSetpoint()) {
//                return false;
//            }
//        }

        boolean higherTimeFrameBullishEma = false;
        boolean higherTimeFrameBearishEma = false;
        if (userSettings.isUsePriceToEma1()) {
            higherTimeFrameBullishEma = filterLong && coinParameters.getCurrentPrice() > coinParameters.getHtfEma1().get(0);
            higherTimeFrameBearishEma = filterShort && coinParameters.getCurrentPrice() < coinParameters.getHtfEma1().get(0);
            if (!higherTimeFrameBullishEma && !higherTimeFrameBearishEma) {
                return false;
            }
        }

//        if (userSettings.isFilterWithHigherTimeFrameStochRsiLevel()) {
//            boolean higherTimeFrameBullishCross = higherTimeFrameBullishEma && coinParameters.getHigherTimeFrameStochRsiHistory().get(0) < 20;
//            boolean higherTimeFrameBearishCross = higherTimeFrameBearishEma && coinParameters.getHigherTimeFrameStochRsiHistory().get(0) > 80;
//            if (!higherTimeFrameBullishCross && !higherTimeFrameBearishCross) {
//                return false;
//            }
//        }

//        if (userSettings.isFilterWithLowerTimeFrameStochRsiValue()) {
//            boolean lowerTimeFrameBullishCross = filterLong && higherTimeFrameBullishEma && coinParameters.getLowerTimeFrameStochRsiValue() < 20;
//            boolean lowerTimeFrameBearishCross = filterShort && higherTimeFrameBearishEma && coinParameters.getLowerTimeFrameStochRsiValue() > 80;
//            if (!lowerTimeFrameBullishCross && !lowerTimeFrameBearishCross) {
//                return false;
//            }
//        }

//        if (userSettings.isFilterWithDistanceCurrentPriceToEma()) {
//            if (coinParameters.getDistanceCurrentPriceToEma() > userSettings.getMaxDistanceCurrentPriceToEmaSetpoint()) {
//                return false;
//            }
//        }

        return true;
    }
}
