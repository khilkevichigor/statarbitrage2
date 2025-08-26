package com.example.core.services;

import com.example.core.trading.interfaces.TradingProvider;
import com.example.core.trading.services.TradingProviderFactory;
import com.example.shared.models.TradingPair;
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
    public void updatePortfolioBalanceBeforeTradeUSDT(TradingPair tradingPair) {
        tradingPair.setPortfolioBeforeTradeUSDT(getBalanceUSDT());
    }

    @Override
    public void updatePortfolioBalanceAfterTradeUSDT(TradingPair tradingPair) {
        tradingPair.setPortfolioAfterTradeUSDT(getBalanceUSDT());
    }

    @Override
    public BigDecimal getBalanceUSDT() {
        TradingProvider provider = tradingProviderFactory.getCurrentProvider();
        BigDecimal totalBalance = provider.getPortfolio().getTotalBalance();
        BigDecimal availableBalance = provider.getPortfolio().getAvailableBalance();

        log.debug("💰 Портфолио: общий баланс = {} USDT, доступный баланс = {} USDT",
                totalBalance, availableBalance);

        // Возвращаем общий баланс (eq) для совпадения с UI
        return totalBalance;
    }
}
