package com.example.statarbitrage.trading.providers;

import com.example.statarbitrage.trading.interfaces.TradingProvider;
import com.example.statarbitrage.trading.interfaces.TradingProviderType;
import com.example.statarbitrage.trading.model.Portfolio;
import com.example.statarbitrage.trading.model.Position;
import com.example.statarbitrage.trading.model.TradeOperationType;
import com.example.statarbitrage.trading.model.TradeResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Реальная торговля через 3Commas API
 * ЗАГЛУШКА - для будущей реализации
 */
@Slf4j
@Service
public class Real3CommasTradingProvider implements TradingProvider {

    @Override
    public Portfolio getPortfolio() {
        throw new UnsupportedOperationException("Реальная торговля через 3Commas пока не реализована");
    }

    @Override
    public boolean hasAvailableBalance(BigDecimal amount) {
        throw new UnsupportedOperationException("Реальная торговля через 3Commas пока не реализована");
    }

    @Override
    public CompletableFuture<TradeResult> openLongPosition(String symbol, BigDecimal amount, BigDecimal leverage) {
        return CompletableFuture.completedFuture(
                TradeResult.failure(TradeOperationType.OPEN_LONG, symbol,
                        "Реальная торговля через 3Commas пока не реализована")
        );
    }

    @Override
    public CompletableFuture<TradeResult> openShortPosition(String symbol, BigDecimal amount, BigDecimal leverage) {
        return CompletableFuture.completedFuture(
                TradeResult.failure(TradeOperationType.OPEN_SHORT, symbol,
                        "Реальная торговля через 3Commas пока не реализована")
        );
    }

    @Override
    public CompletableFuture<TradeResult> closePosition(String positionId) {
        return CompletableFuture.completedFuture(
                TradeResult.failure(TradeOperationType.CLOSE_POSITION, "UNKNOWN",
                        "Реальная торговля через 3Commas пока не реализована")
        );
    }

    @Override
    public List<Position> getActivePositions() {
        return Collections.emptyList();
    }

    @Override
    public Position getPosition(String positionId) {
        return null;
    }

    @Override
    public CompletableFuture<Void> updatePositionPrices() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public BigDecimal getCurrentPrice(String symbol) {
        return null;
    }

    @Override
    public BigDecimal calculateFees(BigDecimal amount, BigDecimal leverage) {
        return BigDecimal.ZERO;
    }

    @Override
    public TradingProviderType getProviderType() {
        return TradingProviderType.REAL_3COMMAS;
    }

    @Override
    public boolean isConnected() {
        return false;
    }

    @Override
    public List<TradeResult> getTradeHistory(int limit) {
        return Collections.emptyList();
    }
}