package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.model.PairData;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public interface PortfolioService {

    void updatePortfolioBalanceBeforeTradeUSDT(PairData pairData);

    void updatePortfolioBalanceAfterTradeUSDT(PairData pairData);

    BigDecimal getBalanceUSDT();
}
