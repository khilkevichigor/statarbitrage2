package com.example.statarbitrage.services;

import com.example.statarbitrage.events.ResetProfitEvent;
import com.example.statarbitrage.model.ChangesData;
import com.example.statarbitrage.model.EntryData;
import com.example.statarbitrage.model.Settings;
import com.example.statarbitrage.utils.EntryDataUtil;
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
    private long entryTime = -1;
    private long maxProfitTime = -1;
    private long minProfitTime = -1;

    //todo получать эвент из TelegramBot на FIND что бы сбросить параметры профита
    @EventListener
    public void onResetProfitEvent(ResetProfitEvent event) {
        maxProfit = null;
        minProfit = null;
        entryTime = -1;
        maxProfitTime = -1;
        minProfitTime = -1;
    }

    public ChangesData calculateChanges(EntryData entryData, boolean isLasb) {
        Settings settings = settingsService.getSettings();

        double capitalLong = settings.getCapitalLong();
        double capitalShort = settings.getCapitalShort();
        double leverage = settings.getLeverage();
        double feePctPerTrade = settings.getFeePctPerTrade();

        BigDecimal aEntry = BigDecimal.valueOf(entryData.getATickerEntryPrice());
        BigDecimal aCurrent = BigDecimal.valueOf(entryData.getATickerCurrentPrice());
        BigDecimal bEntry = BigDecimal.valueOf(entryData.getBTickerEntryPrice());
        BigDecimal bCurrent = BigDecimal.valueOf(entryData.getBTickerCurrentPrice());
        BigDecimal zScoreEntry = BigDecimal.valueOf(entryData.getZScoreEntry());
        BigDecimal zScoreCurrent = BigDecimal.valueOf(entryData.getZScoreCurrent());

        BigDecimal aReturnPct;
        BigDecimal bReturnPct;

        if (isLasb) {
            // A - long, B - short
            aReturnPct = aCurrent.subtract(aEntry)
                    .divide(aEntry, 10, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            bReturnPct = bEntry.subtract(bCurrent) // шорт: считаем наоборот
                    .divide(bEntry, 10, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        } else {
            // A - short, B - long
            aReturnPct = aEntry.subtract(aCurrent) // шорт: считаем наоборот
                    .divide(aEntry, 10, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            bReturnPct = bCurrent.subtract(bEntry)
                    .divide(bEntry, 10, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        BigDecimal capitalLongBD = BigDecimal.valueOf(capitalLong);
        BigDecimal capitalShortBD = BigDecimal.valueOf(capitalShort);
        BigDecimal totalCapital = capitalLongBD.add(capitalShortBD);

        // Учитываем плечо
        BigDecimal leverageBD = BigDecimal.valueOf(leverage);
        BigDecimal effectiveLongCapital = capitalLongBD.multiply(leverageBD);
        BigDecimal effectiveShortCapital = capitalShortBD.multiply(leverageBD);

        BigDecimal longPL = aReturnPct.multiply(effectiveLongCapital)
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);

        BigDecimal shortPL = bReturnPct.multiply(effectiveShortCapital)
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

        BigDecimal aReturnRounded = aReturnPct.setScale(2, RoundingMode.HALF_UP);
        BigDecimal bReturnRounded = bReturnPct.setScale(2, RoundingMode.HALF_UP);
        BigDecimal profitRounded = profitPercentFromTotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal zScoreRounded = zScoreCurrent.subtract(zScoreEntry).setScale(2, RoundingMode.HALF_UP);

        String profitStr = profitRounded + "%";
        StringBuilder sb1 = new StringBuilder();
        sb1.append("Profit: ").append(profitStr).append("\n");
        sb1.append("LONG ")
                .append("(").append(EntryDataUtil.getLongTickerEntryPrice(entryData, isLasb)).append(")")
                .append(" -> ")
                .append(aReturnRounded)
                .append("%")
                .append(" -> ")
                .append("(").append(EntryDataUtil.getLongTickerCurrentPrice(entryData, isLasb)).append(")")
                .append("\n");
        sb1.append("SHORT ")
                .append("(").append(EntryDataUtil.getShortTickerEntryPrice(entryData, isLasb)).append(")")
                .append(" -> ")
                .append(bReturnRounded).append("%")
                .append(" -> ")
                .append("(").append(EntryDataUtil.getShortTickerCurrentPrice(entryData, isLasb)).append(")")
                .append("\n");
        sb1.append("Z ")
                .append("(").append(zScoreEntry).append(")")
                .append(" -> ")
                .append(zScoreRounded).append("%")
                .append(" -> ")
                .append("(").append(zScoreCurrent).append(")")
                .append("\n");

        String longLogMessage = String.format(
                "📊 LONG %s: Entry: %s, Current: %s, Changes: %s%%",
                EntryDataUtil.getLongTicker(entryData, isLasb),
                EntryDataUtil.getLongTickerEntryPrice(entryData, isLasb),
                EntryDataUtil.getLongTickerCurrentPrice(entryData, isLasb),
                EntryDataUtil.getLongReturnRounded(isLasb, aReturnRounded, bReturnRounded)
        );
        log.info(longLogMessage);

        String shortLogMessage = String.format(
                "📉 SHORT %s: Entry: %s, Current: %s, Changes: %s%%",
                EntryDataUtil.getShortTicker(entryData, isLasb),
                EntryDataUtil.getShortTickerEntryPrice(entryData, isLasb),
                EntryDataUtil.getShortTickerCurrentPrice(entryData, isLasb),
                EntryDataUtil.getShortReturnRounded(isLasb, aReturnRounded, bReturnRounded)
        );
        log.info(shortLogMessage);

        String zLogMessage = String.format(
                "📊 Z : Entry: %s, Current: %s, Changes: %s%%",
                entryData.getZScoreEntry(),
                entryData.getZScoreCurrent(),
                zScoreRounded
        );
        log.info(zLogMessage);

        String profitLogMessage = String.format("💰Профит (плечо %.1fx, комиссия %.2f%%) от капитала %.2f$: %s", leverage, feePctPerTrade, totalCapital, profitStr);
        log.info(profitLogMessage);

        // 📝 Логируем максимум, минимум и время
        String minMaxProfitLogMessage = String.format(
                "📈 Максимальный профит: %s%%, минимальный: %s%%",
                maxProfit.setScale(2, RoundingMode.HALF_UP),
                minProfit.setScale(2, RoundingMode.HALF_UP)
        );
        log.info(minMaxProfitLogMessage);

        String timeToProfitLogMessage = String.format(
                "⏱ Время до максимального профита: %d минут, до минимального: %d минут",
                timeSinceEntryToMax,
                timeSinceEntryToMin
        );
        log.info(timeToProfitLogMessage);

        StringBuilder sb2 = new StringBuilder();
        sb2.append(longLogMessage).append("\n");
        sb2.append(shortLogMessage).append("\n");
        sb2.append(zLogMessage).append("\n");
        sb2.append(profitLogMessage).append("\n");
        sb2.append(minMaxProfitLogMessage).append("\n");
        sb2.append(timeToProfitLogMessage).append("\n");

        return ChangesData.builder()
                .totalCapital(totalCapital)

                .longReturnRounded(EntryDataUtil.getLongReturnRounded(isLasb, aReturnRounded, bReturnRounded))
                .shortReturnRounded(EntryDataUtil.getShortReturnRounded(isLasb, aReturnRounded, bReturnRounded))

                .profitRounded(profitRounded)
                .profitStr(profitStr)

                .zScoreRounded(zScoreRounded)
                .zScoreStr(zScoreRounded + "%")

                .chartProfitMessage(sb1.toString())
                .logMessage(sb2.toString())
                .build();
    }
}
