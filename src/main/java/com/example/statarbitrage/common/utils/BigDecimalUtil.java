package com.example.statarbitrage.common.utils;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
public class BigDecimalUtil {
    public static BigDecimal safeScale(String str) {
        if (str == null || str.trim().isEmpty() || "N/A".equals(str)) return null;
        try {
            return new BigDecimal(str).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            log.warn("⚠️ Не удалось сконвертировать в BigDecimal: '{}'", str);
            return null;
        }
    }

    public static BigDecimal safeScale(BigDecimal value) {
        return value != null ? value.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }
}
