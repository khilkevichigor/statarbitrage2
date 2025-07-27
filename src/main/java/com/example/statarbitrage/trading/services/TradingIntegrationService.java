package com.example.statarbitrage.trading.services;

import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
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
public class TradingIntegrationService {

    private final TradingProviderFactory tradingProviderFactory;

    // Синхронизация открытия позиций для избежания конфликтов SQLite
    private final Object openPositionLock = new Object();

    // Хранилище связей между PairData и торговыми позициями
    private final ConcurrentHashMap<Long, String> pairToLongPositionMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> pairToShortPositionMap = new ConcurrentHashMap<>();
    private final PositionSizeService positionSizeService;
    private final AdaptiveAmountService adaptiveAmountService;
    private final ValidateMinimumLotRequirementsService validateMinimumLotRequirementsService;

    /**
     * Открытие пары позиций для статарбитража - СИНХРОННО
     */
    public ArbitragePairTradeInfo openArbitragePair(PairData pairData, Settings settings) {
        log.info("==> openArbitragePair: НАЧАЛО для пары {}", pairData.getPairName());

        synchronized (openPositionLock) {
            try {
                TradingProvider provider = tradingProviderFactory.getCurrentProvider();
                log.info("Текущий торговый провайдер: {}", provider.getClass().getSimpleName());

                BigDecimal positionSize = positionSizeService.calculatePositionSize(provider, settings);
                log.info("Рассчитанный размер позиций: {}", positionSize);

                if (isInvalidPositionSize(positionSize, pairData)) {
                    return buildFailure();
                }

                BigDecimal[] adaptiveAmounts = adaptiveAmountService.calculate(provider, pairData, positionSize);
                BigDecimal longAmount = adaptiveAmounts[0];
                BigDecimal shortAmount = adaptiveAmounts[1];
                log.info("Адаптивное распределение средств: LONG {} = {}, SHORT {} = {}", pairData.getLongTicker(), longAmount, pairData.getShortTicker(), shortAmount);

                if (!validateMinimumLotRequirementsService.validate(provider, pairData, longAmount, shortAmount)) {
                    log.warn("⚠️ ПРОПУСК ПАРЫ: {} не подходит из-за больших минимальных лотов", pairData.getPairName());
                    return buildFailure();
                }

                BigDecimal leverage = BigDecimal.valueOf(settings.getLeverage());
                log.info("Используемое плечо: {}", leverage);

                TradeResult longResult = openLong(provider, pairData, longAmount, leverage);
                if (!longResult.isSuccess()) {
                    return buildFailure();
                }

                TradeResult shortResult = openShort(provider, pairData, shortAmount, leverage);

                if (shortResult.isSuccess()) {
                    savePositionIds(pairData, longResult, shortResult);
                    return buildSuccess(longResult, shortResult, pairData);
                } else {
                    rollbackIfNecessary(provider, longResult, shortResult);
                    return buildFailure();
                }

            } catch (Exception e) {
                log.error("❌ КРИТИЧЕСКАЯ ОШИБКА при открытии арбитражной пары {}: {}", pairData.getPairName(), e.getMessage(), e);
                return buildFailure();
            } finally {
                log.info("<== openArbitragePair: КОНЕЦ для пары {}", pairData.getPairName());
            }
        }
    }

    /**
     * Закрытие пары позиций - СИНХРОННО
     */
    public ArbitragePairTradeInfo closeArbitragePair(PairData pairData) {
        log.info("==> closeArbitragePair: НАЧАЛО для пары {}", pairData.getPairName());

        synchronized (openPositionLock) {
            try {
                String longPositionId = pairToLongPositionMap.get(pairData.getId());
                String shortPositionId = pairToShortPositionMap.get(pairData.getId());

                if (!positionsExistInMap(longPositionId, shortPositionId, pairData)) {
                    return buildFailure();
                }

                TradingProvider provider = tradingProviderFactory.getCurrentProvider();
                log.info("Текущий торговый провайдер: {}", provider.getClass().getSimpleName());

                log.info("🔄 Закрытие арбитражной пары: {}", pairData.getPairName());

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
                log.error("❌ КРИТИЧЕСКАЯ ОШИБКА при закрытии арбитражной пары {}: {}", pairData.getPairName(), e.getMessage(), e);
                return buildFailure();
            } finally {
                log.info("<== closeArbitragePair: КОНЕЦ для пары {}", pairData.getPairName());
            }
        }
    }

    /**
     * Проверка что позиции действительно закрыты на бирже с получением PnL
     */
    public Positioninfo verifyPositionsClosed(PairData pairData) {
        String longPositionId = pairToLongPositionMap.get(pairData.getId());
        String shortPositionId = pairToShortPositionMap.get(pairData.getId());

        if (!positionsExistInMap(longPositionId, shortPositionId, pairData)) {
            return buildClosedPositionInfo(BigDecimal.ZERO);
        }

        TradingProvider provider = tradingProviderFactory.getCurrentProvider();
        provider.updatePositionPrices();

        Position longPosition = provider.getPosition(longPositionId);
        Position shortPosition = provider.getPosition(shortPositionId);

        boolean longClosed = isClosed(longPosition);
        boolean shortClosed = isClosed(shortPosition);

        if (longClosed && shortClosed) {
            BigDecimal finalPnlPercent = calculateTotalPnlPercent(longPosition, shortPosition);
            removePairFromLocalStorage(pairData);
            log.info("🗑️ Удалены закрытые позиции из реестра для пары {}, финальный PnL: {}%", pairData.getPairName(), finalPnlPercent);

            return buildClosedPositionInfo(finalPnlPercent);
        }

        log.warn("⚠️ Не все позиции закрыты на бирже: LONG закрыта={}, SHORT закрыта={}", longClosed, shortClosed);
        return buildOpenPositionInfo();
    }

