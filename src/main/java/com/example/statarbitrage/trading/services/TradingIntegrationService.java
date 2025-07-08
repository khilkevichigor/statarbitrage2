package com.example.statarbitrage.trading.services;

import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.TradeStatus;
import com.example.statarbitrage.trading.interfaces.TradingProvider;
import com.example.statarbitrage.trading.interfaces.TradingProviderType;
import com.example.statarbitrage.trading.model.Portfolio;
import com.example.statarbitrage.trading.model.Position;
import com.example.statarbitrage.trading.model.TradeResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис для интеграции новой торговой системы с существующей системой статарбитража
 */
@Slf4j
@Service
public class TradingIntegrationService {

    private final TradingProviderFactory tradingProviderFactory;

    // Синхронизация открытия позиций для избежания конфликтов SQLite
    private final Object openPositionLock = new Object();

    // Хранилище связей между PairData и торговыми позициями
    private final ConcurrentHashMap<Long, String> pairToLongPositionMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> pairToShortPositionMap = new ConcurrentHashMap<>();

    public TradingIntegrationService(TradingProviderFactory tradingProviderFactory) {
        this.tradingProviderFactory = tradingProviderFactory;
    }

    /**
     * Открытие пары позиций для статарбитража - СИНХРОННО
     */
    public boolean openArbitragePair(PairData pairData) {
        // Синхронизируем всю операцию открытия пары
        synchronized (openPositionLock) {
            try {
                TradingProvider provider = tradingProviderFactory.getCurrentProvider();

                // Рассчитываем размер позиций на основе нового портфолио
                BigDecimal positionSize = calculatePositionSize(provider);
                if (positionSize.compareTo(BigDecimal.ZERO) <= 0) {
                    log.warn("❌ Недостаточно средств для открытия позиций по паре {}/{}",
                            pairData.getLongTicker(), pairData.getShortTicker());
                    return false;
                }

                BigDecimal longAmount = positionSize.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                BigDecimal shortAmount = positionSize.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                BigDecimal leverage = BigDecimal.valueOf(1); // Можно вынести в настройки

                log.info("🔄 Начинаем открытие арбитражной пары: {}/{}",
                        pairData.getLongTicker(), pairData.getShortTicker());

                // Открываем позиции ПОСЛЕДОВАТЕЛЬНО и СИНХРОННО
                log.info("🔵 Открытие LONG позиции: {} с размером {}", pairData.getLongTicker(), longAmount);
                TradeResult longResult = provider.openLongPosition(
                        pairData.getLongTicker(), longAmount, leverage);

                if (!longResult.isSuccess()) {
                    log.error("❌ Не удалось открыть LONG позицию: {}", longResult.getErrorMessage());
                    return false;
                }

                log.info("🔴 Открытие SHORT позиции: {} с размером {}", pairData.getShortTicker(), shortAmount);
                TradeResult shortResult = provider.openShortPosition(
                        pairData.getShortTicker(), shortAmount, leverage);

                if (longResult.isSuccess() && shortResult.isSuccess()) {
                    // Сохраняем связи
                    pairToLongPositionMap.put(pairData.getId(), longResult.getPositionId());
                    pairToShortPositionMap.put(pairData.getId(), shortResult.getPositionId());

                    // Обновляем PairData
                    updatePairDataFromPositions(pairData, longResult, shortResult);

                    log.info("✅ Открыта арбитражная пара: {} LONG / {} SHORT",
                            pairData.getLongTicker(), pairData.getShortTicker());
                    return true;
                } else {
                    // Если одна из позиций не открылась, закрываем успешно открытую
                    if (longResult.isSuccess()) {
                        provider.closePosition(longResult.getPositionId());
                    }
                    if (shortResult.isSuccess()) {
                        provider.closePosition(shortResult.getPositionId());
                    }

                    log.error("❌ Не удалось открыть арбитражную пару {}/{}: Long={}, Short={}",
                            pairData.getLongTicker(), pairData.getShortTicker(),
                            longResult.getErrorMessage(), shortResult.getErrorMessage());
                    return false;
                }

            } catch (Exception e) {
                log.error("❌ Ошибка при открытии арбитражной пары {}/{}: {}",
                        pairData.getLongTicker(), pairData.getShortTicker(), e.getMessage());
                return false;
            }
        }
    }

