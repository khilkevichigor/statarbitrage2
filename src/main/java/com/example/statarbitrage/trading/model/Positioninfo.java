package com.example.statarbitrage.trading.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Результат проверки статуса позиций на бирже
 */
@Data
@Builder
public class Positioninfo {
    /**
     * Все ли позиции закрыты
     */
    private boolean positionsClosed;

    private Position longPosition;
    private Position shortPosition;

    /**
     * Общий PnL по закрытым позициям
     */
    private BigDecimal totalPnLUSDT;
    private BigDecimal totalPnLPercent;
}