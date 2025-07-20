package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.dto.ChangesData;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.trading.model.Position;
import com.example.statarbitrage.trading.model.PositionVerificationResult;
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

    private static final BigDecimal PERCENTAGE_MULTIPLIER = BigDecimal.valueOf(100);
    private static final BigDecimal DIVISION_FOR_AVERAGE = BigDecimal.valueOf(2);
    private static final int PROFIT_CALCULATION_SCALE = 4;
    private static final int PERCENTAGE_CALCULATION_SCALE = 10;
    private static final int DISPLAY_SCALE = 2;
    private static final long MILLISECONDS_IN_MINUTE = 1000 * 60;

    private final TradingIntegrationService tradingIntegrationService;

    public ChangesData getChanges(PairData pairData) {
        try {
            // Получаем данные об открытых позициях
            PositionVerificationResult positionsInfo = tradingIntegrationService.getPositionInfo(pairData);

            if (positionsInfo == null || positionsInfo.getLongPosition() == null || positionsInfo.getShortPosition() == null) {
                log.warn("⚠️ Не удалось получить информацию о позициях для пары {}/{}",
                        pairData.getLongTicker(), pairData.getShortTicker());
                return new ChangesData();
            }

            return getFromPositions(pairData, positionsInfo);

        } catch (Exception e) {
            log.error("❌ Ошибка при обновлении данных из позиций для пары {}/{}: {}",
                    pairData.getLongTicker(), pairData.getShortTicker(), e.getMessage());
        }
        return new ChangesData();
    }

    /**
     * Общий метод обновления данных из открытых позиций
     */
    private ChangesData getFromPositions(PairData pairData, PositionVerificationResult positionsInfo) {

        Position longPosition = positionsInfo.getLongPosition();
        Position shortPosition = positionsInfo.getShortPosition();
        boolean isPositionsClosed = positionsInfo.isPositionsClosed();

        ChangesData changesData = new ChangesData();

        //todo возможно тут нужно разделять расчет для OPEN/CLOSE позиций

        changesData.setLongCurrentPrice(longPosition.getCurrentPrice()); //todo проверить что есть такое
        changesData.setShortCurrentPrice(shortPosition.getCurrentPrice());

        // Рассчитываем текущий нереализованный профит
        BigDecimal totalPnL = longPosition.getUnrealizedPnL().add(shortPosition.getUnrealizedPnL()); //todo см как в Position
        BigDecimal totalFees = longPosition.getOpeningFees().add(shortPosition.getOpeningFees());

        return getProfitAndStatistics(pairData, changesData, totalPnL, totalFees, isPositionsClosed);
    }

    /**
     * Общий метод обновления профита и статистики
     */
    private ChangesData getProfitAndStatistics(PairData pairData, ChangesData changesData, BigDecimal totalPnL, BigDecimal totalFees,
                                               boolean isPositionsClosed) {

        BigDecimal netPnL = totalPnL.subtract(totalFees);

        // Конвертируем в процент от позиции
        BigDecimal profitPercent = calculateProfitPercent(
                netPnL,
                pairData.getLongTickerEntryPrice(),
                pairData.getShortTickerEntryPrice()
        );

        changesData.setProfitChanges(profitPercent);

        log.info("Получен профит из {}: {}/{}: {}% (PnL: {}, комиссии: {})",
                isPositionsClosed ? "закрытых позиций" : "открытых позиций", pairData.getLongTicker(), pairData.getShortTicker(),
                profitPercent, totalPnL, totalFees);

        // Обновляем статистику и экстремумы
        return getStatistics(pairData, changesData);
    }

    /**
     * Рассчитывает процент профита от средней входной цены
     * Единая логика расчета для всех типов операций
     */
    private BigDecimal calculateProfitPercent(BigDecimal netPnL, double longEntryPrice, double shortEntryPrice) {
        try {
            BigDecimal longEntry = BigDecimal.valueOf(longEntryPrice);
            BigDecimal shortEntry = BigDecimal.valueOf(shortEntryPrice);
            BigDecimal avgEntryPrice = longEntry.add(shortEntry).divide(DIVISION_FOR_AVERAGE, PROFIT_CALCULATION_SCALE, RoundingMode.HALF_UP); //todo почему делаем это для разных монет и цен???

            if (avgEntryPrice.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("⚠️ Средняя входная цена меньше или равна нулю: {}", avgEntryPrice);
                return BigDecimal.ZERO;
            }

            return netPnL.divide(avgEntryPrice, PROFIT_CALCULATION_SCALE, RoundingMode.HALF_UP)
                    .multiply(PERCENTAGE_MULTIPLIER);

        } catch (Exception e) {
            log.error("❌ Ошибка при расчете процента профита: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
     * Обновляет статистику и экстремумы для пары
     */
    private ChangesData getStatistics(PairData pairData, ChangesData changesData) {
        calculatePercentageChanges(pairData, changesData);

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

    /**
     * Рассчитывает процентные изменения позиций
     */
    private void calculatePercentageChanges(PairData pairData, ChangesData changesData) {
        BigDecimal longCurrent = changesData.getLongCurrentPrice();
        BigDecimal shortCurrent = changesData.getShortCurrentPrice();
        BigDecimal longEntry = BigDecimal.valueOf(pairData.getLongTickerEntryPrice());
        BigDecimal shortEntry = BigDecimal.valueOf(pairData.getShortTickerEntryPrice());

        BigDecimal longReturnPct = longCurrent.subtract(longEntry)
                .divide(longEntry, PERCENTAGE_CALCULATION_SCALE, RoundingMode.HALF_UP)
                .multiply(PERCENTAGE_MULTIPLIER);

        BigDecimal shortReturnPct = shortEntry.subtract(shortCurrent)
                .divide(shortEntry, PERCENTAGE_CALCULATION_SCALE, RoundingMode.HALF_UP)
                .multiply(PERCENTAGE_MULTIPLIER);

        BigDecimal zScoreEntry = BigDecimal.valueOf(pairData.getZScoreEntry());
        BigDecimal zScoreCurrent = BigDecimal.valueOf(pairData.getZScoreCurrent());

        changesData.setLongChanges(longReturnPct.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP));
        changesData.setShortChanges(shortReturnPct.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP));
        changesData.setZScoreChanges(zScoreCurrent.subtract(zScoreEntry).setScale(DISPLAY_SCALE, RoundingMode.HALF_UP));

//        return new PercentageChanges(
//                longReturnPct.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP),
//                shortReturnPct.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP),
//                zScoreCurrent.subtract(zScoreEntry).setScale(DISPLAY_SCALE, RoundingMode.HALF_UP),
//                longReturnPct,
//                shortReturnPct,
//                longEntry,
//                shortEntry,
//                longCurrent,
//                shortCurrent
//        );
    }

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
        BigDecimal currentProfitForStats = changesData.getProfitChanges() != null ? changesData.getProfitChanges() : BigDecimal.ZERO;

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
        log.info("📊 LONG {}: Entry: {}, Current: {}, Changes: {}%",
                pairData.getLongTicker(), pairData.getLongTickerEntryPrice(), pairData.getLongTickerCurrentPrice(), changesData.getLongChanges());
        log.info("📉 SHORT {}: Entry: {}, Current: {}, Changes: {}%",
                pairData.getShortTicker(), pairData.getShortTickerEntryPrice(), pairData.getShortTickerCurrentPrice(), changesData.getShortChanges());
        log.info("💰 Текущий профит: {}%", changesData.getProfitChanges());
        log.info("📈 Max profit: {}%, Min profit: {}%", changesData.getMaxProfitChanges(), changesData.getMinProfitChanges());
    }

    /**
     * Записи для хранения промежуточных данных расчетов
     */
    private record PercentageChanges(
            BigDecimal longReturnRounded,
            BigDecimal shortReturnRounded,
            BigDecimal zScoreRounded,
            BigDecimal longReturnPct,
            BigDecimal shortReturnPct,
            BigDecimal longEntry,
            BigDecimal shortEntry,
            BigDecimal longCurrent,
            BigDecimal shortCurrent
    ) {
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
