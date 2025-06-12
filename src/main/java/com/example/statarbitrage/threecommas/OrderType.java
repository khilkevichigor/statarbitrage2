package com.example.statarbitrage.threecommas;

public enum OrderType {
    MARKET("market"),
    LIMIT("limit");

    OrderType(String name) {
        this.name = name;
    }

    private final String name;

    public String getName() {
        return name;
    }
}
