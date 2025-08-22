package com.example.statarbitrage.trading.services;

import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.core.services.PortfolioService;
import com.example.statarbitrage.trading.interfaces.TradingProvider;
import com.example.statarbitrage.trading.interfaces.TradingProviderType;
import com.example.statarbitrage.trading.model.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradingIntegrationServiceImpl implements TradingIntegrationService {

    private final TradingProviderFactory tradingProviderFactory;
    private final PositionRepository positionRepository;
    private final PositionSizeService positionSizeService;
    private final AdaptiveAmountService adaptiveAmountService;
    private final ValidateMinimumLotRequirementsService validateMinimumLotRequirementsService;
    private final PortfolioService portfolioService;

    private final Object openPositionLock = new Object();

    @PostConstruct
    public void loadOpenPositions() {
        log.info("Загрузка открытых позиций из базы данных...");
        List<Position> openPositions = positionRepository.findAllByStatus(PositionStatus.OPEN);

        TradingProvider provider = tradingProviderFactory.getCurrentProvider();
        if (provider != null) {
            provider.loadPositions(openPositions);
            log.info("Загружено {} открытых позиций.", openPositions.size());
        } else {
            log.warn("⚠️ TradingProvider не инициализирован, пропускаем загрузку позиций");
        }
    }

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
                    log.debug("Пропуск пары {}: минимальные лоты не соответствуют требованиям", pairData.getPairName());
                    return buildFailure();
                }

                BigDecimal leverage = BigDecimal.valueOf(settings.getLeverage());
                log.debug("Используемое кредитное плечо: {}", leverage);

                BigDecimal balanceUSDT = portfolioService.getBalanceUSDT();

                TradeResult longResult = openLong(provider, pairData, longAmount, leverage);
                if (!longResult.isSuccess()) {
                    log.error("Ошибка открытия ЛОНГ позиции для {}: {}", pairData.getLongTicker(), longResult.getErrorMessage());
                    return buildFailure();
                }

                TradeResult shortResult = openShort(provider, pairData, shortAmount, leverage);
                if (shortResult.isSuccess()) {
                    savePositions(pairData, longResult, shortResult);
                    log.debug("Успешно открыты и сохранены позиции для пары {}: ЛОНГ ID = {}, ШОРТ ID = {}",
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

    @Override
    public ArbitragePairTradeInfo closeArbitragePair(PairData pairData) {
        log.debug("=== Начало закрытия арбитражной пары: {}", pairData.getPairName());

        synchronized (openPositionLock) {
            try {
                Optional<Position> longPositionOpt = positionRepository.findFirstByPairDataIdAndTypeOrderByIdDesc(pairData.getId(), PositionType.LONG);
                Optional<Position> shortPositionOpt = positionRepository.findFirstByPairDataIdAndTypeOrderByIdDesc(pairData.getId(), PositionType.SHORT);

                if (longPositionOpt.isEmpty() || shortPositionOpt.isEmpty()) {
                    log.warn("Не найдены ID позиций для пары {}. Закрытие невозможно.", pairData.getPairName());
                    return buildFailure();
                }

                TradingProvider provider = tradingProviderFactory.getCurrentProvider();
                log.debug("Текущий торговый провайдер: {}", provider.getClass().getSimpleName());

                log.debug("Начинаем закрытие позиций для пары {}", pairData.getPairName());

                TradeResult longResult = closePosition(provider, longPositionOpt.get());
                TradeResult shortResult = closePosition(provider, shortPositionOpt.get());

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
                log.debug("=== Конец закрытия арбитражной пары: {}", pairData.getPairName());
            }
        }
    }

    @Override
    public Positioninfo verifyPositionsClosed(PairData pairData) {
        Optional<Position> longPositionOpt = positionRepository.findFirstByPairDataIdAndTypeOrderByIdDesc(pairData.getId(), PositionType.LONG);
        Optional<Position> shortPositionOpt = positionRepository.findFirstByPairDataIdAndTypeOrderByIdDesc(pairData.getId(), PositionType.SHORT);

        if (longPositionOpt.isEmpty() || shortPositionOpt.isEmpty()) {
            log.warn("Не найдены ID позиций для пары {}. Предполагаем, что позиции закрыты.", pairData.getPairName());
            return buildClosedPositionInfo(BigDecimal.ZERO, BigDecimal.ZERO);
        }

        TradingProvider provider = tradingProviderFactory.getCurrentProvider();
        provider.updatePositionPrices();

        Position longPosition = provider.getPosition(longPositionOpt.get().getPositionId());
        Position shortPosition = provider.getPosition(shortPositionOpt.get().getPositionId());

        boolean longClosed = isClosed(longPosition);
        boolean shortClosed = isClosed(shortPosition);

        if (longClosed && shortClosed) {
            BigDecimal finalPnlUSDT = calculateTotalPnlUSDT(longPosition, shortPosition);
            BigDecimal finalPnlPercent = calculateTotalPnlPercent(longPosition, shortPosition);
            removePairFromLocalStorage(pairData);
            log.debug("Удалены закрытые позиции из репозитория для пары {}. Итоговый PnL: {} USDT ({} %)", pairData.getPairName(), finalPnlUSDT, finalPnlPercent);

            return buildClosedPositionInfo(finalPnlUSDT, finalPnlPercent);
        }

        log.warn("Не все позиции закрыты для пары {}: ЛОНГ закрыта={}, ШОРТ закрыта={}", pairData.getPairName(), longClosed, shortClosed);
        return buildOpenPositionInfo();
    }

    @Override
    public Positioninfo getOpenPositionsInfo(PairData pairData) {
        Optional<Position> longPositionOpt = positionRepository.findFirstByPairDataIdAndTypeOrderByIdDesc(pairData.getId(), PositionType.LONG);
        Optional<Position> shortPositionOpt = positionRepository.findFirstByPairDataIdAndTypeOrderByIdDesc(pairData.getId(), PositionType.SHORT);

        if (longPositionOpt.isEmpty() || shortPositionOpt.isEmpty()) {
            log.warn("Не найдены ID позиций для пары {}. Предполагаем, что позиции закрыты.", pairData.getPairName());
            return buildClosedPositionInfo();
        }

        TradingProvider provider = tradingProviderFactory.getCurrentProvider();
        provider.updatePositionPrices();

        Position longPosition = provider.getPosition(longPositionOpt.get().getPositionId());
        Position shortPosition = provider.getPosition(shortPositionOpt.get().getPositionId());

        if (areBothOpen(longPosition, shortPosition)) {
//            calculateUnrealizedPnL(longPosition, shortPosition);
            BigDecimal totalPnlUSDT = longPosition.getUnrealizedPnLUSDT().add(shortPosition.getUnrealizedPnLUSDT());
            BigDecimal totalPnlPercent = longPosition.getUnrealizedPnLPercent().add(shortPosition.getUnrealizedPnLPercent());

            log.debug("Текущий PnL для открытых позиций пары {}: {} USDT ({} %)", pairData.getPairName(), totalPnlUSDT, totalPnlPercent);

            return buildOpenPositionInfo(longPosition, shortPosition, totalPnlUSDT, totalPnlPercent);
        }

        log.warn("Не все позиции открыты для пары {}: ЛОНГ открыта={}, ШОРТ открыта={}",
                pairData.getPairName(), isOpen(longPosition), isOpen(shortPosition));

        return buildPartiallyClosedInfo(longPosition, shortPosition);
    }

    @Override
    public Positioninfo getPositionInfo(PairData pairData) {
        log.debug("Запрос информации о позициях для пары {}", pairData.getPairName());

        Optional<Position> longPositionOpt = positionRepository.findFirstByPairDataIdAndTypeOrderByIdDesc(pairData.getId(), PositionType.LONG);
        Optional<Position> shortPositionOpt = positionRepository.findFirstByPairDataIdAndTypeOrderByIdDesc(pairData.getId(), PositionType.SHORT);

        if (longPositionOpt.isEmpty() || shortPositionOpt.isEmpty()) {
            log.warn("Не найдены ID позиций для пары {}", pairData.getPairName());
            return Positioninfo.builder().build();
        }

        TradingProvider provider = tradingProviderFactory.getCurrentProvider();
        log.debug("Текущий торговый провайдер: {}", provider.getClass().getSimpleName());

        Position longPosition = provider.getPosition(longPositionOpt.get().getPositionId());
        Position shortPosition = provider.getPosition(shortPositionOpt.get().getPositionId());

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
        log.debug("Удаляем сохранённые ID позиций из репозитория для пары {}", pairData.getPairName());
        List<Position> longPositions = positionRepository.findAllByPairDataIdAndType(pairData.getId(), PositionType.LONG);
        List<Position> shortPositions = positionRepository.findAllByPairDataIdAndType(pairData.getId(), PositionType.SHORT);

        positionRepository.deleteAll(longPositions);
        positionRepository.deleteAll(shortPositions);

        log.debug("Удалены позиции для пары {}: {} лонг позиций, {} шорт позиций",
                pairData.getPairName(), longPositions.size(), shortPositions.size());
    }

    @Override
    public void updateAllPositions() {
        TradingProvider provider = tradingProviderFactory.getCurrentProvider();
        try {
            log.debug("🔄 Обновление цен по всем открытым позициям...");
            provider.updatePositionPrices();
            log.debug("✅ Обновление цен завершено успешно.");
        } catch (Exception e) {
            log.error("❌ Ошибка при обновлении цен по позициям: {}", e.getMessage(), e);
        }
    }

    @Override
    public Portfolio getPortfolioInfo() {
        TradingProvider provider = tradingProviderFactory.getCurrentProvider();
        return provider.getPortfolio();
    }

    @Override
    public boolean canOpenNewPair(Settings settings) {
        TradingProvider provider = tradingProviderFactory.getCurrentProvider();
        BigDecimal requiredAmount = positionSizeService.calculatePositionSize(provider, settings);
        return provider.hasAvailableBalance(requiredAmount);
    }

    @Override
    public TradingProviderSwitchResult switchTradingModeWithDetails(TradingProviderType providerType) {
        log.info("🔄 Переключение режима торговли на: {}", providerType.getDisplayName());
        return tradingProviderFactory.switchToProviderWithDetails(providerType);
    }

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
        log.debug("🟢 Открытие ЛОНГ позиции по тикеру {}. Сумма: {}, плечо: {}", pairData.getLongTicker(), amount, leverage);
        TradeResult result = provider.openLongPosition(pairData.getLongTicker(), amount, leverage);

        if (result.isSuccess()) {
            log.info("✅ ЛОНГ позиция по тикеру {} успешно открыта. ID позиции: {}, PnL: {} USDT ({} %), комиссии: {}",
                    pairData.getLongTicker(),
                    result.getPositionId(),
                    result.getPnlUSDT(),
                    result.getPnlPercent() != null ? result.getPnlPercent() : BigDecimal.ZERO,
                    result.getFees());
        } else {
            log.warn("❌ Не удалось открыть ЛОНГ позицию по тикеру {}. Ошибка: {}", pairData.getLongTicker(), result.getErrorMessage());
        }

        return result;
    }

    private TradeResult openShort(TradingProvider provider, PairData pairData, BigDecimal amount, BigDecimal leverage) {
        log.debug("🔴 Открытие ШОРТ позиции по тикеру {}. Сумма: {}, плечо: {}", pairData.getShortTicker(), amount, leverage);
        TradeResult result = provider.openShortPosition(pairData.getShortTicker(), amount, leverage);

        if (result.isSuccess()) {
            log.info("✅ ШОРТ позиция по тикеру {} успешно открыта. ID позиции: {}, PnL: {} USDT ({} %), комиссии: {}",
                    pairData.getShortTicker(),
                    result.getPositionId(),
                    result.getPnlUSDT(),
                    result.getPnlPercent() != null ? result.getPnlPercent() : BigDecimal.ZERO,
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

    private void savePositions(PairData pairData, TradeResult longResult, TradeResult shortResult) {
        // Обрабатываем лонг позицию
        Position newLongPosition = longResult.getPosition();
        newLongPosition.setPairDataId(pairData.getId());

        Optional<Position> existingLongOpt = positionRepository.findFirstByPairDataIdAndTypeOrderByIdDesc(pairData.getId(), PositionType.LONG);
        Position finalLongPosition;

        if (existingLongOpt.isPresent()) {
            // Усреднение - обновляем существующую позицию актуальными данными от OKX
            Position existingLong = existingLongOpt.get();
            log.debug("🔄 Обновление существующей ЛОНГ позиции при усреднении для пары {}", pairData.getPairName());

            // Обновляем актуальными данными от OKX после усреднения
            existingLong.setSize(newLongPosition.getSize());
            existingLong.setEntryPrice(newLongPosition.getEntryPrice()); // Новая средняя цена
            existingLong.setCurrentPrice(newLongPosition.getCurrentPrice());
            existingLong.setOpeningFees(newLongPosition.getOpeningFees());
            existingLong.setLastUpdated(LocalDateTime.now());

            finalLongPosition = positionRepository.save(existingLong);
            log.debug("✅ Обновлена ЛОНГ позиция: новая средняя цена={}, размер={}",
                    existingLong.getEntryPrice(), existingLong.getSize());
        } else {
            // Первое открытие позиции
            finalLongPosition = positionRepository.save(newLongPosition);
            log.debug("💾 Создана новая ЛОНГ позиция для пары {}: ID = {}", pairData.getPairName(), newLongPosition.getPositionId());
        }

        // Обрабатываем шорт позицию
        Position newShortPosition = shortResult.getPosition();
        newShortPosition.setPairDataId(pairData.getId());

        Optional<Position> existingShortOpt = positionRepository.findFirstByPairDataIdAndTypeOrderByIdDesc(pairData.getId(), PositionType.SHORT);
        Position finalShortPosition;

        if (existingShortOpt.isPresent()) {
            // Усреднение - обновляем существующую позицию актуальными данными от OKX
            Position existingShort = existingShortOpt.get();
            log.debug("🔄 Обновление существующей ШОРТ позиции при усреднении для пары {}", pairData.getPairName());

            // Обновляем актуальными данными от OKX после усреднения
            existingShort.setSize(newShortPosition.getSize());
            existingShort.setEntryPrice(newShortPosition.getEntryPrice()); // Новая средняя цена
            existingShort.setCurrentPrice(newShortPosition.getCurrentPrice());
            existingShort.setOpeningFees(newShortPosition.getOpeningFees());
            existingShort.setLastUpdated(LocalDateTime.now());

            finalShortPosition = positionRepository.save(existingShort);
            log.debug("✅ Обновлена ШОРТ позиция: новая средняя цена={}, размер={}",
                    existingShort.getEntryPrice(), existingShort.getSize());
        } else {
            // Первое открытие позиции
            finalShortPosition = positionRepository.save(newShortPosition);
            log.debug("💾 Создана новая ШОРТ позиция для пары {}: ID = {}", pairData.getPairName(), newShortPosition.getPositionId());
        }

        // Синхронизируем с OKX для получения актуальных данных после усреднения
        if (existingLongOpt.isPresent() || existingShortOpt.isPresent()) {
            log.debug("🔄 Синхронизация с OKX после усреднения для получения актуальных данных");
            TradingProvider provider = tradingProviderFactory.getCurrentProvider();
            if (provider != null) {
                provider.updatePositionPrices(List.of(pairData.getLongTicker(), pairData.getShortTicker()));
                log.debug("✅ Синхронизация с OKX завершена");
            }
        }

        log.debug("💾 Обработаны позиции для пары {}: ЛОНГ ID = {}, ШОРТ ID = {}",
                pairData.getPairName(),
                finalLongPosition.getPositionId(),
                finalShortPosition.getPositionId());
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

    private TradeResult closePosition(TradingProvider provider, Position position) {
        String positionLabel = position.getType() == PositionType.LONG ? "лонг" : "шорт";
        String emoji = position.getType() == PositionType.LONG ? "🔴" : "🟢";

        log.debug("{} Закрытие {} позиции. ID: {}", emoji, positionLabel.toUpperCase(), position.getPositionId());
        TradeResult result = provider.closePosition(position.getPositionId());

        if (result.isSuccess()) {
            position.setStatus(PositionStatus.CLOSED);
            positionRepository.save(position);
            log.debug("✅ Позиция {} успешно закрыта и обновлена в БД. ID: {}, PnL: {} USDT ({} %), Комиссия: {}",
                    positionLabel, position.getPositionId(), result.getPnlUSDT(), result.getPnlPercent(), result.getFees());
        } else {
            log.warn("❌ Не удалось закрыть {} позицию. ID: {}, Ошибка: {}",
                    positionLabel, position.getPositionId(), result.getErrorMessage());
        }

        return result;
    }

    private void logSuccess(PairData pairData, TradeResult longResult, TradeResult shortResult) {
        BigDecimal totalPnLUSDT = longResult.getPnlUSDT().add(shortResult.getPnlUSDT());
        BigDecimal totalPnLPercent = longResult.getPnlPercent().add(shortResult.getPnlPercent());
        BigDecimal totalFees = longResult.getFees().add(shortResult.getFees());

        log.debug("✅ Арбитражная пара: {} УСПЕШНО закрыта.", pairData.getPairName());
        log.debug("📈 Общий доход (PnL): {} USDT ({} %)", totalPnLUSDT, totalPnLPercent);
        log.debug("💸 Общая комиссия: {} USDT", totalFees);
        log.debug("🟢 ЛОНГ: PnL = {} USDT ({} %), комиссия = {}", longResult.getPnlUSDT(), longResult.getPnlPercent(), longResult.getFees());
        log.debug("🔴 ШОРТ: PnL = {} USDT ({} %), комиссия = {}", shortResult.getPnlUSDT(), shortResult.getPnlPercent(), shortResult.getFees());
    }

    private void logFailure(PairData pairData, TradeResult longResult, TradeResult shortResult) {
        log.error("❌ Ошибка при закрытии арбитражной пары {}.", pairData.getPairName());
        log.error("🟢 ЛОНГ позиция ошибка: {}", longResult.getErrorMessage());
        log.error("🔴 ШОРТ позиция ошибка: {}", shortResult.getErrorMessage());
    }

    private BigDecimal calculateTotalPnlUSDT(Position longPosition, Position shortPosition) {
        BigDecimal pnl = BigDecimal.ZERO;
        if (longPosition != null && longPosition.getRealizedPnLUSDT() != null) {
            pnl = pnl.add(longPosition.getRealizedPnLUSDT());
        }
        if (shortPosition != null && shortPosition.getRealizedPnLUSDT() != null) {
            pnl = pnl.add(shortPosition.getRealizedPnLUSDT());
        }
        return pnl;
    }

    private BigDecimal calculateTotalPnlPercent(Position longPosition, Position shortPosition) {
        BigDecimal pnl = BigDecimal.ZERO;
        if (longPosition != null && longPosition.getRealizedPnLPercent() != null) {
            pnl = pnl.add(longPosition.getRealizedPnLPercent());
        }
        if (shortPosition != null && shortPosition.getRealizedPnLPercent() != null) {
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

//    private void calculateUnrealizedPnL(Position longPos, Position shortPos) {
//        if (longPos != null) longPos.calculateUnrealizedPnL();
//        if (shortPos != null) shortPos.calculateUnrealizedPnL();
//    }

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
