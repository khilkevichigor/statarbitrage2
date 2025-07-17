package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.common.model.TradeLog;
import com.example.statarbitrage.core.repositories.TradeLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeLogService {
    private final SettingsService settingsService;

    private final TradeLogRepository tradeLogRepository;

    public void updateTradeLog(PairData pairData, Settings settings) {
        String longTicker = pairData.getLongTicker();
        String shortTicker = pairData.getShortTicker();

        // ищем по паре
        Optional<TradeLog> optional = tradeLogRepository.findLatestByTickers(longTicker, shortTicker);

        TradeLog tradeLog = optional.orElseGet(TradeLog::new);
        tradeLog.setLongTicker(longTicker);
        tradeLog.setShortTicker(shortTicker);

        // мапим поля
        tradeLog.setCurrentProfitPercent(pairData.getProfitChanges());
        tradeLog.setMinProfitPercent(pairData.getMinProfitRounded());
        tradeLog.setMinProfitMinutes(pairData.getTimeInMinutesSinceEntryToMin() + "min");
        tradeLog.setMaxProfitPercent(pairData.getMaxProfitRounded());
        tradeLog.setMaxProfitMinutes(pairData.getTimeInMinutesSinceEntryToMax() + "min");

        tradeLog.setCurrentLongPercent(pairData.getLongChanges());
        tradeLog.setMinLongPercent(pairData.getMinLong());
        tradeLog.setMaxLongPercent(pairData.getMaxLong());

        tradeLog.setCurrentShortPercent(pairData.getShortChanges());
        tradeLog.setMinShortPercent(pairData.getMinShort());
        tradeLog.setMaxShortPercent(pairData.getMaxShort());

        tradeLog.setCurrentZ(pairData.getZScoreCurrent());
        tradeLog.setMinZ(pairData.getMinZ());
        tradeLog.setMaxZ(pairData.getMaxZ());

        tradeLog.setCurrentCorr(pairData.getCorrelationCurrent());
        tradeLog.setMinCorr(pairData.getMinCorr());
        tradeLog.setMaxCorr(pairData.getMaxCorr());

        tradeLog.setExitTake(settings.getExitTake());
        tradeLog.setExitStop(settings.getExitStop());
        tradeLog.setExitZMin(settings.getExitZMin());
        tradeLog.setExitZMax(settings.getExitZMaxPercent());
        tradeLog.setExitTimeHours(settings.getExitTimeHours());

        tradeLog.setExitReason(pairData.getExitReason());

        long entryMillis = pairData.getEntryTime(); // long, например 1721511983000
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        // Преобразуем в LocalDateTime через Instant
        String formattedEntryTime = Instant.ofEpochMilli(entryMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
                .format(formatter);

        tradeLog.setEntryTime(formattedEntryTime);
        tradeLog.setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        tradeLogRepository.save(tradeLog);
    }

    public BigDecimal getSumRealizedProfit() {
        return tradeLogRepository.getSumRealizedProfit();
    }

    public List<TradeLog> getAll() {
        return tradeLogRepository.findAll();
    }
}
