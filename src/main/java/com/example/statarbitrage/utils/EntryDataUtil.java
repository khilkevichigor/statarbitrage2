package com.example.statarbitrage.utils;

import com.example.statarbitrage.model.EntryData;

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

    public static double getLongTickerCurrentPrice(EntryData entryData, boolean isLasb) {
        return isLasb ? entryData.getATickerCurrentPrice() : entryData.getBTickerCurrentPrice();
    }

    public static double getShortTickerCurrentPrice(EntryData entryData, boolean isLasb) {
        return isLasb ? entryData.getBTickerCurrentPrice() : entryData.getATickerCurrentPrice();
    }

    public static String getLongTicker(EntryData entryData, boolean isLasb) {
        return isLasb ? entryData.getA() : entryData.getB();
    }

    public static String getShortTicker(EntryData entryData, boolean isLasb) {
        return isLasb ? entryData.getB() : entryData.getA();
    }

    public static double getLongTickerEntryPrice(EntryData entryData, boolean isLasb) {
        return isLasb ? entryData.getATickerEntryPrice() : entryData.getBTickerEntryPrice();
    }

    public static double getShortTickerEntryPrice(EntryData entryData, boolean isLasb) {
        return isLasb ? entryData.getBTickerEntryPrice() : entryData.getATickerEntryPrice();
    }
}
