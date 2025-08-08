package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.trading.interfaces.TradingProvider;
import com.example.statarbitrage.trading.services.TradingProviderFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioServiceImpl implements PortfolioService {

    private final TradingProviderFactory tradingProviderFactory;

    @Override
    public void updatePortfolioBalanceAfterTradeUSDT(PairData pairData) {
        pairData.setPortfolioAfterTradeUSDT(getBalanceUSDT());
    }

    @Override
    public BigDecimal getBalanceUSDT() {
        TradingProvider provider = tradingProviderFactory.getCurrentProvider();
        return provider.getPortfolio().getAvailableBalance();
    }
}
