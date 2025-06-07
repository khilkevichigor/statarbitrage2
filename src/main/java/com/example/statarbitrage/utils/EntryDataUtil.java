package com.example.statarbitrage.utils;

import com.example.statarbitrage.model.PairData;

import java.math.BigDecimal;

public final class EntryDataUtil {
    private EntryDataUtil() {
    }

    public static BigDecimal getLongReturnRounded(boolean isLasb, BigDecimal aReturnRounded, BigDecimal bReturnRounded) {
        return isLasb ? aReturnRounded : bReturnRounded;
    }

    public static BigDecimal getShortReturnRounded(boolean isLasb, BigDecimal aReturnRounded, BigDecimal bReturnRounded) {
        return isLasb ? bReturnRounded : aReturnRounded;
    }

    public static double getLongTickerCurrentPrice(PairData pairData, boolean isLasb) {
        return isLasb ? pairData.getATickerCurrentPrice() : pairData.getBTickerCurrentPrice();
    }

    public static double getShortTickerCurrentPrice(PairData pairData, boolean isLasb) {
        return isLasb ? pairData.getBTickerCurrentPrice() : pairData.getATickerCurrentPrice();
    }

    public static String getLongTicker(PairData pairData, boolean isLasb) {
        return isLasb ? pairData.getA() : pairData.getB();
    }

    public static String getShortTicker(PairData pairData, boolean isLasb) {
        return isLasb ? pairData.getB() : pairData.getA();
    }

    public static double getLongTickerEntryPrice(PairData pairData, boolean isLasb) {
        return isLasb ? pairData.getATickerEntryPrice() : pairData.getBTickerEntryPrice();
    }

    public static double getShortTickerEntryPrice(PairData pairData, boolean isLasb) {
        return isLasb ? pairData.getBTickerEntryPrice() : pairData.getATickerEntryPrice();
    }
}
