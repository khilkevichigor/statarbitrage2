package com.example.shared.utils;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
public class BigDecimalUtil {
    public static BigDecimal safeScale(String str, int scale) {
        if (str == null || str.trim().isEmpty() || "N/A".equals(str)) return null;
        try {
            return new BigDecimal(str).setScale(scale, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            log.warn("⚠️ Не удалось сконвертировать в BigDecimal: '{}'", str);
            return null;
        }
    }

    public static BigDecimal safeScale(BigDecimal value, int scale) {
        return value != null ? value.setScale(scale, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    public static BigDecimal safeAdd(BigDecimal a, BigDecimal b) {
        return (a != null ? a : BigDecimal.ZERO).add(b != null ? b : BigDecimal.ZERO);
    }

    public static BigDecimal safeGet(BigDecimal val) {
        return val != null ? val : BigDecimal.ZERO;
    }
}
