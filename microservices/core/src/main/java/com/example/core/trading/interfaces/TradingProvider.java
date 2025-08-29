package com.example.core.trading.interfaces;

import com.example.shared.models.Portfolio;
import com.example.shared.models.Position;
import com.example.shared.models.TradeResult;

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
    TradeResult openLongPosition(Long tredingPairId, String symbol, BigDecimal amount, BigDecimal leverage);

    /**
     * Открытие короткой позиции - СИНХРОННО
     */
    TradeResult openShortPosition(Long tradingPairId, String symbol, BigDecimal amount, BigDecimal leverage);

    /**
     * Закрытие позиции - СИНХРОННО
     */
    TradeResult closePosition(String positionId);

    /**
     * Получение позиции по ID
     */
    Position getPosition(String positionId); // positionId - теперь ID от OKX

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

}