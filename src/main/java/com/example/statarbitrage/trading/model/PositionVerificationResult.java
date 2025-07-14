package com.example.statarbitrage.trading.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Результат проверки статуса позиций на бирже
 */
@Data
@Builder
public class PositionVerificationResult {
    /**
     * Все ли позиции закрыты
     */
    private boolean positionsClosed;

    /**
     * Общий PnL по закрытым позициям
     */
    private BigDecimal totalPnL;
}