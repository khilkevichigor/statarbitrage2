package com.example.statarbitrage.threecommas;

public enum TradeSide {
    BUY("buy"),
    SELL("sell");

    TradeSide(String name) {
        this.name = name;
    }

    private final String name;

    public String getName() {
        return name;
    }
}
