package com.example.statarbitrage.core.processors;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Типы ошибок при поиске торговых пар
 */
@Getter
@RequiredArgsConstructor
public enum UpdateChangesType {
    DEFAULT,
    FROM_OPEN_POSITIONS,
    FROM_CLOSED_POSITIONS
}