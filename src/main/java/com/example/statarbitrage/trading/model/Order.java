package com.example.statarbitrage.trading.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Модель торгового ордера
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Order {

    /**
     * ID ордера
     */
    private String orderId;

    /**
     * ID связанной позиции
     */
    private String positionId;

    /**
     * Символ инструмента
     */
    private String symbol;

    /**
     * Тип ордера
     */
    private OrderType orderType;

    /**
     * Сторона сделки
     */
    private OrderSide side;

    /**
     * Размер ордера
     */
    private BigDecimal size;

    /**
     * Цена ордера (для лимитных)
     */
    private BigDecimal price;

    /**
     * Статус ордера
     */
    private OrderStatus status;

    /**
     * Время создания
     */
    private LocalDateTime createdAt;

    /**
     * Время исполнения
     */
    private LocalDateTime executedAt;

    /**
     * Комиссии
     */
    private BigDecimal fees;

    public enum OrderType {
        MARKET, LIMIT, STOP, STOP_LIMIT
    }

    public enum OrderSide {
        BUY, SELL
    }

    public enum OrderStatus {
        PENDING, FILLED, CANCELLED, REJECTED, PARTIALLY_FILLED
    }
}