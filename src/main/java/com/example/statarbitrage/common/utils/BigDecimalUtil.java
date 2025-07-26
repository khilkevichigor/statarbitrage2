package com.example.statarbitrage.common.utils;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
public class BigDecimalUtil {
    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    public static BigDecimal safeScale(String str) {
        if (str == null || "N/A".equals(str)) return null;
        try {
            return new BigDecimal(str).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            log.warn("⚠️ Не удалось сконвертировать в BigDecimal: '{}'", str);
            return null;
        }
    }
}
