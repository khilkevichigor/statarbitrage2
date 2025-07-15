package com.example.statarbitrage.trading.services;

import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.common.model.TradeStatus;
import com.example.statarbitrage.core.services.SettingsService;
import com.example.statarbitrage.trading.interfaces.TradingProvider;
import com.example.statarbitrage.trading.interfaces.TradingProviderType;
import com.example.statarbitrage.trading.model.*;
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
    private final SettingsService settingsService;

    public TradingIntegrationService(TradingProviderFactory tradingProviderFactory, SettingsService settingsService) {
        this.tradingProviderFactory = tradingProviderFactory;
        this.settingsService = settingsService;
    }

    /**
     * Открытие пары позиций для статарбитража - СИНХРОННО
     */
    public OpenArbitragePairResult openArbitragePair(PairData pairData) {
        // Синхронизируем всю операцию открытия пары
        synchronized (openPositionLock) {
            try {
                TradingProvider provider = tradingProviderFactory.getCurrentProvider();

                // Рассчитываем размер позиций на основе нового портфолио
                BigDecimal positionSize = calculatePositionSize(provider);
                if (positionSize.compareTo(BigDecimal.ZERO) <= 0) {
                    log.warn("❌ Недостаточно средств для открытия позиций по паре {}/{}",
                            pairData.getLongTicker(), pairData.getShortTicker());
                    return OpenArbitragePairResult.builder()
                            .success(false)
                            .build();
                }

                // Используем адаптивное распределение для минимизации дисбаланса после lot size корректировки
                BigDecimal[] adaptiveAmounts = calculateAdaptiveAmounts(provider, pairData, positionSize);
                BigDecimal longAmount = adaptiveAmounts[0];
                BigDecimal shortAmount = adaptiveAmounts[1];

                Settings settings = settingsService.getSettings();
                BigDecimal leverage = BigDecimal.valueOf(settings.getLeverage());

                log.info("🔄 Начинаем открытие арбитражной пары: {}/{}",
                        pairData.getLongTicker(), pairData.getShortTicker());
                log.info("💡 Адаптивное распределение: LONG ${}, SHORT ${}", longAmount, shortAmount);

                // Открываем позиции ПОСЛЕДОВАТЕЛЬНО и СИНХРОННО
                log.info("🔵 Открытие LONG позиции: {} с размером {}", pairData.getLongTicker(), longAmount);
                TradeResult longResult = provider.openLongPosition(pairData.getLongTicker(), longAmount, leverage);

                if (!longResult.isSuccess()) {
                    log.error("❌ Не удалось открыть LONG позицию: {}", longResult.getErrorMessage());
                    return OpenArbitragePairResult.builder()
                            .success(false)
                            .build();
                }

                log.info("🔴 Открытие SHORT позиции: {} с размером {}", pairData.getShortTicker(), shortAmount);
                TradeResult shortResult = provider.openShortPosition(
                        pairData.getShortTicker(), shortAmount, leverage);

                if (longResult.isSuccess() && shortResult.isSuccess()) {
                    // Сохраняем связи
                    pairToLongPositionMap.put(pairData.getId(), longResult.getPositionId());
                    pairToShortPositionMap.put(pairData.getId(), shortResult.getPositionId());

                    log.info("✅ Открыта арбитражная пара: {} LONG / {} SHORT",
                            pairData.getLongTicker(), pairData.getShortTicker());

                    return OpenArbitragePairResult.builder()
                            .success(true)
                            .longTradeResult(longResult)
                            .shortTradeResult(shortResult)
                            .build();
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
                    return OpenArbitragePairResult.builder()
                            .success(false)
                            .build();
                }

            } catch (Exception e) {
                log.error("❌ Ошибка при открытии арбитражной пары {}/{}: {}",
                        pairData.getLongTicker(), pairData.getShortTicker(), e.getMessage());
                return OpenArbitragePairResult.builder()
                        .success(false)
                        .build();
            }
        }
    }

    /**
     * Закрытие пары позиций - СИНХРОННО
     */
    public CloseArbitragePairResult closeArbitragePair(PairData pairData) {
        // Синхронизируем всю операцию закрытия пары
        synchronized (openPositionLock) {
            try {
                String longPositionId = pairToLongPositionMap.get(pairData.getId());
                String shortPositionId = pairToShortPositionMap.get(pairData.getId());

                if (longPositionId == null || shortPositionId == null) {
                    log.warn("⚠️ Не найдены позиции для пары {}/{}",
                            pairData.getLongTicker(), pairData.getShortTicker());
                    return CloseArbitragePairResult.builder()
                            .success(false)
                            .build();
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

                return CloseArbitragePairResult.builder()
                        .success(true)
                        .longTradeResult(longCloseResult)
                        .shortTradeResult(shortCloseResult)
                        .build();

            } catch (Exception e) {
                log.error("❌ Ошибка при закрытии арбитражной пары {}/{}: {}",
                        pairData.getLongTicker(), pairData.getShortTicker(), e.getMessage());
                return CloseArbitragePairResult.builder()
                        .success(false)
                        .build();
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
     * Проверка наличия открытых позиций для пары
     */
    public boolean hasOpenPositions(PairData pairData) {
        String longPositionId = pairToLongPositionMap.get(pairData.getId());
        String shortPositionId = pairToShortPositionMap.get(pairData.getId());

        if (longPositionId == null || shortPositionId == null) {
            return false;
        }

        TradingProvider provider = tradingProviderFactory.getCurrentProvider();
        Position longPosition = provider.getPosition(longPositionId);
        Position shortPosition = provider.getPosition(shortPositionId);

        // Если хотя бы одна позиция не найдена, считаем что пара не имеет открытых позиций
        return longPosition != null && shortPosition != null;
    }

    /**
     * Проверка что позиции действительно закрыты на бирже с получением PnL
     */
    public PositionVerificationResult verifyPositionsClosed(PairData pairData) {
        String longPositionId = pairToLongPositionMap.get(pairData.getId());
        String shortPositionId = pairToShortPositionMap.get(pairData.getId());

        if (longPositionId == null || shortPositionId == null) {
            log.debug("📋 Позиции для пары {}/{} не найдены в локальном реестре",
                    pairData.getLongTicker(), pairData.getShortTicker());
            return PositionVerificationResult.builder()
                    .positionsClosed(true)
                    .totalPnL(BigDecimal.ZERO)
                    .build();
        }

        TradingProvider provider = tradingProviderFactory.getCurrentProvider();

        // Обновляем актуальную информацию с биржи
        provider.updatePositionPrices();

        Position longPosition = provider.getPosition(longPositionId);
        Position shortPosition = provider.getPosition(shortPositionId);

        boolean longClosed = (longPosition == null || longPosition.getStatus() == PositionStatus.CLOSED);
        boolean shortClosed = (shortPosition == null || shortPosition.getStatus() == PositionStatus.CLOSED);

        if (longClosed && shortClosed) {
            // Рассчитываем финальный PnL если позиции закрыты
            BigDecimal totalPnL = BigDecimal.ZERO;
            if (longPosition != null) {
                longPosition.calculateUnrealizedPnL();
                totalPnL = totalPnL.add(longPosition.getUnrealizedPnL());
            }
            if (shortPosition != null) {
                shortPosition.calculateUnrealizedPnL();
                totalPnL = totalPnL.add(shortPosition.getUnrealizedPnL());
            }

            // Удаляем из локального реестра если обе позиции закрыты
            pairToLongPositionMap.remove(pairData.getId());
            pairToShortPositionMap.remove(pairData.getId());
            log.info("🗑️ Удалены закрытые позиции из реестра для пары {}/{}, финальный PnL: {}",
                    pairData.getLongTicker(), pairData.getShortTicker(), totalPnL);

            return PositionVerificationResult.builder()
                    .positionsClosed(true)
                    .totalPnL(totalPnL)
                    .build();
        }

        log.warn("⚠️ Не все позиции закрыты на бирже: LONG закрыта={}, SHORT закрыта={}",
                longClosed, shortClosed);
        return PositionVerificationResult.builder()
                .positionsClosed(false)
                .totalPnL(BigDecimal.ZERO)
                .build();
    }

    /**
     * Получение актуальной информации по открытым позициям для обновления changes
     */
    public PositionVerificationResult getOpenPositionsInfo(PairData pairData) {
        String longPositionId = pairToLongPositionMap.get(pairData.getId());
        String shortPositionId = pairToShortPositionMap.get(pairData.getId());

        if (longPositionId == null || shortPositionId == null) {
            log.debug("📋 Позиции для пары {}/{} не найдены в локальном реестре",
                    pairData.getLongTicker(), pairData.getShortTicker());
            return PositionVerificationResult.builder()
                    .positionsClosed(true)
                    .totalPnL(BigDecimal.ZERO)
                    .build();
        }

        TradingProvider provider = tradingProviderFactory.getCurrentProvider();

        // Обновляем актуальную информацию с биржи
        provider.updatePositionPrices();

        Position longPosition = provider.getPosition(longPositionId);
        Position shortPosition = provider.getPosition(shortPositionId);

        boolean longOpen = (longPosition != null && longPosition.getStatus() == PositionStatus.OPEN);
        boolean shortOpen = (shortPosition != null && shortPosition.getStatus() == PositionStatus.OPEN);

        if (longOpen && shortOpen) {
            // Рассчитываем актуальный PnL для открытых позиций
            BigDecimal totalPnL = BigDecimal.ZERO;
            longPosition.calculateUnrealizedPnL();
            shortPosition.calculateUnrealizedPnL();
            totalPnL = longPosition.getUnrealizedPnL().add(shortPosition.getUnrealizedPnL());

            log.debug("📊 Актуальный PnL для открытых позиций {}/{}: {}",
                    pairData.getLongTicker(), pairData.getShortTicker(), totalPnL);

            return PositionVerificationResult.builder()
                    .positionsClosed(false)
                    .totalPnL(totalPnL)
                    .build();
        }

        // Если одна из позиций закрыта или не найдена - это проблема
        log.warn("⚠️ Не все позиции открыты на бирже: LONG открыта={}, SHORT открыта={}",
                longOpen, shortOpen);
        return PositionVerificationResult.builder()
                .positionsClosed(true)
                .totalPnL(BigDecimal.ZERO)
                .build();
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

        // Используем фиксированный размер позиции из настроек
        Settings settings = settingsService.getSettings();
        BigDecimal fixedPositionSize = BigDecimal.valueOf(settings.getMaxPositionSize());

        // Не больше доступного баланса
        return fixedPositionSize.min(portfolio.getAvailableBalance());
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
            pairData.setProfitChanges(profitPercent); //todo profitPercent=0 when CLOSED
        }

        // Устанавливаем статус закрытой
        pairData.setStatus(TradeStatus.CLOSED);

        // Обновляем время обновления
        pairData.setUpdatedTime(System.currentTimeMillis());
    }

    /**
     * Адаптивный расчет сумм для минимизации дисбаланса после lot size корректировки
     */
    private BigDecimal[] calculateAdaptiveAmounts(TradingProvider provider, PairData pairData, BigDecimal totalAmount) {
        try {
            // Получаем текущие цены для расчета lot size
            BigDecimal longPrice = provider.getCurrentPrice(pairData.getLongTicker());
            BigDecimal shortPrice = provider.getCurrentPrice(pairData.getShortTicker());

            if (longPrice == null || shortPrice == null ||
                    longPrice.compareTo(BigDecimal.ZERO) <= 0 || shortPrice.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("⚠️ Не удалось получить цены для адаптивного расчета, используем 50/50");
                BigDecimal half = totalAmount.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                return new BigDecimal[]{half, half};
            }

            // Пытаемся найти оптимальное распределение
            BigDecimal bestLongAmount = null;
            BigDecimal bestShortAmount = null;
            BigDecimal minDifference = BigDecimal.valueOf(Double.MAX_VALUE);

            // Пробуем разные распределения от 40% до 60% для long позиции
            for (int longPercent = 40; longPercent <= 60; longPercent++) {
                BigDecimal longAmount = totalAmount.multiply(BigDecimal.valueOf(longPercent))
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                BigDecimal shortAmount = totalAmount.subtract(longAmount);

                // Симулируем корректировку lot size
                BigDecimal longSizeBeforeAdjust = longAmount.divide(longPrice, 8, RoundingMode.HALF_UP);
                BigDecimal shortSizeBeforeAdjust = shortAmount.divide(shortPrice, 8, RoundingMode.HALF_UP);

                // Применяем примерную корректировку (округление до целых)
                BigDecimal adjustedLongSize = longSizeBeforeAdjust.setScale(0, RoundingMode.DOWN);
                BigDecimal adjustedShortSize = shortSizeBeforeAdjust.setScale(0, RoundingMode.DOWN);

                // Рассчитываем итоговые суммы после корректировки
                BigDecimal adjustedLongAmount = adjustedLongSize.multiply(longPrice);
                BigDecimal adjustedShortAmount = adjustedShortSize.multiply(shortPrice);

                // Считаем разность
                BigDecimal difference = adjustedLongAmount.subtract(adjustedShortAmount).abs();

                if (difference.compareTo(minDifference) < 0) {
                    minDifference = difference;
                    bestLongAmount = longAmount;
                    bestShortAmount = shortAmount;
                }
            }

            if (bestLongAmount != null && bestShortAmount != null) {
                log.info("🎯 Найдено оптимальное распределение: LONG ${}, SHORT ${} (ожидаемая разность после корректировки: ${})",
                        bestLongAmount, bestShortAmount, minDifference);
                return new BigDecimal[]{bestLongAmount, bestShortAmount};
            }

        } catch (Exception e) {
            log.warn("⚠️ Ошибка при адаптивном расчете: {}, используем 50/50", e.getMessage());
        }

        // Fallback к равному распределению
        BigDecimal half = totalAmount.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        return new BigDecimal[]{half, half};
    }

    /**
     * Переключение режима торговли
     */
    public boolean switchTradingMode(TradingProviderType providerType) {
        log.info("🔄 Переключение режима торговли на: {}", providerType.getDisplayName());
        return tradingProviderFactory.switchToProvider(providerType);
    }

    /**
     * Переключение режима торговли с детальной информацией
     */
    public TradingProviderSwitchResult switchTradingModeWithDetails(TradingProviderType providerType) {
        log.info("🔄 Переключение режима торговли на: {}", providerType.getDisplayName());
        return tradingProviderFactory.switchToProviderWithDetails(providerType);
    }

    /**
     * Получение текущего режима торговли
     */
    public TradingProviderType getCurrentTradingMode() {
        return tradingProviderFactory.getCurrentProviderType();
    }
}