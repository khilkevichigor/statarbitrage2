package com.example.statarbitrage.common.model;

/**
 * Статус торговой операции
 */
public enum TradeStatus {
    SELECTED,   // Пара выбрана для торговли
    TRADING,    // Активная торговля
    CLOSED,     // Позиция закрыта
    ERROR,     // Пара с ошибкой
}