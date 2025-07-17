package com.example.statarbitrage.core.processors;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Типы ошибок при поиске торговых пар
 */
@Getter
@RequiredArgsConstructor
public enum FetchPairsErrorType {
    INVALID_REQUEST("Неверный запрос на поиск пар"),
    SETTINGS_NOT_FOUND("Настройки не найдены"),
    CANDLES_FETCH_FAILED("Не удалось получить данные свечей"),
    NO_PAIRS_FOUND("Подходящие пары не найдены"),
    ZSCORE_CALCULATION_FAILED("Ошибка расчета Z-Score");

    private final String description;
}