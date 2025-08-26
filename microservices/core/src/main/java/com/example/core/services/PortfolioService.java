package com.example.core.services;

import com.example.shared.models.TradingPair;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public interface PortfolioService {

    void updatePortfolioBalanceBeforeTradeUSDT(TradingPair tradingPair);

    void updatePortfolioBalanceAfterTradeUSDT(TradingPair tradingPair);

    BigDecimal getBalanceUSDT();
}