    /**
     * Закрытие пары позиций - СИНХРОННО
     */
    public boolean closeArbitragePair(PairData pairData) {
        // Синхронизируем всю операцию закрытия пары
        synchronized (openPositionLock) {
            try {
                String longPositionId = pairToLongPositionMap.get(pairData.getId());
                String shortPositionId = pairToShortPositionMap.get(pairData.getId());

                if (longPositionId == null || shortPositionId == null) {
                    log.warn("⚠️ Не найдены позиции для пары {}/{}",
                            pairData.getLongTicker(), pairData.getShortTicker());
                    return false;
                }

                TradingProvider provider = tradingProviderFactory.getCurrentProvider();

                // Закрываем позиции ПОСЛЕДОВАТЕЛЬНО и СИНХРОННО
                log.info("🔄 Начинаем закрытие арбитражной пары: {}/{}",
                        pairData.getLongTicker(), pairData.getShortTicker());

                TradeResult longCloseResult = provider.closePosition(longPositionId);
                TradeResult shortCloseResult = provider.closePosition(shortPositionId);

                boolean success = longCloseResult.isSuccess() && shortCloseResult.isSuccess();

                if (success) {
                    // Рассчитываем общий результат
                    BigDecimal totalPnL = longCloseResult.getPnl().add(shortCloseResult.getPnl());
                    BigDecimal totalFees = longCloseResult.getFees().add(shortCloseResult.getFees());

                    // Обновляем PairData
                    updatePairDataOnClose(pairData, totalPnL, totalFees);

                    // Удаляем связи
                    pairToLongPositionMap.remove(pairData.getId());
                    pairToShortPositionMap.remove(pairData.getId());

                    log.info("✅ Закрыта арбитражная пара: {} / {} | PnL: {} | Комиссии: {}",
                            pairData.getLongTicker(), pairData.getShortTicker(), totalPnL, totalFees);
                } else {
                    log.error("❌ Ошибка при закрытии арбитражной пары {}/{}: Long={}, Short={}",
                            pairData.getLongTicker(), pairData.getShortTicker(),
                            longCloseResult.getErrorMessage(), shortCloseResult.getErrorMessage());
                }

                return success;

            } catch (Exception e) {
                log.error("❌ Ошибка при закрытии арбитражной пары {}/{}: {}",
                        pairData.getLongTicker(), pairData.getShortTicker(), e.getMessage());
                return false;
            }
        }
    }

    /**
     * Обновление цен и PnL для всех активных пар - СИНХРОННО
     */
    public void updateAllPositions() {
        TradingProvider provider = tradingProviderFactory.getCurrentProvider();
        try {
            provider.updatePositionPrices(); // Полностью синхронно
        } catch (Exception e) {
            log.error("❌ Ошибка при обновлении цен позиций: {}", e.getMessage());
        }
    }

    /**
     * Получение информации о портфолио
     */
    public Portfolio getPortfolioInfo() {
        TradingProvider provider = tradingProviderFactory.getCurrentProvider();
        return provider.getPortfolio();
    }

    /**
     * Проверка, достаточно ли средств для новой пары
     */
    public boolean canOpenNewPair() {
        TradingProvider provider = tradingProviderFactory.getCurrentProvider();
        BigDecimal requiredAmount = calculatePositionSize(provider);
        return provider.hasAvailableBalance(requiredAmount);
    }

    /**
     * Получение реальной прибыли позиции
     */
    public BigDecimal getPositionPnL(PairData pairData) {
        String longPositionId = pairToLongPositionMap.get(pairData.getId());
        String shortPositionId = pairToShortPositionMap.get(pairData.getId());

        if (longPositionId == null || shortPositionId == null) {
            return BigDecimal.ZERO;
        }

        TradingProvider provider = tradingProviderFactory.getCurrentProvider();
        Position longPosition = provider.getPosition(longPositionId);
        Position shortPosition = provider.getPosition(shortPositionId);

        if (longPosition == null || shortPosition == null) {
            return BigDecimal.ZERO;
        }

        // Обновляем расчеты PnL
        longPosition.calculateUnrealizedPnL();
        shortPosition.calculateUnrealizedPnL();

        return longPosition.getUnrealizedPnL().add(shortPosition.getUnrealizedPnL());
    }

    private BigDecimal calculatePositionSize(TradingProvider provider) {
        Portfolio portfolio = provider.getPortfolio();
        if (portfolio == null) {
            return BigDecimal.ZERO;
        }

        // 10% от общего портфолио на одну пару (по умолчанию)
        BigDecimal maxPositionPercent = BigDecimal.valueOf(10);
        BigDecimal maxPositionSize = portfolio.getTotalBalance()
                .multiply(maxPositionPercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        // Не больше доступного баланса
        return maxPositionSize.min(portfolio.getAvailableBalance());
    }

    private void updatePairDataFromPositions(PairData pairData, TradeResult longResult, TradeResult shortResult) {
        // Обновляем цены входа (они могли отличаться от текущих рыночных)
        pairData.setLongTickerEntryPrice(longResult.getExecutionPrice().doubleValue());
        pairData.setShortTickerEntryPrice(shortResult.getExecutionPrice().doubleValue());

        // Статус остается TRADING
        pairData.setStatus(TradeStatus.TRADING);

        // Время входа
        pairData.setEntryTime(longResult.getExecutionTime().atZone(java.time.ZoneId.systemDefault()).toEpochSecond() * 1000);
    }

    private void updatePairDataOnClose(PairData pairData, BigDecimal totalPnL, BigDecimal totalFees) {
        // Рассчитываем процентную прибыль
        BigDecimal positionSize = calculatePositionSize(tradingProviderFactory.getCurrentProvider());
        if (positionSize.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal profitPercent = totalPnL.divide(positionSize, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            pairData.setProfitChanges(profitPercent);
        }

        // Устанавливаем статус закрытой
        pairData.setStatus(TradeStatus.CLOSED);

        // Обновляем время обновления
        pairData.setUpdatedTime(System.currentTimeMillis());
    }

    /**
     * Переключение режима торговли
     */
    public boolean switchTradingMode(TradingProviderType providerType) {
        log.info("🔄 Переключение режима торговли на: {}", providerType.getDisplayName());
        return tradingProviderFactory.switchToProvider(providerType);
    }

    /**
     * Получение текущего режима торговли
     */
    public TradingProviderType getCurrentTradingMode() {
        return tradingProviderFactory.getCurrentProviderType();
    }
}