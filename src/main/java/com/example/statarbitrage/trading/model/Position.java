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
     * Расчет нереализованной прибыли/убытка (Net PnL).
     * Учитывает комиссию за открытие, так как она уже уплачена.
     * Комиссия за закрытие будет учтена при расчете реализованного PnL.
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

        // 1. Рассчитываем "грязную" прибыль/убыток (Gross PnL)
        BigDecimal grossPnL = priceDiff.multiply(size);

        // 2. Вычитаем комиссию за открытие (она уже уплачена)
        BigDecimal feesPaid = (this.openingFees != null) ? this.openingFees : BigDecimal.ZERO;
        this.unrealizedPnL = grossPnL.subtract(feesPaid);

        // 3. Рассчитываем процентную прибыль на основе чистого PnL
        if (allocatedAmount != null && allocatedAmount.compareTo(BigDecimal.ZERO) > 0) {
            this.unrealizedPnLPercent = this.unrealizedPnL.divide(allocatedAmount, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        } else {
            this.unrealizedPnLPercent = BigDecimal.ZERO;
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