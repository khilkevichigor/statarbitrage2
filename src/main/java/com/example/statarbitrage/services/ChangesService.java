package com.example.statarbitrage.services;

import com.example.statarbitrage.model.ChangesData;
import com.example.statarbitrage.model.EntryData;
import com.example.statarbitrage.model.Settings;
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

    private BigDecimal maxProfit = null;
    private BigDecimal minProfit = null;
    private long entryTime = -1;
    private long maxProfitTime = -1;
    private long minProfitTime = -1;

    public ChangesData calculateChanges(EntryData entryData) {
        Settings settings = settingsService.getSettings();

        double capitalLong = settings.getCapitalLong();
        double capitalShort = settings.getCapitalShort();
        double leverage = settings.getLeverage();
        double feePctPerTrade = settings.getFeePctPerTrade();

        BigDecimal longEntry = BigDecimal.valueOf(entryData.getLongTickerEntryPrice());
        BigDecimal longCurrent = BigDecimal.valueOf(entryData.getLongTickerCurrentPrice());
        BigDecimal shortEntry = BigDecimal.valueOf(entryData.getShortTickerEntryPrice());
        BigDecimal shortCurrent = BigDecimal.valueOf(entryData.getShortTickerCurrentPrice());
        BigDecimal zScoreEntry = BigDecimal.valueOf(entryData.getZScoreEntry());
        BigDecimal zScoreCurrent = BigDecimal.valueOf(entryData.getZScoreCurrent());

        BigDecimal longReturnPct = longCurrent.subtract(longEntry)
                .divide(longEntry, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        BigDecimal shortReturnPct = shortEntry.subtract(shortCurrent)
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

        long timeSinceEntryToMax = (maxProfitTime - entryTime) / (1000 * 60); // в секундах
        long timeSinceEntryToMin = (minProfitTime - entryTime) / (1000 * 60);

        BigDecimal longReturnRounded = longReturnPct.setScale(2, RoundingMode.HALF_UP);
        BigDecimal shortReturnRounded = shortReturnPct.setScale(2, RoundingMode.HALF_UP);
        BigDecimal profitRounded = profitPercentFromTotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal zScoreRounded = zScoreCurrent.subtract(zScoreEntry).setScale(2, RoundingMode.HALF_UP);

        String profitStr = profitRounded + "%";
        StringBuilder sb = new StringBuilder();
        sb.append("Profit: ").append(profitStr).append("\n");
        sb.append("LONG ")
                .append("(").append(entryData.getLongTickerEntryPrice()).append(")")
                .append(" -> ")
                .append(longReturnRounded)
                .append("%")
                .append(" -> ")
                .append("(").append(entryData.getLongTickerCurrentPrice()).append(")")
                .append("\n");
        sb.append("SHORT ")
                .append("(").append(entryData.getShortTickerEntryPrice()).append(")")
                .append(" -> ")
                .append(shortReturnRounded).append("%")
                .append(" -> ")
                .append("(").append(entryData.getShortTickerCurrentPrice()).append(")")
                .append("\n");
        sb.append("Z ")
                .append("(").append(zScoreEntry).append(")")
                .append(" -> ")
                .append(zScoreRounded).append("%")
                .append(" -> ")
                .append("(").append(zScoreCurrent).append(")")
                .append("\n");

        log.info("📊 LONG {{}}: Entry: {}, Current: {}, Changes: {}%",
                entryData.getLongticker(), entryData.getLongTickerEntryPrice(), entryData.getLongTickerCurrentPrice(), longReturnRounded);
        log.info("📊 SHORT {{}}: Entry: {}, Current: {}, Changes: {}%",
                entryData.getShortticker(), entryData.getShortTickerEntryPrice(), entryData.getShortTickerCurrentPrice(), shortReturnRounded);
        log.info("📊 Z : Entry: {}, Current: {}, Changes: {}%",
                entryData.getZScoreEntry(), entryData.getZScoreCurrent(), zScoreRounded);

        String logMsg = String.format("💰Профит (плечо %.1fx, комиссия %.2f%%) от капитала %.2f$: %s", leverage, feePctPerTrade, totalCapital, profitStr);
        log.info(logMsg);

        // 📝 Логируем максимум, минимум и время
        log.info("📈 Максимальный профит: {}%, минимальный: {}%", maxProfit.setScale(2, RoundingMode.HALF_UP), minProfit.setScale(2, RoundingMode.HALF_UP));
        log.info("⏱ Время до максимального профита: {} минут, до минимального: {} минут", timeSinceEntryToMax, timeSinceEntryToMin);

        return ChangesData.builder()
                .totalCapital(totalCapital)

                .longReturnRounded(longReturnRounded)
                .shortReturnRounded(shortReturnRounded)

                .profitRounded(profitRounded)
                .profitStr(profitStr)

                .zScoreRounded(zScoreRounded)
                .zScoreStr(zScoreRounded + "%")

                .chartProfitMessage(sb.toString())
                .logMessage(logMsg)
                .build();
    }
}
