package com.example.statarbitrage.trading.interfaces;

/**
 * Типы провайдеров торговли
 */
public enum TradingProviderType {
    VIRTUAL("Виртуальная торговля"),
    REAL_3COMMAS("3Commas API"),
    REAL_OKX("OKX API"),
    REAL_BINANCE("Binance API"),
    REAL_BYBIT("Bybit API");

    private final String displayName;

    TradingProviderType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isVirtual() {
        return this == VIRTUAL;
    }

    public boolean isReal() {
        return !isVirtual();
    }
}