package com.example.statarbitrage.utils;

public final class TradingViewUtil {
    private static final String BASE_URL = "https://ru.tradingview.com/chart/s1SlZ9Mu/?symbol=OKX%3A";

    private TradingViewUtil() {
    }

    public static String getTradingViewLink(String symbol) {
        String cleanSymbol = StringUtil.getTradingViewCleanSymbol(symbol);
        String tradingViewUrl = BASE_URL + cleanSymbol;
        return String.format("[📈 График](%s)", tradingViewUrl);
    }
}