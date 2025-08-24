package com.example.core.core.services;

import com.example.core.common.model.PairData;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public interface PortfolioService {

    void updatePortfolioBalanceBeforeTradeUSDT(PairData pairData);

    void updatePortfolioBalanceAfterTradeUSDT(PairData pairData);

    BigDecimal getBalanceUSDT();
}
