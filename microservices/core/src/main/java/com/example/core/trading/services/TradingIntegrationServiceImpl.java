package com.example.core.trading.services;

import com.example.core.repositories.PositionRepository;
import com.example.core.services.PortfolioService;
import com.example.core.trading.interfaces.TradingProvider;
import com.example.core.trading.interfaces.TradingProviderType;
import com.example.shared.models.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    public ArbitragePairTradeInfo openArbitragePair(TradingPair tradingPair, Settings settings) {
        log.debug("=== Начало открытия арбитражной пары: {}", tradingPair.getPairName());

        synchronized (openPositionLock) {
            try {
                TradingProvider provider = tradingProviderFactory.getCurrentProvider();
                log.debug("Текущий торговый провайдер: {}", provider.getClass().getSimpleName());

                BigDecimal positionSize = positionSizeService.calculatePositionSize(provider, settings);
                log.debug("Вычислен размер позиции: {}", positionSize);

                if (isInvalidPositionSize(positionSize, tradingPair)) {
                    log.warn("Недостаточный размер позиции для пары {}: {}", tradingPair.getPairName(), positionSize);
                    return buildFailure();
                }

                BigDecimal[] adaptiveAmounts = adaptiveAmountService.calculate(provider, tradingPair, positionSize);
                BigDecimal longAmount = adaptiveAmounts[0];
                BigDecimal shortAmount = adaptiveAmounts[1];
                log.debug("Адаптивное распределение средств: ЛОНГ {} = {}, ШОРТ {} = {}",
                        tradingPair.getLongTicker(), longAmount, tradingPair.getShortTicker(), shortAmount);

                if (!validateMinimumLotRequirementsService.validate(provider, tradingPair, longAmount, shortAmount)) {
                    log.debug("Пропуск пары {}: минимальные лоты не соответствуют требованиям", tradingPair.getPairName());
                    return buildFailure();
                }

                BigDecimal leverage = BigDecimal.valueOf(settings.getLeverage());
                log.debug("Используемое кредитное плечо: {}", leverage);

                BigDecimal balanceUSDT = portfolioService.getBalanceUSDT();

                TradeResult longResult = openLong(provider, tradingPair, longAmount, leverage);
                if (!longResult.isSuccess()) {
                    log.error("Ошибка открытия ЛОНГ позиции для {}: {}", tradingPair.getLongTicker(), longResult.getErrorMessage());
                    return buildFailure();
                }

                TradeResult shortResult = openShort(provider, tradingPair, shortAmount, leverage);
                if (shortResult.isSuccess()) {
                    savePositions(tradingPair, longResult, shortResult);
                    log.debug("Успешно открыты и сохранены позиции для пары {}: ЛОНГ ID = {}, ШОРТ ID = {}",
                            tradingPair.getPairName(), longResult.getPositionId(), shortResult.getPositionId());
                    return buildSuccess(longResult, shortResult, balanceUSDT, tradingPair);
                } else {
                    log.error("Ошибка открытия ШОРТ позиции для {}: {}", tradingPair.getShortTicker(), shortResult.getErrorMessage());
                    rollbackIfNecessary(provider, longResult, shortResult);
                    return buildFailure();
                }

            } catch (Exception e) {
                log.error("Критическая ошибка при открытии арбитражной пары {}: {}", tradingPair.getPairName(), e.getMessage(), e);
                return buildFailure();
            } finally {
                log.debug("=== Конец открытия арбитражной пары: {}", tradingPair.getPairName());
            }
        }
    }

    @Override
    public ArbitragePairTradeInfo closeArbitragePair(TradingPair tradingPair) {
        log.debug("===> Начало закрытия арбитражной пары: {}", tradingPair.getPairName());

        synchronized (openPositionLock) {
            try {
                Optional<Position> longPositionOpt = positionRepository.findFirstByPairDataIdAndTypeOrderByIdDesc(tradingPair.getId(), PositionType.LONG);
                Optional<Position> shortPositionOpt = positionRepository.findFirstByPairDataIdAndTypeOrderByIdDesc(tradingPair.getId(), PositionType.SHORT);

                if (longPositionOpt.isEmpty() || shortPositionOpt.isEmpty()) {
                    log.warn("Не найдены ID позиций для пары {}. Закрытие невозможно.", tradingPair.getPairName());
                    return buildFailure();
                }

                TradingProvider provider = tradingProviderFactory.getCurrentProvider();
                log.debug("Текущий торговый провайдер: {}", provider.getClass().getSimpleName());

                log.debug("Начинаем закрытие позиций для пары {}", tradingPair.getPairName());

                TradeResult longResult = closePosition(provider, longPositionOpt.get());
                TradeResult shortResult = closePosition(provider, shortPositionOpt.get());

                if (longResult.isSuccess() && shortResult.isSuccess()) {
                    logSuccess(tradingPair, longResult, shortResult);
                } else {
                    logFailure(tradingPair, longResult, shortResult);
                }

                return ArbitragePairTradeInfo.builder()
                        .success(longResult.isSuccess() && shortResult.isSuccess())
                        .longTradeResult(longResult)
                        .shortTradeResult(shortResult)
                        .build();

            } catch (Exception e) {
                log.error("Критическая ошибка при закрытии арбитражной пары {}: {}", tradingPair.getPairName(), e.getMessage(), e);
                return buildFailure();
            } finally {
                log.debug("<=== Конец закрытия арбитражной пары: {}", tradingPair.getPairName());
            }
        }
    }

    @Override
    public Positioninfo verifyPositionsClosed(TradingPair tradingPair) {
        Optional<Position> longPositionOpt = positionRepository.findFirstByPairDataIdAndTypeOrderByIdDesc(tradingPair.getId(), PositionType.LONG);
        Optional<Position> shortPositionOpt = positionRepository.findFirstByPairDataIdAndTypeOrderByIdDesc(tradingPair.getId(), PositionType.SHORT);

        if (longPositionOpt.isEmpty() || shortPositionOpt.isEmpty()) {
            log.warn("Не найдены ID позиций для пары {}. Предполагаем, что позиции закрыты.", tradingPair.getPairName());
            return buildClosedPositionInfo(BigDecimal.ZERO, BigDecimal.ZERO);
        }

        TradingProvider provider = tradingProviderFactory.getCurrentProvider();
        // ВАЖНО: обновляем цены позиций для получения актуального статуса
//        provider.updatePositionPrices(List.of(pairData.getLongTicker(), pairData.getShortTicker()));

        Position longPosition = provider.getPosition(longPositionOpt.get().getPositionId());
        Position shortPosition = provider.getPosition(shortPositionOpt.get().getPositionId());

        boolean longClosed = isClosed(longPosition);
        boolean shortClosed = isClosed(shortPosition);

        if (longClosed && shortClosed) {
            BigDecimal finalPnlUSDT = calculateTotalPnlUSDT(longPosition, shortPosition);
            BigDecimal finalPnlPercent = calculateTotalPnlPercent(longPosition, shortPosition);
            deletePositions(tradingPair);
            log.debug("Удалены закрытые позиции из репозитория для пары {}. Итоговый PnL: {} USDT ({} %)", tradingPair.getPairName(), finalPnlUSDT, finalPnlPercent);

            return buildClosedPositionInfo(finalPnlUSDT, finalPnlPercent);
        }

        log.warn("Не все позиции закрыты для пары {}: ЛОНГ закрыта={}, ШОРТ закрыта={}", tradingPair.getPairName(), longClosed, shortClosed);
        return buildOpenPositionInfo();
    }

    @Override
    public Positioninfo getOpenPositionsInfo(TradingPair tradingPair) {
        Optional<Position> longPositionOpt = positionRepository.findFirstByPairDataIdAndTypeOrderByIdDesc(tradingPair.getId(), PositionType.LONG);
        Optional<Position> shortPositionOpt = positionRepository.findFirstByPairDataIdAndTypeOrderByIdDesc(tradingPair.getId(), PositionType.SHORT);

        if (longPositionOpt.isEmpty() || shortPositionOpt.isEmpty()) {
            log.warn("Не найдены ID позиций для пары {}. Предполагаем, что позиции закрыты.", tradingPair.getPairName());
            return buildClosedPositionInfo();
        }

        TradingProvider provider = tradingProviderFactory.getCurrentProvider();
        // ВАЖНО: обновляем цены позиций перед получением данных о PnL
//        provider.updatePositionPrices(List.of(pairData.getLongTicker(), pairData.getShortTicker()));

        Position longPosition = provider.getPosition(longPositionOpt.get().getPositionId());
        Position shortPosition = provider.getPosition(shortPositionOpt.get().getPositionId());

        if (areBothOpen(longPosition, shortPosition)) {
//            calculateUnrealizedPnL(longPosition, shortPosition);
            // Отладочное логирование для выявления null значений
            log.debug("🔍 ОТЛАДКА: longPosition.getUnrealizedPnLUSDT() = {}", longPosition.getUnrealizedPnLUSDT());
            log.debug("🔍 ОТЛАДКА: shortPosition.getUnrealizedPnLUSDT() = {}", shortPosition.getUnrealizedPnLUSDT());
            log.debug("🔍 ОТЛАДКА: longPosition.getUnrealizedPnLPercent() = {}", longPosition.getUnrealizedPnLPercent());
            log.debug("🔍 ОТЛАДКА: shortPosition.getUnrealizedPnLPercent() = {}", shortPosition.getUnrealizedPnLPercent());
            log.debug("🔍 ОТЛАДКА: longPosition = {}", longPosition);
            log.debug("🔍 ОТЛАДКА: shortPosition = {}", shortPosition);

            BigDecimal totalPnlUSDT = longPosition.getUnrealizedPnLUSDT().add(shortPosition.getUnrealizedPnLUSDT());

            // Рассчитываем взвешенный процентный профит пары
            BigDecimal totalPnlPercent = calculatePairWeightedPnlPercent(longPosition, shortPosition);

            log.debug("Текущий PnL для открытых позиций пары {}: {} USDT ({} %)", tradingPair.getPairName(), totalPnlUSDT, totalPnlPercent);

            return buildOpenPositionInfo(longPosition, shortPosition, totalPnlUSDT, totalPnlPercent);
        }

        log.warn("Не все позиции открыты для пары {}: ЛОНГ открыта={}, ШОРТ открыта={}",
                tradingPair.getPairName(), isOpen(longPosition), isOpen(shortPosition));

        return buildPartiallyClosedInfo(longPosition, shortPosition);
    }

    @Override
    public Positioninfo getPositionInfo(TradingPair tradingPair) {
        log.debug("Запрос информации о позициях для пары {}", tradingPair.getPairName());

        Optional<Position> longPositionOpt = positionRepository.findFirstByPairDataIdAndTypeOrderByIdDesc(tradingPair.getId(), PositionType.LONG);
        Optional<Position> shortPositionOpt = positionRepository.findFirstByPairDataIdAndTypeOrderByIdDesc(tradingPair.getId(), PositionType.SHORT);

        if (longPositionOpt.isEmpty() || shortPositionOpt.isEmpty()) {
            log.warn("Не найдены ID позиций для пары {}", tradingPair.getPairName());
            return Positioninfo.builder().build();
        }

        TradingProvider provider = tradingProviderFactory.getCurrentProvider();
        log.debug("Текущий торговый провайдер: {}", provider.getClass().getSimpleName());

        Position longPosition = provider.getPosition(longPositionOpt.get().getPositionId());
        Position shortPosition = provider.getPosition(shortPositionOpt.get().getPositionId());

        // Если позиции null в памяти провайдера, используем данные из БД (закрытые позиции)
        if (longPosition == null) {
            longPosition = longPositionOpt.get();
            log.debug("ЛОНГ позиция не найдена в памяти провайдера, используем данные из БД: статус={}", longPosition.getStatus());
        }
        if (shortPosition == null) {
            shortPosition = shortPositionOpt.get();
            log.debug("ШОРТ позиция не найдена в памяти провайдера, используем данные из БД: статус={}", shortPosition.getStatus());
        }

        if (positionsAreNull(longPosition, shortPosition, tradingPair)) {
            log.error("Позиции равны null для пары {}", tradingPair.getPairName());
            return Positioninfo.builder().build();
        }

        boolean bothClosed = isClosed(longPosition) && isClosed(shortPosition);
        log.debug("Статус позиций для пары {}: ЛОНГ закрыта={}, ШОРТ закрыта={}", tradingPair.getPairName(), isClosed(longPosition), isClosed(shortPosition));

        if (bothClosed) {
            log.debug("Обе позиции для пары {} уже закрыты.", tradingPair.getPairName());
            return buildPositionInfo(true, longPosition, shortPosition);
        }

        log.debug("Позиции для пары {} еще открыты, обновляем цены...", tradingPair.getPairName());
//        provider.updatePositionPrices(List.of(pairData.getLongTicker(), pairData.getShortTicker()));
//        log.debug("Цены для пары {} обновлены.", pairData.getPairName());

        return buildPositionInfo(false, longPosition, shortPosition);
    }

    @Override
    public void deletePositions(TradingPair tradingPair) {
        log.debug("Удаляем сохранённые позиции из бд для пары {}", tradingPair.getPairName());
        List<Position> longPositions = positionRepository.findAllByPairDataIdAndType(tradingPair.getId(), PositionType.LONG);
        List<Position> shortPositions = positionRepository.findAllByPairDataIdAndType(tradingPair.getId(), PositionType.SHORT);

        positionRepository.deleteAll(longPositions);
        positionRepository.deleteAll(shortPositions);

        log.debug("Удалены позиции для пары {}: {} лонг позиций, {} шорт позиций",
                tradingPair.getPairName(), longPositions.size(), shortPositions.size());
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

    private boolean isInvalidPositionSize(BigDecimal size, TradingPair tradingPair) {
        if (size.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("⚠️ Недостаточно средств для открытия позиций по паре {}. Размер позиции: {}", tradingPair.getPairName(), size);
            return true;
        }
        return false;
    }

    private TradeResult openLong(TradingProvider provider, TradingPair tradingPair, BigDecimal amount, BigDecimal leverage) {
        log.debug("🟢 Открытие ЛОНГ позиции по тикеру {}. Сумма: {}, плечо: {}", tradingPair.getLongTicker(), amount, leverage);
        TradeResult result = provider.openLongPosition(tradingPair.getLongTicker(), amount, leverage);

        if (result.isSuccess()) {
            log.debug("✅ ЛОНГ позиция по тикеру {} успешно открыта. ID позиции: {}, PnL: {} USDT ({} %), комиссии: {}",
                    tradingPair.getLongTicker(),
                    result.getPositionId(),
                    result.getPnlUSDT(),
                    result.getPnlPercent() != null ? result.getPnlPercent() : BigDecimal.ZERO,
                    result.getFees());
        } else {
            log.warn("❌ Не удалось открыть ЛОНГ позицию по тикеру {}. Ошибка: {}", tradingPair.getLongTicker(), result.getErrorMessage());
        }

        return result;
    }

    private TradeResult openShort(TradingProvider provider, TradingPair tradingPair, BigDecimal amount, BigDecimal leverage) {
        log.debug("🔴 Открытие ШОРТ позиции по тикеру {}. Сумма: {}, плечо: {}", tradingPair.getShortTicker(), amount, leverage);
        TradeResult result = provider.openShortPosition(tradingPair.getShortTicker(), amount, leverage);

        if (result.isSuccess()) {
            log.debug("✅ ШОРТ позиция по тикеру {} успешно открыта. ID позиции: {}, PnL: {} USDT ({} %), комиссии: {}",
                    tradingPair.getShortTicker(),
                    result.getPositionId(),
                    result.getPnlUSDT(),
                    result.getPnlPercent() != null ? result.getPnlPercent() : BigDecimal.ZERO,
                    result.getFees());
        } else {
            log.warn("❌ Не удалось открыть ШОРТ позицию по тикеру {}. Ошибка: {}", tradingPair.getShortTicker(), result.getErrorMessage());
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

    private void savePositions(TradingPair tradingPair, TradeResult longResult, TradeResult shortResult) {
        // Обрабатываем лонг позицию
        Position newLongPosition = longResult.getPosition();
        newLongPosition.setPairDataId(tradingPair.getId());

        Optional<Position> existingLongOpt = positionRepository.findFirstByPairDataIdAndTypeOrderByIdDesc(tradingPair.getId(), PositionType.LONG);
        Position finalLongPosition;

        if (existingLongOpt.isPresent()) {
            // Усреднение - обновляем существующую позицию актуальными данными от OKX
            Position existingLong = existingLongOpt.get();
            log.debug("🔄 Обновление существующей ЛОНГ позиции при усреднении для пары {}: ID = {}", tradingPair.getPairName(), existingLong.getPositionId());

            // ВАЖНО: сохраняем тот же positionId при усреднении
            String existingPositionId = existingLong.getPositionId();

            // Обновляем актуальными данными от OKX после усреднения
            existingLong.setSize(newLongPosition.getSize());
            existingLong.setEntryPrice(newLongPosition.getEntryPrice()); // Новая средняя цена
            existingLong.setCurrentPrice(newLongPosition.getCurrentPrice());
            existingLong.setOpeningFees(newLongPosition.getOpeningFees());
            existingLong.setLastUpdated(LocalDateTime.now());

            finalLongPosition = positionRepository.save(existingLong);


            log.debug("✅ Обновлена ЛОНГ позиция: ID = {}, новая средняя цена={}, размер={}",
                    existingPositionId, existingLong.getEntryPrice(), existingLong.getSize());
        } else {
            // Первое открытие позиции
            finalLongPosition = positionRepository.save(newLongPosition);
            log.debug("💾 Создана новая ЛОНГ позиция для пары {}: ID = {}", tradingPair.getPairName(), newLongPosition.getPositionId());
        }

        // Обрабатываем шорт позицию
        Position newShortPosition = shortResult.getPosition();
        newShortPosition.setPairDataId(tradingPair.getId());

        Optional<Position> existingShortOpt = positionRepository.findFirstByPairDataIdAndTypeOrderByIdDesc(tradingPair.getId(), PositionType.SHORT);
        Position finalShortPosition;

        if (existingShortOpt.isPresent()) {
            // Усреднение - обновляем существующую позицию актуальными данными от OKX
            Position existingShort = existingShortOpt.get();
            log.debug("🔄 Обновление существующей ШОРТ позиции при усреднении для пары {}: ID = {}", tradingPair.getPairName(), existingShort.getPositionId());

            // ВАЖНО: сохраняем тот же positionId при усреднении
            String existingPositionId = existingShort.getPositionId();

            // Обновляем актуальными данными от OKX после усреднения
            existingShort.setSize(newShortPosition.getSize());
            existingShort.setEntryPrice(newShortPosition.getEntryPrice()); // Новая средняя цена
            existingShort.setCurrentPrice(newShortPosition.getCurrentPrice());
            existingShort.setOpeningFees(newShortPosition.getOpeningFees());
            existingShort.setLastUpdated(LocalDateTime.now());

            finalShortPosition = positionRepository.save(existingShort);


            log.debug("✅ Обновлена ШОРТ позиция: ID = {}, новая средняя цена={}, размер={}",
                    existingPositionId, existingShort.getEntryPrice(), existingShort.getSize());
        } else {
            // Первое открытие позиции
            finalShortPosition = positionRepository.save(newShortPosition);
            log.debug("💾 Создана новая ШОРТ позиция для пары {}: ID = {}", tradingPair.getPairName(), newShortPosition.getPositionId());
        }

        // Синхронизируем с OKX для получения актуальных данных после усреднения
        if (existingLongOpt.isPresent() || existingShortOpt.isPresent()) {
            log.debug("🔄 Синхронизация с OKX после усреднения для получения актуальных данных");
            TradingProvider provider = tradingProviderFactory.getCurrentProvider();
            if (provider != null) {
                provider.updatePositionPrices(List.of(tradingPair.getLongTicker(), tradingPair.getShortTicker()));
                log.debug("✅ Синхронизация с OKX завершена");
            }
        }

        log.debug("💾 Обработаны позиции для пары {}: ЛОНГ ID = {}, ШОРТ ID = {}",
                tradingPair.getPairName(),
                finalLongPosition.getPositionId(),
                finalShortPosition.getPositionId());
    }

    private ArbitragePairTradeInfo buildSuccess(TradeResult longResult, TradeResult shortResult, BigDecimal balanceUSDT, TradingPair tradingPair) {
        log.debug("✅ УСПЕХ: Арбитражная пара открыта — ЛОНГ: {} (ID: {}), ШОРТ: {} (ID: {}), БАЛАНС 'ДО': {} USDT",
                tradingPair.getLongTicker(),
                longResult.getPositionId(),
                tradingPair.getShortTicker(),
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
            // Обновляем позицию данными от OKX
            position.setStatus(PositionStatus.CLOSED);
            if (result.getPnlUSDT() != null) {
                position.setRealizedPnLUSDT(result.getPnlUSDT());
            }
            if (result.getPnlPercent() != null) {
                position.setRealizedPnLPercent(result.getPnlPercent());
            }
            if (result.getExecutionPrice() != null) {
                position.setClosingPrice(result.getExecutionPrice());
            }
            position.setLastUpdated(LocalDateTime.now());

            positionRepository.save(position);
            log.debug("✅ Позиция {} успешно закрыта и обновлена в БД. ID: {}, PnL: {} USDT ({} %), Комиссия: {}",
                    positionLabel, position.getPositionId(), safeGet(result.getPnlUSDT()), safeGet(result.getPnlPercent()), safeGet(result.getFees()));
        } else {
            log.warn("❌ Не удалось закрыть {} позицию. ID: {}, Ошибка: {}",
                    positionLabel, position.getPositionId(), result.getErrorMessage());
        }

        return result;
    }

    private void logSuccess(TradingPair tradingPair, TradeResult longResult, TradeResult shortResult) {
        BigDecimal totalPnLUSDT = safeGet(longResult.getPnlUSDT()).add(safeGet(shortResult.getPnlUSDT()));
        BigDecimal totalPnLPercent = safeGet(longResult.getPnlPercent()).add(safeGet(shortResult.getPnlPercent()));
        BigDecimal totalFees = safeGet(longResult.getFees()).add(safeGet(shortResult.getFees()));

        log.debug("✅ Арбитражная пара: {} УСПЕШНО закрыта.", tradingPair.getPairName());
        log.debug("📈 Общий доход (PnL): {} USDT ({} %)", totalPnLUSDT, totalPnLPercent);
        log.debug("💸 Общая комиссия: {} USDT", totalFees);
        log.debug("🟢 ЛОНГ: PnL = {} USDT ({} %), комиссия = {}", safeGet(longResult.getPnlUSDT()), safeGet(longResult.getPnlPercent()), safeGet(longResult.getFees()));
        log.debug("🔴 ШОРТ: PnL = {} USDT ({} %), комиссия = {}", safeGet(shortResult.getPnlUSDT()), safeGet(shortResult.getPnlPercent()), safeGet(shortResult.getFees()));
    }

    private void logFailure(TradingPair tradingPair, TradeResult longResult, TradeResult shortResult) {
        log.error("❌ Ошибка при закрытии арбитражной пары {}.", tradingPair.getPairName());
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

    private boolean positionsAreNull(Position longPosition, Position shortPosition, TradingPair tradingPair) {
        if (longPosition == null || shortPosition == null) {
            log.error("❌ Ошибка: позиции равны null для пары '{}'. ЛОНГ позиция: {}, ШОРТ позиция: {}",
                    tradingPair.getPairName(), longPosition, shortPosition);
            return true;
        }
        log.debug("Получены позиции для пары '{}': ЛОНГ={}, ШОРТ={}", tradingPair.getPairName(), longPosition, shortPosition);
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

    /**
     * Расчет взвешенного процентного профита для пары позиций
     * Формула: (PnL%_long * allocation_long + PnL%_short * allocation_short) / (allocation_long + allocation_short)
     */
    private BigDecimal calculatePairWeightedPnlPercent(Position longPosition, Position shortPosition) {
        if (longPosition == null || shortPosition == null) {
            log.warn("⚠️ Одна из позиций равна null: long={}, short={}", longPosition != null, shortPosition != null);
            return BigDecimal.ZERO;
        }

        // Безопасное получение allocated amounts
        BigDecimal longAlloc = safeGet(longPosition.getAllocatedAmount());
        BigDecimal shortAlloc = safeGet(shortPosition.getAllocatedAmount());
        BigDecimal totalAlloc = longAlloc.add(shortAlloc);

        if (totalAlloc.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("⚠️ Нулевое allocatedAmount для пары: long={}, short={}", longAlloc, shortAlloc);
            return BigDecimal.ZERO;
        }

        // Безопасное получение процентных PnL
        BigDecimal longPnlPercent = safeGet(longPosition.getUnrealizedPnLPercent());
        BigDecimal shortPnlPercent = safeGet(shortPosition.getUnrealizedPnLPercent());

        // Взвешенный процентный профит: (P1 * A1 + P2 * A2) / (A1 + A2)
        BigDecimal weightedPnlPercent = longPnlPercent.multiply(longAlloc)
                .add(shortPnlPercent.multiply(shortAlloc))
                .divide(totalAlloc, 8, RoundingMode.HALF_UP);

        log.debug("📊 Взвешенный PnL% для пары: long={}% ({}), short={}% ({}) -> result={}%",
                longPnlPercent, longAlloc, shortPnlPercent, shortAlloc, weightedPnlPercent);

        return weightedPnlPercent;
    }

    /**
     * Безопасное получение значения с заменой null на ZERO
     */
    private BigDecimal safeGet(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
