package com.example.statarbitrage.threecommas;

public enum LeverageType {
    CROSS("cross"),
    ISOLATED("isolated");

    LeverageType(String name) {
        this.name = name;
    }

    private final String name;

    public String getName() {
        return name;
    }
}
