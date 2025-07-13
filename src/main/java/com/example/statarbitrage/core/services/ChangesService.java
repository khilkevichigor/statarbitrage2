package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.core.repositories.PairDataRepository;
import com.example.statarbitrage.trading.model.Portfolio;
import com.example.statarbitrage.trading.services.TradingIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChangesService {
    private final SettingsService settingsService;
    private final PairDataRepository pairDataRepository;
    private final TradingIntegrationService tradingIntegrationService;

    public void calculateVirtual(PairData pairData) {
        Settings settings = settingsService.getSettings();

        double capitalLong = settings.getMaxPositionLong();
        double capitalShort = settings.getMaxPositionShort();
        double leverage = settings.getLeverage();
        double feePctPerTrade = settings.getFeePctPerTrade();

        BigDecimal longEntry = BigDecimal.valueOf(pairData.getLongTickerEntryPrice());
        BigDecimal longCurrent = BigDecimal.valueOf(pairData.getLongTickerCurrentPrice());
        BigDecimal shortEntry = BigDecimal.valueOf(pairData.getShortTickerEntryPrice());
        BigDecimal shortCurrent = BigDecimal.valueOf(pairData.getShortTickerCurrentPrice());
        BigDecimal zScoreEntry = BigDecimal.valueOf(pairData.getZScoreEntry());
        BigDecimal zScoreCurrent = BigDecimal.valueOf(pairData.getZScoreCurrent());
        BigDecimal corrCurrent = BigDecimal.valueOf(pairData.getCorrelationCurrent());

        BigDecimal longReturnPct = longCurrent.subtract(longEntry)
                .divide(longEntry, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        BigDecimal shortReturnPct = shortEntry.subtract(shortCurrent)
                .divide(shortEntry, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        BigDecimal totalCapital = BigDecimal.valueOf(capitalLong + capitalShort);
        BigDecimal leverageBD = BigDecimal.valueOf(leverage);
        BigDecimal feePct = BigDecimal.valueOf(feePctPerTrade);

        BigDecimal longPL = longReturnPct
                .multiply(BigDecimal.valueOf(capitalLong).multiply(leverageBD))
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);

        BigDecimal shortPL = shortReturnPct
                .multiply(BigDecimal.valueOf(capitalShort).multiply(leverageBD))
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);

        BigDecimal totalPL = longPL.add(shortPL);

        BigDecimal totalFees = BigDecimal.valueOf(capitalLong + capitalShort)
                .multiply(leverageBD)
                .multiply(feePct)
                .multiply(BigDecimal.valueOf(2)) // вход + выход
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);

        BigDecimal netPL = totalPL.subtract(totalFees);

        BigDecimal profitPercentFromTotal = netPL
                .divide(totalCapital, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        // Текущее время
        long entryTime = pairData.getEntryTime();
        long now = System.currentTimeMillis();

        // Округления
        BigDecimal longReturnRounded = longReturnPct.setScale(2, RoundingMode.HALF_UP);
        BigDecimal shortReturnRounded = shortReturnPct.setScale(2, RoundingMode.HALF_UP);
        BigDecimal profitRounded = profitPercentFromTotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal zScoreRounded = zScoreCurrent.subtract(zScoreEntry).setScale(2, RoundingMode.HALF_UP);

        // 🔄 Отслеживание максимумов и минимумов с учетом истории
        BigDecimal currentMinProfit = pairData.getMinProfitRounded();
        BigDecimal currentMaxProfit = pairData.getMaxProfitRounded();
        long currentTimeToMax = pairData.getTimeInMinutesSinceEntryToMax();
        long currentTimeToMin = pairData.getTimeInMinutesSinceEntryToMin();

        long currentTimeInMinutes = (now - entryTime) / (1000 * 60);

        // Обновляем максимум прибыли
        BigDecimal maxProfitRounded;
        long timeInMinutesSinceEntryToMax;
        if (currentMaxProfit == null || profitRounded.compareTo(currentMaxProfit) > 0) {
            maxProfitRounded = profitRounded;
            timeInMinutesSinceEntryToMax = currentTimeInMinutes;
            log.debug("🚀 Новый максимум прибыли: {}% за {} мин", maxProfitRounded, timeInMinutesSinceEntryToMax);
        } else {
            maxProfitRounded = currentMaxProfit;
            timeInMinutesSinceEntryToMax = currentTimeToMax;
        }

        // Обновляем минимум прибыли
        BigDecimal minProfitRounded;
        long timeInMinutesSinceEntryToMin;
        if (currentMinProfit == null || profitRounded.compareTo(currentMinProfit) < 0) {
            minProfitRounded = profitRounded;
            timeInMinutesSinceEntryToMin = currentTimeInMinutes;
            log.debug("📉 Новый минимум прибыли: {}% за {} мин", minProfitRounded, timeInMinutesSinceEntryToMin);
        } else {
            minProfitRounded = currentMinProfit;
            timeInMinutesSinceEntryToMin = currentTimeToMin;
        }

        // Отслеживание экстремумов других показателей
        BigDecimal minZ = updateMin(pairData.getMinZ(), zScoreCurrent);
        BigDecimal maxZ = updateMax(pairData.getMaxZ(), zScoreCurrent);

        BigDecimal minLong = updateMin(pairData.getMinLong(), longReturnPct);
        BigDecimal maxLong = updateMax(pairData.getMaxLong(), longReturnPct);

        BigDecimal minShort = updateMin(pairData.getMinShort(), shortReturnPct);
        BigDecimal maxShort = updateMax(pairData.getMaxShort(), shortReturnPct);

        BigDecimal minCorr = updateMin(pairData.getMinCorr(), corrCurrent);
        BigDecimal maxCorr = updateMax(pairData.getMaxCorr(), corrCurrent);

        // ✅ Записываем в PairData
        pairData.setLongChanges(longReturnRounded);
        pairData.setShortChanges(shortReturnRounded);
        pairData.setProfitChanges(profitRounded);
        pairData.setZScoreChanges(zScoreRounded);

        pairData.setMinProfitRounded(minProfitRounded);
        pairData.setMaxProfitRounded(maxProfitRounded);
        pairData.setTimeInMinutesSinceEntryToMax(timeInMinutesSinceEntryToMax);
        pairData.setTimeInMinutesSinceEntryToMin(timeInMinutesSinceEntryToMin);

        pairData.setMinZ(minZ);
        pairData.setMaxZ(maxZ);
        pairData.setMinLong(minLong);
        pairData.setMaxLong(maxLong);
        pairData.setMinShort(minShort);
        pairData.setMaxShort(maxShort);
        pairData.setMinCorr(minCorr);
        pairData.setMaxCorr(maxCorr);

        pairDataRepository.save(pairData);

        // 📝 Логирование
        log.info("📊 LONG {}: Entry: {}, Current: {}, Changes: {}%",
                pairData.getLongTicker(), longEntry, longCurrent, longReturnRounded);

        log.info("📉 SHORT {}: Entry: {}, Current: {}, Changes: {}%",
                pairData.getShortTicker(), shortEntry, shortCurrent, shortReturnRounded);

        log.info("📊 Z Entry: {}, Current: {}, ΔZ: {}",
                zScoreEntry, zScoreCurrent, zScoreRounded);

        log.info("💰 Профит (плечо {}x, комиссия {}%): {}%", leverage, feePctPerTrade, profitRounded);

        log.info("📈 Max profit: {}%, Min profit: {}%", maxProfitRounded, minProfitRounded);
        log.info("⏱ Время до max: {} мин, до min: {} мин", timeInMinutesSinceEntryToMax, timeInMinutesSinceEntryToMin);
        log.info("📊 Z max/min: {} / {}", maxZ, minZ);
        log.info("📈 Long max/min: {} / {}", maxLong, minLong);
        log.info("📉 Short max/min: {} / {}", maxShort, minShort);
        log.info("📉 Corr max/min: {} / {}", maxCorr, minCorr);
    }

    /**
     * Расчет профита для реальной торговли на основе открытых позиций
     */
    public void calculateReal(PairData pairData) {
        try {
            // Получаем текущие цены
            BigDecimal longCurrent = BigDecimal.valueOf(pairData.getLongTickerCurrentPrice());
            BigDecimal shortCurrent = BigDecimal.valueOf(pairData.getShortTickerCurrentPrice());
            BigDecimal zScoreEntry = BigDecimal.valueOf(pairData.getZScoreEntry());
            BigDecimal zScoreCurrent = BigDecimal.valueOf(pairData.getZScoreCurrent());
            BigDecimal corrCurrent = BigDecimal.valueOf(pairData.getCorrelationCurrent());

            // Сначала обновляем цены позиций с биржи для актуальных данных
            tradingIntegrationService.updateAllPositions();

            // Затем получаем реальный PnL для данной пары с актуальными ценами
            BigDecimal realPnL = tradingIntegrationService.getPositionPnL(pairData);

            // Расчет процентных изменений позиций на основе текущих и входных цен
            BigDecimal longEntry = BigDecimal.valueOf(pairData.getLongTickerEntryPrice());
            BigDecimal shortEntry = BigDecimal.valueOf(pairData.getShortTickerEntryPrice());

            // Процентное изменение LONG позиции
            BigDecimal longReturnPct = longCurrent.subtract(longEntry)
                    .divide(longEntry, 10, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            // Процентное изменение SHORT позиции (инвертировано)
            BigDecimal shortReturnPct = shortEntry.subtract(shortCurrent)
                    .divide(shortEntry, 10, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            // Получаем информацию о портфолио для расчета процентной прибыли
            Portfolio portfolio = tradingIntegrationService.getPortfolioInfo();
            BigDecimal totalBalance = portfolio != null ? portfolio.getTotalBalance() : BigDecimal.ZERO;

            // Расчет профита в процентах от общего портфолио
            BigDecimal profitPercentFromTotal = BigDecimal.ZERO;
            if (totalBalance.compareTo(BigDecimal.ZERO) > 0) {
                profitPercentFromTotal = realPnL
                        .divide(totalBalance, 10, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            }

            // Текущее время
            long entryTime = pairData.getEntryTime();
            long now = System.currentTimeMillis();
            long currentTimeInMinutes = (now - entryTime) / (1000 * 60);

            // Округления
            BigDecimal longReturnRounded = longReturnPct.setScale(2, RoundingMode.HALF_UP);
            BigDecimal shortReturnRounded = shortReturnPct.setScale(2, RoundingMode.HALF_UP);
            BigDecimal profitRounded = profitPercentFromTotal.setScale(2, RoundingMode.HALF_UP);
            BigDecimal zScoreRounded = zScoreCurrent.subtract(zScoreEntry).setScale(2, RoundingMode.HALF_UP);

            // 🔄 Отслеживание максимумов и минимумов с учетом истории
            BigDecimal currentMinProfit = pairData.getMinProfitRounded();
            BigDecimal currentMaxProfit = pairData.getMaxProfitRounded();
            long currentTimeToMax = pairData.getTimeInMinutesSinceEntryToMax();
            long currentTimeToMin = pairData.getTimeInMinutesSinceEntryToMin();

            // Обновляем максимум прибыли
            BigDecimal maxProfitRounded;
            long timeInMinutesSinceEntryToMax;
            if (currentMaxProfit == null || profitRounded.compareTo(currentMaxProfit) > 0) {
                maxProfitRounded = profitRounded;
                timeInMinutesSinceEntryToMax = currentTimeInMinutes;
                log.debug("🚀 Новый максимум прибыли (реальная торговля): {}% за {} мин", maxProfitRounded, timeInMinutesSinceEntryToMax);
            } else {
                maxProfitRounded = currentMaxProfit;
                timeInMinutesSinceEntryToMax = currentTimeToMax;
            }

            // Обновляем минимум прибыли
            BigDecimal minProfitRounded;
            long timeInMinutesSinceEntryToMin;
            if (currentMinProfit == null || profitRounded.compareTo(currentMinProfit) < 0) {
                minProfitRounded = profitRounded;
                timeInMinutesSinceEntryToMin = currentTimeInMinutes;
                log.debug("📉 Новый минимум прибыли (реальная торговля): {}% за {} мин", minProfitRounded, timeInMinutesSinceEntryToMin);
            } else {
                minProfitRounded = currentMinProfit;
                timeInMinutesSinceEntryToMin = currentTimeToMin;
            }

            // Отслеживание экстремумов других показателей
            BigDecimal minZ = updateMin(pairData.getMinZ(), zScoreCurrent);
            BigDecimal maxZ = updateMax(pairData.getMaxZ(), zScoreCurrent);

            BigDecimal minLong = updateMin(pairData.getMinLong(), longReturnPct);
            BigDecimal maxLong = updateMax(pairData.getMaxLong(), longReturnPct);

            BigDecimal minShort = updateMin(pairData.getMinShort(), shortReturnPct);
            BigDecimal maxShort = updateMax(pairData.getMaxShort(), shortReturnPct);

            BigDecimal minCorr = updateMin(pairData.getMinCorr(), corrCurrent);
            BigDecimal maxCorr = updateMax(pairData.getMaxCorr(), corrCurrent);

            // ✅ Записываем в PairData
            pairData.setLongChanges(longReturnRounded);
            pairData.setShortChanges(shortReturnRounded);
            pairData.setProfitChanges(profitRounded);
            pairData.setZScoreChanges(zScoreRounded);

            pairData.setMinProfitRounded(minProfitRounded);
            pairData.setMaxProfitRounded(maxProfitRounded);
            pairData.setTimeInMinutesSinceEntryToMax(timeInMinutesSinceEntryToMax);
            pairData.setTimeInMinutesSinceEntryToMin(timeInMinutesSinceEntryToMin);

            pairData.setMinZ(minZ);
            pairData.setMaxZ(maxZ);
            pairData.setMinLong(minLong);
            pairData.setMaxLong(maxLong);
            pairData.setMinShort(minShort);
            pairData.setMaxShort(maxShort);
            pairData.setMinCorr(minCorr);
            pairData.setMaxCorr(maxCorr);

            pairDataRepository.save(pairData);

            // 📝 Логирование
            log.info("🔴 РЕАЛЬНАЯ ТОРГОВЛЯ - {}/{}", pairData.getLongTicker(), pairData.getShortTicker());
            log.info("📊 LONG {}: Entry: {}, Current: {}, Changes: {}%",
                    pairData.getLongTicker(), longEntry, longCurrent, longReturnRounded);
            log.info("📉 SHORT {}: Entry: {}, Current: {}, Changes: {}%",
                    pairData.getShortTicker(), shortEntry, shortCurrent, shortReturnRounded);
            log.info("📊 Z Entry: {}, Current: {}, ΔZ: {}",
                    zScoreEntry, zScoreCurrent, zScoreRounded);
            log.info("💰 Реальный PnL: {} USDT ({}% от портфолио)",
                    realPnL.setScale(2, RoundingMode.HALF_UP), profitRounded);
            log.info("💼 Общий баланс портфолио: {} USDT", totalBalance.setScale(2, RoundingMode.HALF_UP));
            log.info("📈 Max profit: {}%, Min profit: {}%", maxProfitRounded, minProfitRounded);
            log.info("⏱ Время до max: {} мин, до min: {} мин", timeInMinutesSinceEntryToMax, timeInMinutesSinceEntryToMin);

        } catch (Exception e) {
            log.error("❌ Ошибка при расчете реального профита для пары {}/{}: {}",
                    pairData.getLongTicker(), pairData.getShortTicker(), e.getMessage());
        }
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
