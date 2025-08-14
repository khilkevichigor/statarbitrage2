package com.example.statarbitrage.common.model;

/**
 * Статус торговой операции
 */
public enum TradeStatus {
    SELECTED,   // Пара выбрана для торговли
    TRADING,    // Активная торговля
    OBSERVED,   // Наблюдаемая пара
    CLOSED,     // Позиция закрыта
    ERROR,     // Пара с ошибкой
}