    /**
     * Получение актуальной информации по открытым позициям для обновления changes
     */
    public Positioninfo getOpenPositionsInfo(PairData pairData) {
        String longPositionId = pairToLongPositionMap.get(pairData.getId());
        String shortPositionId = pairToShortPositionMap.get(pairData.getId());

        if (!positionsExistInMap(longPositionId, shortPositionId, pairData)) {
            return buildClosedPositionInfo();
        }

        TradingProvider provider = tradingProviderFactory.getCurrentProvider();
        provider.updatePositionPrices();

        Position longPosition = provider.getPosition(longPositionId);
        Position shortPosition = provider.getPosition(shortPositionId);

        if (areBothOpen(longPosition, shortPosition)) {
            calculateUnrealizedPnL(longPosition, shortPosition);
            BigDecimal totalPnL = longPosition.getUnrealizedPnLUSDT().add(shortPosition.getUnrealizedPnLUSDT());

            log.debug("📊 Актуальный PnL для открытых позиций {}: {}", pairData.getPairName(), totalPnL);

            return buildOpenPositionInfo(longPosition, shortPosition, totalPnL);
        }

        log.warn("⚠️ Не все позиции открыты на бирже: LONG открыта={}, SHORT открыта={}",
                isOpen(longPosition), isOpen(shortPosition));

        return buildPartiallyClosedInfo(longPosition, shortPosition);
    }

    /**
     * Получение актуальной информации по позициям для пары
     */
    public Positioninfo getPositionInfo(PairData pairData) {
        log.info("ℹ️ Запрос информации о позициях для пары {}", pairData.getPairName());

        String longPositionId = pairToLongPositionMap.get(pairData.getId());
        String shortPositionId = pairToShortPositionMap.get(pairData.getId());

        if (!positionsExistInMap(longPositionId, shortPositionId, pairData)) {
            return Positioninfo.builder().build();
        }

        TradingProvider provider = tradingProviderFactory.getCurrentProvider();
        log.debug("Текущий провайдер: {}", provider.getClass().getSimpleName());

        Position longPosition = provider.getPosition(longPositionId);
        Position shortPosition = provider.getPosition(shortPositionId);

        if (positionsAreNull(longPosition, shortPosition, pairData)) {
            return Positioninfo.builder().build();
        }

        boolean bothClosed = isClosed(longPosition) && isClosed(shortPosition);
        log.debug("Статус позиций: LONG закрыта={}, SHORT закрыта={}", isClosed(longPosition), isClosed(shortPosition));

        if (bothClosed) {
            log.info("✅ Обе позиции для пары {} уже закрыты.", pairData.getPairName());
            return buildPositionInfo(true, longPosition, shortPosition);
        }

        log.debug("Позиции еще открыты, обновляем цены...");
        provider.updatePositionPrices(List.of(pairData.getLongTicker(), pairData.getShortTicker()));
        log.debug("Цены обновлены.");

        return buildPositionInfo(false, longPosition, shortPosition);
    }

