package com.example.statarbitrage.checkers;

import com.example.statarbitrage.model.CoinParameters;
import com.example.statarbitrage.model.UserSettings;

public final class VolumeChecker {
    private VolumeChecker() {
    }

    public static boolean check(UserSettings userSettings, CoinParameters coinParameters, boolean isEnabled) {
        if (!isEnabled) {
            return true;
        }
        if (!userSettings.isUseVolume() || userSettings.getVolume().getThresholdMln() == null) {
            return true;
        }
        return coinParameters.getVol24h() >= userSettings.getVolume().getThresholdMln();
    }
}
