package com.example.statarbitrage.common.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class BigDecimalUtil {
    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    public static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(SCALE, ROUNDING_MODE);
    }
}
