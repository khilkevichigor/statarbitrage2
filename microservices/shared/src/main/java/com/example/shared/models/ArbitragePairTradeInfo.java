package com.example.shared.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Результат торговой операции
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArbitragePairTradeInfo {

    /**
     * Успешность операции
     */
    private boolean success;

    private TradeResult longTradeResult;

    private TradeResult shortTradeResult;

    private BigDecimal portfolioBalanceBeforeTradeUSDT; //баланс до


}