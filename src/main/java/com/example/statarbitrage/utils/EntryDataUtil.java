package com.example.statarbitrage.utils;

import com.example.statarbitrage.bot.TradeType;
import com.example.statarbitrage.model.PairData;

import java.math.BigDecimal;

public final class EntryDataUtil {
    private EntryDataUtil() {
    }

    public static BigDecimal getLongReturnRounded(PairData pairData, BigDecimal aReturnRounded, BigDecimal bReturnRounded) {
        if (pairData.getTradeType().equals(TradeType.GENERAL.name())) {
            return pairData.getLongTicker().equals(pairData.getA()) ? aReturnRounded : bReturnRounded;
        } else if (pairData.getTradeType().equals(TradeType.LASB.name())) {
            return aReturnRounded;
        } else if (pairData.getTradeType().equals(TradeType.LBSA.name())) {
            return bReturnRounded;
        }
        throw new IllegalArgumentException("Unknown tradeType: " + pairData.getTradeType());
    }

    public static BigDecimal getShortReturnRounded(PairData pairData, BigDecimal aReturnRounded, BigDecimal bReturnRounded) {
        if (pairData.getTradeType().equals(TradeType.GENERAL.name())) {
            return pairData.getShortTicker().equals(pairData.getA()) ? aReturnRounded : bReturnRounded;
        } else if (pairData.getTradeType().equals(TradeType.LASB.name())) {
            return bReturnRounded;
        } else if (pairData.getTradeType().equals(TradeType.LBSA.name())) {
            return aReturnRounded;
        }
        throw new IllegalArgumentException("Unknown tradeType: " + pairData.getTradeType());
    }

    public static double getLongTickerCurrentPrice(PairData pairData) {
        if (pairData.getTradeType().equals(TradeType.GENERAL.name())) {
            return pairData.getLongTicker().equals(pairData.getA()) ? pairData.getATickerCurrentPrice() : pairData.getBTickerCurrentPrice();
        } else if (pairData.getTradeType().equals(TradeType.LASB.name())) {
            return pairData.getATickerCurrentPrice();
        } else if (pairData.getTradeType().equals(TradeType.LBSA.name())) {
            return pairData.getBTickerCurrentPrice();
        }
        throw new IllegalArgumentException("Unknown tradeType: " + pairData.getTradeType());
    }

    public static double getShortTickerCurrentPrice(PairData pairData) {
        if (pairData.getTradeType().equals(TradeType.GENERAL.name())) {
            return pairData.getShortTicker().equals(pairData.getA()) ? pairData.getATickerCurrentPrice() : pairData.getBTickerCurrentPrice();
        } else if (pairData.getTradeType().equals(TradeType.LASB.name())) {
            return pairData.getBTickerCurrentPrice();
        } else if (pairData.getTradeType().equals(TradeType.LBSA.name())) {
            return pairData.getATickerCurrentPrice();
        }
        throw new IllegalArgumentException("Unknown tradeType: " + pairData.getTradeType());
    }

    public static String getLongTicker(PairData pairData) {
        if (pairData.getTradeType().equals(TradeType.GENERAL.name())) {
            return pairData.getLongTicker();
        } else if (pairData.getTradeType().equals(TradeType.LASB.name())) {
            return pairData.getA();
        } else if (pairData.getTradeType().equals(TradeType.LBSA.name())) {
            return pairData.getB();
        }
        throw new IllegalArgumentException("Unknown tradeType: " + pairData.getTradeType());
    }

    public static String getShortTicker(PairData pairData) {
        if (pairData.getTradeType().equals(TradeType.GENERAL.name())) {
            return pairData.getShortTicker();
        } else if (pairData.getTradeType().equals(TradeType.LASB.name())) {
            return pairData.getB();
        } else if (pairData.getTradeType().equals(TradeType.LBSA.name())) {
            return pairData.getA();
        }
        throw new IllegalArgumentException("Unknown tradeType: " + pairData.getTradeType());
    }

    public static double getLongTickerEntryPrice(PairData pairData) {
        if (pairData.getTradeType().equals(TradeType.GENERAL.name())) {
            return pairData.getLongTicker().equals(pairData.getA()) ? pairData.getATickerEntryPrice() : pairData.getBTickerEntryPrice();
        } else if (pairData.getTradeType().equals(TradeType.LASB.name())) {
            return pairData.getATickerEntryPrice();
        } else if (pairData.getTradeType().equals(TradeType.LBSA.name())) {
            return pairData.getBTickerEntryPrice();
        }
        throw new IllegalArgumentException("Unknown tradeType: " + pairData.getTradeType());
    }

    public static double getShortTickerEntryPrice(PairData pairData) {
        if (pairData.getTradeType().equals(TradeType.GENERAL.name())) {
            return pairData.getShortTicker().equals(pairData.getA()) ? pairData.getATickerEntryPrice() : pairData.getBTickerEntryPrice();
        } else if (pairData.getTradeType().equals(TradeType.LASB.name())) {
            return pairData.getBTickerEntryPrice();
        } else if (pairData.getTradeType().equals(TradeType.LBSA.name())) {
            return pairData.getATickerEntryPrice();
        }
        throw new IllegalArgumentException("Unknown tradeType: " + pairData.getTradeType());
    }
}
