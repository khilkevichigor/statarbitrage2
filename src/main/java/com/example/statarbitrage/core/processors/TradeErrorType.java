package com.example.statarbitrage.core.processors;

import com.example.statarbitrage.common.model.TradeStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Типы ошибок при обновлении трейда
 */
@Getter
@RequiredArgsConstructor
public enum TradeErrorType {
    MANUALLY_CLOSED_NO_POSITIONS(TradeStatus.ERROR_800, "Позиции были закрыты на бирже вручную"),
    POSITIONS_NOT_FOUND(TradeStatus.ERROR_810, "При обновлении не найдены позиции на бирже"),
    MANUAL_CLOSE_FAILED(TradeStatus.ERROR_710, "Не удалось закрыть позиции вручную"),
    AUTO_CLOSE_FAILED(TradeStatus.ERROR_200, "Не удалось автоматически закрыть арбитражную пару");

    private final TradeStatus status;
    private final String description;
}