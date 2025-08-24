package com.example.core.trading.interfaces;

import com.example.shared.models.Portfolio;
import com.example.shared.models.Position;

import java.math.BigDecimal;

/**
 * Интерфейс для управления портфолио
 */
public interface PortfolioManager {

    /**
     * Инициализация портфолио с начальным балансом
     */
    void initializePortfolio(BigDecimal initialBalance);

    /**
     * Получение текущего состояния портфолио
     */
    Portfolio getCurrentPortfolio();

    /**
     * Резервирование средств для позиции
     */
    boolean reserveBalance(BigDecimal amount);

    /**
     * Освобождение зарезервированных средств
     */
    void releaseReservedBalance(BigDecimal amount);

    /**
     * Обновление портфолио после открытия позиции
     */
    void onPositionOpened(Position position);

    /**
     * Обновление портфолио после закрытия позиции
     */
    void onPositionClosed(Position position, BigDecimal pnl, BigDecimal fees);

    /**
     * Расчет максимального размера позиции
     */
    BigDecimal calculateMaxPositionSize();

    /**
     * Проверка доступности средств
     */
    boolean hasAvailableBalance(BigDecimal amount);

    /**
     * Получение текущей доходности портфолио (%)
     */
    BigDecimal getPortfolioReturn();

    /**
     * Получение максимальной просадки
     */
    BigDecimal getMaxDrawdown();

    /**
     * Обновление цен всех позиций и пересчет портфолио
     */
    void updatePortfolioValue();

    /**
     * Сохранение состояния портфолио в базу данных
     */
    void savePortfolio();

    /**
     * Загрузка состояния портфолио из базы данных
     */
    void loadPortfolio();
}