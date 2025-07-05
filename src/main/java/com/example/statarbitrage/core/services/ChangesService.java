package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
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

    public void calculateAndAdd(PairData pairData) {
        Settings settings = settingsService.getSettings();

        double capitalLong = settings.getCapitalLong();
        double capitalShort = settings.getCapitalShort();
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

        long timeInMinutesSinceEntryToMax = (now - entryTime) / (1000 * 60);
        long timeInMinutesSinceEntryToMin = timeInMinutesSinceEntryToMax; // сейчас одинаково, т.к. только 1 точка

        // Округления
        BigDecimal longReturnRounded = longReturnPct.setScale(2, RoundingMode.HALF_UP);
        BigDecimal shortReturnRounded = shortReturnPct.setScale(2, RoundingMode.HALF_UP);
        BigDecimal profitRounded = profitPercentFromTotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal zScoreRounded = zScoreCurrent.subtract(zScoreEntry).setScale(2, RoundingMode.HALF_UP);

        BigDecimal minProfitRounded = profitRounded;
        BigDecimal maxProfitRounded = profitRounded;

        BigDecimal minZ = zScoreCurrent;
        BigDecimal maxZ = zScoreCurrent;

        BigDecimal minLong = longReturnPct;
        BigDecimal maxLong = longReturnPct;

        BigDecimal minShort = shortReturnPct;
        BigDecimal maxShort = shortReturnPct;

        BigDecimal minCorr = corrCurrent;
        BigDecimal maxCorr = corrCurrent;

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
}
