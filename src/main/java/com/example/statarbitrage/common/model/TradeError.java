package com.example.statarbitrage.common.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Статус торговой операции
 */
@Getter
@AllArgsConstructor
public enum TradeError {

    ERROR_100("Не удалось открыть арбитражную пару через торговую систему"),
    ERROR_110("Недостаточно средств в торговом депо для открытия пары"),
    ERROR_200("Не удалось закрыть арбитражную пару через торговую систему");

    private final String description;
}