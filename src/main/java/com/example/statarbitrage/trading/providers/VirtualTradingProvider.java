package com.example.statarbitrage.trading.providers;

import com.example.statarbitrage.client_okx.OkxClient;
import com.example.statarbitrage.trading.interfaces.PortfolioManager;
import com.example.statarbitrage.trading.interfaces.TradingProvider;
import com.example.statarbitrage.trading.interfaces.TradingProviderType;
import com.example.statarbitrage.trading.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Виртуальная реализация торговли
 * Симулирует торговые операции без реальных сделок
 */
@Slf4j
@Service
public class VirtualTradingProvider implements TradingProvider {

    private final PortfolioManager portfolioManager;
    private final OkxClient okxClient; // Для получения реальных цен

    // Хранилище виртуальных позиций
    private final ConcurrentHashMap<String, Position> positions = new ConcurrentHashMap<>();
    private final List<TradeResult> tradeHistory = new ArrayList<>();

    public VirtualTradingProvider(PortfolioManager portfolioManager, OkxClient okxClient) {
        this.portfolioManager = portfolioManager;
        this.okxClient = okxClient;
    }

    @Override
    public Portfolio getPortfolio() {
        return portfolioManager.getCurrentPortfolio();
    }

    @Override
    public boolean hasAvailableBalance(BigDecimal amount) {
        return portfolioManager.hasAvailableBalance(amount);
    }

    @Override
    public CompletableFuture<TradeResult> openLongPosition(String symbol, BigDecimal amount, BigDecimal leverage) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Проверяем доступность средств
                if (!portfolioManager.hasAvailableBalance(amount)) {
                    return TradeResult.failure(TradeOperationType.OPEN_LONG, symbol,
                            "Недостаточно средств: требуется " + amount + ", доступно " +
                                    portfolioManager.getCurrentPortfolio().getAvailableBalance());
                }

                // Получаем текущую цену
                BigDecimal currentPrice = getCurrentPrice(symbol);
                if (currentPrice == null) {
                    return TradeResult.failure(TradeOperationType.OPEN_LONG, symbol,
                            "Не удалось получить текущую цену для " + symbol);
                }

                // Резервируем средства
                if (!portfolioManager.reserveBalance(amount)) {
                    return TradeResult.failure(TradeOperationType.OPEN_LONG, symbol,
                            "Не удалось зарезервировать средства");
                }

                // Рассчитываем размер позиции
                BigDecimal positionSize = amount.multiply(leverage).divide(currentPrice, 8, RoundingMode.HALF_UP);

                // Рассчитываем комиссии
                BigDecimal fees = calculateFees(amount, leverage);

                // Создаем позицию
                String positionId = UUID.randomUUID().toString();
                Position position = Position.builder()
                        .positionId(positionId)
                        .symbol(symbol)
                        .type(PositionType.LONG)
                        .size(positionSize)
                        .entryPrice(currentPrice)
                        .currentPrice(currentPrice)
                        .leverage(leverage)
                        .allocatedAmount(amount)
                        .unrealizedPnL(BigDecimal.ZERO)
                        .unrealizedPnLPercent(BigDecimal.ZERO)
                        .openingFees(fees)
                        .status(PositionStatus.OPEN)
                        .openTime(LocalDateTime.now())
                        .lastUpdated(LocalDateTime.now())
                        .build();

                // Сохраняем позицию
                positions.put(positionId, position);

                // Уведомляем портфолио
                portfolioManager.onPositionOpened(position);

                // Создаем результат
                TradeResult result = TradeResult.success(positionId, TradeOperationType.OPEN_LONG,
                        symbol, positionSize, currentPrice, fees);
                result.setPnl(BigDecimal.ZERO);

                tradeHistory.add(result);

                log.info("🟢 Виртуально открыта LONG позиция: {} | Размер: {} | Цена: {} | Комиссия: {}",
                        symbol, positionSize, currentPrice, fees);

