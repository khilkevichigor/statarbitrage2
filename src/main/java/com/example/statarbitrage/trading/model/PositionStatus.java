package com.example.statarbitrage.trading.model;

/**
 * Статус торговой позиции
 */
public enum PositionStatus {
    PENDING("Ожидание открытия"),
    OPEN("Открыта"),
    CLOSING("Закрывается"),
    CLOSED("Закрыта"),
    FAILED("Ошибка");

    private final String displayName;

    PositionStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}