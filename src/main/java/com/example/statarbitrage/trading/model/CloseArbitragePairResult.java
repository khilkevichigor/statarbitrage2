package com.example.statarbitrage.trading.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Результат торговой операции
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CloseArbitragePairResult {

    /**
     * Успешность операции
     */
    private boolean success;

    private TradeResult longTradeResult;

    private TradeResult shortTradeResult;


}