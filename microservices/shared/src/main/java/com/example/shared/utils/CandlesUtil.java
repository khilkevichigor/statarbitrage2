package com.example.shared.utils;

import com.example.shared.dto.Candle;

import java.util.List;

public final class CandlesUtil {
    private CandlesUtil() {
    }

    public static Candle getLastCandle(List<Candle> candleList) {
        return candleList.get(candleList.size() - 1);
    }

    public static double getLastClose(List<Candle> candleList) {
        return getLastCandle(candleList).getClose();
    }
}