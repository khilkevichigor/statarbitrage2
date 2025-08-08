package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.trading.interfaces.TradingProvider;
import com.example.statarbitrage.trading.services.TradingProviderFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final TradingProviderFactory tradingProviderFactory;

    public void updatePortfolioBalanceBeforeTradeUSDT(PairData pairData) {
        TradingProvider provider = tradingProviderFactory.getCurrentProvider();
        pairData.setPortfolioBeforeTradeUSDT(provider.getPortfolio().getAvailableBalance());
    }

    public void updatePortfolioBalanceAfterTradeUSDT(PairData pairData) {
        TradingProvider provider = tradingProviderFactory.getCurrentProvider();
        pairData.setPortfolioAfterTradeUSDT(provider.getPortfolio().getAvailableBalance());
    }
}
