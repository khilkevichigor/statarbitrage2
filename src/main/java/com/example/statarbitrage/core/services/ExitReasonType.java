package com.example.statarbitrage.core.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ExitReasonType {
    EXIT_REASON_BY_TAKE("Выход по тейку"),
    EXIT_REASON_BY_STOP("Выход по стопу"),
    EXIT_REASON_BY_Z_MIN("Выход по Z min"),
    EXIT_REASON_BY_Z_MAX("Выход по Z max"),
    EXIT_REASON_BY_TIME("Выход по времени"),
    EXIT_REASON_BY_BREAKEVEN("Выход по безубытку"),
    EXIT_REASON_MANUALLY("Выход в ручную");

    private final String description;
}