    public void removePairFromLocalStorage(PairData pairData) {
        log.info("Удаляем связи из реестра для пары {}", pairData.getPairName());
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
    public boolean canOpenNewPair(Settings settings) {
        TradingProvider provider = tradingProviderFactory.getCurrentProvider();
        BigDecimal requiredAmount = positionSizeService.calculatePositionSize(provider, settings);
        return provider.hasAvailableBalance(requiredAmount);
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

    private boolean isInvalidPositionSize(BigDecimal size, PairData pairData) {
        if (size.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("⚠️ Недостаточно средств для открытия позиций по паре {}. Размер позиции: {}", pairData.getPairName(), size);
            return true;
        }
        return false;
    }

    private TradeResult openLong(TradingProvider provider, PairData pairData, BigDecimal amount, BigDecimal leverage) {
        log.info("🟢 Открытие LONG позиции: {} с размером {}", pairData.getLongTicker(), amount);
        TradeResult result = provider.openLongPosition(pairData.getLongTicker(), amount, leverage);
        log.info("Результат открытия LONG позиции: {}", result);
        return result;
    }

    private TradeResult openShort(TradingProvider provider, PairData pairData, BigDecimal amount, BigDecimal leverage) {
        log.info("🔴 Открытие SHORT позиции: {} с размером {}", pairData.getShortTicker(), amount);
        TradeResult result = provider.openShortPosition(pairData.getShortTicker(), amount, leverage);
        log.info("Результат открытия SHORT позиции: {}", result);
        return result;
    }

    private void rollbackIfNecessary(TradingProvider provider, TradeResult longResult, TradeResult shortResult) {
        log.warn("Одна из позиций не открылась. Производим откат...");
        if (longResult.isSuccess()) {
            log.info("Закрываем успешно открытую LONG позицию: {}", longResult.getPositionId());
            provider.closePosition(longResult.getPositionId());
        }
        if (shortResult.isSuccess()) {
            log.info("Закрываем успешно открытую SHORT позицию: {}", shortResult.getPositionId());
            provider.closePosition(shortResult.getPositionId());
        }
    }

    private void savePositionIds(PairData pairData, TradeResult longResult, TradeResult shortResult) {
        pairToLongPositionMap.put(pairData.getId(), longResult.getPositionId());
        pairToShortPositionMap.put(pairData.getId(), shortResult.getPositionId());
        log.info("Сохранены ID позиций в мапу: LONG ID = {}, SHORT ID = {}", longResult.getPositionId(), shortResult.getPositionId());
    }

    private ArbitragePairTradeInfo buildSuccess(TradeResult longResult, TradeResult shortResult, PairData pairData) {
        log.info("✅ Открыта арбитражная пара: {} LONG / {} SHORT", pairData.getLongTicker(), pairData.getShortTicker());
        return ArbitragePairTradeInfo.builder()
                .success(true)
                .longTradeResult(longResult)
                .shortTradeResult(shortResult)
                .build();
    }

    private ArbitragePairTradeInfo buildFailure() {
        return ArbitragePairTradeInfo.builder()
                .success(false)
                .build();
    }

    private boolean positionsExistInMap(String longId, String shortId, PairData pairData) {
        if (longId == null || shortId == null) {
            log.warn("⚠️ Не найдены ID позиций в мапе для пары {}.", pairData.getPairName());
            return false;
        }
        log.info("ID позиций из мапы: LONG ID = {}, SHORT ID = {}", longId, shortId);
        return true;
    }

    private TradeResult closePosition(TradingProvider provider, String positionId, String type) {
        log.info("{} Закрытие {} позиции: ID = {}", type.equals("LONG") ? "🔴" : "🟢", type, positionId);
        TradeResult result = provider.closePosition(positionId);
        log.info("Результат закрытия {} позиции: {}", type, result);
        return result;
    }

    private void logSuccess(PairData pairData, TradeResult longResult, TradeResult shortResult) {
        BigDecimal totalPnL = longResult.getPnl().add(shortResult.getPnl());
        BigDecimal totalFees = longResult.getFees().add(shortResult.getFees());
        log.info("✅ УСПЕШНО закрыта арбитражная пара: {} | Общий PnL: {} | Общие комиссии: {}",
                pairData.getPairName(), totalPnL, totalFees);
    }

    private void logFailure(PairData pairData, TradeResult longResult, TradeResult shortResult) {
        log.error("❌ ОШИБКА при закрытии арбитражной пары {}: Long={}, Short={}",
                pairData.getPairName(),
                longResult.getErrorMessage(), shortResult.getErrorMessage());
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

    private Positioninfo buildClosedPositionInfo(BigDecimal pnl) {
        return Positioninfo.builder()
                .positionsClosed(true)
                .totalPnL(pnl)
                .build();
    }

    private Positioninfo buildOpenPositionInfo() {
        return Positioninfo.builder()
                .positionsClosed(false)
                .totalPnL(BigDecimal.ZERO)
                .build();
    }

    private boolean isOpen(Position position) {
        return position != null && position.getStatus() == PositionStatus.OPEN;
    }

    private boolean areBothOpen(Position longPos, Position shortPos) {
        return isOpen(longPos) && isOpen(shortPos);
    }

    private void calculateUnrealizedPnL(Position longPos, Position shortPos) {
        // todo заменить на получение данных с OKX
        longPos.calculateUnrealizedPnL();
        shortPos.calculateUnrealizedPnL();
    }

    private Positioninfo buildOpenPositionInfo(Position longPos, Position shortPos, BigDecimal totalPnL) {
        return Positioninfo.builder()
                .positionsClosed(false)
                .longPosition(longPos)
                .shortPosition(shortPos)
                .totalPnL(totalPnL)
                .build();
    }

    private Positioninfo buildPartiallyClosedInfo(Position longPos, Position shortPos) {
        return Positioninfo.builder()
                .positionsClosed(true)
                .longPosition(longPos)
                .shortPosition(shortPos)
                .totalPnL(BigDecimal.ZERO)
                .build();
    }

    private Positioninfo buildClosedPositionInfo() {
        return Positioninfo.builder()
                .positionsClosed(true)
                .totalPnL(BigDecimal.ZERO)
                .build();
    }

    private boolean positionsAreNull(Position longPosition, Position shortPosition, PairData pairData) {
        if (longPosition == null || shortPosition == null) {
            log.error("❌ Ошибка расчета changes - позиции равны null для пары {}", pairData.getPairName());
            return true;
        }
        log.debug("Получены позиции: LONG={}, SHORT={}", longPosition, shortPosition);
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