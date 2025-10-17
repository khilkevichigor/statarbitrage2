package com.example.core.trading.providers;

import com.example.core.trading.interfaces.TradingProvider;
import com.example.core.trading.interfaces.TradingProviderType;
import com.example.shared.dto.Portfolio;
import com.example.shared.dto.TradeResult;
import com.example.shared.enums.TradeOperationType;
import com.example.shared.models.Position;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Реальная торговля через 3Commas API
 * ЗАГЛУШКА - для будущей реализации
 * ВСЕ МЕТОДЫ СИНХРОННЫЕ - асинхронность убрана!
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
    public TradeResult openLongPosition(Long tradingPairId, String symbol, BigDecimal amount, BigDecimal leverage) {
        // СИНХРОННЫЙ метод - убрана асинхронность!
        return TradeResult.failure(TradeOperationType.OPEN_LONG, symbol,
                "Реальная торговля через 3Commas пока не реализована");
    }

    @Override
    public TradeResult openShortPosition(Long tradingPairId, String symbol, BigDecimal amount, BigDecimal leverage) {
        // СИНХРОННЫЙ метод - убрана асинхронность!
        return TradeResult.failure(TradeOperationType.OPEN_SHORT, symbol,
                "Реальная торговля через 3Commas пока не реализована");
    }

    @Override
    public TradeResult closePosition(String positionId) {
        // СИНХРОННЫЙ метод - убрана асинхронность!
        return TradeResult.failure(TradeOperationType.CLOSE_POSITION, "UNKNOWN",
                "Реальная торговля через 3Commas пока не реализована");
    }

    @Override
    public Position getPosition(String positionId) {
        return null;
    }

    @Override
    public void updatePositionPrices(List<String> tickers) {
        //
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

    @Override
    public void loadPositions(List<Position> positions) {
        // Stub implementation
    }

}