package com.example.statarbitrage.core.processors;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Типы ошибок при обновлении трейда
 */
@Getter
@RequiredArgsConstructor
public enum TradeErrorType {
    MANUALLY_CLOSED_NO_POSITIONS("Позиции были закрыты на бирже вручную"),
    POSITIONS_NOT_FOUND("При обновлении не найдены позиции на бирже"),
    MANUAL_CLOSE_FAILED("Не удалось закрыть позиции вручную"),
    AUTO_CLOSE_FAILED("Не удалось автоматически закрыть арбитражную пару");

    private final String description;
}