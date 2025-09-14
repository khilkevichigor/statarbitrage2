package com.example.core.services;

import com.example.shared.models.Pair;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public interface PortfolioService {

    void updatePortfolioBalanceBeforeTradeUSDT(Pair tradingPair);

    void updatePortfolioBalanceAfterTradeUSDT(Pair tradingPair);

    BigDecimal getBalanceUSDT();
}
