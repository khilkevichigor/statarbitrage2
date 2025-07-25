package com.example.statarbitrage.trading.services;

import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.core.services.SettingsService;
import com.example.statarbitrage.trading.interfaces.TradingProvider;
import com.example.statarbitrage.trading.interfaces.TradingProviderType;
import com.example.statarbitrage.trading.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
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
    public ArbitragePairTradeInfo openArbitragePair(PairData pairData, Settings settings) {
        log.info("==> openArbitragePair: НАЧАЛО для пары {}/{}", pairData.getLongTicker(), pairData.getShortTicker());
        // Синхронизируем всю операцию открытия пары
        synchronized (openPositionLock) {
            try {
                TradingProvider provider = tradingProviderFactory.getCurrentProvider();
                log.info("Текущий торговый провайдер: {}", provider.getClass().getSimpleName());

                // Рассчитываем размер позиций на основе нового портфолио
                BigDecimal positionSize = calculatePositionSize(provider);
                log.info("Рассчитанный размер позиций: {}", positionSize);
                if (positionSize.compareTo(BigDecimal.ZERO) <= 0) {
                    log.warn("⚠️ Недостаточно средств для открытия позиций по паре {}/{}. Размер позиции: {}",
                            pairData.getLongTicker(), pairData.getShortTicker(), positionSize);
                    return ArbitragePairTradeInfo.builder()
                            .success(false)
                            .build();
                }

                // Используем адаптивное распределение для минимизации дисбаланса после lot size корректировки
                BigDecimal[] adaptiveAmounts = calculateAdaptiveAmounts(provider, pairData, positionSize);
                BigDecimal longAmount = adaptiveAmounts[0];
                BigDecimal shortAmount = adaptiveAmounts[1];
                log.info("Адаптивное распределение средств: LONG {} = {}, SHORT {} = {}", pairData.getLongTicker(), longAmount, pairData.getShortTicker(), shortAmount);

                // НОВАЯ ПРОВЕРКА: Проверяем не приведет ли минимальный лот к превышению в 3+ раза
                if (!validatePairForMinimumLotRequirements(provider, pairData, longAmount, shortAmount)) {
                    log.warn("⚠️ ПРОПУСК ПАРЫ: {} / {} не подходит из-за больших минимальных лотов",
                            pairData.getLongTicker(), pairData.getShortTicker());
                    return ArbitragePairTradeInfo.builder()
                            .success(false)
                            .build();
                }

                BigDecimal leverage = BigDecimal.valueOf(settings.getLeverage());
                log.info("Используемое плечо: {}", leverage);

                log.info("🔄 Начинаем открытие арбитражной пары: {}/{}",
                        pairData.getLongTicker(), pairData.getShortTicker());
                log.info("💡 Адаптивное распределение: LONG ${}, SHORT ${}", longAmount, shortAmount);

                // Открываем позиции ПОСЛЕДОВАТЕЛЬНО и СИНХРОННО
                log.info("🟢 Открытие LONG позиции: {} с размером {}", pairData.getLongTicker(), longAmount);
                TradeResult longResult = provider.openLongPosition(pairData.getLongTicker(), longAmount, leverage);
                log.info("Результат открытия LONG позиции: {}", longResult);

                if (!longResult.isSuccess()) {
                    log.error("❌ Не удалось открыть LONG позицию: {}", longResult.getErrorMessage());
                    return ArbitragePairTradeInfo.builder()
                            .success(false)
                            .build();
                }

                log.info("🔴 Открытие SHORT позиции: {} с размером {}", pairData.getShortTicker(), shortAmount);
                TradeResult shortResult = provider.openShortPosition(
                        pairData.getShortTicker(), shortAmount, leverage);
                log.info("Результат открытия SHORT позиции: {}", shortResult);

                if (longResult.isSuccess() && shortResult.isSuccess()) {
                    // Сохраняем связи
                    pairToLongPositionMap.put(pairData.getId(), longResult.getPositionId());
                    pairToShortPositionMap.put(pairData.getId(), shortResult.getPositionId());
                    log.info("Сохранены ID позиций в мапу: LONG ID = {}, SHORT ID = {}", longResult.getPositionId(), shortResult.getPositionId());

                    log.info("✅ Открыта арбитражная пара: {} LONG / {} SHORT",
                            pairData.getLongTicker(), pairData.getShortTicker());

                    return ArbitragePairTradeInfo.builder()
                            .success(true)
                            .longTradeResult(longResult)
                            .shortTradeResult(shortResult)
                            .build();
                } else {
                    // Если одна из позиций не открылась, закрываем успешно открытую
                    log.warn("Одна из позиций не открылась. Производим откат...");
                    if (longResult.isSuccess()) {
                        log.info("Закрываем успешно открытую LONG позицию: {}", longResult.getPositionId());
                        provider.closePosition(longResult.getPositionId());
                    }
                    if (shortResult.isSuccess()) {
                        log.info("Закрываем успешно открытую SHORT позицию: {}", shortResult.getPositionId());
                        provider.closePosition(shortResult.getPositionId());
                    }

                    log.error("❌ Не удалось открыть арбитражную пару {} / {}: Long={}, Short={}",
                            pairData.getLongTicker(), pairData.getShortTicker(),
                            longResult.getErrorMessage(), shortResult.getErrorMessage());
                    return ArbitragePairTradeInfo.builder()
                            .success(false)
                            .build();
                }

            } catch (Exception e) {
                log.error("❌ КРИТИЧЕСКАЯ ОШИБКА при открытии арбитражной пары {} / {}: {}",
                        pairData.getLongTicker(), pairData.getShortTicker(), e.getMessage(), e);
                return ArbitragePairTradeInfo.builder()
                        .success(false)
                        .build();
            } finally {
                log.info("<== openArbitragePair: КОНЕЦ для пары {}/{}", pairData.getLongTicker(), pairData.getShortTicker());
            }
        }
    }

    /**
     * Закрытие пары позиций - СИНХРОННО
     */
    public ArbitragePairTradeInfo closeArbitragePair(PairData pairData) {
        log.info("==> closeArbitragePair: НАЧАЛО для пары {}/{}", pairData.getLongTicker(), pairData.getShortTicker());
        // Синхронизируем всю операцию закрытия пары
        synchronized (openPositionLock) {
            try {
                String longPositionId = pairToLongPositionMap.get(pairData.getId());
                String shortPositionId = pairToShortPositionMap.get(pairData.getId());
                log.info("ID позиций из мапы: LONG ID = {}, SHORT ID = {}", longPositionId, shortPositionId);

                if (longPositionId == null || shortPositionId == null) {
                    log.warn("⚠️ Не найдены ID позиций в мапе для пары {}/{}. Невозможно закрыть.",
                            pairData.getLongTicker(), pairData.getShortTicker());
                    return ArbitragePairTradeInfo.builder()
                            .success(false)
                            .build();
                }

                TradingProvider provider = tradingProviderFactory.getCurrentProvider();
                log.info("Текущий торговый провайдер: {}", provider.getClass().getSimpleName());

                // Закрываем позиции ПОСЛЕДОВАТЕЛЬНО и СИНХРОННО
                log.info("🔄 Начинаем закрытие арбитражной пары: {}/{}",
                        pairData.getLongTicker(), pairData.getShortTicker());

                log.info("🔴 Закрытие LONG позиции: ID = {}", longPositionId);
                TradeResult longCloseResult = provider.closePosition(longPositionId);
                log.info("Результат закрытия LONG позиции: {}", longCloseResult);

                log.info("🟢 Закрытие SHORT позиции: ID = {}", shortPositionId);
                TradeResult shortCloseResult = provider.closePosition(shortPositionId);
                log.info("Результат закрытия SHORT позиции: {}", shortCloseResult);

                boolean success = longCloseResult.isSuccess() && shortCloseResult.isSuccess();

                if (success) {
                    // Рассчитываем общий результат
                    BigDecimal totalPnL = longCloseResult.getPnl().add(shortCloseResult.getPnl());
                    BigDecimal totalFees = longCloseResult.getFees().add(shortCloseResult.getFees());

                    log.info("✅ УСПЕШНО закрыта арбитражная пара: {} / {} | Общий PnL: {} | Общие комиссии: {}",
                            pairData.getLongTicker(), pairData.getShortTicker(), totalPnL, totalFees);

                    return ArbitragePairTradeInfo.builder()
                            .success(true)
                            .longTradeResult(longCloseResult)
                            .shortTradeResult(shortCloseResult)
                            .build();
                } else {
                    log.error("❌ ОШИБКА при закрытии арбитражной пары {} / {}: Long={}, Short={}",
                            pairData.getLongTicker(), pairData.getShortTicker(),
                            longCloseResult.getErrorMessage(), shortCloseResult.getErrorMessage());

                    return ArbitragePairTradeInfo.builder()
                            .success(false)
                            .longTradeResult(longCloseResult)
                            .shortTradeResult(shortCloseResult)
                            .build();
                }

            } catch (Exception e) {
                log.error("❌ КРИТИЧЕСКАЯ ОШИБКА при закрытии арбитражной пары {} / {}: {}",
                        pairData.getLongTicker(), pairData.getShortTicker(), e.getMessage(), e);
                return ArbitragePairTradeInfo.builder()
                        .success(false)
                        .build();
            } finally {
                log.info("<== closeArbitragePair: КОНЕЦ для пары {}/{}", pairData.getLongTicker(), pairData.getShortTicker());
            }
        }
    }

    public void removePairFromLocalStorage(PairData pairData) {
        log.info("Удаляем связи из реестра для пары {} / {}", pairData.getLongTicker(), pairData.getShortTicker());
        pairToLongPositionMap.remove(pairData.getId());
        pairToShortPositionMap.remove(pairData.getId());
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
    public Positioninfo verifyPositionsClosed(PairData pairData) {
        String longPositionId = pairToLongPositionMap.get(pairData.getId());
        String shortPositionId = pairToShortPositionMap.get(pairData.getId());

        if (longPositionId == null || shortPositionId == null) {
            log.debug("📋 Позиции для пары {}/{} не найдены в локальном реестре",
                    pairData.getLongTicker(), pairData.getShortTicker());
            return Positioninfo.builder()
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
                totalPnL = totalPnL.add(longPosition.getUnrealizedPnLUSDT());
            }
            if (shortPosition != null) {
                shortPosition.calculateUnrealizedPnL();
                totalPnL = totalPnL.add(shortPosition.getUnrealizedPnLUSDT());
            }

            // Удаляем из локального реестра если обе позиции закрыты
            removePairFromLocalStorage(pairData);
            log.info("🗑️ Удалены закрытые позиции из реестра для пары {}/{}, финальный PnL: {}",
                    pairData.getLongTicker(), pairData.getShortTicker(), totalPnL);

            return Positioninfo.builder()
                    .positionsClosed(true)
                    .totalPnL(totalPnL)
                    .build();
        }

        log.warn("⚠️ Не все позиции закрыты на бирже: LONG закрыта={}, SHORT закрыта={}",
                longClosed, shortClosed);
        return Positioninfo.builder()
                .positionsClosed(false)
                .totalPnL(BigDecimal.ZERO)
                .build();
    }

    /**
     * Получение актуальной информации по открытым позициям для обновления changes
     */
    public Positioninfo getOpenPositionsInfo(PairData pairData) {
        String longPositionId = pairToLongPositionMap.get(pairData.getId());
        String shortPositionId = pairToShortPositionMap.get(pairData.getId());

        if (longPositionId == null || shortPositionId == null) {
            log.debug("📋 Позиции для пары {}/{} не найдены в локальном реестре",
                    pairData.getLongTicker(), pairData.getShortTicker());
            return Positioninfo.builder()
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
            totalPnL = longPosition.getUnrealizedPnLUSDT().add(shortPosition.getUnrealizedPnLUSDT());

            log.debug("📊 Актуальный PnL для открытых позиций {}/{}: {}",
                    pairData.getLongTicker(), pairData.getShortTicker(), totalPnL);

            return Positioninfo.builder()
                    .positionsClosed(false)
                    .longPosition(longPosition)
                    .shortPosition(shortPosition)
                    .totalPnL(totalPnL)
                    .build();
        }

        // Если одна из позиций закрыта или не найдена - это проблема
        log.warn("⚠️ Не все позиции открыты на бирже: LONG открыта={}, SHORT открыта={}",
                longOpen, shortOpen);
        return Positioninfo.builder()
                .positionsClosed(true)
                .longPosition(longPosition)
                .shortPosition(shortPosition)
                .totalPnL(BigDecimal.ZERO)
                .build();
    }

    /**
     * Получение актуальной информации по позициям для пары
     */
    public Positioninfo getPositionInfo(PairData pairData) {
        log.info("ℹ️ Запрос информации о позициях для пары {} / {}", pairData.getLongTicker(), pairData.getShortTicker());
        String longPositionId = pairToLongPositionMap.get(pairData.getId());
        String shortPositionId = pairToShortPositionMap.get(pairData.getId());

        if (longPositionId == null || shortPositionId == null) {
            log.debug("📋 Позиции для пары {} / {} не найдены в локальном реестре",
                    pairData.getLongTicker(), pairData.getShortTicker());
            return Positioninfo.builder().build();
        }
        log.debug("Найдены ID позиций: LONG={}, SHORT={}", longPositionId, shortPositionId);

        TradingProvider provider = tradingProviderFactory.getCurrentProvider();
        log.debug("Текущий провайдер: {}", provider.getClass().getSimpleName());

        Position longPosition = provider.getPosition(longPositionId);
        Position shortPosition = provider.getPosition(shortPositionId);
        log.debug("Получены позиции: LONG={}, SHORT={}", longPosition, shortPosition);

        if (longPosition == null || shortPosition == null) {
            log.error("❌ Ошибка расчета changes - позиции равны null для пары {} / {}", pairData.getLongTicker(), pairData.getShortTicker());
            return Positioninfo.builder().build();
        }

        boolean longClosed = (longPosition.getStatus() == PositionStatus.CLOSED);
        boolean shortClosed = (shortPosition.getStatus() == PositionStatus.CLOSED);
        log.debug("Статус позиций: LONG закрыта={}, SHORT закрыта={}", longClosed, shortClosed);

        if (longClosed && shortClosed) {
            log.info("✅ Обе позиции для пары {} / {} уже закрыты.", pairData.getLongTicker(), pairData.getShortTicker());
            // Рассчитываем финальный PnL если позиции закрыты
            BigDecimal totalPnL = BigDecimal.ZERO;
            if (longPosition != null) {
                longPosition.calculateUnrealizedPnL();
                totalPnL = totalPnL.add(longPosition.getUnrealizedPnLUSDT());
                log.debug("Финальный PnL для LONG позиции {}: {}", longPositionId, longPosition.getUnrealizedPnLUSDT());
            }
            if (shortPosition != null) {
                shortPosition.calculateUnrealizedPnL();
                totalPnL = totalPnL.add(shortPosition.getUnrealizedPnLUSDT());
                log.debug("Финальный PnL для SHORT позиции {}: {}", shortPositionId, shortPosition.getUnrealizedPnLUSDT());
            }

            log.info("🗑️ Позиции уже закрыты для пары {} / {}, финальный PnL: {}",
                    pairData.getLongTicker(), pairData.getShortTicker(), totalPnL);

            return Positioninfo.builder()
                    .positionsClosed(true)
                    .longPosition(longPosition)
                    .shortPosition(shortPosition)
                    .totalPnL(totalPnL)
                    .build();
        }

        log.debug("Позиции еще открыты, обновляем цены...");
        // Обновляем актуальную информацию с биржи
        provider.updatePositionPrices(List.of(pairData.getLongTicker(), pairData.getShortTicker()));
        log.debug("Цены обновлены.");

        return Positioninfo.builder()
                .positionsClosed(false)
                .longPosition(longPosition)
                .shortPosition(shortPosition)
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

        return longPosition.getUnrealizedPnLUSDT().add(shortPosition.getUnrealizedPnLUSDT());
    }

    private BigDecimal calculatePositionSize(TradingProvider provider) {
        Portfolio portfolio = provider.getPortfolio();
        if (portfolio == null) {
            return BigDecimal.ZERO;
        }

        // Используем фиксированный размер позиции из настроек
        Settings settings = settingsService.getSettings();
        BigDecimal totalAllocation = BigDecimal.valueOf(settings.getMaxShortMarginSize()).add(BigDecimal.valueOf(settings.getMaxLongMarginSize()));

        log.info("💰 Расчет размера позиций: общая аллокация {}$ (без учета плеча)",
                totalAllocation);

        // Не больше доступного баланса
        BigDecimal resultSize = totalAllocation.min(portfolio.getAvailableBalance());

        log.info("💰 Итоговый размер позиций: {}$ (ограничен балансом: {}$)", resultSize, portfolio.getAvailableBalance());
        return resultSize;
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

    /**
     * Получение размера позиции для данной пары
     * Используется для правильного расчета процентного профита
     */
    public BigDecimal getPositionSize(PairData pairData) {
        try {
            TradingProvider provider = tradingProviderFactory.getCurrentProvider();

            // Получаем ID позиций для данной пары из внутренних карт
            String longPositionId = pairToLongPositionMap.get(pairData.getId());
            String shortPositionId = pairToShortPositionMap.get(pairData.getId());

            if (longPositionId == null || shortPositionId == null) {
                return null; // Позиции не найдены
            }

            // Получаем позиции
            Position longPosition = provider.getPosition(longPositionId);
            Position shortPosition = provider.getPosition(shortPositionId);

            if (longPosition != null && shortPosition != null &&
                    longPosition.getAllocatedAmount() != null && shortPosition.getAllocatedAmount() != null) {

                // Возвращаем сумму выделенных сумм для обеих позиций
                BigDecimal totalAllocated = longPosition.getAllocatedAmount().add(shortPosition.getAllocatedAmount());

                log.debug("📏 Размер позиции для {}/{}: {} USDT (LONG: {}, SHORT: {})",
                        pairData.getLongTicker(), pairData.getShortTicker(), totalAllocated,
                        longPosition.getAllocatedAmount(), shortPosition.getAllocatedAmount());

                return totalAllocated;
            }

        } catch (Exception e) {
            log.debug("⚠️ Не удалось получить размер позиции для {}/{}: {}",
                    pairData.getLongTicker(), pairData.getShortTicker(), e.getMessage());
        }

        return null; // Fallback в PairDataService будет использовать примерный расчет
    }

    /**
     * Проверка пары на соответствие требованиям минимального лота
     * Возвращает false если минимальный лот для любой позиции превышает желаемую сумму в 3+ раза
     */
    private boolean validatePairForMinimumLotRequirements(TradingProvider provider, PairData pairData, BigDecimal longAmount, BigDecimal shortAmount) {
        try {
            // Получаем текущие цены
            BigDecimal longPrice = provider.getCurrentPrice(pairData.getLongTicker());
            BigDecimal shortPrice = provider.getCurrentPrice(pairData.getShortTicker());

            if (longPrice == null || shortPrice == null ||
                    longPrice.compareTo(BigDecimal.ZERO) <= 0 || shortPrice.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("⚠️ Не удалось получить цены для проверки минимальных лотов {}/{}",
                        pairData.getLongTicker(), pairData.getShortTicker());
                return true; // Позволяем торговлю если не удалось получить цены
            }

            // Проверяем LONG позицию
            if (!validatePositionForMinimumLot(pairData.getLongTicker(), longAmount, longPrice)) {
                return false;
            }

            // Проверяем SHORT позицию
            if (!validatePositionForMinimumLot(pairData.getShortTicker(), shortAmount, shortPrice)) {
                return false;
            }

            log.info("✅ Пара {}/{} прошла проверку минимальных лотов",
                    pairData.getLongTicker(), pairData.getShortTicker());
            return true;

        } catch (Exception e) {
            log.error("❌ Ошибка при проверке минимальных лотов для {}/{}: {}",
                    pairData.getLongTicker(), pairData.getShortTicker(), e.getMessage());
            return true; // Позволяем торговлю при ошибке проверки
        }
    }

    /**
     * Проверка конкретной позиции на соответствие требованиям минимального лота
     */
    private boolean validatePositionForMinimumLot(String symbol, BigDecimal desiredAmount, BigDecimal currentPrice) {
        try {
            // Рассчитываем желаемый размер позиции
            BigDecimal desiredSize = desiredAmount.divide(currentPrice, 8, RoundingMode.HALF_UP);

            // Симулируем корректировку минимального лота (упрощенно)
            BigDecimal adjustedSize = desiredSize.setScale(0, RoundingMode.DOWN);
            if (adjustedSize.compareTo(BigDecimal.ONE) < 0) {
                adjustedSize = BigDecimal.ONE; // Минимальный лот = 1 единица
            }

            // Рассчитываем итоговую стоимость после корректировки
            BigDecimal adjustedAmount = adjustedSize.multiply(currentPrice);
            BigDecimal excessRatio = adjustedAmount.divide(desiredAmount, 4, RoundingMode.HALF_UP);

            // Если превышение больше 3x - блокируем пару
            if (excessRatio.compareTo(BigDecimal.valueOf(3.0)) > 0) {
                log.warn("❌ БЛОКИРОВКА: {} минимальный лот приводит к позиции ${{}} вместо желаемых ${{}} (превышение в {} раз)",
                        symbol, adjustedAmount, desiredAmount, excessRatio);
                return false;
            }

            log.debug("✅ {} прошел проверку: желаемая сумма=${{}}, итоговая сумма=${{}}, соотношение={}",
                    symbol, desiredAmount, adjustedAmount, excessRatio);
            return true;

        } catch (Exception e) {
            log.error("❌ Ошибка при проверке минимального лота для {}: {}", symbol, e.getMessage());
            return true; // Позволяем торговлю при ошибке
        }
    }
}