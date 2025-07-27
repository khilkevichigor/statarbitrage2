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
    private BigDecimal totalPnL;

    @Override
    public String toString() {
        return "\nPositionInfo {\n" +
                "  positionsClosed: " + positionsClosed + "\n" +
                "  totalPnL: " + totalPnL + "\n" +
                "  longPosition: " + formatPosition(longPosition) + "\n" +
                "  shortPosition: " + formatPosition(shortPosition) + "\n" +
                '}';
    }

    private String formatPosition(Position position) {
        if (position == null) {
            return "null";
        }
        return "\n    Position {\n" +
                "      positionId: " + position.getPositionId() + "\n" +
                "      symbol: " + position.getSymbol() + "\n" +
                "      allocatedAmount: " + position.getAllocatedAmount() + "\n" +
                "      unrealizedPnLUSDT: " + position.getUnrealizedPnLUSDT() + "\n" +
                "    }";
    }
}