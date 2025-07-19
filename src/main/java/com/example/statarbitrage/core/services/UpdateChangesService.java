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
public class UpdateChangesService {
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

            // Обновляем текущие цены
            pairData.setLongTickerCurrentPrice(longPosition.getCurrentPrice().doubleValue());
            pairData.setShortTickerCurrentPrice(shortPosition.getCurrentPrice().doubleValue());

            // Рассчитываем текущий нереализованный профит
            BigDecimal totalPnL = longPosition.getUnrealizedPnL().add(shortPosition.getUnrealizedPnL());
            BigDecimal totalFees = longPosition.getOpeningFees().add(shortPosition.getOpeningFees());
            BigDecimal netPnL = totalPnL.subtract(totalFees);

            // Конвертируем в процент от позиции
            BigDecimal profitPercent = calculateProfitPercent(
                    netPnL,
                    pairData.getLongTickerEntryPrice(),
                    pairData.getShortTickerEntryPrice()
            );

            pairData.setProfitChanges(profitPercent);

            log.info("📊 Обновлен профит из открытых позиций {}/{}: {}% (PnL: {}, комиссии: {})",
                    pairData.getLongTicker(), pairData.getShortTicker(),
                    profitPercent, totalPnL, totalFees);

            // Обновляем статистику и экстремумы
            updatePairDataStatistics(pairData);

            log.info("✅ Обновлены данные из открытых позиций для пары {}/{}",
                    pairData.getLongTicker(), pairData.getShortTicker());

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

            // Обновляем текущие цены на основе фактических цен исполнения
            pairData.setLongTickerCurrentPrice(longResult.getExecutionPrice().doubleValue());
            pairData.setShortTickerCurrentPrice(shortResult.getExecutionPrice().doubleValue());

            // Рассчитываем чистый профит
            BigDecimal totalPnL = longResult.getPnl().add(shortResult.getPnl());
            BigDecimal totalFees = longResult.getFees().add(shortResult.getFees());
            BigDecimal netPnL = totalPnL.subtract(totalFees);

            // Конвертируем в процент от позиции
            BigDecimal profitPercent = calculateProfitPercent(
                    netPnL,
                    pairData.getLongTickerEntryPrice(),
                    pairData.getShortTickerEntryPrice()
            );

            pairData.setProfitChanges(profitPercent);

            log.info("🏦 Обновлен профит из результатов закрытия {}/{}: {}% (PnL: {}, комиссии: {})",
                    pairData.getLongTicker(), pairData.getShortTicker(),
                    profitPercent, totalPnL, totalFees);

            // Обновляем статистику и экстремумы
            updatePairDataStatistics(pairData);

            log.info("✅ Обновлены данные из результатов закрытия для пары {}/{}",
                    pairData.getLongTicker(), pairData.getShortTicker());

        } catch (Exception e) {
            log.error("❌ Ошибка при обновлении данных из результатов закрытия для пары {}/{}: {}",
                    pairData.getLongTicker(), pairData.getShortTicker(), e.getMessage());
        }
    }

    /**
     * Рассчитывает процент профита от средней входной цены
     * Единая логика расчета для всех типов операций
     */
    private BigDecimal calculateProfitPercent(BigDecimal netPnL, double longEntryPrice, double shortEntryPrice) {
        try {
            BigDecimal longEntry = BigDecimal.valueOf(longEntryPrice);
            BigDecimal shortEntry = BigDecimal.valueOf(shortEntryPrice);
            BigDecimal avgEntryPrice = longEntry.add(shortEntry).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);

            if (avgEntryPrice.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("⚠️ Средняя входная цена меньше или равна нулю: {}", avgEntryPrice);
                return BigDecimal.ZERO;
            }

            return netPnL.divide(avgEntryPrice, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

        } catch (Exception e) {
            log.error("❌ Ошибка при расчете процента профита: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
     * Обновляет статистику и экстремумы для пары
     */
    private void updatePairDataStatistics(PairData pairData) {
        BigDecimal longCurrent = BigDecimal.valueOf(pairData.getLongTickerCurrentPrice());
        BigDecimal shortCurrent = BigDecimal.valueOf(pairData.getShortTickerCurrentPrice());

        BigDecimal zScoreEntry = BigDecimal.valueOf(pairData.getZScoreEntry());
        BigDecimal zScoreCurrent = BigDecimal.valueOf(pairData.getZScoreCurrent());
        BigDecimal corrCurrent = BigDecimal.valueOf(pairData.getCorrelationCurrent());

        // Расчет процентных изменений позиций
        BigDecimal longEntry = BigDecimal.valueOf(pairData.getLongTickerEntryPrice());
        BigDecimal shortEntry = BigDecimal.valueOf(pairData.getShortTickerEntryPrice());

        BigDecimal longReturnPct = longCurrent.subtract(longEntry)
                .divide(longEntry, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        BigDecimal shortReturnPct = shortEntry.subtract(shortCurrent)
                .divide(shortEntry, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        // Текущее время
        long entryTime = pairData.getEntryTime();
        long now = System.currentTimeMillis();
        long currentTimeInMinutes = (now - entryTime) / (1000 * 60);

        // Округления
        BigDecimal longReturnRounded = longReturnPct.setScale(2, RoundingMode.HALF_UP);
        BigDecimal shortReturnRounded = shortReturnPct.setScale(2, RoundingMode.HALF_UP);
        BigDecimal zScoreRounded = zScoreCurrent.subtract(zScoreEntry).setScale(2, RoundingMode.HALF_UP);

        // Используем уже рассчитанный профит для статистики
        BigDecimal currentProfitForStats = pairData.getProfitChanges() != null ? pairData.getProfitChanges() : BigDecimal.ZERO;

        // Обновляем min/max профита
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

        // Обновляем экстремумы всех метрик
        updateExtremumValues(pairData, longReturnPct, shortReturnPct, zScoreCurrent, corrCurrent);

        // Записываем в PairData (профит НЕ трогаем - он уже обновлен через ProfitUpdateService)
        pairData.setLongChanges(longReturnRounded);
        pairData.setShortChanges(shortReturnRounded);
        pairData.setZScoreChanges(zScoreRounded);
        pairData.setMinProfitRounded(minProfitRounded);
        pairData.setMaxProfitRounded(maxProfitRounded);
        pairData.setTimeInMinutesSinceEntryToMax(timeInMinutesSinceEntryToMax);
        pairData.setTimeInMinutesSinceEntryToMin(timeInMinutesSinceEntryToMin);

        log.info("Финальное обновление изменений для пары {}/{}", pairData.getLongTicker(), pairData.getShortTicker());
        log.info("📊 LONG {}: Entry: {}, Current: {}, Changes: {}%", pairData.getLongTicker(), longEntry, longCurrent, longReturnRounded);
        log.info("📉 SHORT {}: Entry: {}, Current: {}, Changes: {}%", pairData.getShortTicker(), shortEntry, shortCurrent, shortReturnRounded);
        log.info("💰 Текущий профит: {}%", currentProfitForStats);
        log.info("📈 Max profit: {}%, Min profit: {}%", maxProfitRounded, minProfitRounded);
    }

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
