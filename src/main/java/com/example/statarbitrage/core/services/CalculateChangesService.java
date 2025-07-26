package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.dto.ChangesData;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.trading.model.Position;
import com.example.statarbitrage.trading.model.Positioninfo;
import com.example.statarbitrage.trading.services.TradingIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalculateChangesService {

    //    private static final BigDecimal PERCENTAGE_MULTIPLIER = BigDecimal.valueOf(100);
//    private static final int PROFIT_CALCULATION_SCALE = 4;
    private static final long MILLISECONDS_IN_MINUTE = 1000 * 60;

    private final TradingIntegrationService tradingIntegrationService;

    public ChangesData getChanges(PairData pairData) {
        log.info("==> getChanges: НАЧАЛО для пары {}/{}", pairData.getLongTicker(), pairData.getShortTicker());
        try {
            // Получаем данные об открытых позициях
            log.info("Запрашиваем информацию о позициях...");
            Positioninfo positionsInfo = tradingIntegrationService.getPositionInfo(pairData);
            log.info("Получена информация о позициях: {}", positionsInfo);

            if (positionsInfo == null || positionsInfo.getLongPosition() == null || positionsInfo.getShortPosition() == null) {
                log.warn("⚠️ Не удалось получить полную информацию о позициях для пары {}/{}. PositionInfo: {}",
                        pairData.getLongTicker(), pairData.getShortTicker(), positionsInfo);
                return new ChangesData(); // Возвращаем пустой объект, чтобы избежать NPE
            }

            ChangesData result = getFromPositions(pairData, positionsInfo);
            log.info("<== getChanges: КОНЕЦ для пары {}/{}. Результат: {}", pairData.getLongTicker(), pairData.getShortTicker(), result);
            return result;

        } catch (Exception e) {
            log.error("❌ КРИТИЧЕСКАЯ ОШИБКА при обновлении данных (getChanges) для пары {}/{}: {}",
                    pairData.getLongTicker(), pairData.getShortTicker(), e.getMessage(), e);
        }
        // В случае исключения, возвращаем пустой объект
        log.info("<== getChanges: КОНЕЦ (с ошибкой) для пары {}/{}", pairData.getLongTicker(), pairData.getShortTicker());
        return new ChangesData();
    }

    /**
     * Общий метод обновления данных из позиций
     */
    private ChangesData getFromPositions(PairData pairData, Positioninfo positionsInfo) {
        log.info("--> getFromPositions: НАЧАЛО для пары {}/{}", pairData.getLongTicker(), pairData.getShortTicker());
        Position longPosition = positionsInfo.getLongPosition();
        Position shortPosition = positionsInfo.getShortPosition();
        boolean isPositionsClosed = positionsInfo.isPositionsClosed();
        log.info("Статус позиций: isPositionsClosed = {}", isPositionsClosed);

        ChangesData changesData = new ChangesData();
        changesData.setLongCurrentPrice(longPosition.getCurrentPrice());
        changesData.setShortCurrentPrice(shortPosition.getCurrentPrice());
        log.info("Текущие цены: LONG {} = {}, SHORT {} = {}", longPosition.getSymbol(), longPosition.getCurrentPrice(), shortPosition.getSymbol(), shortPosition.getCurrentPrice());

        if (isPositionsClosed) {
            log.info("Позиции определены как ЗАКРЫТЫЕ. Переход в getFromClosedPositions.");
            return getFromClosedPositions(pairData, changesData, longPosition, shortPosition);
        } else {
            log.info("Позиции определены как ОТКРЫТЫЕ. Переход в getFromOpenPositions.");
            return getFromOpenPositions(pairData, changesData, longPosition, shortPosition);
        }
    }

    /**
     * Расчет данных для открытых позиций (нереализованный PnL)
     */
    private ChangesData getFromOpenPositions(PairData pairData, ChangesData changesData, Position longPosition, Position shortPosition) {
        log.info("--> getFromOpenPositions для пары {}/{}", pairData.getLongTicker(), pairData.getShortTicker());
        // 1. Суммируем нереализованный PnL (он уже очищен от комиссии за открытие в классе Position)
        BigDecimal netPnlUSDT = longPosition.getUnrealizedPnLUSDT().add(shortPosition.getUnrealizedPnLUSDT());
        log.info("Рассчитан нереализованный PnL в USDT: {} (Long: {}, Short: {})", netPnlUSDT, longPosition.getUnrealizedPnLUSDT(), shortPosition.getUnrealizedPnLUSDT());
        BigDecimal netPnlPercent = longPosition.getUnrealizedPnLPercent().add(shortPosition.getUnrealizedPnLPercent());
        log.info("Рассчитан нереализованный PnL в %: {} (Long: {}, Short: {})", netPnlPercent, longPosition.getUnrealizedPnLPercent(), shortPosition.getUnrealizedPnLPercent());

        // 2. Суммируем комиссии за открытие для статистики
        BigDecimal totalFees = longPosition.getOpeningFees().add(shortPosition.getOpeningFees());
        log.info("Рассчитаны комиссии за открытие: {} (Long: {}, Short: {})", totalFees, longPosition.getOpeningFees(), shortPosition.getOpeningFees());

        // 3. Обновляем статистику
        log.info("Переход в getProfitAndStatistics для открытых позиций.");
        return getProfitAndStatistics(pairData, changesData, netPnlUSDT, netPnlPercent, totalFees, false, longPosition, shortPosition);
    }

    /**
     * Расчет данных для закрытых позиций (реализованный PnL)
     */
    private ChangesData getFromClosedPositions(PairData pairData, ChangesData changesData, Position longPosition, Position shortPosition) {
        log.info("--> getFromClosedPositions для пары {}/{}", pairData.getLongTicker(), pairData.getShortTicker());
        // 1. Суммируем реализованный PnL (он уже очищен от всех комиссий в классе Position)
        BigDecimal netPnlUSDT = longPosition.getRealizedPnL().add(shortPosition.getRealizedPnL());
        log.info("Рассчитан реализованный PnL в USDT: {} (Long: {}, Short: {})", netPnlUSDT, longPosition.getRealizedPnL(), shortPosition.getRealizedPnL());
        BigDecimal netPnlPercent = longPosition.getRealizedPnLPercent().add(shortPosition.getRealizedPnLPercent());
        log.info("Рассчитан реализованный PnL в %: {} (Long: {}, Short: {})", netPnlPercent, longPosition.getRealizedPnLPercent(), shortPosition.getRealizedPnLPercent());

        // 2. Суммируем все комиссии для статистики
        BigDecimal totalFees = (longPosition.getOpeningFees() != null ? longPosition.getOpeningFees() : BigDecimal.ZERO)
                .add(longPosition.getClosingFees() != null ? longPosition.getClosingFees() : BigDecimal.ZERO)
                .add(shortPosition.getOpeningFees() != null ? shortPosition.getOpeningFees() : BigDecimal.ZERO)
                .add(shortPosition.getClosingFees() != null ? shortPosition.getClosingFees() : BigDecimal.ZERO);
        log.info("Рассчитаны общие комиссии (открытие + закрытие): {}", totalFees);

        // 3. Обновляем статистику
        log.info("Переход в getProfitAndStatistics для закрытых позиций.");
        return getProfitAndStatistics(pairData, changesData, netPnlUSDT, netPnlPercent, totalFees, true, longPosition, shortPosition);
    }

    /**
     * Общий метод обновления профита и статистики
     */
    private ChangesData getProfitAndStatistics(PairData pairData, ChangesData changesData, BigDecimal netPnlUSDT, BigDecimal netPnlPercent, BigDecimal totalFees,
                                               boolean isPositionsClosed, Position longPosition, Position shortPosition) {
        log.info("--> getProfitAndStatistics для пары {}/{}. isPositionsClosed={}", pairData.getLongTicker(), pairData.getShortTicker(), isPositionsClosed);

        // Сумма инвестиций
        changesData.setLongAllocatedAmount(longPosition.getAllocatedAmount());
        changesData.setShortAllocatedAmount(shortPosition.getAllocatedAmount());

        // Рассчитываем общую сумму инвестиций из позиций
        BigDecimal totalInvestmentUSDT = longPosition.getAllocatedAmount()
                .add(shortPosition.getAllocatedAmount());

        changesData.setTotalInvestmentUSDT(totalInvestmentUSDT);
        log.info("Общая сумма инвестиций в USDT: {} (Long: {}USDT, Short: {}USDT)", totalInvestmentUSDT, longPosition.getAllocatedAmount(), shortPosition.getAllocatedAmount());

        // Конвертируем в процент от позиции
//        BigDecimal profitPercent = calculateProfitPercent(
//                netPnlUSDT,
//                totalInvestmentUSDT
//        );
        log.info("Рассчитан процент профита: {}", netPnlPercent);

        changesData.setLongChanges(longPosition.getUnrealizedPnLPercent());
        changesData.setShortChanges(shortPosition.getUnrealizedPnLPercent());

        changesData.setProfitUSDTChanges(netPnlUSDT);
        changesData.setProfitPercentChanges(netPnlPercent);

        log.info("Получен профит из {}: {}/{}: {}% (Net PnL: {}USDT, с учетом комиссии: {})",
                isPositionsClosed ? "закрытых позиций" : "открытых позиций", pairData.getLongTicker(), pairData.getShortTicker(),
                netPnlPercent, netPnlUSDT, totalFees);

        // Обновляем статистику и экстремумы
        log.info("Переход в getStatistics для обновления статистики и экстремумов.");
        return getStatistics(pairData, changesData);
    }

//    /**
//     * Рассчитывает процент профита от общей суммы инвестиций (ROI)
//     * Единая логика расчета для всех типов операций
//     */
//    private BigDecimal calculateProfitPercent(BigDecimal netPnlUSDT, BigDecimal totalInvestmentUSDT) {
//        if (totalInvestmentUSDT == null || totalInvestmentUSDT.compareTo(BigDecimal.ZERO) <= 0) {
//            log.warn("⚠️ Сумма инвестиций равна нулю или не задана, невозможно рассчитать процент профита.");
//            return BigDecimal.ZERO;
//        }
//
//        try {
//            return netPnlUSDT.divide(totalInvestmentUSDT, PROFIT_CALCULATION_SCALE, RoundingMode.HALF_UP)
//                    .multiply(PERCENTAGE_MULTIPLIER);
//        } catch (Exception e) {
//            log.error("❌ Ошибка при расчете процента профита: {}", e.getMessage());
//            return BigDecimal.ZERO;
//        }
//    }

    /**
     * Обновляет статистику и экстремумы для пары
     */
    private ChangesData getStatistics(PairData pairData, ChangesData changesData) {
//        calculatePercentageChanges(pairData, changesData, longPosition, shortPosition);

        BigDecimal zScoreEntry = BigDecimal.valueOf(pairData.getZScoreEntry());
        BigDecimal zScoreCurrent = BigDecimal.valueOf(pairData.getZScoreCurrent());
        changesData.setZScoreChanges(zScoreCurrent.subtract(zScoreEntry).setScale(2, RoundingMode.HALF_UP));

        long currentTimeInMinutes = calculateTimeInMinutes(pairData.getEntryTime());
        ProfitExtremums profitExtremums = updateProfitExtremums(changesData, currentTimeInMinutes);

        // Обновляем экстремумы всех метрик
        updateExtremumValues(pairData, changesData, changesData.getLongChanges(), changesData.getShortChanges(),
                BigDecimal.valueOf(pairData.getZScoreCurrent()), BigDecimal.valueOf(pairData.getCorrelationCurrent()));

        // Записываем в ChangesData
        changesData.setMinProfitChanges(profitExtremums.minProfit());
        changesData.setMaxProfitChanges(profitExtremums.maxProfit());

        changesData.setTimeInMinutesSinceEntryToMax(profitExtremums.timeToMax());
        changesData.setTimeInMinutesSinceEntryToMin(profitExtremums.timeToMin());

        logFinalResults(pairData, changesData);

        return changesData;
    }

//    /**
//     * Рассчитывает процентные изменения позиций
//     */
//    private void calculatePercentageChanges(PairData pairData, ChangesData changesData, Position longPosition, Position shortPosition) {
////        BigDecimal longCurrent = changesData.getLongCurrentPrice();
////        BigDecimal shortCurrent = changesData.getShortCurrentPrice();
////        BigDecimal longEntry = BigDecimal.valueOf(pairData.getLongTickerEntryPrice());
////        BigDecimal shortEntry = BigDecimal.valueOf(pairData.getShortTickerEntryPrice());
////
////        BigDecimal longReturnPct = longCurrent.subtract(longEntry)
////                .divide(longEntry, PERCENTAGE_CALCULATION_SCALE, RoundingMode.HALF_UP)
////                .multiply(PERCENTAGE_MULTIPLIER);
////
////        BigDecimal shortReturnPct = shortEntry.subtract(shortCurrent)
////                .divide(shortEntry, PERCENTAGE_CALCULATION_SCALE, RoundingMode.HALF_UP)
////                .multiply(PERCENTAGE_MULTIPLIER);
////
////        BigDecimal zScoreEntry = BigDecimal.valueOf(pairData.getZScoreEntry());
////        BigDecimal zScoreCurrent = BigDecimal.valueOf(pairData.getZScoreCurrent());
////
////        changesData.setLongChanges(longReturnPct.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP));
////        changesData.setShortChanges(shortReturnPct.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP));
////        changesData.setZScoreChanges(zScoreCurrent.subtract(zScoreEntry).setScale(DISPLAY_SCALE, RoundingMode.HALF_UP));
//
//        BigDecimal zScoreEntry = BigDecimal.valueOf(pairData.getZScoreEntry());
//        BigDecimal zScoreCurrent = BigDecimal.valueOf(pairData.getZScoreCurrent());
//        changesData.setZScoreChanges(zScoreCurrent.subtract(zScoreEntry).setScale(2, RoundingMode.HALF_UP));
//
//        changesData.setLongChanges(longPosition.getUnrealizedPnLPercent());
//        changesData.setShortChanges(shortPosition.getUnrealizedPnLPercent());
//    }

    /**
     * Рассчитывает время в минутах с момента входа
     */
    private long calculateTimeInMinutes(long entryTime) {
        long now = System.currentTimeMillis();
        return (now - entryTime) / MILLISECONDS_IN_MINUTE;
    }

    /**
     * Обновляет экстремумы профита
     */
    private ProfitExtremums updateProfitExtremums(ChangesData changesData, long currentTimeInMinutes) {
        BigDecimal currentProfitForStats = changesData.getProfitPercentChanges() != null ? changesData.getProfitPercentChanges() : BigDecimal.ZERO;

        MaxProfitResult maxResult = updateMaxProfit(currentProfitForStats, changesData.getMaxProfitChanges(), changesData.getTimeInMinutesSinceEntryToMax(), currentTimeInMinutes);
        MinProfitResult minResult = updateMinProfit(currentProfitForStats, changesData.getMinProfitChanges(), changesData.getTimeInMinutesSinceEntryToMin(), currentTimeInMinutes);

        return new ProfitExtremums(maxResult.maxProfit(), minResult.minProfit(), maxResult.timeToMax(),
                minResult.timeToMin(), currentProfitForStats);
    }

    private MaxProfitResult updateMaxProfit(BigDecimal currentProfit, BigDecimal currentMaxProfit, long currentTimeToMax, long currentTimeInMinutes) {
        if (currentMaxProfit == null || currentProfit.compareTo(currentMaxProfit) > 0) {
            log.debug("🚀 Новый максимум прибыли: {}% за {} мин", currentProfit, currentTimeInMinutes);
            return new MaxProfitResult(currentProfit, currentTimeInMinutes);
        }
        return new MaxProfitResult(currentMaxProfit, currentTimeToMax);
    }

    private MinProfitResult updateMinProfit(BigDecimal currentProfit, BigDecimal currentMinProfit, long currentTimeToMin, long currentTimeInMinutes) {
        if (currentMinProfit == null || currentProfit.compareTo(currentMinProfit) < 0) {
            log.debug("📉 Новый минимум прибыли: {}% за {} мин", currentProfit, currentTimeInMinutes);
            return new MinProfitResult(currentProfit, currentTimeInMinutes);
        }
        return new MinProfitResult(currentMinProfit, currentTimeToMin);
    }

    /**
     * Логирует финальные результаты
     */
    private void logFinalResults(PairData pairData, ChangesData changesData) {
        log.info("Финальное обновление изменений для пары {}/{}", pairData.getLongTicker(), pairData.getShortTicker());
        log.info("📊 LONG {}: Entry: {}, Current: {}, Changes: {}%", pairData.getLongTicker(), pairData.getLongTickerEntryPrice(), changesData.getLongCurrentPrice(), changesData.getLongChanges());
        log.info("📉 SHORT {}: Entry: {}, Current: {}, Changes: {}%", pairData.getShortTicker(), pairData.getShortTickerEntryPrice(), changesData.getShortCurrentPrice(), changesData.getShortChanges());
        log.info("💰 Текущий профит: {}USDT ({}%)", changesData.getProfitUSDTChanges(), changesData.getProfitPercentChanges());
        log.info("📈 Max profit: {}%, Min profit: {}%", changesData.getMaxProfitChanges(), changesData.getMinProfitChanges());
    }

    private record ProfitExtremums(
            BigDecimal maxProfit,
            BigDecimal minProfit,
            long timeToMax,
            long timeToMin,
            BigDecimal currentProfit
    ) {
    }

    // Helper records for the results
    private record MaxProfitResult(BigDecimal maxProfit, long timeToMax) {
    }

    private record MinProfitResult(BigDecimal minProfit, long timeToMin) {
    }

    /**
     * Обновляет экстремумы всех метрик
     */
    private void updateExtremumValues(PairData pairData, ChangesData changesData, BigDecimal longReturnPct, BigDecimal shortReturnPct,
                                      BigDecimal zScoreCurrent, BigDecimal corrCurrent) {
        changesData.setMinZ(updateMin(pairData.getMinZ(), zScoreCurrent));
        changesData.setMaxZ(updateMax(pairData.getMaxZ(), zScoreCurrent));
        changesData.setMinLong(updateMin(pairData.getMinLong(), longReturnPct));
        changesData.setMaxLong(updateMax(pairData.getMaxLong(), longReturnPct));
        changesData.setMinShort(updateMin(pairData.getMinShort(), shortReturnPct));
        changesData.setMaxShort(updateMax(pairData.getMaxShort(), shortReturnPct));
        changesData.setMinCorr(updateMin(pairData.getMinCorr(), corrCurrent));
        changesData.setMaxCorr(updateMax(pairData.getMaxCorr(), corrCurrent));
    }

    /**
     * Обновляет минимальное значение
     */
    private BigDecimal updateMin(BigDecimal currentMin, BigDecimal newValue) {
        if (currentMin == null) {
            return newValue;
        }
        return newValue.compareTo(currentMin) < 0 ? newValue : currentMin;
    }

    /**
     * Обновляет максимальное значение
     */
    private BigDecimal updateMax(BigDecimal currentMax, BigDecimal newValue) {
        if (currentMax == null) return newValue;
        return newValue.compareTo(currentMax) > 0 ? newValue : currentMax;
    }
}
