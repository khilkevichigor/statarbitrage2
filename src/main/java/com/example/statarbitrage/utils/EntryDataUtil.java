package com.example.statarbitrage.utils;

import com.example.statarbitrage.bot.TradeType;
import com.example.statarbitrage.model.PairData;

import java.math.BigDecimal;

public final class EntryDataUtil {
    private EntryDataUtil() {
    }

    public static BigDecimal getLongReturnRounded(PairData pairData, TradeType tradeType, BigDecimal aReturnRounded, BigDecimal bReturnRounded) {
        if (tradeType.equals(TradeType.GENERAL)) {
            return pairData.getLongTicker().equals(pairData.getA()) ? aReturnRounded : bReturnRounded;
        } else if (tradeType.equals(TradeType.LASB)) {
            return aReturnRounded;
        } else if (tradeType.equals(TradeType.LBSA)) {
            return bReturnRounded;
        }
        throw new IllegalArgumentException("Unknown tradeType: " + tradeType);
    }

    public static BigDecimal getShortReturnRounded(PairData pairData, TradeType tradeType, BigDecimal aReturnRounded, BigDecimal bReturnRounded) {
        if (tradeType.equals(TradeType.GENERAL)) {
            return pairData.getShortTicker().equals(pairData.getA()) ? aReturnRounded : bReturnRounded;
        } else if (tradeType.equals(TradeType.LASB)) {
            return bReturnRounded;
        } else if (tradeType.equals(TradeType.LBSA)) {
            return aReturnRounded;
        }
        throw new IllegalArgumentException("Unknown tradeType: " + tradeType);
    }

    public static double getLongTickerCurrentPrice(PairData pairData, TradeType tradeType) {
        if (tradeType.equals(TradeType.GENERAL)) {
            return pairData.getLongTicker().equals(pairData.getA()) ? pairData.getATickerCurrentPrice() : pairData.getBTickerCurrentPrice();
        } else if (tradeType.equals(TradeType.LASB)) {
            return pairData.getATickerCurrentPrice();
        } else if (tradeType.equals(TradeType.LBSA)) {
            return pairData.getBTickerCurrentPrice();
        }
        throw new IllegalArgumentException("Unknown tradeType: " + tradeType);
    }

    public static double getShortTickerCurrentPrice(PairData pairData, TradeType tradeType) {
        if (tradeType.equals(TradeType.GENERAL)) {
            return pairData.getShortTicker().equals(pairData.getA()) ? pairData.getATickerCurrentPrice() : pairData.getBTickerCurrentPrice();
        } else if (tradeType.equals(TradeType.LASB)) {
            return pairData.getBTickerCurrentPrice();
        } else if (tradeType.equals(TradeType.LBSA)) {
            return pairData.getATickerCurrentPrice();
        }
        throw new IllegalArgumentException("Unknown tradeType: " + tradeType);
    }

    public static String getLongTicker(PairData pairData, TradeType tradeType) {
        if (tradeType.equals(TradeType.GENERAL)) {
            return pairData.getLongTicker();
        } else if (tradeType.equals(TradeType.LASB)) {
            return pairData.getA();
        } else if (tradeType.equals(TradeType.LBSA)) {
            return pairData.getB();
        }
        throw new IllegalArgumentException("Unknown tradeType: " + tradeType);
    }

    public static String getShortTicker(PairData pairData, TradeType tradeType) {
        if (tradeType.equals(TradeType.GENERAL)) {
            return pairData.getShortTicker();
        } else if (tradeType.equals(TradeType.LASB)) {
            return pairData.getB();
        } else if (tradeType.equals(TradeType.LBSA)) {
            return pairData.getA();
        }
        throw new IllegalArgumentException("Unknown tradeType: " + tradeType);
    }

    public static double getLongTickerEntryPrice(PairData pairData, TradeType tradeType) {
        if (tradeType.equals(TradeType.GENERAL)) {
            return pairData.getLongTicker().equals(pairData.getA()) ? pairData.getATickerEntryPrice() : pairData.getBTickerEntryPrice();
        } else if (tradeType.equals(TradeType.LASB)) {
            return pairData.getATickerEntryPrice();
        } else if (tradeType.equals(TradeType.LBSA)) {
            return pairData.getBTickerEntryPrice();
        }
        throw new IllegalArgumentException("Unknown tradeType: " + tradeType);
    }

    public static double getShortTickerEntryPrice(PairData pairData, TradeType tradeType) {
        if (tradeType.equals(TradeType.GENERAL)) {
            return pairData.getShortTicker().equals(pairData.getA()) ? pairData.getATickerEntryPrice() : pairData.getBTickerEntryPrice();
        } else if (tradeType.equals(TradeType.LASB)) {
            return pairData.getBTickerEntryPrice();
        } else if (tradeType.equals(TradeType.LBSA)) {
            return pairData.getATickerEntryPrice();
        }
        throw new IllegalArgumentException("Unknown tradeType: " + tradeType);
    }
}
