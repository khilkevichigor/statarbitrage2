package com.example.statarbitrage.trading.services;

import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.core.services.PortfolioService;
import com.example.statarbitrage.trading.interfaces.TradingProvider;
import com.example.statarbitrage.trading.interfaces.TradingProviderType;
import com.example.statarbitrage.trading.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис для интеграции новой торговой системы с существующей системой статарбитража
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradingIntegrationServiceImpl implements TradingIntegrationService {

    private final TradingProviderFactory tradingProviderFactory;

    // Синхронизация открытия позиций для избежания конфликтов SQLite
    private final Object openPositionLock = new Object();

    // Хранилище связей между PairData и торговыми позициями
    private final ConcurrentHashMap<Long, String> pairToLongPositionMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> pairToShortPositionMap = new ConcurrentHashMap<>();
    private final PositionSizeService positionSizeService;
    private final AdaptiveAmountService adaptiveAmountService;
    private final ValidateMinimumLotRequirementsService validateMinimumLotRequirementsService;
    private final PortfolioService portfolioService;

    /**
     * Открытие пары позиций для статарбитража - СИНХРОННО
     */
    @Override
    public ArbitragePairTradeInfo openArbitragePair(PairData pairData, Settings settings) {
        log.debug("=== Начало открытия арбитражной пары: {}", pairData.getPairName());

        synchronized (openPositionLock) {
            try {
                TradingProvider provider = tradingProviderFactory.getCurrentProvider();
                log.debug("Текущий торговый провайдер: {}", provider.getClass().getSimpleName());

                BigDecimal positionSize = positionSizeService.calculatePositionSize(provider, settings);
                log.debug("Вычислен размер позиции: {}", positionSize);

                if (isInvalidPositionSize(positionSize, pairData)) {
                    log.warn("Недостаточный размер позиции для пары {}: {}", pairData.getPairName(), positionSize);
                    return buildFailure();
                }

                BigDecimal[] adaptiveAmounts = adaptiveAmountService.calculate(provider, pairData, positionSize);
                BigDecimal longAmount = adaptiveAmounts[0];
                BigDecimal shortAmount = adaptiveAmounts[1];
                log.debug("Адаптивное распределение средств: ЛОНГ {} = {}, ШОРТ {} = {}",
                        pairData.getLongTicker(), longAmount, pairData.getShortTicker(), shortAmount);

                if (!validateMinimumLotRequirementsService.validate(provider, pairData, longAmount, shortAmount)) {
                    log.warn("Пропуск пары {}: минимальные лоты не соответствуют требованиям", pairData.getPairName());
                    return buildFailure();
                }

                BigDecimal leverage = BigDecimal.valueOf(settings.getLeverage());
                log.debug("Используемое кредитное плечо: {}", leverage);

                BigDecimal balanceUSDT = portfolioService.getBalanceUSDT();//баланс до

                TradeResult longResult = openLong(provider, pairData, longAmount, leverage);
                if (!longResult.isSuccess()) {
                    log.error("Ошибка открытия ЛОНГ позиции для {}: {}", pairData.getLongTicker(), longResult.getErrorMessage());
                    return buildFailure();
                }

                TradeResult shortResult = openShort(provider, pairData, shortAmount, leverage);
                if (shortResult.isSuccess()) {
                    savePositionIds(pairData, longResult, shortResult);
                    log.debug("Успешно открыты позиции для пары {}: ЛОНГ ID = {}, ШОРТ ID = {}",
                            pairData.getPairName(), longResult.getPositionId(), shortResult.getPositionId());
                    return buildSuccess(longResult, shortResult, balanceUSDT, pairData);
                } else {
                    log.error("Ошибка открытия ШОРТ позиции для {}: {}", pairData.getShortTicker(), shortResult.getErrorMessage());
                    rollbackIfNecessary(provider, longResult, shortResult);
                    return buildFailure();
                }

            } catch (Exception e) {
                log.error("Критическая ошибка при открытии арбитражной пары {}: {}", pairData.getPairName(), e.getMessage(), e);
                return buildFailure();
            } finally {
                log.debug("=== Конец открытия арбитражной пары: {}", pairData.getPairName());
            }
        }
    }

    /**
     * Закрытие пары позиций - СИНХРОННО
     */
    @Override
    public ArbitragePairTradeInfo closeArbitragePair(PairData pairData) {
        log.info("=== Начало закрытия арбитражной пары: {}", pairData.getPairName());

        synchronized (openPositionLock) {
            try {
                String longPositionId = pairToLongPositionMap.get(pairData.getId());
                String shortPositionId = pairToShortPositionMap.get(pairData.getId());

                if (positionsMissingInMap(longPositionId, shortPositionId, pairData)) {
                    log.warn("Не найдены ID позиций для пары {}. Закрытие невозможно.", pairData.getPairName());
                    return buildFailure();
                }

                TradingProvider provider = tradingProviderFactory.getCurrentProvider();
                log.info("Текущий торговый провайдер: {}", provider.getClass().getSimpleName());

                log.info("Начинаем закрытие позиций для пары {}", pairData.getPairName());

                TradeResult longResult = closePosition(provider, longPositionId, "LONG");
                TradeResult shortResult = closePosition(provider, shortPositionId, "SHORT");

                if (longResult.isSuccess() && shortResult.isSuccess()) {
                    logSuccess(pairData, longResult, shortResult);
                } else {
                    logFailure(pairData, longResult, shortResult);
                }

                return ArbitragePairTradeInfo.builder()
                        .success(longResult.isSuccess() && shortResult.isSuccess())
                        .longTradeResult(longResult)
                        .shortTradeResult(shortResult)
                        .build();

            } catch (Exception e) {
                log.error("Критическая ошибка при закрытии арбитражной пары {}: {}", pairData.getPairName(), e.getMessage(), e);
                return buildFailure();
            } finally {
                log.info("=== Конец закрытия арбитражной пары: {}", pairData.getPairName());
            }
        }
    }

    /**
     * Проверка что позиции действительно закрыты на бирже с получением PnL
     */
    @Override
    public Positioninfo verifyPositionsClosed(PairData pairData) {
        String longPositionId = pairToLongPositionMap.get(pairData.getId());
        String shortPositionId = pairToShortPositionMap.get(pairData.getId());

        if (positionsMissingInMap(longPositionId, shortPositionId, pairData)) {
            log.warn("Не найдены ID позиций для пары {}. Предполагаем, что позиции закрыты.", pairData.getPairName());
            return buildClosedPositionInfo(BigDecimal.ZERO, BigDecimal.ZERO);
        }

        TradingProvider provider = tradingProviderFactory.getCurrentProvider();
        provider.updatePositionPrices();

        Position longPosition = provider.getPosition(longPositionId);
        Position shortPosition = provider.getPosition(shortPositionId);

        boolean longClosed = isClosed(longPosition);
        boolean shortClosed = isClosed(shortPosition);

        if (!areBothClosed(longPosition, shortPosition)) {
            BigDecimal finalPnlUSDT = calculateTotalPnlUSDT(longPosition, shortPosition);
            BigDecimal finalPnlPercent = calculateTotalPnlPercent(longPosition, shortPosition);
            removePairFromLocalStorage(pairData);
            log.debug("Удалены закрытые позиции из реестра для пары {}. Итоговый PnL: {} USDT ({} %)", pairData.getPairName(), finalPnlUSDT, finalPnlPercent);

            return buildClosedPositionInfo(finalPnlUSDT, finalPnlPercent);
        }

        log.warn("Не все позиции закрыты для пары {}: ЛОНГ закрыта={}, ШОРТ закрыта={}", pairData.getPairName(), longClosed, shortClosed);
        return buildOpenPositionInfo();
    }

    /**
     * Получение актуальной информации по открытым позициям для обновления changes
     */
    @Override
    public Positioninfo getOpenPositionsInfo(PairData pairData) {
        String longPositionId = pairToLongPositionMap.get(pairData.getId());
        String shortPositionId = pairToShortPositionMap.get(pairData.getId());

        if (positionsMissingInMap(longPositionId, shortPositionId, pairData)) {
            log.warn("Не найдены ID позиций для пары {}. Предполагаем, что позиции закрыты.", pairData.getPairName());
            return buildClosedPositionInfo();
        }

        TradingProvider provider = tradingProviderFactory.getCurrentProvider();
        provider.updatePositionPrices();

        Position longPosition = provider.getPosition(longPositionId);
        Position shortPosition = provider.getPosition(shortPositionId);

        if (areBothOpen(longPosition, shortPosition)) {
            calculateUnrealizedPnL(longPosition, shortPosition);
            BigDecimal totalPnlUSDT = longPosition.getUnrealizedPnLUSDT().add(shortPosition.getUnrealizedPnLUSDT());
            BigDecimal totalPnlPercent = longPosition.getUnrealizedPnLPercent().add(shortPosition.getUnrealizedPnLPercent());

            log.info("Текущий PnL для открытых позиций пары {}: {} USDT ({} %)", pairData.getPairName(), totalPnlUSDT, totalPnlPercent);

            return buildOpenPositionInfo(longPosition, shortPosition, totalPnlUSDT, totalPnlPercent);
        }

        log.warn("Не все позиции открыты для пары {}: ЛОНГ открыта={}, ШОРТ открыта={}",
                pairData.getPairName(), isOpen(longPosition), isOpen(shortPosition));

        return buildPartiallyClosedInfo(longPosition, shortPosition);
    }

    /**
     * Получение актуальной информации по позициям для пары
     */
    @Override
    public Positioninfo getPositionInfo(PairData pairData) {
        log.debug("Запрос информации о позициях для пары {}", pairData.getPairName());

        String longPositionId = pairToLongPositionMap.get(pairData.getId());
        String shortPositionId = pairToShortPositionMap.get(pairData.getId());

        if (positionsMissingInMap(longPositionId, shortPositionId, pairData)) {
            log.warn("Не найдены ID позиций для пары {}", pairData.getPairName());
            return Positioninfo.builder().build();
        }

        TradingProvider provider = tradingProviderFactory.getCurrentProvider();
        log.debug("Текущий торговый провайдер: {}", provider.getClass().getSimpleName());

        Position longPosition = provider.getPosition(longPositionId);
        Position shortPosition = provider.getPosition(shortPositionId);

        if (positionsAreNull(longPosition, shortPosition, pairData)) {
            log.error("Позиции равны null для пары {}", pairData.getPairName());
            return Positioninfo.builder().build();
        }

        boolean bothClosed = isClosed(longPosition) && isClosed(shortPosition);
        log.debug("Статус позиций для пары {}: ЛОНГ закрыта={}, ШОРТ закрыта={}", pairData.getPairName(), isClosed(longPosition), isClosed(shortPosition));

        if (bothClosed) {
            log.debug("Обе позиции для пары {} уже закрыты.", pairData.getPairName());
            return buildPositionInfo(true, longPosition, shortPosition);
        }

        log.debug("Позиции для пары {} еще открыты, обновляем цены...", pairData.getPairName());
        provider.updatePositionPrices(List.of(pairData.getLongTicker(), pairData.getShortTicker()));
        log.debug("Цены для пары {} обновлены.", pairData.getPairName());

        return buildPositionInfo(false, longPosition, shortPosition);
    }

    @Override
    public void removePairFromLocalStorage(PairData pairData) {
        log.debug("Удаляем сохранённые ID позиций из локального реестра для пары {}", pairData.getPairName());
        pairToLongPositionMap.remove(pairData.getId());
        pairToShortPositionMap.remove(pairData.getId());
    }

    /**
     * Обновление цен и PnL для всех активных пар - СИНХРОННО
     */
    @Override
    public void updateAllPositions() {
        TradingProvider provider = tradingProviderFactory.getCurrentProvider();
        try {
            log.debug("🔄 Обновление цен по всем открытым позициям...");
            provider.updatePositionPrices(); // Полностью синхронно
            log.debug("✅ Обновление цен завершено успешно.");
        } catch (Exception e) {
            log.error("❌ Ошибка при обновлении цен по позициям: {}", e.getMessage(), e);
        }
    }

    /**
     * Получение информации о портфолио
     */
    @Override
    public Portfolio getPortfolioInfo() {
        TradingProvider provider = tradingProviderFactory.getCurrentProvider();
        return provider.getPortfolio();
    }

    /**
     * Проверка, достаточно ли средств для новой пары
     */
    @Override
    public boolean canOpenNewPair(Settings settings) {
        TradingProvider provider = tradingProviderFactory.getCurrentProvider();
        BigDecimal requiredAmount = positionSizeService.calculatePositionSize(provider, settings);
        return provider.hasAvailableBalance(requiredAmount);
    }

    /**
     * Переключение режима торговли с детальной информацией
     */
    @Override
    public TradingProviderSwitchResult switchTradingModeWithDetails(TradingProviderType providerType) {
        log.info("🔄 Переключение режима торговли на: {}", providerType.getDisplayName());
        return tradingProviderFactory.switchToProviderWithDetails(providerType);
    }

    /**
     * Получение текущего режима торговли
     */
    @Override
    public TradingProviderType getCurrentTradingMode() {
        return tradingProviderFactory.getCurrentProviderType();
    }

    private boolean isInvalidPositionSize(BigDecimal size, PairData pairData) {
        if (size.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("⚠️ Недостаточно средств для открытия позиций по паре {}. Размер позиции: {}", pairData.getPairName(), size);
            return true;
        }
        return false;
    }

    private TradeResult openLong(TradingProvider provider, PairData pairData, BigDecimal amount, BigDecimal leverage) {
        log.info("🟢 Открытие ЛОНГ позиции по тикеру {}. Сумма: {}, плечо: {}", pairData.getLongTicker(), amount, leverage);
        TradeResult result = provider.openLongPosition(pairData.getLongTicker(), amount, leverage);

        if (result.isSuccess()) {
            log.info("✅ ЛОНГ позиция по тикеру {} успешно открыта. ID позиции: {}, PnL: {} USDT ({} %), комиссии: {}",
                    pairData.getLongTicker(),
                    result.getPositionId(),
                    result.getPnlUSDT(),
                    result.getPnlPercent(),
                    result.getFees());
        } else {
            log.warn("❌ Не удалось открыть ЛОНГ позицию по тикеру {}. Ошибка: {}", pairData.getLongTicker(), result.getErrorMessage());
        }

        return result;
    }

    private TradeResult openShort(TradingProvider provider, PairData pairData, BigDecimal amount, BigDecimal leverage) {
        log.info("🔴 Открытие ШОРТ позиции по тикеру {}. Сумма: {}, плечо: {}", pairData.getShortTicker(), amount, leverage);
        TradeResult result = provider.openShortPosition(pairData.getShortTicker(), amount, leverage);

        if (result.isSuccess()) {
            log.info("✅ ШОРТ позиция по тикеру {} успешно открыта. ID позиции: {}, PnL: {} USDT ({} %), комиссии: {}",
                    pairData.getShortTicker(),
                    result.getPositionId(),
                    result.getPnlUSDT(),
                    result.getPnlPercent(),
                    result.getFees());
        } else {
            log.warn("❌ Не удалось открыть ШОРТ позицию по тикеру {}. Ошибка: {}", pairData.getShortTicker(), result.getErrorMessage());
        }

        return result;
    }

    private void rollbackIfNecessary(TradingProvider provider, TradeResult longResult, TradeResult shortResult) {
        log.warn("⚠️ Одна из позиций не была успешно открыта. Начинаем откат...");

        if (longResult.isSuccess()) {
            log.debug("🔁 Закрытие ранее открытой ЛОНГ позиции. ID: {}", longResult.getPositionId());
            TradeResult closeResult = provider.closePosition(longResult.getPositionId());

            if (closeResult.isSuccess()) {
                log.debug("✅ ЛОНГ позиция успешно закрыта.");
            } else {
                log.error("❌ Ошибка при закрытии ЛОНГ позиции: {}", closeResult.getErrorMessage());
            }
        }

        if (shortResult.isSuccess()) {
            log.debug("🔁 Закрытие ранее открытой ШОРТ позиции. ID: {}", shortResult.getPositionId());
            TradeResult closeResult = provider.closePosition(shortResult.getPositionId());

            if (closeResult.isSuccess()) {
                log.debug("✅ ШОРТ позиция успешно закрыта.");
            } else {
                log.error("❌ Ошибка при закрытии ШОРТ позиции: {}", closeResult.getErrorMessage());
            }
        }
    }

    private void savePositionIds(PairData pairData, TradeResult longResult, TradeResult shortResult) {
        pairToLongPositionMap.put(pairData.getId(), longResult.getPositionId());
        pairToShortPositionMap.put(pairData.getId(), shortResult.getPositionId());

        log.debug("💾 Сохранены ID открытых позиций для пары {}: ЛОНГ = {}, ШОРТ = {}",
                pairData.getPairName(),
                longResult.getPositionId(),
                shortResult.getPositionId());
    }

    private ArbitragePairTradeInfo buildSuccess(TradeResult longResult, TradeResult shortResult, BigDecimal balanceUSDT, PairData pairData) {
        log.info("✅ УСПЕХ: Арбитражная пара открыта — ЛОНГ: {} (ID: {}), ШОРТ: {} (ID: {}), БАЛАНС 'ДО': {} USDT",
                pairData.getLongTicker(),
                longResult.getPositionId(),
                pairData.getShortTicker(),
                shortResult.getPositionId(),
                balanceUSDT);

        return ArbitragePairTradeInfo.builder()
                .success(true)
                .longTradeResult(longResult)
                .shortTradeResult(shortResult)
                .portfolioBalanceBeforeTradeUSDT(balanceUSDT)
                .build();
    }

    private ArbitragePairTradeInfo buildFailure() {
        return ArbitragePairTradeInfo.builder()
                .success(false)
                .build();
    }

    private boolean positionsMissingInMap(String longId, String shortId, PairData pairData) {
        if (longId == null || shortId == null) {
            log.warn("⚠️ Не найдены ID позиций в локальном хранилище для пары {}. ЛОНГ: {}, ШОРТ: {}",
                    pairData.getPairName(), longId, shortId);
            return true;
        }
        log.debug("✅ Найдены ID позиций: ЛОНГ = {}, ШОРТ = {} для пары {}", longId, shortId, pairData.getPairName());
        return false;
    }

    private TradeResult closePosition(TradingProvider provider, String positionId, String type) {
        String emoji = type.equalsIgnoreCase("LONG") ? "🔴" : "🟢";
        String positionLabel = type.equalsIgnoreCase("LONG") ? "лонг" : "шорт";

        log.debug("{} Закрытие {} позиции. ID: {}", emoji, positionLabel.toUpperCase(), positionId);
        TradeResult result = provider.closePosition(positionId);

        if (result.isSuccess()) {
            log.debug("✅ Позиция {} успешно закрыта. ID: {}, PnL: {} USDT ({} %), Комиссия: {}",
                    positionLabel, positionId, result.getPnlUSDT(), result.getPnlPercent(), result.getFees());
        } else {
            log.warn("❌ Не удалось закрыть {} позицию. ID: {}, Ошибка: {}",
                    positionLabel, positionId, result.getErrorMessage());
        }

        return result;
    }


    private void logSuccess(PairData pairData, TradeResult longResult, TradeResult shortResult) {
        BigDecimal totalPnLUSDT = longResult.getPnlUSDT().add(shortResult.getPnlUSDT());
        BigDecimal totalPnLPercent = longResult.getPnlPercent().add(shortResult.getPnlPercent());
        BigDecimal totalFees = longResult.getFees().add(shortResult.getFees());

        log.info("✅ Арбитражная пара {} УСПЕШНО закрыта.", pairData.getPairName());
        log.info("📈 Общий доход (PnL): {} USDT ({} %)", totalPnLUSDT, totalPnLPercent);
        log.info("💸 Общая комиссия: {} USDT", totalFees);
        log.info("🟢 ЛОНГ: PnL = {} USDT ({} %), комиссия = {}", longResult.getPnlUSDT(), longResult.getPnlPercent(), longResult.getFees());
        log.info("🔴 ШОРТ: PnL = {} USDT ({} %), комиссия = {}", shortResult.getPnlUSDT(), shortResult.getPnlPercent(), shortResult.getFees());
    }

    private void logFailure(PairData pairData, TradeResult longResult, TradeResult shortResult) {
        log.error("❌ Ошибка при закрытии арбитражной пары {}.", pairData.getPairName());
        log.error("🟢 ЛОНГ позиция ошибка: {}", longResult.getErrorMessage());
        log.error("🔴 ШОРТ позиция ошибка: {}", shortResult.getErrorMessage());
    }

    private BigDecimal calculateTotalPnlUSDT(Position longPosition, Position shortPosition) {
        BigDecimal pnl = BigDecimal.ZERO;
        if (longPosition != null) {
            pnl = pnl.add(longPosition.getRealizedPnLUSDT());
        }
        if (shortPosition != null) {
            pnl = pnl.add(shortPosition.getRealizedPnLUSDT());
        }
        return pnl;
    }

    private BigDecimal calculateTotalPnlPercent(Position longPosition, Position shortPosition) {
        BigDecimal pnl = BigDecimal.ZERO;
        if (longPosition != null) {
            pnl = pnl.add(longPosition.getRealizedPnLPercent());
        }
        if (shortPosition != null) {
            pnl = pnl.add(shortPosition.getRealizedPnLPercent());
        }
        return pnl;
    }

    private Positioninfo buildClosedPositionInfo(BigDecimal pnlUSDT, BigDecimal pnlPercent) {
        return Positioninfo.builder()
                .positionsClosed(true)
                .totalPnLUSDT(pnlUSDT)
                .totalPnLPercent(pnlPercent)
                .build();
    }

    private Positioninfo buildOpenPositionInfo() {
        return Positioninfo.builder()
                .positionsClosed(false)
                .totalPnLUSDT(BigDecimal.ZERO)
                .totalPnLPercent(BigDecimal.ZERO)
                .build();
    }

    private boolean isOpen(Position position) {
        return position != null && position.getStatus() == PositionStatus.OPEN;
    }

    private boolean areBothOpen(Position longPos, Position shortPos) {
        return isOpen(longPos) && isOpen(shortPos);
    }

    private boolean areBothClosed(Position longPos, Position shortPos) {
        return isClosed(longPos) && isClosed(shortPos);
    }

    private void calculateUnrealizedPnL(Position longPos, Position shortPos) {
        longPos.calculateUnrealizedPnL();
        shortPos.calculateUnrealizedPnL();
    }

    private Positioninfo buildOpenPositionInfo(Position longPos, Position shortPos, BigDecimal totalPnLUSDT, BigDecimal totalPnLPercent) {
        return Positioninfo.builder()
                .positionsClosed(false)
                .longPosition(longPos)
                .shortPosition(shortPos)
                .totalPnLUSDT(totalPnLUSDT)
                .totalPnLPercent(totalPnLPercent)
                .build();
    }

    private Positioninfo buildPartiallyClosedInfo(Position longPos, Position shortPos) {
        return Positioninfo.builder()
                .positionsClosed(true)
                .longPosition(longPos)
                .shortPosition(shortPos)
                .totalPnLUSDT(BigDecimal.ZERO)
                .totalPnLPercent(BigDecimal.ZERO)
                .build();
    }

    private Positioninfo buildClosedPositionInfo() {
        return Positioninfo.builder()
                .positionsClosed(true)
                .totalPnLUSDT(BigDecimal.ZERO)
                .totalPnLPercent(BigDecimal.ZERO)
                .build();
    }

    private boolean positionsAreNull(Position longPosition, Position shortPosition, PairData pairData) {
        if (longPosition == null || shortPosition == null) {
            log.error("❌ Ошибка: позиции равны null для пары '{}'. ЛОНГ позиция: {}, ШОРТ позиция: {}",
                    pairData.getPairName(), longPosition, shortPosition);
            return true;
        }
        log.debug("Получены позиции для пары '{}': ЛОНГ={}, ШОРТ={}", pairData.getPairName(), longPosition, shortPosition);
        return false;
    }

    private boolean isClosed(Position position) {
        return position != null && position.getStatus() == PositionStatus.CLOSED;
    }

    private Positioninfo buildPositionInfo(boolean closed, Position longPos, Position shortPos) {
        return Positioninfo.builder()
                .positionsClosed(closed)
                .longPosition(longPos)
                .shortPosition(shortPos)
                .build();
    }
}