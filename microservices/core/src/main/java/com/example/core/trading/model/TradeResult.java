package com.example.core.trading.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Результат торговой операции
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TradeResult {

    /**
     * Успешность операции
     */
    private boolean success;

    /**
     * ID созданной/закрытой позиции
     */
    private String positionId;

    /**
     * Тип операции
     */
    private TradeOperationType operationType;

    /**
     * Символ инструмента
     */
    private String symbol;

    /**
     * Размер операции (запрошенный)
     */
    private BigDecimal size;

    /**
     * Фактически исполненный размер
     */
    private BigDecimal executedSize;

    /**
     * Цена исполнения
     */
    private BigDecimal executionPrice;

    /**
     * Комиссии
     */
    private BigDecimal fees;

    /**
     * Прибыль/убыток (для операций закрытия)
     */
    private BigDecimal pnlUSDT;

    /**
     * Прибыль/убыток (для операций закрытия)
     */
    private BigDecimal pnlPercent;

    /**
     * Время исполнения
     */
    private LocalDateTime executionTime;

    /**
     * Сообщение об ошибке (если success = false)
     */
    private String errorMessage;

    /**
     * Дополнительная информация
     */
    private String metadata;

    /**
     * Идентификатор внешнего ордера (для связи с биржей)
     */
    private String externalOrderId;

    /**
     * Позиция, которая была создана или изменена
     */
    private Position position;

    /**
     * Создание успешного результата
     */
    public static TradeResult success(String positionId, TradeOperationType operationType,
                                      String symbol, BigDecimal pnlUSDT, BigDecimal pnlPercent, BigDecimal executedSize, BigDecimal executionPrice,
                                      BigDecimal fees, String externalOrderId, Position position) {
        return TradeResult.builder()
                .success(true)
                .positionId(positionId)
                .operationType(operationType)
                .symbol(symbol)
                .pnlUSDT(pnlUSDT)
                .pnlPercent(pnlPercent)
                .executedSize(executedSize)
                .executionPrice(executionPrice)
                .fees(fees)
                .executionTime(LocalDateTime.now())
                .externalOrderId(externalOrderId)
                .position(position)
                .build();
    }

    /**
     * Создание результата с ошибкой
     */
    public static TradeResult failure(TradeOperationType operationType, String symbol,
                                      String errorMessage) {
        return TradeResult.builder()
                .success(false)
                .operationType(operationType)
                .symbol(symbol)
                .errorMessage(errorMessage)
                .executionTime(LocalDateTime.now())
                .build();
    }
}