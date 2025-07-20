package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.dto.ChangesData;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.trading.model.ArbitragePairTradeInfo;
import com.example.statarbitrage.trading.model.Position;
import com.example.statarbitrage.trading.model.PositionVerificationResult;
import com.example.statarbitrage.trading.model.TradeResult;
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

    /**
     * Обновляет все данные используя ProfitUpdateService для открытых позиций
     */
    public ChangesData getChangesDataFromOpenPositions(PairData pairData) {
        try {
            // Получаем данные об открытых позициях
            PositionVerificationResult openPositionsInfo = tradingIntegrationService.getOpenPositionsInfo(pairData);

            Position longPosition = openPositionsInfo.getLongPosition();
            Position shortPosition = openPositionsInfo.getShortPosition();

            if (longPosition == null || shortPosition == null) {
                log.warn("⚠️ Не удалось получить информацию о позициях для пары {}/{}",
                        pairData.getLongTicker(), pairData.getShortTicker());
                return new ChangesData();
            }

            // Обновляем данные из позиций
            return getFromPositions(pairData, longPosition, shortPosition, "📊", "открытых позиций");

        } catch (Exception e) {
            log.error("❌ Ошибка при обновлении данных из открытых позиций для пары {}/{}: {}",
                    pairData.getLongTicker(), pairData.getShortTicker(), e.getMessage());
        }
        return new ChangesData();
    }

    /**
     * Обновляет все данные используя ProfitUpdateService для закрытых позиций
     */
    public ChangesData getChangesDataFromTradeResults(PairData pairData, ArbitragePairTradeInfo tradeInfo) {
        try {
            TradeResult longResult = tradeInfo.getLongTradeResult();
            TradeResult shortResult = tradeInfo.getShortTradeResult();

            if (longResult == null || shortResult == null) {
                log.warn("⚠️ Не удалось получить результаты закрытия для пары {}/{}",
                        pairData.getLongTicker(), pairData.getShortTicker());
                return new ChangesData();
            }

            // Обновляем данные из результатов торговли
            return getFromTradeResults(pairData, longResult, shortResult, "🏦", "результатов закрытия");

        } catch (Exception e) {
            log.error("❌ Ошибка при обновлении данных из результатов закрытия для пары {}/{}: {}",
                    pairData.getLongTicker(), pairData.getShortTicker(), e.getMessage());
        }
        return new ChangesData();
    }

    /**
     * Общий метод обновления данных из открытых позиций
     */
    private ChangesData getFromPositions(PairData pairData, Position longPosition, Position shortPosition,
                                         String logEmoji, String operationType) {

        ChangesData changesData = new ChangesData();

        changesData.setLongCurrentPrice(longPosition.getCurrentPrice());
        changesData.setShortCurrentPrice(shortPosition.getCurrentPrice());

        // Рассчитываем текущий нереализованный профит
        BigDecimal totalPnL = longPosition.getUnrealizedPnL().add(shortPosition.getUnrealizedPnL());
        BigDecimal totalFees = longPosition.getOpeningFees().add(shortPosition.getOpeningFees());

        return getProfitAndStatistics(pairData, changesData, totalPnL, totalFees, logEmoji, operationType);
    }

    /**
     * Общий метод обновления данных из результатов торговли
     */
    private ChangesData getFromTradeResults(PairData pairData, TradeResult longResult, TradeResult shortResult,
                                            String logEmoji, String operationType) {

        ChangesData changesData = new ChangesData();
        // Обновляем текущие цены на основе фактических цен исполнения
        changesData.setLongCurrentPrice(longResult.getExecutionPrice());
        changesData.setShortCurrentPrice(shortResult.getExecutionPrice());

        // Рассчитываем чистый профит
        BigDecimal totalPnL = longResult.getPnl().add(shortResult.getPnl());
        BigDecimal totalFees = longResult.getFees().add(shortResult.getFees());

        return getProfitAndStatistics(pairData, changesData, totalPnL, totalFees, logEmoji, operationType);
    }

    /**
     * Общий метод обновления профита и статистики
     */
    private ChangesData getProfitAndStatistics(PairData pairData, ChangesData changesData, BigDecimal totalPnL, BigDecimal totalFees,
                                               String logEmoji, String operationType) {

        BigDecimal netPnL = totalPnL.subtract(totalFees);

        // Конвертируем в процент от позиции
        BigDecimal profitPercent = calculateProfitPercent(
                netPnL,
                pairData.getLongTickerEntryPrice(),
                pairData.getShortTickerEntryPrice()
        );

        changesData.setProfitChanges(profitPercent);

        log.info("{} Получен профит из {}: {}/{}: {}% (PnL: {}, комиссии: {})",
                logEmoji, operationType, pairData.getLongTicker(), pairData.getShortTicker(),
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
            BigDecimal avgEntryPrice = longEntry.add(shortEntry).divide(DIVISION_FOR_AVERAGE, PROFIT_CALCULATION_SCALE, RoundingMode.HALF_UP);

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
        PercentageChanges percentageChanges = calculatePercentageChanges(pairData);
        long currentTimeInMinutes = calculateTimeInMinutes(pairData.getEntryTime());
        ProfitExtremums profitExtremums = updateProfitExtremums(pairData, currentTimeInMinutes);

        // Обновляем экстремумы всех метрик
        updateExtremumValues(pairData, changesData, percentageChanges.longReturnPct(), percentageChanges.shortReturnPct(),
                BigDecimal.valueOf(pairData.getZScoreCurrent()), BigDecimal.valueOf(pairData.getCorrelationCurrent()));

        logFinalResults(pairData, percentageChanges);

        // Записываем в ChangesData
        return setPairDataChanges(changesData, percentageChanges, profitExtremums);
    }

    /**
     * Рассчитывает процентные изменения позиций
     */
    private PercentageChanges calculatePercentageChanges(PairData pairData) {
        BigDecimal longCurrent = BigDecimal.valueOf(pairData.getLongTickerCurrentPrice());
        BigDecimal shortCurrent = BigDecimal.valueOf(pairData.getShortTickerCurrentPrice());
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

        return new PercentageChanges(
                longReturnPct.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP),
                shortReturnPct.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP),
                zScoreCurrent.subtract(zScoreEntry).setScale(DISPLAY_SCALE, RoundingMode.HALF_UP),
                longReturnPct,
                shortReturnPct,
                longEntry,
                shortEntry,
                longCurrent,
                shortCurrent
        );
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
    private ProfitExtremums updateProfitExtremums(PairData pairData, long currentTimeInMinutes) {
        BigDecimal currentProfitForStats = pairData.getProfitChanges() != null ? pairData.getProfitChanges() : BigDecimal.ZERO;

        MaxProfitResult maxResult = updateMaxProfit(currentProfitForStats, pairData.getMaxProfitChanges(), pairData.getTimeInMinutesSinceEntryToMax(), currentTimeInMinutes);
        MinProfitResult minResult = updateMinProfit(currentProfitForStats, pairData.getMinProfitChanges(), pairData.getTimeInMinutesSinceEntryToMin(), currentTimeInMinutes);

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
     * Записывает данные в PairData
     */
    private ChangesData setPairDataChanges(ChangesData changesData, PercentageChanges changes, ProfitExtremums extremums) {
        changesData.setLongChanges(changes.longReturnRounded());
        changesData.setShortChanges(changes.shortReturnRounded());
        changesData.setZScoreChanges(changes.zScoreRounded());
        changesData.setMinProfitChanges(extremums.minProfit());
        changesData.setMaxProfitChanges(extremums.maxProfit());
        changesData.setTimeInMinutesSinceEntryToMax(extremums.timeToMax());
        changesData.setTimeInMinutesSinceEntryToMin(extremums.timeToMin());

        return changesData;
    }

    /**
     * Логирует финальные результаты
     */
    private void logFinalResults(PairData pairData, PercentageChanges changes) {
        log.info("Финальное обновление изменений для пары {}/{}", pairData.getLongTicker(), pairData.getShortTicker());
        log.info("📊 LONG {}: Entry: {}, Current: {}, Changes: {}%",
                pairData.getLongTicker(), changes.longEntry(), changes.longCurrent(), changes.longReturnRounded());
        log.info("📉 SHORT {}: Entry: {}, Current: {}, Changes: {}%",
                pairData.getShortTicker(), changes.shortEntry(), changes.shortCurrent(), changes.shortReturnRounded());
        log.info("💰 Текущий профит: {}%", pairData.getProfitChanges());
        log.info("📈 Max profit: {}%, Min profit: {}%", pairData.getMaxProfitChanges(), pairData.getMinProfitChanges());
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
        if (currentMin == null) return newValue;
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
