package com.example.statarbitrage.trading.interfaces;

import com.example.statarbitrage.trading.model.Portfolio;
import com.example.statarbitrage.trading.model.Position;
import com.example.statarbitrage.trading.model.TradeResult;

import java.math.BigDecimal;
import java.util.List;

/**
 * Основной интерфейс для провайдеров торговли.
 * Позволяет легко переключаться между виртуальной и реальной торговлей.
 * ВСЕ МЕТОДЫ СИНХРОННЫЕ - асинхронность убрана для устранения SQLite BUSY!
 */
public interface TradingProvider {

    /**
     * Получение информации о портфолио
     */
    Portfolio getPortfolio();

    /**
     * Проверка доступности средств для торговли
     */
    boolean hasAvailableBalance(BigDecimal amount);

    /**
     * Открытие длинной позиции - СИНХРОННО
     */
    TradeResult openLongPosition(String symbol, BigDecimal amount, BigDecimal leverage);

    /**
     * Открытие короткой позиции - СИНХРОННО
     */
    TradeResult openShortPosition(String symbol, BigDecimal amount, BigDecimal leverage);

    /**
     * Закрытие позиции - СИНХРОННО
     */
    TradeResult closePosition(String positionId);

    /**
     * Получение позиции по ID
     */
    Position getPosition(String positionId);

    /**
     * Обновление цен всех позиций - СИНХРОННО
     */
    void updatePositionPrices(List<String> tickers);

    /**
     * Получение текущей рыночной цены
     */
    BigDecimal getCurrentPrice(String symbol);

    /**
     * Расчет комиссий для операции
     */
    BigDecimal calculateFees(BigDecimal amount, BigDecimal leverage);

    /**
     * Получение типа провайдера (VIRTUAL, REAL_3COMMAS, REAL_OKX, etc.)
     */
    TradingProviderType getProviderType();

    /**
     * Проверка соединения с провайдером
     */
    boolean isConnected();

    /**
     * Получение истории операций
     */
    List<TradeResult> getTradeHistory(int limit);

    /**
     * Загрузка существующих позиций в провайдер
     */
    void loadPositions(List<Position> positions);
    
    /**
     * Обновление позиции в памяти (для синхронизации при усреднении)
     */
    void updatePositionInMemory(String positionId, Position updatedPosition);
}