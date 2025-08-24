package com.example.core.core.processors;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Типы ошибок при обновлении трейда
 */
@Getter
@RequiredArgsConstructor
public enum UpdateTradeErrorType {
    MANUALLY_CLOSED_NO_POSITIONS("Нет открытых позиций на бирже! Возможно они были закрыты на бирже вручную"),
    POSITIONS_NOT_FOUND("При обновлении не найдены позиции на бирже"),
    MANUAL_CLOSE_FAILED("Не удалось закрыть позиции вручную"),
    AUTO_CLOSE_FAILED("Не удалось автоматически закрыть арбитражную пару");

    private final String description;
}