                return result;

            } catch (Exception e) {
                log.error("Ошибка при открытии LONG позиции {}: {}", symbol, e.getMessage());
                return TradeResult.failure(TradeOperationType.OPEN_LONG, symbol, e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<TradeResult> openShortPosition(String symbol, BigDecimal amount, BigDecimal leverage) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Проверяем доступность средств
                if (!portfolioManager.hasAvailableBalance(amount)) {
                    return TradeResult.failure(TradeOperationType.OPEN_SHORT, symbol,
                            "Недостаточно средств: требуется " + amount + ", доступно " +
                                    portfolioManager.getCurrentPortfolio().getAvailableBalance());
                }

                // Получаем текущую цену
                BigDecimal currentPrice = getCurrentPrice(symbol);
                if (currentPrice == null) {
                    return TradeResult.failure(TradeOperationType.OPEN_SHORT, symbol,
                            "Не удалось получить текущую цену для " + symbol);
                }

                // Резервируем средства
                if (!portfolioManager.reserveBalance(amount)) {
                    return TradeResult.failure(TradeOperationType.OPEN_SHORT, symbol,
                            "Не удалось зарезервировать средства");
                }

                // Рассчитываем размер позиции
                BigDecimal positionSize = amount.multiply(leverage).divide(currentPrice, 8, RoundingMode.HALF_UP);

                // Рассчитываем комиссии
                BigDecimal fees = calculateFees(amount, leverage);

                // Создаем позицию
                String positionId = UUID.randomUUID().toString();
                Position position = Position.builder()
                        .positionId(positionId)
                        .symbol(symbol)
                        .type(PositionType.SHORT)
                        .size(positionSize)
                        .entryPrice(currentPrice)
                        .currentPrice(currentPrice)
                        .leverage(leverage)
                        .allocatedAmount(amount)
                        .unrealizedPnL(BigDecimal.ZERO)
                        .unrealizedPnLPercent(BigDecimal.ZERO)
                        .openingFees(fees)
                        .status(PositionStatus.OPEN)
                        .openTime(LocalDateTime.now())
                        .lastUpdated(LocalDateTime.now())
                        .build();

                // Сохраняем позицию
                positions.put(positionId, position);

                // Уведомляем портфолио
                portfolioManager.onPositionOpened(position);

                // Создаем результат
                TradeResult result = TradeResult.success(positionId, TradeOperationType.OPEN_SHORT,
                        symbol, positionSize, currentPrice, fees);
                result.setPnl(BigDecimal.ZERO);

                tradeHistory.add(result);

                log.info("🔴 Виртуально открыта SHORT позиция: {} | Размер: {} | Цена: {} | Комиссия: {}",
                        symbol, positionSize, currentPrice, fees);

                return result;

            } catch (Exception e) {
                log.error("Ошибка при открытии SHORT позиции {}: {}", symbol, e.getMessage());
                return TradeResult.failure(TradeOperationType.OPEN_SHORT, symbol, e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<TradeResult> closePosition(String positionId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Position position = positions.get(positionId);
                if (position == null) {
                    return TradeResult.failure(TradeOperationType.CLOSE_POSITION, "UNKNOWN",
                            "Позиция не найдена: " + positionId);
                }

                if (position.getStatus() != PositionStatus.OPEN) {
                    return TradeResult.failure(TradeOperationType.CLOSE_POSITION, position.getSymbol(),
                            "Позиция не открыта: " + position.getStatus());
                }

                // Получаем текущую цену
                BigDecimal currentPrice = getCurrentPrice(position.getSymbol());
                if (currentPrice == null) {
                    return TradeResult.failure(TradeOperationType.CLOSE_POSITION, position.getSymbol(),
                            "Не удалось получить текущую цену для " + position.getSymbol());
                }

                // Обновляем цену и рассчитываем PnL
                position.setCurrentPrice(currentPrice);
                position.calculateUnrealizedPnL();

                // Рассчитываем комиссии за закрытие
                BigDecimal closingFees = calculateFees(position.getAllocatedAmount(), position.getLeverage());
                BigDecimal totalFees = position.getOpeningFees().add(closingFees);

                // Финальный PnL с учетом комиссий
                BigDecimal finalPnL = position.getUnrealizedPnL().subtract(closingFees);

                // Закрываем позицию
                position.setStatus(PositionStatus.CLOSED);
                position.setLastUpdated(LocalDateTime.now());

                // Освобождаем средства и уведомляем портфолио
                portfolioManager.releaseReservedBalance(position.getAllocatedAmount());
                portfolioManager.onPositionClosed(position, finalPnL, totalFees);

                // Удаляем из активных позиций
                positions.remove(positionId);

                // Создаем результат
                TradeResult result = TradeResult.success(positionId, TradeOperationType.CLOSE_POSITION,
                        position.getSymbol(), position.getSize(), currentPrice, closingFees);
                result.setPnl(finalPnL);

                tradeHistory.add(result);

                log.info("⚫ Виртуально закрыта позиция: {} {} | Цена: {} | PnL: {} | Комиссии: {}",
                        position.getSymbol(), position.getDirectionString(), currentPrice, finalPnL, totalFees);

                return result;

            } catch (Exception e) {
                log.error("Ошибка при закрытии позиции {}: {}", positionId, e.getMessage());
                return TradeResult.failure(TradeOperationType.CLOSE_POSITION, "UNKNOWN", e.getMessage());
            }
        });
    }

    @Override
    public List<Position> getActivePositions() {
        return new ArrayList<>(positions.values());
    }

    @Override
    public Position getPosition(String positionId) {
        return positions.get(positionId);
    }

    @Override
    public CompletableFuture<Void> updatePositionPrices() {
        return CompletableFuture.runAsync(() -> {
            for (Position position : positions.values()) {
                try {
                    BigDecimal currentPrice = getCurrentPrice(position.getSymbol());
                    if (currentPrice != null) {
                        position.setCurrentPrice(currentPrice);
                        position.calculateUnrealizedPnL();
                        position.setLastUpdated(LocalDateTime.now());
                    }
                } catch (Exception e) {
                    log.warn("Не удалось обновить цену для позиции {}: {}",
                            position.getPositionId(), e.getMessage());
                }
            }

            // Обновляем портфолио
            portfolioManager.updatePortfolioValue();
        });
    }

    @Override
    public BigDecimal getCurrentPrice(String symbol) {
        try {
            // Получаем реальную цену через OKX клиент
            // Пока используем заглушку - для полной интеграции нужно адаптировать OkxClient
            // TODO: Интегрировать с реальными ценами из OKX
            return BigDecimal.valueOf(1.0 + (Math.random() * 0.1 - 0.05)); // Симуляция небольших изменений цены
        } catch (Exception e) {
            log.warn("Не удалось получить цену для {}: {}", symbol, e.getMessage());
            return BigDecimal.valueOf(1.0);
        }
    }

    @Override
    public BigDecimal calculateFees(BigDecimal amount, BigDecimal leverage) {
        // Комиссия = 0.1% от объема сделки (amount * leverage)
        BigDecimal feeRate = BigDecimal.valueOf(0.001); // 0.1%
        return amount.multiply(leverage).multiply(feeRate);
    }

    @Override
    public TradingProviderType getProviderType() {
        return TradingProviderType.VIRTUAL;
    }

    @Override
    public boolean isConnected() {
        return true; // Виртуальная торговля всегда "подключена"
    }

    @Override
    public List<TradeResult> getTradeHistory(int limit) {
        return tradeHistory.stream()
                .sorted((a, b) -> b.getExecutionTime().compareTo(a.getExecutionTime()))
                .limit(limit)
                .toList();
    }
}