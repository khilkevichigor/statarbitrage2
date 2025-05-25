package com.example.statarbitrage.utils;

public final class StringUtil {
    private StringUtil() {
    }

    public static String getTradingViewCleanSymbol(String symbol) {
        //BTCUSDT.P
        return symbol.replace("-SWAP", "").replace("-", "") + ".P";
    }

    public static String getSymbol(String input) {
        String symbolPart = input.trim().toUpperCase().replaceAll("[\\s_]", "");

        // Уже в правильном формате BTC-USDT-SWAP
        if (symbolPart.matches("^[A-Z]+-[A-Z]+-SWAP$")) {
            return symbolPart;
        }

        // В формате ETH-USDT → добавляем SWAP
        if (symbolPart.matches("^[A-Z]+-[A-Z]+$")) {
            return symbolPart + "-SWAP";
        }

        // Возможные котируемые валюты
        String[] quoteAssets = {"USDT", "USDC", "BUSD", "USD", "BTC", "ETH"};

        for (String quote : quoteAssets) {
            if (symbolPart.endsWith(quote) && symbolPart.length() > quote.length()) {
                String base = symbolPart.substring(0, symbolPart.length() - quote.length());
                return base + "-" + quote + "-SWAP";
            }
        }

        // Если просто тикер, например: BTC, PI, PEPE
        return symbolPart + "-USDT-SWAP";
    }

}