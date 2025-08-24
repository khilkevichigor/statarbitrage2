package com.example.shared.models;

/**
 * Тип торговой операции
 */
public enum TradeOperationType {
    OPEN_LONG("Открытие длинной позиции"),
    OPEN_SHORT("Открытие короткой позиции"),
    CLOSE_POSITION("Закрытие позиции"),
    UPDATE_POSITION("Обновление позиции");

    private final String displayName;

    TradeOperationType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}