package com.example.statarbitrage.common.model;

/**
 * Статус торговой операции
 */
public enum TradeStatus {
    FOUND,      // Найдена потенциальная пара
    SELECTED,   // Пара выбрана для торговли
    TRADING,    // Активная торговля
    CLOSING,    // Закрытие позиции
    CLOSED,     // Позиция закрыта
    ERROR,     // Пара с ошибкой
}