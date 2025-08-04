package com.example.statarbitrage.trading.interfaces;

import lombok.Getter;

/**
 * Типы провайдеров торговли
 */
@Getter
public enum TradingProviderType {
    REAL_3COMMAS("3Commas API"),
    REAL_OKX("OKX API");

    private final String displayName;

    TradingProviderType(String displayName) {
        this.displayName = displayName;
    }

}