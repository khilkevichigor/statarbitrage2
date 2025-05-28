package com.example.statarbitrage.services;

import com.example.statarbitrage.model.EntryData;
import com.example.statarbitrage.model.ProfitData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfitService {
    private FileService fileService;

    public ProfitData calculateAndSetProfit(
            EntryData entryData,
            double capitalLong,
            double capitalShort,
            double leverage,
            double feePctPerTrade
    ) {
        BigDecimal longEntry = BigDecimal.valueOf(entryData.getLongTickerEntryPrice());
        BigDecimal longCurrent = BigDecimal.valueOf(entryData.getLongTickerCurrentPrice());
        BigDecimal shortEntry = BigDecimal.valueOf(entryData.getShortTickerEntryPrice());
        BigDecimal shortCurrent = BigDecimal.valueOf(entryData.getShortTickerCurrentPrice());

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

        BigDecimal longReturnRounded = longReturnPct.setScale(2, RoundingMode.HALF_UP);
        BigDecimal shortReturnRounded = shortReturnPct.setScale(2, RoundingMode.HALF_UP);
        BigDecimal profitRounded = profitPercentFromTotal.setScale(2, RoundingMode.HALF_UP);

        String profitStr = profitRounded + "%";
        entryData.setProfit(profitStr);

        fileService.writeEntryDataToJson(Collections.singletonList(entryData));

        log.info("📊 LONG {{}}: Entry: {}, Current: {}, Profit: {}%",
                entryData.getLongticker(), entryData.getLongTickerEntryPrice(), entryData.getLongTickerCurrentPrice(), longReturnRounded);
        log.info("📊 SHORT {{}}: Entry: {}, Current: {}, Profit: {}%",
                entryData.getShortticker(), entryData.getShortTickerEntryPrice(), entryData.getShortTickerCurrentPrice(), shortReturnRounded);

        String logMsg = String.format("💰Профит (леверидж %.1fx, комиссия %.2f%%) от капитала %.2f$: %s", leverage, feePctPerTrade, totalCapital, profitStr);
        log.info(logMsg);

        return ProfitData.builder()
                .totalCapital(totalCapital)
                .longReturnRounded(longReturnRounded)
                .shortReturnRounded(shortReturnRounded)
                .profitRounded(profitRounded)
                .profitStr(profitStr)
                .logMessage(logMsg)
                .build();
    }

}
