package com.example.statarbitrage.trading.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Модель торговой позиции (универсальная для виртуальной и реальной торговли)
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Position {

    /**
     * Уникальный идентификатор позиции
     */
    private String positionId;

    /**
     * ID пары в системе статарбитража
     */
    private Long pairDataId;

    /**
     * Символ торгового инструмента
     */
    private String symbol;

    /**
     * Тип позиции
     */
    private PositionType type;

    /**
     * Размер позиции (в базовой валюте)
     */
    private BigDecimal size;

    /**
     * Цена входа
     */
    private BigDecimal entryPrice;

    /**
     * Текущая рыночная цена
     */
    private BigDecimal currentPrice;

    /**
     * Плечо
     */
    private BigDecimal leverage;

    /**
     * Выделенная сумма из депозита
     */
    private BigDecimal allocatedAmount;

    /**
     * Нереализованная прибыль/убыток
     */
    private BigDecimal unrealizedPnL;

    /**
     * Нереализованная прибыль/убыток (%)
     */
    private BigDecimal unrealizedPnLPercent;

    /**
     * Комиссии за открытие
     */
    private BigDecimal openingFees;

    /**
     * Комиссии за закрытие
     */
    private BigDecimal closingFees;

    /**
     * Статус позиции
     */
    private PositionStatus status;

    /**
     * Время открытия
     */
    private LocalDateTime openTime;

    /**
     * Время последнего обновления
     */
    private LocalDateTime lastUpdated;

    /**
     * Дополнительные метаданные (JSON)
     */
    private String metadata;

    /**
     * Идентификатор внешнего ордера (для связи с биржей)
     */
    private String externalOrderId;

    /**
     * Расчет нереализованной прибыли/убытка
     */
    public void calculateUnrealizedPnL() {
        if (entryPrice == null || currentPrice == null || size == null || size.compareTo(BigDecimal.ZERO) == 0) {
            unrealizedPnL = BigDecimal.ZERO;
            unrealizedPnLPercent = BigDecimal.ZERO;
            return;
        }

        BigDecimal priceDiff;
        if (type == PositionType.LONG) {
            priceDiff = currentPrice.subtract(entryPrice);
        } else {
            priceDiff = entryPrice.subtract(currentPrice);
        }

        // Абсолютная прибыль/убыток рассчитывается на основе изменения цены и размера позиции.
        // Плечо не участвует в расчете абсолютного PnL, но влияет на размер необходимого залога (allocatedAmount).
        unrealizedPnL = priceDiff.multiply(size);

        // Процентная прибыль рассчитывается как отношение абсолютного PnL к выделенному залогу.
        if (allocatedAmount != null && allocatedAmount.compareTo(BigDecimal.ZERO) > 0) {
            unrealizedPnLPercent = unrealizedPnL.divide(allocatedAmount, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        } else {
            unrealizedPnLPercent = BigDecimal.ZERO;
        }
    }

    /**
     * Проверка, открыта ли позиция
     */
    public boolean isOpen() {
        return status == PositionStatus.OPEN;
    }

    /**
     * Получение направления позиции как строки
     */
    public String getDirectionString() {
        return type == PositionType.LONG ? "LONG" : "SHORT";
    }
}