package com.example.statarbitrage.core.services;

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
    public void updateChangesFromOpenPositions(PairData pairData) {
        try {
            // Получаем данные об открытых позициях
            PositionVerificationResult openPositionsInfo = tradingIntegrationService.getOpenPositionsInfo(pairData);

            Position longPosition = openPositionsInfo.getLongPosition();
            Position shortPosition = openPositionsInfo.getShortPosition();

            if (longPosition == null || shortPosition == null) {
                log.warn("⚠️ Не удалось получить информацию о позициях для пары {}/{}",
                        pairData.getLongTicker(), pairData.getShortTicker());
                return;
            }

            // Обновляем данные из позиций
            updateFromPositions(pairData, longPosition, shortPosition, "📊", "открытых позиций");

        } catch (Exception e) {
            log.error("❌ Ошибка при обновлении данных из открытых позиций для пары {}/{}: {}",
                    pairData.getLongTicker(), pairData.getShortTicker(), e.getMessage());
        }
    }

    /**
     * Обновляет все данные используя ProfitUpdateService для закрытых позиций
     */
    public void updateChangesFromTradeResults(PairData pairData, ArbitragePairTradeInfo tradeInfo) {
        try {
            TradeResult longResult = tradeInfo.getLongTradeResult();
            TradeResult shortResult = tradeInfo.getShortTradeResult();

            if (longResult == null || shortResult == null) {
                log.warn("⚠️ Не удалось получить результаты закрытия для пары {}/{}",
                        pairData.getLongTicker(), pairData.getShortTicker());
                return;
            }

            // Обновляем данные из результатов торговли
            updateFromTradeResults(pairData, longResult, shortResult, "🏦", "результатов закрытия");

        } catch (Exception e) {
            log.error("❌ Ошибка при обновлении данных из результатов закрытия для пары {}/{}: {}",
                    pairData.getLongTicker(), pairData.getShortTicker(), e.getMessage());
        }
    }

    /**
     * Общий метод обновления данных из открытых позиций
     */
    private void updateFromPositions(PairData pairData, Position longPosition, Position shortPosition, 
                                   String logEmoji, String operationType) {
        // Обновляем текущие цены
        pairData.setLongTickerCurrentPrice(longPosition.getCurrentPrice().doubleValue());
        pairData.setShortTickerCurrentPrice(shortPosition.getCurrentPrice().doubleValue());

        // Рассчитываем текущий нереализованный профит
        BigDecimal totalPnL = longPosition.getUnrealizedPnL().add(shortPosition.getUnrealizedPnL());
        BigDecimal totalFees = longPosition.getOpeningFees().add(shortPosition.getOpeningFees());
        
        updateProfitAndStatistics(pairData, totalPnL, totalFees, logEmoji, operationType);
    }

    /**
     * Общий метод обновления данных из результатов торговли
     */
    private void updateFromTradeResults(PairData pairData, TradeResult longResult, TradeResult shortResult,
                                      String logEmoji, String operationType) {
        // Обновляем текущие цены на основе фактических цен исполнения
        pairData.setLongTickerCurrentPrice(longResult.getExecutionPrice().doubleValue());
        pairData.setShortTickerCurrentPrice(shortResult.getExecutionPrice().doubleValue());

        // Рассчитываем чистый профит
        BigDecimal totalPnL = longResult.getPnl().add(shortResult.getPnl());
        BigDecimal totalFees = longResult.getFees().add(shortResult.getFees());
        
        updateProfitAndStatistics(pairData, totalPnL, totalFees, logEmoji, operationType);
    }

    /**
     * Общий метод обновления профита и статистики
     */
    private void updateProfitAndStatistics(PairData pairData, BigDecimal totalPnL, BigDecimal totalFees,
                                         String logEmoji, String operationType) {
        BigDecimal netPnL = totalPnL.subtract(totalFees);

        // Конвертируем в процент от позиции
        BigDecimal profitPercent = calculateProfitPercent(
                netPnL,
                pairData.getLongTickerEntryPrice(),
                pairData.getShortTickerEntryPrice()
        );

        pairData.setProfitChanges(profitPercent);

        log.info("{} Обновлен профит из {}: {}/{}: {}% (PnL: {}, комиссии: {})",
                logEmoji, operationType, pairData.getLongTicker(), pairData.getShortTicker(),
                profitPercent, totalPnL, totalFees);

        // Обновляем статистику и экстремумы
        updatePairDataStatistics(pairData);

        log.info("✅ Обновлены данные из {} для пары {}/{}",
                operationType, pairData.getLongTicker(), pairData.getShortTicker());
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
    private void updatePairDataStatistics(PairData pairData) {
        PercentageChanges percentageChanges = calculatePercentageChanges(pairData);
        long currentTimeInMinutes = calculateTimeInMinutes(pairData.getEntryTime());
        ProfitExtremums profitExtremums = updateProfitExtremums(pairData, currentTimeInMinutes);
        
        // Обновляем экстремумы всех метрик
        updateExtremumValues(pairData, percentageChanges.longReturnPct(), percentageChanges.shortReturnPct(), 
                           BigDecimal.valueOf(pairData.getZScoreCurrent()), BigDecimal.valueOf(pairData.getCorrelationCurrent()));

        // Записываем в PairData
        setPairDataChanges(pairData, percentageChanges, profitExtremums);
        
        // Логируем результаты
        logFinalResults(pairData, percentageChanges);
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
        
        BigDecimal currentMinProfit = pairData.getMinProfitRounded();
        BigDecimal currentMaxProfit = pairData.getMaxProfitRounded();
        long currentTimeToMax = pairData.getTimeInMinutesSinceEntryToMax();
        long currentTimeToMin = pairData.getTimeInMinutesSinceEntryToMin();

        BigDecimal maxProfitRounded;
        long timeInMinutesSinceEntryToMax;
        if (currentMaxProfit == null || currentProfitForStats.compareTo(currentMaxProfit) > 0) {
            maxProfitRounded = currentProfitForStats;
            timeInMinutesSinceEntryToMax = currentTimeInMinutes;
            log.debug("🚀 Новый максимум прибыли: {}% за {} мин", maxProfitRounded, timeInMinutesSinceEntryToMax);
        } else {
            maxProfitRounded = currentMaxProfit;
            timeInMinutesSinceEntryToMax = currentTimeToMax;
        }

        BigDecimal minProfitRounded;
        long timeInMinutesSinceEntryToMin;
        if (currentMinProfit == null || currentProfitForStats.compareTo(currentMinProfit) < 0) {
            minProfitRounded = currentProfitForStats;
            timeInMinutesSinceEntryToMin = currentTimeInMinutes;
            log.debug("📉 Новый минимум прибыли: {}% за {} мин", minProfitRounded, timeInMinutesSinceEntryToMin);
        } else {
            minProfitRounded = currentMinProfit;
            timeInMinutesSinceEntryToMin = currentTimeToMin;
        }

        return new ProfitExtremums(maxProfitRounded, minProfitRounded, timeInMinutesSinceEntryToMax, 
                                 timeInMinutesSinceEntryToMin, currentProfitForStats);
    }

    /**
     * Записывает данные в PairData
     */
    private void setPairDataChanges(PairData pairData, PercentageChanges changes, ProfitExtremums extremums) {
        pairData.setLongChanges(changes.longReturnRounded());
        pairData.setShortChanges(changes.shortReturnRounded());
        pairData.setZScoreChanges(changes.zScoreRounded());
        pairData.setMinProfitRounded(extremums.minProfit());
        pairData.setMaxProfitRounded(extremums.maxProfit());
        pairData.setTimeInMinutesSinceEntryToMax(extremums.timeToMax());
        pairData.setTimeInMinutesSinceEntryToMin(extremums.timeToMin());
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
        log.info("📈 Max profit: {}%, Min profit: {}%", pairData.getMaxProfitRounded(), pairData.getMinProfitRounded());
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
    ) {}

    private record ProfitExtremums(
            BigDecimal maxProfit,
            BigDecimal minProfit,
            long timeToMax,
            long timeToMin,
            BigDecimal currentProfit
    ) {}

    /**
     * Обновляет экстремумы всех метрик
     */
    private void updateExtremumValues(PairData pairData, BigDecimal longReturnPct, BigDecimal shortReturnPct,
                                      BigDecimal zScoreCurrent, BigDecimal corrCurrent) {
        BigDecimal minZ = updateMin(pairData.getMinZ(), zScoreCurrent);
        BigDecimal maxZ = updateMax(pairData.getMaxZ(), zScoreCurrent);
        BigDecimal minLong = updateMin(pairData.getMinLong(), longReturnPct);
        BigDecimal maxLong = updateMax(pairData.getMaxLong(), longReturnPct);
        BigDecimal minShort = updateMin(pairData.getMinShort(), shortReturnPct);
        BigDecimal maxShort = updateMax(pairData.getMaxShort(), shortReturnPct);
        BigDecimal minCorr = updateMin(pairData.getMinCorr(), corrCurrent);
        BigDecimal maxCorr = updateMax(pairData.getMaxCorr(), corrCurrent);

        pairData.setMinZ(minZ);
        pairData.setMaxZ(maxZ);
        pairData.setMinLong(minLong);
        pairData.setMaxLong(maxLong);
        pairData.setMinShort(minShort);
        pairData.setMaxShort(maxShort);
        pairData.setMinCorr(minCorr);
        pairData.setMaxCorr(maxCorr);
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
