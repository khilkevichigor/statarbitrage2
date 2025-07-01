package com.example.statarbitrage.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class NumberFormatter {

    private NumberFormatter() {
        // Utility class
    }

    public static String format(BigDecimal value) {
        return format(value, 2);
    }

    public static String format(BigDecimal value, int scale) {
        return value == null ? "n/a" : value.setScale(scale, RoundingMode.HALF_UP).toString();
    }

    public static String format(double value) {
        return format(value, 2);
    }

    public static String format(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).toString();
    }

    public static BigDecimal formatBigDecimal(double value) {
        return formatBigDecimal(value, 2);
    }

    public static BigDecimal formatBigDecimal(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }
}