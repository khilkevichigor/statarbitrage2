package com.example.core.trading.model;

/**
 * Тип торговой позиции
 */
public enum PositionType {
    LONG("Длинная позиция"),
    SHORT("Короткая позиция");

    private final String displayName;

    PositionType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}