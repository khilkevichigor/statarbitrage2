package com.example.statarbitrage.services;

import com.example.statarbitrage.events.ResetProfitEvent;
import com.example.statarbitrage.model.ChangesData;
import com.example.statarbitrage.model.PairData;
import com.example.statarbitrage.model.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChangesService {
    private final SettingsService settingsService;

    private BigDecimal maxProfit = null;
    private BigDecimal minProfit = null;
    private BigDecimal maxZ = null;
    private BigDecimal minZ = null;
    private BigDecimal maxLong = null;
    private BigDecimal minLong = null;
    private BigDecimal maxShort = null;
    private BigDecimal minShort = null;
    private BigDecimal maxCorr = null;
    private BigDecimal minCorr = null;
    private long entryTime = -1;
    private long maxProfitTime = -1;
    private long minProfitTime = -1;

    @EventListener
    public void onResetProfitEvent(ResetProfitEvent event) {
        resetToDefault();
    }

    private void resetToDefault() {
        maxProfit = null;
        minProfit = null;
        entryTime = -1;
        maxProfitTime = -1;
        minProfitTime = -1;

        maxZ = null;
        minZ = null;
        maxLong = null;
        minLong = null;
        maxShort = null;
        minShort = null;
        maxCorr = null;
        minCorr = null;
    }

    public ChangesData calculate(PairData pairData) {
        Settings settings = settingsService.getSettingsFromJson();

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
        BigDecimal corrEntry = BigDecimal.valueOf(pairData.getCorrelationEntry());
        BigDecimal corrCurrent = BigDecimal.valueOf(pairData.getCorrelationCurrent());

        BigDecimal longReturnPct = longCurrent.subtract(longEntry)
                .divide(longEntry, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        BigDecimal shortReturnPct = shortEntry.subtract(shortCurrent) // шорт: считаем наоборот
                .divide(shortEntry, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        BigDecimal capitalLongBD = BigDecimal.valueOf(capitalLong);
        BigDecimal capitalShortBD = BigDecimal.valueOf(capitalShort);
        BigDecimal totalCapital = capitalLongBD.add(capitalShortBD);

        // Учитываем плечо
        BigDecimal leverageBD = BigDecimal.valueOf(leverage);
        BigDecimal effectiveLongCapital = capitalLongBD.multiply(leverageBD);
        BigDecimal effectiveShortCapital = capitalShortBD.multiply(leverageBD);

        BigDecimal longPL = longReturnPct.multiply(effectiveLongCapital)
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);

        BigDecimal shortPL = shortReturnPct.multiply(effectiveShortCapital)
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);

        BigDecimal totalPL = longPL.add(shortPL);

        // Учёт комиссий: комиссия взимается дважды (вход и выход)
        BigDecimal feePct = BigDecimal.valueOf(feePctPerTrade);
        BigDecimal totalFees = effectiveLongCapital.add(effectiveShortCapital)
                .multiply(feePct)
                .multiply(BigDecimal.valueOf(2)) // вход и выход
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);

        BigDecimal netPL = totalPL.subtract(totalFees);

        BigDecimal profitPercentFromTotal = netPL
                .divide(totalCapital, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        // === 💾 Сохраняем entryTime при первом вызове
        if (entryTime == -1) {
            entryTime = System.currentTimeMillis();
            maxProfit = profitPercentFromTotal;
            minProfit = profitPercentFromTotal;
            maxProfitTime = entryTime;
            minProfitTime = entryTime;
        }

        // === 🔄 Обновляем максимум и минимум
        if (profitPercentFromTotal.compareTo(maxProfit) > 0) {
            maxProfit = profitPercentFromTotal;
            maxProfitTime = System.currentTimeMillis();
        }

        if (profitPercentFromTotal.compareTo(minProfit) < 0) {
            minProfit = profitPercentFromTotal;
            minProfitTime = System.currentTimeMillis();
        }

        if (maxZ == null || zScoreCurrent.compareTo(maxZ) > 0) {
            maxZ = zScoreCurrent;
        }
        if (minZ == null || zScoreCurrent.compareTo(minZ) < 0) {
            minZ = zScoreCurrent;
        }
        if (maxLong == null || longReturnPct.compareTo(maxLong) > 0) {
            maxLong = longReturnPct;
        }
        if (minLong == null || longReturnPct.compareTo(minLong) < 0) {
            minLong = longReturnPct;
        }
        if (maxShort == null || shortReturnPct.compareTo(maxShort) > 0) {
            maxShort = shortReturnPct;
        }
        if (minShort == null || shortReturnPct.compareTo(minShort) < 0) {
            minShort = shortReturnPct;
        }
        if (maxCorr == null || corrCurrent.compareTo(maxCorr) > 0) {
            maxCorr = corrCurrent;
        }
        if (minCorr == null || corrCurrent.compareTo(minCorr) < 0) {
            minCorr = corrCurrent;
        }

        long timeInMinutesSinceEntryToMax = (maxProfitTime - entryTime) / (1000 * 60); // в минутах
        long timeInMinutesSinceEntryToMin = (minProfitTime - entryTime) / (1000 * 60);

        BigDecimal longReturnRounded = longReturnPct.setScale(2, RoundingMode.HALF_UP);
        BigDecimal shortReturnRounded = shortReturnPct.setScale(2, RoundingMode.HALF_UP);
        BigDecimal profitRounded = profitPercentFromTotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal zScoreRounded = zScoreCurrent.subtract(zScoreEntry).setScale(2, RoundingMode.HALF_UP);

        String longLogMessage = String.format(
                "📊 LONG %s: Entry: %s, Current: %s, Changes: %s%%",
                pairData.getLongTicker(),
                pairData.getLongTickerEntryPrice(),
                pairData.getLongTickerCurrentPrice(),
                longReturnRounded
        );
        log.info(longLogMessage);

        String shortLogMessage = String.format(
                "📉 SHORT %s: Entry: %s, Current: %s, Changes: %s%%",
                pairData.getShortTicker(),
                pairData.getShortTickerEntryPrice(),
                pairData.getShortTickerCurrentPrice(),
                shortReturnRounded
        );
        log.info(shortLogMessage);

        String zLogMessage = String.format(
                "📊 Z : Entry: %s, Current: %s, Changes: %s%%",
                pairData.getZScoreEntry(),
                pairData.getZScoreCurrent(),
                zScoreRounded
        );
        log.info(zLogMessage);

        String profitLogMessage = String.format("💰Профит (плечо %.1fx, комиссия %.2f%%) от капитала %.2f$: %s%%", leverage, feePctPerTrade, totalCapital, profitRounded);
        log.info(profitLogMessage);

        // 📝 Логируем максимум, минимум и время
        BigDecimal maxProfitRounded = maxProfit.setScale(2, RoundingMode.HALF_UP);
        BigDecimal minProfitRounded = minProfit.setScale(2, RoundingMode.HALF_UP);
        String minMaxProfitLogMessage = String.format(
                "📈 Максимальный профит: %s%%, минимальный: %s%%",
                maxProfitRounded,
                minProfitRounded
        );
        log.info(minMaxProfitLogMessage);

        String timeToProfitLogMessage = String.format(
                "⏱ Время до максимального профита: %d минут, до минимального: %d минут",
                timeInMinutesSinceEntryToMax,
                timeInMinutesSinceEntryToMin
        );
        log.info(timeToProfitLogMessage);

        log.info(String.format("📊 Z max/min: %s / %s", maxZ.setScale(2, RoundingMode.HALF_UP), minZ.setScale(2, RoundingMode.HALF_UP)));
        log.info(String.format("📈 Long max/min: %s / %s", maxLong.setScale(2, RoundingMode.HALF_UP), minLong.setScale(2, RoundingMode.HALF_UP)));
        log.info(String.format("📉 Short max/min: %s / %s", maxShort.setScale(2, RoundingMode.HALF_UP), minShort.setScale(2, RoundingMode.HALF_UP)));
        log.info(String.format("📉 Corr max/min: %s / %s", maxCorr.setScale(2, RoundingMode.HALF_UP), minCorr.setScale(2, RoundingMode.HALF_UP)));

        return ChangesData.builder()

                .longReturnRounded(longReturnRounded)
                .shortReturnRounded(shortReturnRounded)

                .profitRounded(profitRounded)

                .minProfitRounded(minProfitRounded)
                .maxProfitRounded(maxProfitRounded)

                .zScoreRounded(zScoreRounded)

                .timeInMinutesSinceEntryToMax(timeInMinutesSinceEntryToMax)
                .timeInMinutesSinceEntryToMin(timeInMinutesSinceEntryToMin)

                .minZ(minZ)
                .maxZ(maxZ)

                .minLong(minLong)
                .maxLong(maxLong)

                .minShort(minShort)
                .maxShort(maxShort)

                .minCorr(minCorr)
                .maxCorr(maxCorr)

                .build();
    }
}
