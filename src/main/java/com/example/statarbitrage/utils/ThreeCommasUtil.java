package com.example.statarbitrage.utils;

public final class ThreeCommasUtil {

    private ThreeCommasUtil() {

    }

    /**
     * Преобразует OKX символ (например, XRP-USDT-SWAP) в формат 3Commas (например, USDT_XRP-USDT-SWAP).
     */
    public static String get3CommasTicker(String okxSymbol) {
        if (okxSymbol == null || !okxSymbol.endsWith("-SWAP")) {
            throw new IllegalArgumentException("Invalid OKX symbol format: " + okxSymbol);
        }

        String withoutSwap = okxSymbol.replace("-SWAP", ""); // "XRP-USDT"
        String[] parts = withoutSwap.split("-");

        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid symbol format (expected XXX-YYY-SWAP): " + okxSymbol);
        }

        // Формат 3Commas: QUOTE_BASE-QUOTE-SWAP → USDT_XRP-USDT-SWAP
        return parts[1] + "_" + parts[0] + "-" + parts[1] + "-SWAP";
    }
}
