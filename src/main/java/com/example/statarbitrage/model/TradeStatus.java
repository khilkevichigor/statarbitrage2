package com.example.statarbitrage.model;

/**
 * Статус торговой операции
 */
public enum TradeStatus {
    FOUND,      // Найдена потенциальная пара
    SELECTED,   // Пара выбрана для торговли
    TRADING,    // Активная торговля
    CLOSING,    // Закрытие позиции
    CLOSED,     // Позиция закрыта
    ERROR       // Ошибка в торговле
}