package com.example.statarbitrage.conditionals;

import com.example.statarbitrage.checkers.*;
import com.example.statarbitrage.model.CoinParameters;
import com.example.statarbitrage.model.UserSettings;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Random;

@Slf4j
public final class CandleConditions {
    private static final Random random = new Random();

    private CandleConditions() {
    }

    public static boolean check(UserSettings userSettings, CoinParameters coinParameters) {
        boolean randomizerIsEnabled = userSettings.isUseRandomizer();

        if (userSettings.isUseLong()) {
            if (isGoodToTrade("LONG", userSettings, coinParameters)) {
                String emoji = getEmoji("🟢", randomizerIsEnabled);
                coinParameters.setEmoji(emoji);
                return true;
            }
        }

        if (userSettings.isUseShort()) {
            if (isGoodToTrade("SHORT", userSettings, coinParameters)) {
                String emoji = getEmoji("🔴", randomizerIsEnabled);
                coinParameters.setEmoji(emoji);
                return true;
            }
        }

        coinParameters.setEmoji("⚪");
        return false;
    }

    private static String getEmoji(String emoji, boolean randomizerIsEnabled) {
        if (!randomizerIsEnabled) {
            return emoji;
        } else {
            boolean isLong = random.nextBoolean(); // true = long, false = short
            if (isLong) {
                return "R🟢"; // Лонг
            } else {
                return "R🔴"; // Шорт
            }
        }
    }

    private static boolean isGoodToTrade(String direction, UserSettings userSettings, CoinParameters coinParameters) {
        List<Boolean> checks = List.of(
                EmaEnoughDataChecker.check(userSettings, coinParameters, true),
                VolumeChecker.check(userSettings, coinParameters, true),
                PriceAboveBelowEmaChecker.check(direction, userSettings, coinParameters, true),
                EmaAngleChecker.check(direction, userSettings, coinParameters, true),
                PriceToEmaDistanceChecker.check(userSettings, coinParameters, true),
                EmasPositionChecker.check(direction, userSettings, coinParameters, true),
                PriceCrossEmaChecker.check(direction, userSettings, coinParameters, true),
                HtfStochRsiChecker.check(direction, userSettings, coinParameters, true),
                LtfStochRsiChecker.check(direction, userSettings, coinParameters, true),
                DirectionTopGainersLosersChecker.check(direction, userSettings, coinParameters, false), //todo удалить...
                StochRsiDivergenceChecker.check(direction, userSettings, coinParameters, false), //todo сыро
                TrendBreakChecker.check(direction, userSettings, coinParameters, false), //todo сыро
                HtfRsiLevelChecker.check(direction, userSettings, coinParameters, true)
        );

        return checks.stream().allMatch(Boolean::booleanValue);
    }
}